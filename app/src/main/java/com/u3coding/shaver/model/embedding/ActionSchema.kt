package com.u3coding.shaver.model.embedding

data class ActionSchema(
    val actionId: String,
    val description: String,
    val examples: List<String>,
    val paramSchema: Map<String, String>,
    val riskLevel: ActionRiskLevel = ActionRiskLevel.LOW
)

enum class ActionRiskLevel {
    LOW,
    MEDIUM,
    HIGH
}