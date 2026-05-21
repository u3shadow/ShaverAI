package com.u3coding.shaver.model.runtime

data class IntentResult(
    val intent: IntentType,
    val confidence: Float,
    val source: String,
    val latencyMs: Long = 0L,
    val backend: String = "CPU",
    val error: String? = null
)

enum class IntentType {
    NORMAL_CHAT,
    DEVICE_CONTROL,
    RULE_CREATE,
    STATE_QUERY,
    UNKNOWN
}