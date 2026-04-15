package com.u3coding.shaver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel: ViewModel() {

    private lateinit var currentJob: Job
    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private val repo = ChatRepo(ApiProvider.api)

    fun requiresBluetoothPermission(input: String): Boolean {
        return input.contains(OPEN_BLUETOOTH_CMD, ignoreCase = true) ||
            input.contains(CLOSE_BLUETOOTH_CMD, ignoreCase = true)
    }

    fun addUserMessage(input: ChatRequest.Message) {
        val list = _messages.value.toMutableList()
        list.add(
            UiMessage(
                id = input.id,
                role = input.role,
                content = input.content,
                wifiName = input.wifiName,
                time = input.time
            )
        )
        _messages.value = list
    }
    fun updateLastAiMessage(text: String) {
        val list = _messages.value.toMutableList()

        if (list.lastOrNull()?.role == "assistant") {
            val old = list.last()
            list[list.lastIndex] = old.copy(content = text, status = "generating")
        } else {
            list.add(
                UiMessage(
                    id = System.currentTimeMillis().toString(),
                    role = "assistant",
                    content = text,
                    status = "generating"
                )
            )
        }

        _messages.value = list
    }
    fun sendStreamMessage(input: String) {
       val message =  ChatRequest.Message(id =  System.currentTimeMillis().toString(),role = "user", content = input)
        //获取当前wifissid
      //  message.wifiName =  wifiProvider.getCurrentWifiSSID()
        val history = listOf(
            message
        )

        addUserMessage(message)
        when {
            input.contains(OPEN_BLUETOOTH_CMD, ignoreCase = true) -> {
                ChangeBlueTooth().open()
                updateLastMessage("AI: Bluetooth is turned on.")
            }

            input.contains(CLOSE_BLUETOOTH_CMD, ignoreCase = true) -> {
                ChangeBlueTooth().close()
                updateLastMessage("AI: Bluetooth is turned off.")
            }

            else -> {
                currentJob = viewModelScope.launch {
                    var currentText = ""

                    repo.streamChat(history).collect { token ->
                        currentText += token
                        updateLastAiMessage(currentText)
                    }
                }
            }
        }
    }

    fun stopGenerating() {
        currentJob?.cancel()
    }
    private fun updateLastMessage(text: String) {
        val list = _messages.value.toMutableList()
        if (list.lastOrNull()?.role == "assistant") {
            list[list.lastIndex] = UiMessage(
                id = list.last().id,
                role = "assistant",
                content = text,
                wifiName = list.last().wifiName,
                time = list.last().time,
                status = "done"
            )
        } else {
            list.add(UiMessage(
                id = System.currentTimeMillis().toString(),
                role = "user",
                content = text,
                status = "done"
            ))
        }
        _messages.value = list
    }

    companion object {
        private const val OPEN_BLUETOOTH_CMD = "打开蓝牙"
        private const val CLOSE_BLUETOOTH_CMD = "关闭蓝牙"
    }
}
