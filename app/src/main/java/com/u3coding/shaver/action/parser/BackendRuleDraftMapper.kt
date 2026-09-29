package com.u3coding.shaver.action.parser

import com.u3coding.shaver.action.executor.BackendActionMapper
import com.u3coding.shaver.data.remote.AgentChatResponse
import com.u3coding.shaver.data.remote.BackendActionItem
import com.u3coding.shaver.data.remote.RuleCreateRequest

/** Convert a confirmed backend draft into the existing POST /rules contract. */
object BackendRuleDraftMapper {
    fun fromResponse(response: AgentChatResponse, userId: String): RuleCreateRequest? {
        val item = response.action_plan?.actions?.singleOrNull() ?: return null
        if (item.action != "create_rule") return null
        val name = (item.params["name"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val trigger = normalizedMap(item.params["trigger"]) ?: return null
        val action = normalizedMap(item.params["action"]) ?: return null
        val actionType = action["type"] as? String ?: return null
        val actionParams = action["params"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val typedParams = normalizedMap(actionParams) ?: return null
        try {
            BackendActionMapper.toLocalAction(BackendActionItem(actionType, typedParams), null)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return RuleCreateRequest(userId, name, trigger, action)
    }

    private fun normalizedMap(value: Any?): Map<String, Any?>? {
        val source = value as? Map<*, *> ?: return null
        if (source.keys.any { it !is String }) return null
        return source.entries.associate { (key, item) -> key as String to normalize(item) }
    }

    private fun normalize(value: Any?): Any? = when (value) {
        is Map<*, *> -> normalizedMap(value)
        is List<*> -> value.map(::normalize)
        is Number -> {
            val decimal = value.toDouble()
            if (decimal.isFinite() && decimal % 1.0 == 0.0 &&
                decimal >= Int.MIN_VALUE && decimal <= Int.MAX_VALUE
            ) decimal.toInt() else value
        }
        else -> value
    }
}
