package com.u3coding.shaver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages.asStateFlow()

    private val repo = ChatRepo()

    fun requiresBluetoothPermission(input: String): Boolean {
        return input.contains(OPEN_BLUETOOTH_CMD, ignoreCase = true) ||
            input.contains(CLOSE_BLUETOOTH_CMD, ignoreCase = true)
    }

    fun sendMessage(input: String) {
        val message = input.trim()
        if (message.isBlank()) {
            return
        }

        _messages.value = _messages.value + "User: $message"

        when {
            message.contains(OPEN_BLUETOOTH_CMD, ignoreCase = true) -> {
                ChangeBlueTooth().open()
                updateLastMessage("AI: Bluetooth is turned on.")
            }

            message.contains(CLOSE_BLUETOOTH_CMD, ignoreCase = true) -> {
                ChangeBlueTooth().close()
                updateLastMessage("AI: Bluetooth is turned off.")
            }

            else -> {
                viewModelScope.launch {
                    val tokens = repo.streamChat(message)
                    var current = ""
                    tokens.forEach { token ->
                        delay(300)
                        current += token
                        updateLastMessage("AI: $current")
                    }
                }
            }
        }
    }

    private fun updateLastMessage(text: String) {
        val list = _messages.value.toMutableList()
        if (list.lastOrNull()?.startsWith("AI:") == true) {
            list[list.lastIndex] = text
        } else {
            list.add(text)
        }
        _messages.value = list
    }

    companion object {
        private const val OPEN_BLUETOOTH_CMD = "打开蓝牙"
        private const val CLOSE_BLUETOOTH_CMD = "关闭蓝牙"
    }
}
