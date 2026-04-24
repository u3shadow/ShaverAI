package com.u3coding.shaver.action

sealed class ModelDecision {
    data class ChatReply(val content: String) : ModelDecision()

    data class ToolCall(
        val actionId: String,
        val params: Map<String, Any>
    ) : ModelDecision()

    data class Invalid(
        val reason: String,
        val raw: String
    ) : ModelDecision()
}