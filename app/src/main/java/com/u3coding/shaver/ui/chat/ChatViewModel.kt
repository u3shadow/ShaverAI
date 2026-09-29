package com.u3coding.shaver.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u3coding.shaver.action.executor.ActionExecutor
import com.u3coding.shaver.action.executor.BackendActionMapper
import com.u3coding.shaver.action.model.Action
import com.u3coding.shaver.action.parser.BackendRuleDraftMapper
import com.u3coding.shaver.action.rule.RuleRunResult
import com.u3coding.shaver.data.remote.AgentChatRequest
import com.u3coding.shaver.data.remote.AgentChatResponse
import com.u3coding.shaver.data.remote.BackendActionPlan
import com.u3coding.shaver.data.remote.BackendApiProvider
import com.u3coding.shaver.data.remote.DeviceCallbackRequest
import com.u3coding.shaver.data.remote.DeviceContext
import com.u3coding.shaver.data.remote.DeviceEventRequest
import com.u3coding.shaver.data.remote.RuleCreateRequest
import com.u3coding.shaver.data.repository.BackendRepository
import com.u3coding.shaver.data.repository.BackendUserIdStore
import com.u3coding.shaver.model.MessageStatus
import com.u3coding.shaver.model.Role
import com.u3coding.shaver.model.UiMessage
import com.u3coding.shaver.ui.permission.DevicePermission
import com.u3coding.shaver.ui.permission.DevicePermissionPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext

