package com.u3coding.shaver.model

enum class Role {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class MessageStatus {
    GENERATING,
    DONE,
    ERROR,
    CANCELLED
}

data class UiMessage(
    val id: String,
    val role: Role,
    val content: String,
    val wifiSsid: String? = null,
    val status: MessageStatus = MessageStatus.DONE,
    val createdAt: Long = System.currentTimeMillis()
)
