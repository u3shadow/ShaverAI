package com.u3coding.shaver.model

class NormalChatContextManager {
    //管理一个uimessage的list,可以添加新的message,也可以清空message
    private val number = 10
    private val messages = mutableListOf<UiMessage>()
    fun getMessages(): List<UiMessage> {
        return messages
    }
    fun addMessage(message: UiMessage) {
        messages.add(message)
    }
    fun clearMessages() {
        messages.clear()
    }
    fun getLastMessages(): List<UiMessage> {
        val count = messages.count()
        //获取messages的最后10条，如果不足10条就获取全部
        val lastMessages = if (count > number) messages.takeLast(number) else messages
        return lastMessages
    }
}