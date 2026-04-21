package com.u3coding.shaver.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u3coding.shaver.data.remote.ApiProvider
import com.u3coding.shaver.data.remote.ChatMessage
import com.u3coding.shaver.data.repository.ChatRepo
import com.u3coding.shaver.action.Action
import com.u3coding.shaver.action.ActionDTO
import com.u3coding.shaver.action.ActionExecutor
import com.u3coding.shaver.action.ActionParser
import com.u3coding.shaver.action.InputClassifier
import com.u3coding.shaver.action.InputType
import com.u3coding.shaver.action.PromptBuilder
import com.u3coding.shaver.action.RuleEngine
import com.u3coding.shaver.action.RuleRepo
import com.u3coding.shaver.action.RuleRunResult
import com.u3coding.shaver.model.CommandChatContextManager
import com.u3coding.shaver.model.MessageStatus
import com.u3coding.shaver.model.NormalChatContextManager
import com.u3coding.shaver.model.Role
import com.u3coding.shaver.model.UiMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(val executor: ActionExecutor) : ViewModel() {

    private var currentJob: Job? = null
    private var currentRoundWifiSsid: String? = null
    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    private val _lastSuccessfulActions = MutableStateFlow<List<Action>>(emptyList())
    private val normalContextManager = NormalChatContextManager()
    private val commandContextManager = CommandChatContextManager()
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()
    val lastSuccessfulActions: StateFlow<List<Action>> = _lastSuccessfulActions.asStateFlow()
    private val repo = ChatRepo(ApiProvider.api)
    fun requiresBluetoothPermission(input: String): Boolean {
        return input.contains(OPEN_BLUETOOTH_CMD, ignoreCase = true) ||
            input.contains(CLOSE_BLUETOOTH_CMD, ignoreCase = true)
    }

    fun handleUserInput(input: String, wifiSsid: String?) {
        when (InputClassifier.classify(input)) {
            InputType.NormalChat -> handleNormalChatInput(input, wifiSsid)
            InputType.CommandChat -> handleCommandInput(input, wifiSsid)
        }
    }

    private fun handleNormalChatInput(input: String, wifiSsid: String?) {
        val message = input.trim()
        if (message.isBlank()) {
            return
        }
        currentRoundWifiSsid = wifiSsid
        addUserMessage(message, wifiSsid)
        val history = getHistory(InputType.NormalChat, message, wifiSsid, emptyList())
        runStreamRequest(InputType.NormalChat, history)
    }

    private fun handleCommandInput(input: String, wifiSsid: String?) {
        val message = input.trim()
        if (message.isBlank()) {
            return
        }
        currentRoundWifiSsid = wifiSsid
        addUserMessage(message, wifiSsid)
        val history = getHistory(InputType.CommandChat, message, wifiSsid, emptyList())
        runStreamRequest(InputType.CommandChat, history)
    }

    private fun runStreamRequest(inputType: InputType, history: List<ChatMessage>) {
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            var currentText = ""
            try {
                repo.streamChat(history).collect { token ->
                    currentText += token
                    updateLastAiMessage(currentText)
                }
                markLastAiDone()
                appendAssistantToContext(inputType, currentText, currentRoundWifiSsid)
                if (inputType == InputType.CommandChat) {
                    val result = ActionParser().parseActionDTO(currentText)
                    if (!ActionParser().ActionValidator(result)) {
                        markLastAiError("解析结果不合法")
                    } else {
                        handleParsedActionResult(result)
                    }
                }
            } catch (e: Exception) {
                markLastAiError("请求失败：${e.message ?: "unknown error"}")
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
            history1 = buildRequestMessages(normalContextManager.getLastMessages())
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
        _lastSuccessfulActions.value = listOf(action)
    }

    fun onActionsExecuted(actions: List<Action>) {
        _lastSuccessfulActions.value = actions
    }

    fun onRuleRunResult(result: RuleRunResult) {
        val content = when (result) {
            is RuleRunResult.Success -> "执行成功"
            is RuleRunResult.NoRule -> "未找到对应规则"
            is RuleRunResult.SkippedDuplicate -> "已经执行过相同规则，跳过"
            is RuleRunResult.Failed -> "执行失败，原因：${result.reason}"
        }
        val list = _messages.value.toMutableList()
        list.add(
            UiMessage(
                id = "rule-${System.currentTimeMillis()}",
                role = Role.SYSTEM,
                content = content,
                wifiSsid = currentRoundWifiSsid,
                status = MessageStatus.DONE
            )
        )
        _messages.value = list
    }

    private fun buildRequestUiMessages(uiMessages: List<UiMessage>, wifiSsid: String?): List<UiMessage> {
        if (wifiSsid.isNullOrBlank()) {
            return uiMessages
        }
        return PromptBuilder().build(uiMessages.last().content, wifiSsid,messages.value).let { systemPrompt ->
            listOf(UiMessage(
                id = "system-${System.currentTimeMillis()}",
                role = Role.SYSTEM,
                content = systemPrompt,
                wifiSsid = wifiSsid,
                status = MessageStatus.DONE
            )) + uiMessages
        }
    }

    fun stopGenerating() {
        currentJob?.cancel()
        markLastAiCancelled()
    }

    private fun addUserMessage(input: String, wifiSsid: String?) {
        val list = _messages.value.toMutableList()
        list.add(
            UiMessage(
                id = System.currentTimeMillis().toString(),
                role = Role.USER,
                content = input,
                wifiSsid = wifiSsid,
                status = MessageStatus.DONE
            )
        )
        _messages.value = list
    }

    private fun updateLastAiMessage(text: String) {
        val list = _messages.value.toMutableList()
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
        _messages.value = list
    }

    private fun markLastAiDone() {
        val list = _messages.value.toMutableList()
        val index = list.indexOfLast { it.role == Role.ASSISTANT }
        if (index >= 0) {
            list[index] = list[index].copy(status = MessageStatus.DONE)
            _messages.value = list
        }
    }

    private fun markLastAiError(text: String) {
        val list = _messages.value.toMutableList()
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
        _messages.value = list
    }

    private fun markLastAiCancelled() {
        val list = _messages.value.toMutableList()
        val index = list.indexOfLast { it.role == Role.ASSISTANT && it.status == MessageStatus.GENERATING }
        if (index >= 0) {
            list[index] = list[index].copy(status = MessageStatus.CANCELLED)
            _messages.value = list
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
        private const val OPEN_BLUETOOTH_CMD = "打开蓝牙"
        private const val CLOSE_BLUETOOTH_CMD = "关闭蓝牙"
    }
}
