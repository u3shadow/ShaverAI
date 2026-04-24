package com.u3coding.shaver.action.model

data class ModelDecisionDTO(
    val type: String?,
    val content: String?,
    val action: String?,
    val params: Map<String, Any>?
)