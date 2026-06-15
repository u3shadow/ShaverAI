package com.u3coding.shaver.action.model

import com.u3coding.shaver.action.rule.RuleEventType

data class ActionDTO(
    val trigger: String?,
    val operation: String?,
    val params: Map<String, Any>?,
    val event: String? = null,
    val eventType: String? = null
) {
    fun toAction(): Action {
        return Action(
            trigger = trigger ?: "",
            operation = operation ?: "",
            params = params ?: emptyMap(),
            eventType = RuleEventType.normalize(eventType ?: event)
        )
    }
}
