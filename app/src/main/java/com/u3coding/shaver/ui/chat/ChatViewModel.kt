package com.u3coding.shaver.ui.chat

import android.app.Application
import android.content.Context
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u3coding.shaver.data.remote.ApiProvider
import com.u3coding.shaver.data.remote.ChatMessage
import com.u3coding.shaver.data.repository.ChatRepo
import com.u3coding.shaver.action.model.Action
import com.u3coding.shaver.action.model.ActionDTO
import com.u3coding.shaver.action.executor.ActionExecutor
import com.u3coding.shaver.action.agent.AgentTask
import com.u3coding.shaver.action.agent.AgentTaskScheduler
import com.u3coding.shaver.action.parser.InputClassifier
import com.u3coding.shaver.action.parser.InputType
import com.u3coding.shaver.action.model.ModelDecision
import com.u3coding.shaver.action.model.OperationList
import com.u3coding.shaver.action.prompt.PromptBuilder
import com.u3coding.shaver.action.rule.RuleRepo
import com.u3coding.shaver.action.rule.RuleRunResult
import com.u3coding.shaver.action.parser.ToolCallParser
import com.u3coding.shaver.model.CommandChatContextManager
import com.u3coding.shaver.model.IntentFeatureExtractor
import com.u3coding.shaver.model.LocalIntent
import com.u3coding.shaver.model.LocalIntentModelProvider
import com.u3coding.shaver.model.MessageStatus
import com.u3coding.shaver.model.NormalChatContextManager
import com.u3coding.shaver.model.Role
import com.u3coding.shaver.model.UiMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class ChatViewModel(val executor: ActionExecutor,val applicationContext: Context) : ViewModel() {
    private val localIntentModelProvider = LocalIntentModelProvider(applicationContext,
        IntentFeatureExtractor())
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<ChatUiEvent>()
    val events = _events.asSharedFlow()
    private var currentJob: Job? = null
    private var actionUpdateVersionCounter: Long = 0L
    private var currentRoundWifiSsid: String? = null
    private val normalContextManager = NormalChatContextManager()
    private val commandContextManager = CommandChatContextManager()
    private val repo = ChatRepo(ApiProvider.api)
    private val scheduler = AgentTaskScheduler(
        scope = viewModelScope,
        onChatTask = { task ->
            handleNormalChatInput(task.text, task.wifiSsid)
        },
        onCommandTask = { task ->
            handleCommandInput(task.text, task.wifiSsid)
        },
        onSystemEventTask = { task ->
            handleSystemEventTask(task)
        }
    )
    fun canSendMessage(
        input: String,
        hasBluetoothPermission: Boolean,
        hasWriteSettingsPermission: Boolean,
        hasWifiPermission: Boolean
    ): Boolean {
        val message = input.trim()
        if (message.isBlank()) {
            return false
        }
        if (requiresBluetoothPermission(message) && !hasBluetoothPermission) {
            emitEvent(ChatUiEvent.RequestBluetoothPermission("蓝牙操作需要蓝牙连接权限"))
            return false
        }
        if (!hasWriteSettingsPermission) {
            emitEvent(ChatUiEvent.ShowToast("未授予修改系统设置权限，亮度设置可能仅在应用内生效"))
            return false
        }
        if (!hasWifiPermission) {
            emitEvent(ChatUiEvent.ShowToast("未授予 Wi-Fi 读取权限"))
            return false
        }
        return true
    }

    fun requiresBluetoothPermission(input: String): Boolean {
        return input.contains(OPEN_BLUETOOTH_CMD, ignoreCase = true) ||
            input.contains(CLOSE_BLUETOOTH_CMD, ignoreCase = true)
    }

    fun handleUserInput(input: String, wifiSsid: String?) {
        val message = input.trim()
        if (message.isBlank()) return

        val inputType = resolveInputType(message)
        submitInputTask(inputType, message, wifiSsid)
    }

    private fun resolveInputType(message: String): InputType {
        val localResult = localIntentModelProvider.predict(message)

        if (localResult.confidence >= LOCAL_INTENT_CONFIDENCE_THRESHOLD) {
            return when (localResult.intent) {
                LocalIntent.NormalChat -> InputType.NormalChat
                LocalIntent.DeviceControl,
                LocalIntent.RuleGeneration -> InputType.CommandChat
            }
        }

        return InputClassifier.classify(message)
    }

    private fun submitInputTask(
        inputType: InputType,
        message: String,
        wifiSsid: String?
    ) {
        val task = when (inputType) {
            InputType.NormalChat -> AgentTask.ChatTask(
                text = message,
                wifiSsid = wifiSsid
            )

            InputType.CommandChat -> AgentTask.CommandTask(
                text = message,
                wifiSsid = wifiSsid
            )
        }

        scheduler.submit(task)
    }

    fun onSystemEvent(event: String, wifiSsid: String?) {
        scheduler.submit(
            AgentTask.SystemEventTask(
                event = event,
                wifiSsid = wifiSsid
            )
        )
    }
    private suspend fun handleSystemEventTask(task: AgentTask.SystemEventTask) {
        val content = when (task.event) {
            "wifi_changed" -> "检测到 Wi-Fi 变化：${task.wifiSsid ?: "未知 Wi-Fi"}"
            else -> "收到系统事件：${task.event}"
        }

        val newMessage = UiMessage(
            id = "system-${System.currentTimeMillis()}",
            role = Role.SYSTEM,
            content = content,
            wifiSsid = task.wifiSsid,
            status = MessageStatus.DONE
        )

        _uiState.update { old ->
            old.copy(messages = old.messages + newMessage)
        }
    }
    private suspend fun handleNormalChatInput(input: String, wifiSsid: String?) {
        val message = input.trim()
        if (message.isBlank()) {
            return
        }
        currentRoundWifiSsid = wifiSsid
        addUserMessage(message, wifiSsid)
        val history = getHistory(InputType.NormalChat, message, wifiSsid, emptyList())
        runStreamRequest(InputType.NormalChat, history)
    }

    private suspend  fun handleCommandInput(input: String, wifiSsid: String?) {
        val message = input.trim()
        if (message.isBlank()) {
            return
        }
        currentRoundWifiSsid = wifiSsid
        addUserMessage(message, wifiSsid)
        val history = getHistory(InputType.CommandChat, message, wifiSsid, emptyList())
        runStreamRequest(InputType.CommandChat, history)
    }

    private suspend fun runStreamRequest(inputType: InputType, history: List<ChatMessage>) {
        _uiState.update {
            it.copy(
                isGenerating = true,
                canSend = false,
                showStopButton = true
            )
        }
        var currentText = ""
        try {
            repo.streamChat(history).collect { token ->
                currentText += token
                updateLastAiMessage(currentText)
            }

            if (inputType == InputType.CommandChat) {
                val assistantText = handleModelDecision(currentText, currentRoundWifiSsid)
                if (assistantText.isNotBlank()) {
                    appendAssistantToContext(inputType, assistantText, currentRoundWifiSsid)
                }
            } else {
                markLastAiDone()
                appendAssistantToContext(inputType, currentText, currentRoundWifiSsid)
            }
        } catch (e: CancellationException) {
            markLastAiCancelled()
            throw e
        } catch (e: Exception) {
            markLastAiError("请求失败：${e.message ?: "unknown error"}")
            emitEvent(ChatUiEvent.ShowSnackbar("请求失败：${e.message ?: "unknown error"}"))
        } finally {
            _uiState.update {
                it.copy(
                    isGenerating = false,
                    canSend = true,
                    showStopButton = false
                )
            }
        }
    }

    private fun getHistory(
        inputType: InputType,
        input: String,
        wifiSsid: String?,
        history: List<ChatMessage>
    ): List<ChatMessage> {
        var history1 = history
        if (inputType == InputType.CommandChat) {
            commandContextManager.addMessage(
                UiMessage(
                    id = System.currentTimeMillis().toString(),
                    role = Role.USER,
                    content = input,
                    wifiSsid = wifiSsid,
                    status = MessageStatus.DONE
                )
            )
            history1 = buildRequestMessages(
                buildRequestUiMessages(
                    commandContextManager.getLastMessages(),
                    wifiSsid
                )
            )
        } else {
            //使用Input和wifiSsid构造一个临时的UiMessage，放在历史记录的最后面，来构造请求上下文
            normalContextManager.addMessage(
                UiMessage(
                    id = System.currentTimeMillis().toString(),
                    role = Role.USER,
                    content = input,
                    wifiSsid = wifiSsid,
                    status = MessageStatus.DONE
                )
            )
            history1 = buildRequestMessages(
                buildNormalChatRequestUiMessages(
                    normalContextManager.getLastMessages()
                )
            )
        }
        return history1
    }

    private fun handleParsedActionResult(result: ActionDTO) {
        if (result.operation == null) {
            // 没有解析出操作，直接返回
            return
        }
        val action = result.toAction()
        RuleRepo.addRule(action)
        // 这里直接执行动作，实际应用中可能需要用户确认
        executor.execute(action)
        actionUpdateVersionCounter += 1
        _uiState.update { old ->
            old.copy(
                lastSuccessfulActions = listOf(action),
                actionUpdateVersion = actionUpdateVersionCounter
            )
        }
    }

    private fun handleModelDecision(rawOutput: String, wifiSsid: String?): String {
        return when (val decision = ToolCallParser().parse(rawOutput)) {
            is ModelDecision.ChatReply -> {
                replaceLastAiMessage(
                    text = decision.content,
                    status = MessageStatus.DONE
                )
                decision.content
            }

            is ModelDecision.ToolCall -> {
                val error = validateToolCall(decision)
                if (error != null) {
                    markLastAiError(error)
                    emitEvent(ChatUiEvent.ShowSnackbar(error))
                    return ""
                }

                val action = decision.toAction(wifiSsid)
                executeAction(action)
                val reply = "已执行：${action.operation}"
                replaceLastAiMessage(
                    text = reply,
                    status = MessageStatus.DONE
                )
                reply
            }

            is ModelDecision.Invalid -> {
                val error = "解析结果不合法：${decision.reason}"
                markLastAiError(error)
                emitEvent(ChatUiEvent.ShowSnackbar(error))
                ""
            }
        }
    }

    private fun validateToolCall(decision: ModelDecision.ToolCall): String? {
        if (!OperationList.isValidOperation(decision.actionId)) {
            return "不支持的操作：${decision.actionId}"
        }
        if (decision.actionId == "set_volume" || decision.actionId == "set_brightness") {
            val value = decision.params["value"]
            if (value !is Number) {
                return "${decision.actionId} 缺少数值参数 value"
            }
        }
        return null
    }

    private fun ModelDecision.ToolCall.toAction(wifiSsid: String?): Action {
        return Action(
            trigger = wifiSsid.orEmpty(),
            operation = actionId,
            params = params
        )
    }

    private fun executeAction(action: Action) {
        RuleRepo.addRule(action)
        executor.execute(action)
        actionUpdateVersionCounter += 1
        _uiState.update { old ->
            old.copy(
                lastSuccessfulActions = listOf(action),
                actionUpdateVersion = actionUpdateVersionCounter
            )
        }
    }

    fun onActionsExecuted(actions: List<Action>) {
        actionUpdateVersionCounter += 1
        _uiState.update { old ->
            old.copy(
                lastSuccessfulActions = actions,
                actionUpdateVersion = actionUpdateVersionCounter
            )
        }
    }

    fun onRuleRunResult(result: RuleRunResult) {
        val content = when (result) {
            is RuleRunResult.Success -> "执行成功"
            is RuleRunResult.NoRule -> "未找到对应规则"
            is RuleRunResult.SkippedDuplicate -> "已经执行过相同规则，跳过"
            is RuleRunResult.Failed -> "执行失败，原因：${result.reason}"
        }
        val newMessage = UiMessage(
            id = "rule-${System.currentTimeMillis()}",
            role = Role.SYSTEM,
            content = content,
            wifiSsid = currentRoundWifiSsid,
            status = MessageStatus.DONE
        )
        _uiState.update { old ->
            old.copy(messages = old.messages + newMessage)
        }
    }

    private fun buildRequestUiMessages(uiMessages: List<UiMessage>, wifiSsid: String?): List<UiMessage> {
        if (wifiSsid.isNullOrBlank()) {
            return uiMessages
        }
        return PromptBuilder().buildToolCallingPrompt(uiMessages.last().content, wifiSsid, uiState.value.messages).let { systemPrompt ->
            listOf(UiMessage(
                id = "system-${System.currentTimeMillis()}",
                role = Role.SYSTEM,
                content = systemPrompt,
                wifiSsid = wifiSsid,
                status = MessageStatus.DONE
            )) + uiMessages
        }
    }

    private fun buildNormalChatRequestUiMessages(uiMessages: List<UiMessage>): List<UiMessage> {
        val capabilityPrompt = PromptBuilder().buildAppCapabilityPrompt()
        return listOf(
            UiMessage(
                id = "system-normal-${System.currentTimeMillis()}",
                role = Role.SYSTEM,
                content = capabilityPrompt,
                wifiSsid = currentRoundWifiSsid,
                status = MessageStatus.DONE
            )
        ) + uiMessages
    }

    fun stopGenerating() {
        scheduler.stopCurrentChat()
        _uiState.update {
            it.copy(
                isGenerating = false,
                canSend = true,
                showStopButton = false
            )
        }
    }

    private fun addUserMessage(input: String, wifiSsid: String?) {
        val newMessage = UiMessage(
            id = System.currentTimeMillis().toString(),
            role = Role.USER,
            content = input,
            wifiSsid = wifiSsid,
            status = MessageStatus.DONE
        )
        _uiState.update { old ->
            old.copy(messages = old.messages + newMessage)
        }
    }

    private fun updateLastAiMessage(text: String) {
        val list = uiState.value.messages.toMutableList()
        if (list.lastOrNull()?.role == Role.ASSISTANT) {
            val old = list.last()
            list[list.lastIndex] = old.copy(content = text, status = MessageStatus.GENERATING)
        } else {
            list.add(
                UiMessage(
                    id = System.currentTimeMillis().toString(),
                    role = Role.ASSISTANT,
                    content = text,
                    wifiSsid = currentRoundWifiSsid,
                    status = MessageStatus.GENERATING
                )
            )
        }
        _uiState.update { old ->
            old.copy(messages = list)
        }
    }

    private fun markLastAiDone() {
        val list = uiState.value.messages.toMutableList()
        val index = list.indexOfLast { it.role == Role.ASSISTANT }
        if (index >= 0) {
            list[index] = list[index].copy(status = MessageStatus.DONE)
            _uiState.update { old ->
                old.copy(messages = list)
            }
        }
    }

    private fun markLastAiError(text: String) {
        val list = uiState.value.messages.toMutableList()
        val index = list.indexOfLast { it.role == Role.ASSISTANT }
        if (index >= 0) {
            list[index] = list[index].copy(content = text, status = MessageStatus.ERROR)
        } else {
            list.add(
                UiMessage(
                    id = System.currentTimeMillis().toString(),
                    role = Role.ASSISTANT,
                    content = text,
                    wifiSsid = currentRoundWifiSsid,
                    status = MessageStatus.ERROR
                )
            )
        }
        _uiState.update { old ->
            old.copy(messages = list)
        }
    }

    private fun replaceLastAiMessage(text: String, status: MessageStatus) {
        val list = uiState.value.messages.toMutableList()
        val index = list.indexOfLast { it.role == Role.ASSISTANT }
        if (index >= 0) {
            list[index] = list[index].copy(
                content = text,
                status = status
            )
            _uiState.update { old ->
                old.copy(messages = list)
            }
        }
    }

    private fun markLastAiCancelled() {
        val list = uiState.value.messages.toMutableList()
        val index = list.indexOfLast { it.role == Role.ASSISTANT && it.status == MessageStatus.GENERATING }
        if (index >= 0) {
            list[index] = list[index].copy(status = MessageStatus.CANCELLED)
            _uiState.update { old ->
                old.copy(messages = list)
            }
        }
    }

    private fun buildRequestMessages(uiMessages: List<UiMessage>): List<ChatMessage> {
        return uiMessages.map {
            ChatMessage(
                role = when (it.role) {
                    Role.USER -> "user"
                    Role.ASSISTANT -> "assistant"
                    Role.SYSTEM -> "system"
                },
                content = it.content
            )
        }
    }

    private fun appendAssistantToContext(
        inputType: InputType,
        text: String,
        wifiSsid: String?
    ) {
        val msg = UiMessage(
            id = System.currentTimeMillis().toString(),
            role = Role.ASSISTANT,
            content = text,
            wifiSsid = wifiSsid,
            status = MessageStatus.DONE
        )
        when (inputType) {
            InputType.NormalChat -> normalContextManager.addMessage(msg)
            InputType.CommandChat -> commandContextManager.addMessage(msg)
        }
    }

    companion object {
        private const val LOCAL_INTENT_CONFIDENCE_THRESHOLD = 0.7f
        private const val OPEN_BLUETOOTH_CMD = "打开蓝牙"
        private const val CLOSE_BLUETOOTH_CMD = "关闭蓝牙"
    }

    private fun emitEvent(event: ChatUiEvent) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }
}
data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val canSend: Boolean = true,
    val showStopButton: Boolean = false,
    val inputMode: InputType = InputType.NormalChat,
    val lastSuccessfulActions: List<Action> = emptyList(),
    val actionUpdateVersion: Long = 0L
)
sealed class ChatUiEvent {
    data class ShowToast(val message: String) : ChatUiEvent()
    data class RequestBluetoothPermission(val reason: String) : ChatUiEvent()
    data class ShowSnackbar(val message: String) : ChatUiEvent()
    data class Navigate(val route: String) : ChatUiEvent()
}
