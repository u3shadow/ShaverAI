package com.u3coding.shaver.action.prompt

import com.u3coding.shaver.model.Role
import com.u3coding.shaver.model.UiMessage
import com.u3coding.shaver.action.rule.WifiAliasRepo

class PromptBuilder {

    fun build(
        input: String,
        currentWifi: String?,
        messages: List<UiMessage>
    ): String {
        val wifiText = currentWifi?.takeIf { it.isNotBlank() } ?: "unknown"
        val historyText = buildRecentHistory(messages)
        val aliasText = buildAliasText()

        return """
You are an Android automation rule parser.

Convert the user input into one JSON object for Wi-Fi scene rules.
Return JSON only. Do not return markdown, code fences, or explanations.

Current Wi-Fi:
$wifiText

Known Wi-Fi aliases:
$aliasText

Recent chat history:
$historyText

Current user input:
$input

Return this JSON shape:
{
  "trigger": "string or null",
  "event_type": "wifi_connected | wifi_disconnected",
  "operation": "set_volume | set_brightness | open_bluetooth | close_bluetooth | null",
  "params": {
    "value": "number or null"
  }
}

Rules:
1. trigger is the Wi-Fi scene name.
2. If the user mentions a Wi-Fi name, use that as trigger.
3. If the user means the current place/current Wi-Fi and current Wi-Fi is known, use current Wi-Fi as trigger.
4. If the Wi-Fi scene cannot be determined, use null.
5. operation must be one of set_volume, set_brightness, open_bluetooth, close_bluetooth.
6. If the operation cannot be determined, use null.
7. If the operation does not need a numeric value, params.value must be null.
8. If the user asks for silent mode, use set_volume with value 0.
9. Use event_type wifi_disconnected when the user says to apply settings after leaving, disconnecting from, or exiting a Wi-Fi. Otherwise use wifi_connected.

Example:
{
  "trigger": "OfficeWiFi",
  "event_type": "wifi_connected",
  "operation": "set_volume",
  "params": {
    "value": 0
  }
}

Now parse the current user input and return JSON only:
""".trimIndent()
    }

    private fun buildRecentHistory(messages: List<UiMessage>): String {
        val recentMessages = messages
            .filter { it.role == Role.USER || it.role == Role.ASSISTANT }
            .takeLast(4)

        if (recentMessages.isEmpty()) {
            return "\u65e0"
        }

        return recentMessages.joinToString("\n") { message ->
            val roleText = when (message.role) {
                Role.USER -> "\u7528\u6237"
                Role.ASSISTANT -> "AI"
                Role.SYSTEM -> "\u7cfb\u7edf"
            }
            "$roleText: ${message.content}"
        }
    }

