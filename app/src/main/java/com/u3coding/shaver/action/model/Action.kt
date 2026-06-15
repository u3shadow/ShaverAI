package com.u3coding.shaver.action.model

import com.u3coding.shaver.action.rule.RuleEventType

data class Action(
    val trigger: String,
    val operation: String,
    val params: Map<String, Any>,
    val eventType: String = RuleEventType.WIFI_CONNECTED
)
