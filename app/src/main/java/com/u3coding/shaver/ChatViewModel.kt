package com.u3coding.shaver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private var currentJob: Job? = null
    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private val repo = ChatRepo(ApiProvider.api)

    fun requiresBluetoothPermission(input: String): Boolean {
        return input.contains(OPEN_BLUETOOTH_CMD, ignoreCase = true) ||
            input.contains(CLOSE_BLUETOOTH_CMD, ignoreCase = true)
    }

    fun sendStreamMessage(input: String) {
        val message = input.trim()
        if (message.isBlank()) {
            return
        }

        addUserMessage(message)

        when {
            message.contains(OPEN_BLUETOOTH_CMD, ignoreCase = true) -> {
                ChangeBlueTooth().open()
                addAssistantDoneMessage("Bluetooth is turned on.")
            }

            message.contains(CLOSE_BLUETOOTH_CMD, ignoreCase = true) -> {
                ChangeBlueTooth().close()
                addAssistantDoneMessage("Bluetooth is turned off.")
            }

            else -> {
                val history = buildRequestMessages(_messages.value)
                currentJob?.cancel()
                currentJob = viewModelScope.launch {
                    var currentText = ""
                    try {
                        repo.streamChat(history).collect { token ->
                            currentText += token
                            updateLastAiMessage(currentText)
                        }
                        markLastAiDone()
                    } catch (e: Exception) {
                        markLastAiError("请求失败：${e.message ?: "unknown error"}")
                    }
                }
            }
        }
    }

    fun stopGenerating() {
        currentJob?.cancel()
        markLastAiCancelled()
    }

    private fun addUserMessage(input: String) {
        val list = _messages.value.toMutableList()
        list.add(
            UiMessage(
                id = System.currentTimeMillis().toString(),
                role = Role.USER,
                content = input,
                status = MessageStatus.DONE
            )
        )
        _messages.value = list
    }

    private fun addAssistantDoneMessage(text: String) {
        val list = _messages.value.toMutableList()
        list.add(
            UiMessage(
                id = System.currentTimeMillis().toString(),
                role = Role.ASSISTANT,
                content = text,
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

    companion object {
        private const val OPEN_BLUETOOTH_CMD = "打开蓝牙"
        private const val CLOSE_BLUETOOTH_CMD = "关闭蓝牙"
    }
}