    fun buildToolCallingPrompt(
        input: String,
        currentWifi: String?,
        messages: List<UiMessage>
    ): String {
        val wifiText = currentWifi?.takeIf { it.isNotBlank() } ?: "unknown"
        val historyText = buildRecentHistory(messages)
        val aliasText = buildAliasText()

        return """
You are the tool-calling decision maker for this Android app.

Decide whether the user input should become a tool call or a normal chat reply.
Return JSON only. Do not return markdown, code fences, or explanations.
The top-level JSON object must contain a type field.

Current Wi-Fi:
$wifiText

Known Wi-Fi aliases:
$aliasText

Recent chat history:
$historyText

Current user input:
$input

If the user wants to change phone settings, return tool_call:
{
  "type": "tool_call",
  "event_type": "wifi_connected",
  "target": "current Wi-Fi SSID or alias",
  "action": "set_volume",
  "params": {
    "value": 3
  }
}

If the user is only chatting, asking what the app can do, or the intent is unclear, return chat_reply:
{
  "type": "chat_reply",
  "content": "I can help you set volume and brightness."
}

Available actions:
1. set_volume: params.value must be a number from 0 to 100.
2. set_brightness: params.value must be a number from 0 to 100.
3. open_bluetooth: params must be an empty object {}.
4. close_bluetooth: params must be an empty object {}.
5. set_wifi_alias: params.alias must be a non-empty string, used to name a Wi-Fi scene.

Available event_type values:
1. wifi_connected: apply when the device connects to the current Wi-Fi.
2. wifi_disconnected: apply when the device leaves/disconnects from the current Wi-Fi and has not connected to a new Wi-Fi.

Target rules:
1. If the user mentions a known alias, put that alias in target.
2. If the user mentions a Wi-Fi SSID directly, put that SSID in target.
3. If the user says current Wi-Fi or does not mention a target, omit target.
4. For phone setting actions, do not put the target alias into params.
5. For set_wifi_alias, target is the Wi-Fi being named. If the user says current Wi-Fi, omit target and use the Current Wi-Fi above.

Decision rules:
1. Use set_volume for volume changes or silent mode.
2. Use set_brightness for brightness changes.
3. Use open_bluetooth when the user asks to turn Bluetooth on.
4. Use close_bluetooth when the user asks to turn Bluetooth off.
5. Use set_wifi_alias when the user wants to name, rename, call, alias, or remember a Wi-Fi as a friendly place name, such as home or office.
6. If the user asks how to configure aliases or scenes, guide them in Chinese to say examples like: "\u628a\u5f53\u524d Wi-Fi \u53eb\u505a\u5bb6" or "\u4ee5\u540e\u8fd9\u4e2a Wi-Fi \u5c31\u53eb\u516c\u53f8".
7. If the user asks what you can do, return chat_reply and explain in Chinese that the app can configure volume, brightness, and Bluetooth for the current Wi-Fi, then automatically apply saved settings on next launch.
8. If the user mentions after leaving, after disconnecting from, exiting, or no longer connected to Wi-Fi, set event_type to wifi_disconnected.
9. Otherwise set event_type to wifi_connected.
10. If the intent is unclear, return chat_reply and ask a follow-up question.

Examples:
{
  "type": "tool_call",
  "event_type": "wifi_connected",
  "target": "Home",
  "action": "set_volume",
  "params": {
    "value": 3
  }
}

{
  "type": "tool_call",
  "action": "set_wifi_alias",
  "params": {
    "alias": "\u5bb6"
  }
}

{
  "type": "chat_reply",
  "content": "\u6211\u53ef\u4ee5\u5e2e\u4f60\u901a\u8fc7\u804a\u5929\u8bbe\u7f6e\u5f53\u524d Wi-Fi \u73af\u5883\u4e0b\u7684\u624b\u673a\u914d\u7f6e\uff0c\u6bd4\u5982\u97f3\u91cf\u3001\u4eae\u5ea6\u548c\u84dd\u7259\u5f00\u5173\uff0c\u5e76\u5728\u4e0b\u6b21\u542f\u52a8\u65f6\u81ea\u52a8\u5e94\u7528\u8fd9\u4e9b\u914d\u7f6e\u3002"
}

Now return JSON for the current user input:
""".trimIndent()
    }

    fun buildAppCapabilityPrompt(): String {
        return """
When the user asks what this app can do, answer in Chinese: \u76ee\u524d\u8f6f\u4ef6\u53ef\u4ee5\u901a\u8fc7\u4e0e AI \u804a\u5929\uff0c\u8bbe\u7f6e\u5f53\u524d Wi-Fi \u73af\u5883\u4e0b\u7684\u624b\u673a\u914d\u7f6e\uff0c\u4f8b\u5982\u97f3\u91cf\u3001\u4eae\u5ea6\u3001\u84dd\u7259\u5f00\u5173\uff1b\u5e76\u4e14\u5728\u4e0b\u6b21\u542f\u52a8\u65f6\uff0c\u4f1a\u81ea\u52a8\u5e94\u7528\u8fd9\u4e9b\u5df2\u4fdd\u5b58\u7684\u914d\u7f6e\u3002 For other questions, answer normally.
Also mention that the app can configure settings for both connecting to a Wi-Fi and disconnecting from a Wi-Fi.
""".trimIndent()
    }

    private fun buildAliasText(): String {
        return runCatching {
            WifiAliasRepo.getAllAliases()
                .takeIf { it.isNotEmpty() }
                ?.joinToString("\n") { "${it.alias} -> ${it.ssid}" }
                ?: "none"
        }.getOrDefault("none")
    }
}
