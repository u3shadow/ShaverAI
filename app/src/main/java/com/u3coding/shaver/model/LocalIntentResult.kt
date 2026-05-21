package com.u3coding.shaver.model

data class LocalIntentResult(
    val intent: LocalIntent,
    val confidence: Float,
    val latencyMs: Long = 0L,
    val backend: String = "CPU",
    val available: Boolean = true,
    val error: String? = null
)

enum class LocalIntent {
    NormalChat,
    DeviceControl,
    RuleGeneration,
    Unknown
}