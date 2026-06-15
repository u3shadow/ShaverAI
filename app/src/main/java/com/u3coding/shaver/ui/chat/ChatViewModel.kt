package com.u3coding.shaver.ui.chat

import android.content.Context
import android.util.Log
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
import com.u3coding.shaver.action.rule.RuleEventType
import com.u3coding.shaver.action.rule.RuleRepo
import com.u3coding.shaver.action.rule.RuleRunResult
import com.u3coding.shaver.action.rule.WifiAliasRepo
import com.u3coding.shaver.action.parser.ToolCallParser
import com.u3coding.shaver.model.CommandChatContextManager
import com.u3coding.shaver.model.IntentFeatureExtractor
import com.u3coding.shaver.model.LocalIntentModelProvider
import com.u3coding.shaver.model.MessageStatus
import com.u3coding.shaver.model.NormalChatContextManager
import com.u3coding.shaver.model.Role
import com.u3coding.shaver.model.UiMessage
import com.u3coding.shaver.model.benchmark.LocalModelBenchmark
import com.u3coding.shaver.model.runtime.IntentType
import com.u3coding.shaver.model.runtime.LocalModelBackend
import com.u3coding.shaver.model.runtime.LocalModelRuntime
import com.u3coding.shaver.model.runtime.TfliteLocalModelRuntime
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
    private val localModelRuntime: LocalModelRuntime = TfliteLocalModelRuntime(
        LocalIntentModelProvider(
            applicationContext,
            IntentFeatureExtractor(),
            LocalModelBackend.CPU
        )
    )
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
    private fun runLocalModelBenchmark() {
        viewModelScope.launch {
            val results = LocalModelBenchmark(applicationContext).runAll()

            results.forEach { result ->
                Log.d(
                    "LocalModelBenchmark",
                    "backend=${result.backend}, " +
                            "success=${result.success}, " +
                            "firstRun=${result.firstRunMs}ms, " +
                            "avg=${result.avgRunMs}ms, " +
                            "min=${result.minRunMs}ms, " +
                            "max=${result.maxRunMs}ms, " +
                            "error=${result.error}"
                )
            }
        }
    }
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
            emitEvent(ChatUiEvent.RequestBluetoothPermission("\u84dd\u7259\u64cd\u4f5c\u9700\u8981\u84dd\u7259\u8fde\u63a5\u6743\u9650"))
            return false
        }
        if (requiresWriteSettingsPermission(message) && !hasWriteSettingsPermission) {
            emitEvent(ChatUiEvent.ShowToast("\u672a\u6388\u4e88\u4fee\u6539\u7cfb\u7edf\u8bbe\u7f6e\u6743\u9650\uff0c\u4eae\u5ea6\u8bbe\u7f6e\u53ef\u80fd\u4ec5\u5728\u5e94\u7528\u5185\u751f\u6548"))
            return false
        }
        if (!hasWifiPermission) {
            emitEvent(ChatUiEvent.ShowToast("\u672a\u6388\u4e88\u65e0\u7ebf\u7f51\u8bfb\u53d6\u6743\u9650"))
            return false
        }
        return true
    }

    fun requiresBluetoothPermission(input: String): Boolean {
        return input.contains(OPEN_BLUETOOTH_CMD, ignoreCase = true) ||
            input.contains(CLOSE_BLUETOOTH_CMD, ignoreCase = true)
    }

    private fun requiresWriteSettingsPermission(input: String): Boolean {
        return input.contains(BRIGHTNESS_CMD, ignoreCase = true)
    }

    fun handleUserInput(input: String, wifiSsid: String?) {
        val message = input.trim()
        if (message.isBlank()) return

        val inputType = resolveInputType(message)
        submitInputTask(inputType, message, wifiSsid)
    }

    private fun resolveInputType(message: String): InputType {
        if (isWifiAliasCommand(message)) {
            return InputType.CommandChat
        }

        val localResult = localModelRuntime.classifyIntent(message)
        Log.d(
            "LocalIntentDecision",
            "input=$message, " +
                    "intent=${localResult.intent}, " +
                    "confidence=${localResult.confidence}, " +
                    "source=${localResult.source}, " +
                    "backend=${localResult.backend}, " +
                    "latency=${localResult.latencyMs}ms, " +
                    "decision=${if (localResult.confidence >= LOCAL_INTENT_CONFIDENCE_THRESHOLD) "use_local" else "fallback"}"
        )
        if (localResult.confidence >= LOCAL_INTENT_CONFIDENCE_THRESHOLD) {
            return when (localResult.intent) {
                IntentType.NORMAL_CHAT -> InputType.NormalChat
                IntentType.DEVICE_CONTROL,
                IntentType.RULE_CREATE -> InputType.CommandChat
                else -> {InputClassifier.classify(message)}
            }
        }

        return InputClassifier.classify(message)
    }

    private fun isWifiAliasCommand(message: String): Boolean {
        return listOf(
            "\u522b\u540d",
            "\u53eb\u505a",
            "\u53eb\u4f5c",
            "\u547d\u540d",
            "\u53d6\u540d"
        ).any { keyword ->
            message.contains(keyword, ignoreCase = true)
        }
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
            "wifi_changed" -> "\u68c0\u6d4b\u5230\u65e0\u7ebf\u7f51\u53d8\u5316\uff1a${task.wifiSsid ?: "\u672a\u77e5\u65e0\u7ebf\u7f51"}"
            "wifi_disconnected" -> "\u68c0\u6d4b\u5230\u65e0\u7ebf\u7f51\u65ad\u5f00\uff1a${task.wifiSsid ?: "\u672a\u77e5\u65e0\u7ebf\u7f51"}"
            else -> "\u6536\u5230\u7cfb\u7edf\u4e8b\u4ef6\uff1a${task.event}"
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
            val error = "\u8bf7\u6c42\u5931\u8d25\uff1a${e.message ?: "\u672a\u77e5\u9519\u8bef"}"
            markLastAiError(error)
            emitEvent(ChatUiEvent.ShowSnackbar(error))
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
            //浣跨敤Input鍜寃ifiSsid鏋勯€犱竴涓复鏃剁殑UiMessage锛屾斁鍦ㄥ巻鍙茶褰曠殑鏈€鍚庨潰锛屾潵鏋勯€犺姹備笂涓嬫枃
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
            // 娌℃湁瑙ｆ瀽鍑烘搷浣滐紝鐩存帴杩斿洖
            return
        }
        val action = result.toAction()
        RuleRepo.addRule(action)
        // 杩欓噷鐩存帴鎵ц鍔ㄤ綔锛屽疄闄呭簲鐢ㄤ腑鍙兘闇€瑕佺敤鎴风‘璁?
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

                if (decision.actionId == SET_WIFI_ALIAS_ACTION) {
                    val reply = saveWifiAlias(decision, wifiSsid)
                    replaceLastAiMessage(
                        text = reply,
                        status = MessageStatus.DONE
                    )
                    return reply
                }

                val action = decision.toAction(wifiSsid)
                saveActionAndMaybeExecute(action)
                val reply = if (RuleEventType.normalize(action.eventType) == RuleEventType.WIFI_DISCONNECTED) {
                    "\u5df2\u4fdd\u5b58\u65ad\u5f00\u65e0\u7ebf\u7f51\u540e\u7684\u914d\u7f6e\uff1a${action.toDisplayName()}"
                } else {
                    "\u5df2\u6267\u884c\uff1a${action.toDisplayName()}"
                }
                replaceLastAiMessage(
                    text = reply,
                    status = MessageStatus.DONE
                )
                reply
            }

            is ModelDecision.Invalid -> {
                val error = "\u89e3\u6790\u7ed3\u679c\u4e0d\u5408\u6cd5\uff1a${decision.reason}"
                markLastAiError(error)
                emitEvent(ChatUiEvent.ShowSnackbar(error))
                ""
            }
        }
    }

    private fun validateToolCall(decision: ModelDecision.ToolCall): String? {
        if (!OperationList.isValidOperation(decision.actionId)) {
            return "\u4e0d\u652f\u6301\u7684\u64cd\u4f5c\uff1a${decision.actionId}"
        }
        if (decision.actionId == SET_WIFI_ALIAS_ACTION) {
            val alias = decision.params["alias"]?.toString()?.trim()
            if (alias.isNullOrBlank()) {
                return "\u522b\u540d\u4e0d\u80fd\u4e3a\u7a7a"
            }
            return null
        }
        if (decision.actionId == "set_volume" || decision.actionId == "set_brightness") {
            val value = decision.params["value"]
            if (value !is Number) {
                return "${decision.actionId} \u7f3a\u5c11\u6570\u503c\u53c2\u6570 value"
            }
        }
        return null
    }

    private fun saveWifiAlias(
        decision: ModelDecision.ToolCall,
        wifiSsid: String?
    ): String {
        val alias = decision.params["alias"]?.toString()?.trim().orEmpty()
        val targetSsid = resolveAliasTargetSsid(decision.target, wifiSsid)
        if (targetSsid.isBlank()) {
            val message = "\u6211\u8fd8\u6ca1\u8bfb\u5230\u5f53\u524d\u65e0\u7ebf\u7f51\uff0c\u8bf7\u8fde\u4e0a\u65e0\u7ebf\u7f51\u540e\u518d\u5bf9\u6211\u8bf4\uff1a\u628a\u5f53\u524d\u65e0\u7ebf\u7f51\u53eb\u505a$alias"
            emitEvent(ChatUiEvent.ShowSnackbar(message))
            return message
        }

        WifiAliasRepo.saveAlias(targetSsid, alias)
        return "\u5df2\u628a\u65e0\u7ebf\u7f51\u201c$targetSsid\u201d\u7684\u522b\u540d\u8bbe\u7f6e\u4e3a\u201c$alias\u201d\u3002\u4ee5\u540e\u4f60\u53ef\u4ee5\u76f4\u63a5\u8bf4\uff1a\u8fdb\u5165$alias\u65f6\u8c03\u6574\u4eae\u5ea6\uff0c\u6216\u79bb\u5f00$alias\u65f6\u8c03\u6574\u97f3\u91cf\u3002"
    }

    private fun resolveAliasTargetSsid(target: String?, wifiSsid: String?): String {
        val cleanTarget = target?.trim().orEmpty()
        val targetMeansCurrentWifi = cleanTarget.isBlank() ||
            cleanTarget.equals("current Wi-Fi", ignoreCase = true) ||
            cleanTarget.equals("current wifi", ignoreCase = true) ||
            cleanTarget == "\u5f53\u524d Wi-Fi" ||
            cleanTarget == "\u5f53\u524d wifi" ||
            cleanTarget == "\u8fd9\u4e2a Wi-Fi" ||
            cleanTarget == "\u8fd9\u4e2a wifi"

        if (targetMeansCurrentWifi) {
            return wifiSsid.orEmpty()
        }

        return WifiAliasRepo.resolveTargetToSsid(cleanTarget).orEmpty()
    }

    private fun ModelDecision.ToolCall.toAction(wifiSsid: String?): Action {
        val resolvedSsid = WifiAliasRepo.resolveTargetToSsid(target) ?: wifiSsid.orEmpty()
        return Action(
            trigger = resolvedSsid,
            operation = actionId,
            params = params,
            eventType = RuleEventType.normalize(eventType)
        )
    }

    private fun saveActionAndMaybeExecute(action: Action) {
        RuleRepo.addRule(action)
        if (RuleEventType.normalize(action.eventType) == RuleEventType.WIFI_CONNECTED) {
            executor.execute(action)
        }
        actionUpdateVersionCounter += 1
        _uiState.update { old ->
            old.copy(
                lastSuccessfulActions = listOf(action),
                actionUpdateVersion = actionUpdateVersionCounter
            )
        }
    }

    private fun Action.toDisplayName(): String {
        return when (operation) {
            "set_volume" -> "\u97f3\u91cf"
            "set_brightness" -> "\u4eae\u5ea6"
            "open_bluetooth" -> "\u6253\u5f00\u84dd\u7259"
            "close_bluetooth" -> "\u5173\u95ed\u84dd\u7259"
            else -> operation
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
            is RuleRunResult.Success -> "\u6267\u884c\u6210\u529f"
            is RuleRunResult.NoRule -> "\u672a\u627e\u5230\u5bf9\u5e94\u89c4\u5219"
            is RuleRunResult.SkippedDuplicate -> "\u5df2\u7ecf\u6267\u884c\u8fc7\u76f8\u540c\u89c4\u5219\uff0c\u8df3\u8fc7"
            is RuleRunResult.Failed -> "\u6267\u884c\u5931\u8d25\uff0c\u539f\u56e0\uff1a${result.reason}"
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
        private const val OPEN_BLUETOOTH_CMD = "\u6253\u5f00\u84dd\u7259"
        private const val CLOSE_BLUETOOTH_CMD = "\u5173\u95ed\u84dd\u7259"
        private const val BRIGHTNESS_CMD = "\u4eae\u5ea6"
        private const val SET_WIFI_ALIAS_ACTION = "set_wifi_alias"
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

