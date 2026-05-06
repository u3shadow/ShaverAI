package com.u3coding.shaver.model

data class LocalIntentResult(
    val intent: LocalIntent,
    val confidence: Float
)

enum class LocalIntent {
    NormalChat,
    DeviceControl,
    RuleGeneration
}