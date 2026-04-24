package com.u3coding.shaver.action.parser

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.u3coding.shaver.action.model.ModelDecision

class ToolCallParser(
    private val gson: Gson = Gson()
) {
    fun parse(raw: String): ModelDecision {
        val text = raw.trim()
        if (text.isBlank()) return invalid("\u6a21\u578b\u8f93\u51fa\u4e3a\u7a7a", raw)

        val root = try {
            JsonParser.parseString(text)
        } catch (e: JsonSyntaxException) {
            return invalid("\u6a21\u578b\u8f93\u51fa\u4e0d\u662f\u5408\u6cd5 JSON", raw)
        } catch (e: IllegalStateException) {
            return invalid("\u6a21\u578b\u8f93\u51fa\u4e0d\u662f\u5408\u6cd5 JSON", raw)
        }

        val jsonObject = root.asObjectOrNull()
            ?: return invalid("\u6a21\u578b\u8f93\u51fa\u5fc5\u987b\u662f JSON \u5bf9\u8c61", raw)

        parseByType(jsonObject, raw)?.let { return it }
        parseTopLevelChatReply(jsonObject, raw)?.let { return it }
        parseTopLevelToolCall(jsonObject, raw)?.let { return it }

        return invalid("\u65e0\u6cd5\u8bc6\u522b\u6a21\u578b\u8f93\u51fa\u7c7b\u578b", raw)
    }

    private fun parseByType(jsonObject: JsonObject, raw: String): ModelDecision? {
        val type = jsonObject.stringOrNull("type")?.lowercase() ?: return null
        return when (type) {
            "chat_reply" -> parseChatReply(jsonObject, raw)
            "tool_call" -> parseToolCall(jsonObject, raw)
            else -> invalid("\u672a\u77e5\u6a21\u578b\u8f93\u51fa\u7c7b\u578b: $type", raw)
        }
    }

    private fun parseTopLevelChatReply(jsonObject: JsonObject, raw: String): ModelDecision? {
        val chatReply = jsonObject.get("chat_reply") ?: return null
        val content = when {
            chatReply.isJsonObject -> chatReply.asJsonObject.stringOrNull("content")
            chatReply.isJsonNull -> null
            else -> runCatching { chatReply.asString }.getOrNull()
        }

        return if (content.isNullOrBlank()) {
            invalid("chat_reply.content \u4e0d\u80fd\u4e3a\u7a7a", raw)
        } else {
            ModelDecision.ChatReply(content)
        }
    }

    private fun parseTopLevelToolCall(jsonObject: JsonObject, raw: String): ModelDecision? {
        val toolCall = jsonObject.get("tool_call") ?: return null
        val toolCallObject = toolCall.asObjectOrNull()
            ?: return invalid("tool_call \u5fc5\u987b\u662f JSON \u5bf9\u8c61", raw)

        return parseToolCall(toolCallObject, raw)
    }

    private fun parseChatReply(jsonObject: JsonObject, raw: String): ModelDecision {
        val content = jsonObject.stringOrNull("content")
        return if (content.isNullOrBlank()) {
            invalid("chat_reply.content \u4e0d\u80fd\u4e3a\u7a7a", raw)
        } else {
            ModelDecision.ChatReply(content)
        }
    }

    private fun parseToolCall(jsonObject: JsonObject, raw: String): ModelDecision {
        val actionId = jsonObject.stringOrNull("action_id")
            ?: jsonObject.stringOrNull("action")
            ?: jsonObject.stringOrNull("tool")
            ?: jsonObject.stringOrNull("name")

        if (actionId.isNullOrBlank()) {
            return invalid("tool_call.action_id \u4e0d\u80fd\u4e3a\u7a7a", raw)
        }

        val paramsElement = jsonObject.get("params")
        val params = when {
            paramsElement == null || paramsElement.isJsonNull -> emptyMap()
            paramsElement.isJsonObject -> jsonObjectToMap(paramsElement)
            else -> return invalid("tool_call.params \u5fc5\u987b\u662f JSON \u5bf9\u8c61", raw)
        }

        return ModelDecision.ToolCall(
            actionId = actionId,
            params = params
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun jsonObjectToMap(jsonElement: JsonElement): Map<String, Any> {
        val params = gson.fromJson(jsonElement, Map::class.java) as? Map<Any, Any>
            ?: return emptyMap()
        return params.mapKeys { it.key.toString() }
    }

    private fun invalid(reason: String, raw: String): ModelDecision.Invalid {
        return ModelDecision.Invalid(
            reason = reason,
            raw = raw
        )
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? {
        return if (isJsonObject) asJsonObject else null
    }

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        return runCatching { value.asString.trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }
}