class ChatViewModel(
    private val executor: ActionExecutor,
    context: Context
) : ViewModel() {
    private val appContext = context.applicationContext
    private val backend = BackendRepository(BackendApiProvider.api)
    private val userId = BackendUserIdStore(appContext).getOrCreate()
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    // Keep permission and confirmation events while Activity is briefly stopped.
    private val _events = Channel<ChatUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var currentChatJob: Job? = null
    private var pendingRule: RuleCreateRequest? = null
    private var currentWifiSsid: String? = null
    private var actionVersion = 0L
    private var pendingExecution: PendingExecution? = null
    private var pendingPermission: DevicePermission? = null

    fun canSendMessage(input: String): Boolean = input.isNotBlank()

    fun handleUserInput(input: String, wifiSsid: String?) {
        val message = input.trim()
        if (message.isEmpty()) return
        if (pendingExecution != null) {
            viewModelScope.launch {
                _events.send(ChatUiEvent.ShowSnackbar("请先完成或取消当前设备权限授权"))
            }
            return
        }
        currentChatJob?.cancel()
        pendingRule = null
        currentWifiSsid = wifiSsid
        appendMessage(Role.USER, message, MessageStatus.DONE)
        _uiState.update { it.copy(isGenerating = true, canSend = false, showStopButton = true) }

        currentChatJob = viewModelScope.launch {
            val runningJob = currentCoroutineContext()[Job]
            try {
                val reply = backend.chat(
                    AgentChatRequest(
                        user_id = userId,
                        message = message,
                        device_context = DeviceContext(wifi = wifiSsid)
                    )
                )
                val successful = handleChatResponse(reply.value, reply.requestId, wifiSsid)
                if (successful != null) _events.send(ChatUiEvent.RequestFinished(successful))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showError(error.message ?: "服务器请求失败")
                _events.send(ChatUiEvent.RequestFinished(false))
            } finally {
                if (currentChatJob === runningJob) {
                    _uiState.update {
                        it.copy(isGenerating = false, canSend = true, showStopButton = false)
                    }
                }
            }
        }
    }

    private suspend fun handleChatResponse(
        response: AgentChatResponse,
        requestId: String,
        wifiSsid: String?
    ): Boolean? {
        when (response.type) {
            "action_plan" -> {
                val plan = response.action_plan
                    ?: throw IllegalStateException("服务器未返回动作计划")
                if (!plan.need_android_execute || plan.execution_target != "android" ||
                    plan.requires_confirmation
                ) {
                    throw IllegalStateException("该计划不能由 Android 直接执行")
                }
                return executeOrRequestPermission(
                    PendingExecution(plan, requestId, wifiSsid, PlanSource.CHAT, response.answer)
                )
            }
            "confirmation_required" -> {
                pendingRule = BackendRuleDraftMapper.fromResponse(response, userId)
                appendMessage(Role.ASSISTANT, response.answer, MessageStatus.DONE)
                if (pendingRule != null) {
                    _events.send(ChatUiEvent.ConfirmRule(pendingRule!!.name))
                } else {
                    _events.send(ChatUiEvent.ShowSnackbar("此计划暂不支持在 Android 端确认"))
                }
                return true
            }
            "error" -> {
                showError(response.answer.ifBlank { response.errors.joinToString("；") })
                return false
            }
            else -> {
                appendMessage(Role.ASSISTANT, response.answer, MessageStatus.DONE)
                return true
            }
        }
    }

    fun confirmPendingRule() {
        val draft = pendingRule ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, canSend = false) }
            try {
                val created = backend.createRule(draft).value
                pendingRule = null
                appendMessage(Role.ASSISTANT, "规则已保存：${created.name} (${created.rule_id})", MessageStatus.DONE)
                _events.send(ChatUiEvent.RequestFinished(true))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showError(error.message ?: "规则保存失败")
                _events.send(ChatUiEvent.RequestFinished(false))
            } finally {
                _uiState.update { it.copy(isGenerating = false, canSend = true) }
            }
        }
    }

    fun discardPendingRule() {
        pendingRule = null
        appendMessage(Role.ASSISTANT, "已取消保存规则", MessageStatus.DONE)
    }

    fun onSystemEvent(event: String, wifiSsid: String?) {
        currentWifiSsid = wifiSsid
        appendMessage(
            Role.SYSTEM,
            if (event == "wifi_changed") "检测到 Wi-Fi：${wifiSsid ?: "未知"}"
            else "检测到 Wi-Fi 断开：${wifiSsid ?: "未知"}",
            MessageStatus.DONE
        )
        if (event != "wifi_changed" || wifiSsid.isNullOrBlank()) return

        viewModelScope.launch {
            val reply = try {
                backend.reportEvent(
                    DeviceEventRequest(
                        user_id = userId,
                        params = mapOf("ssid" to wifiSsid)
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Backend event request failed; using local rules", error)
                _events.send(ChatUiEvent.ShowSnackbar("服务器不可用，尝试本地场景"))
                _events.send(ChatUiEvent.UseLocalConnectedRule(wifiSsid))
                return@launch
            }

            if (!reply.value.matched) {
                _events.send(ChatUiEvent.UseLocalConnectedRule(wifiSsid))
                return@launch
            }

            try {
                val plan = reply.value.action_plan
                    ?: throw IllegalStateException("规则已命中但服务器未返回动作计划")
                if (!plan.need_android_execute || plan.execution_target != "android" ||
                    plan.requires_confirmation
                ) {
                    throw IllegalStateException("规则动作不能由 Android 直接执行")
                }
                val successful = executeOrRequestPermission(
                    PendingExecution(plan, reply.requestId, wifiSsid, PlanSource.DEVICE_EVENT)
                )
                if (successful != null) _events.send(ChatUiEvent.RequestFinished(successful))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Matched backend rule could not be executed", error)
                showError(error.message ?: "服务器规则执行失败")
                _events.send(ChatUiEvent.RequestFinished(false))
            }
        }
    }

    private suspend fun executeOrRequestPermission(pending: PendingExecution): Boolean? {
        require(pending.plan.actions.isNotEmpty()) { "服务器返回空动作计划" }
        // Validate every action and every permission before changing any device state.
        val actions = pending.plan.actions.map {
            BackendActionMapper.toLocalAction(it, pending.wifiSsid)
        }
        val missing = DevicePermissionPolicy.firstMissing(
            actions = actions,
            canWriteSettings = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                Settings.System.canWrite(appContext),
            canConnectBluetooth = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    appContext, Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
        )
        if (missing != null) {
            pendingExecution = pending
            pendingPermission = missing
            _events.send(ChatUiEvent.RequestDevicePermission(missing))
            return null
        }

        pendingExecution = null
        pendingPermission = null
        val errors = executePlan(pending.plan, pending.requestId, pending.wifiSsid)
        if (pending.source == PlanSource.CHAT) {
            appendMessage(
                Role.ASSISTANT,
                if (errors.isEmpty()) pending.answer else "部分动作执行失败：${errors.joinToString("；")}",
                if (errors.isEmpty()) MessageStatus.DONE else MessageStatus.ERROR
            )
        } else if (errors.isNotEmpty()) {
            showError(errors.joinToString("；"))
        }
        return errors.isEmpty()
    }

    fun onDevicePermissionResult(permission: DevicePermission, granted: Boolean) {
        val pending = pendingExecution ?: return
        if (permission != pendingPermission) return
        viewModelScope.launch {
            if (!granted) {
                pendingExecution = null
                pendingPermission = null
                val reason = when (permission) {
                    DevicePermission.WRITE_SETTINGS -> "未授予修改系统设置权限，设备计划未执行"
                    DevicePermission.BLUETOOTH_CONNECT -> "未授予蓝牙连接权限，设备计划未执行"
                }
                showError(reason)
                _events.send(ChatUiEvent.RequestFinished(false))
                reportUnexecutedActions(pending, reason)
                return@launch
            }
            try {
                val successful = executeOrRequestPermission(pending)
                if (successful != null) _events.send(ChatUiEvent.RequestFinished(successful))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                pendingExecution = null
                pendingPermission = null
                showError(error.message ?: "设备计划执行失败")
                _events.send(ChatUiEvent.RequestFinished(false))
            }
        }
    }

    private suspend fun reportUnexecutedActions(pending: PendingExecution, reason: String) {
        if (pending.requestId.isBlank()) return
        pending.plan.actions.forEach { item ->
            try {
                backend.reportCallback(
                    DeviceCallbackRequest(
                        request_id = pending.requestId,
                        action = item.action,
                        status = "failed",
                        error = reason
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Could not report denied plan ${pending.requestId}", error)
            }
        }
    }

    private suspend fun executePlan(
        plan: BackendActionPlan,
        requestId: String,
        wifiSsid: String?
    ): List<String> {
        require(plan.actions.isNotEmpty()) { "服务器返回空动作计划" }
        // Validate the whole plan before touching device state.
        val actions = plan.actions.map { BackendActionMapper.toLocalAction(it, wifiSsid) }
        val successfulActions = mutableListOf<Action>()
        val errors = mutableListOf<String>()

        plan.actions.zip(actions).forEach { (item, action) ->
            var failure: String? = null
            try {
                checkPermission(action)
                val executed = withContext(Dispatchers.IO) { executor.execute(action) }
                if (!executed) failure = "${item.action} 未能执行"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failure = "${item.action}：${error.message ?: "执行失败"}"
            }

            if (failure == null) successfulActions += action else errors += failure!!
            if (requestId.isNotBlank()) {
                try {
                    backend.reportCallback(
                        DeviceCallbackRequest(
                            request_id = requestId,
                            action = item.action,
                            status = if (failure == null) "success" else "failed",
                            result = if (failure == null) item.params else emptyMap(),
                            error = failure
                        )
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Device callback failed for $requestId", error)
                    _events.send(ChatUiEvent.ShowSnackbar("动作回调上报失败，可用 request_id=$requestId 排查"))
                }
            }
        }

        if (successfulActions.isNotEmpty()) onActionsExecuted(successfulActions)
        return errors
    }

    private fun checkPermission(action: Action) {
        if (action.operation == "set_brightness" &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.System.canWrite(appContext)
        ) {
            throw SecurityException("未授予修改系统设置权限")
        }
        if ((action.operation == "open_bluetooth" || action.operation == "close_bluetooth") &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("未授予蓝牙连接权限")
        }
    }

    fun onActionsExecuted(actions: List<Action>) {
        actionVersion += 1
        _uiState.update {
            it.copy(lastSuccessfulActions = actions, actionUpdateVersion = actionVersion)
        }
    }

    fun onRuleRunResult(result: RuleRunResult) {
        val text = when (result) {
            is RuleRunResult.Success -> "本地场景执行成功"
            is RuleRunResult.NoRule -> "没有匹配的场景规则"
            is RuleRunResult.SkippedDuplicate -> "相同场景已执行，跳过"
            is RuleRunResult.Failed -> "本地场景执行失败：${result.reason}"
        }
        appendMessage(Role.SYSTEM, text, MessageStatus.DONE)
    }

    fun stopGenerating() {
        currentChatJob?.cancel()
        currentChatJob = null
        _uiState.update { it.copy(isGenerating = false, canSend = true, showStopButton = false) }
    }

    private fun appendMessage(role: Role, text: String, status: MessageStatus) {
        val message = UiMessage(
            id = "${System.currentTimeMillis()}-${System.nanoTime()}",
            role = role,
            content = text,
            wifiSsid = currentWifiSsid,
            status = status
        )
        _uiState.update { it.copy(messages = it.messages + message) }
    }

    private suspend fun showError(message: String) {
        appendMessage(Role.ASSISTANT, message, MessageStatus.ERROR)
        _events.send(ChatUiEvent.ShowSnackbar(message))
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}

private enum class PlanSource { CHAT, DEVICE_EVENT }

private data class PendingExecution(
    val plan: BackendActionPlan,
    val requestId: String,
    val wifiSsid: String?,
    val source: PlanSource,
    val answer: String = ""
)

data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val canSend: Boolean = true,
    val showStopButton: Boolean = false,
    val lastSuccessfulActions: List<Action> = emptyList(),
    val actionUpdateVersion: Long = 0L
)

sealed class ChatUiEvent {
    data class ShowToast(val message: String) : ChatUiEvent()
    data class RequestDevicePermission(val permission: DevicePermission) : ChatUiEvent()
    data class ShowSnackbar(val message: String) : ChatUiEvent()
    data class ConfirmRule(val name: String) : ChatUiEvent()
    data class UseLocalConnectedRule(val ssid: String) : ChatUiEvent()
    data class RequestFinished(val success: Boolean) : ChatUiEvent()
    data class Navigate(val route: String) : ChatUiEvent()
}
