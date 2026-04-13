package com.u3coding.shaver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages = _messages
    val repo = ChatRepo()
    fun sendMessage(input: String) {
        _messages.value = _messages.value + "用户：$input"
        viewModelScope.launch {
            val tokens = repo.streamChat(input)
            var current = ""
            tokens.forEach {
                delay(300)
                current += it
                updateLastMessage(current)
            }
        }
    }
    private fun updateLastMessage(text: String) {
        val list = _messages.value.toMutableList()
        if (list.lastOrNull()?.startsWith("AI") == true) {
            list[list.lastIndex] = "AI：$text"
        } else {
            list.add("AI：$text")
        }
        _messages.value = list
    }
}