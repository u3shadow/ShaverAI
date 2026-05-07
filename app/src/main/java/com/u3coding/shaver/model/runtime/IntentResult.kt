package com.u3coding.shaver.model.runtime

data class IntentResult(
    val intent: IntentType,
    val confidence: Float,
    val source: String
)

enum class IntentType {
    NORMAL_CHAT,
    DEVICE_CONTROL,
    RULE_CREATE,
    STATE_QUERY,
    UNKNOWN
}