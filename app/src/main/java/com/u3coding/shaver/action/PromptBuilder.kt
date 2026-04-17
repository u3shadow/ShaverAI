package com.u3coding.shaver.action

import com.u3coding.shaver.model.Role
import com.u3coding.shaver.model.UiMessage


class PromptBuilder {

    fun build(
        input: String,
        currentWifi: String?,
        messages: List<UiMessage>
    ): String {
        val wifiText = currentWifi?.takeIf { it.isNotBlank() } ?: "unknown"
        val historyText = buildRecentHistory(messages)

        return """
你是一个 Android 端自动化规则解析器。

你的任务是把用户输入解析成一个 JSON 对象，用于配置 Wi-Fi 场景规则。

你必须严格遵守以下要求：
1. 只返回 JSON
2. 不要返回解释
3. 不要返回 markdown
4. 不要返回代码块标记
5. 不要返回多余说明

当前 Wi-Fi：
$wifiText

最近聊天记录：
$historyText

当前用户输入：
$input

请返回 JSON，格式如下：
{
  "trigger": "string or null",
  "operation": "set_volume | set_brightness | open_bluetooth | close_bluetooth | null",
  "params": {
    "value": "number or null"
  }
}

字段规则：
1. trigger 表示 Wi-Fi 场景名称
2. 如果用户明确提到某个 Wi-Fi 名称，则 trigger 使用该名称
3. 如果用户没有明确提到 Wi-Fi 名称，但表达的是“当前环境/这里/这个 Wi-Fi 下/以后在这里”这类场景规则，且当前 Wi-Fi 已知，则 trigger 使用当前 Wi-Fi
4. 如果无法确定 Wi-Fi 场景，则 trigger 返回 null
5. operation 只能是以下值之一：
   - set_volume
   - set_brightness
   - open_bluetooth
   - close_bluetooth
6. 如果无法确定 operation，则返回 null
7. 如果动作不需要数值参数，params.value 返回 null
8. 如果用户表达“静音”，等价于：
   - operation = "set_volume"
   - params.value = 0

示例1：
当前 Wi-Fi：公司WiFi
最近聊天记录：
无
当前用户输入：公司WiFi 下把音量调成 0
返回：
{
  "trigger": "公司WiFi",
  "operation": "set_volume",
  "params": {
    "value": 0
  }
}

示例2：
当前 Wi-Fi：公司WiFi
最近聊天记录：
用户：我到公司了
AI：好的
当前用户输入：以后在这里把音量调成 0
返回：
{
  "trigger": "公司WiFi",
  "operation": "set_volume",
  "params": {
    "value": 0
  }
}

示例3：
当前 Wi-Fi：家里WiFi
最近聊天记录：
用户：我回家了
AI：好的
当前用户输入：回家后打开蓝牙
返回：
{
  "trigger": "家里WiFi",
  "operation": "open_bluetooth",
  "params": {
    "value": null
  }
}

示例4：
当前 Wi-Fi：unknown
最近聊天记录：
无
当前用户输入：以后在这里把亮度调成 80
返回：
{
  "trigger": null,
  "operation": "set_brightness",
  "params": {
    "value": 80
  }
}

示例5：
当前 Wi-Fi：公司WiFi
最近聊天记录：
无
当前用户输入：打开蓝牙
返回：
{
  "trigger": null,
  "operation": "open_bluetooth",
  "params": {
    "value": null
  }
}

现在请解析，并且只返回 JSON：
""".trimIndent()
    }

    private fun buildRecentHistory(messages: List<UiMessage>): String {
        val recentMessages = messages
            .filter { it.role == Role.USER || it.role == Role.ASSISTANT }
            .takeLast(4)

        if (recentMessages.isEmpty()) {
            return "无"
        }

        return recentMessages.joinToString("\n") { message ->
            val roleText = when (message.role) {
                Role.USER -> "用户"
                Role.ASSISTANT -> "AI"
                Role.SYSTEM -> "系统"
            }
            "$roleText：${message.content}"
        }
    }
}