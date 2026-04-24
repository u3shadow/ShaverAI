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
当前 Wi-Fi：公司WiFi
最近聊天记录：
用户：我回家了
AI：好的
当前用户输入：回家后打开蓝牙
返回：
{
  "trigger": "公司WiFi",
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
  "trigger": 公司WiFi,
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

    fun buildToolCallingPrompt(
        input: String,
        currentWifi: String?,
        messages: List<UiMessage>
    ): String {
        val wifiText = currentWifi?.takeIf { it.isNotBlank() } ?: "unknown"
        val historyText = buildRecentHistory(messages)

        return """
你是这个 Android App 的工具调用决策器。

你的任务是根据用户输入，判断应该直接聊天回复，还是调用一个手机配置工具。

必须严格遵守：
1. 只返回 JSON
2. 不要返回 markdown
3. 不要返回代码块
4. 不要返回解释说明
5. JSON 顶层必须包含 type 字段

当前 Wi-Fi：
$wifiText

最近聊天记录：
$historyText

当前用户输入：
$input

当用户想设置手机配置时，返回 tool_call：
{
  "type": "tool_call",
  "action": "set_volume",
  "params": {
    "value": 3
  }
}

当用户只是普通聊天、询问能力、或没有明确配置意图时，返回 chat_reply：
{
  "type": "chat_reply",
  "content": "我可以帮你设置音量和亮度。"
}

可用 action 只有：
1. set_volume：设置音量，params.value 为 0 到 100 的数字
2. set_brightness：设置亮度，params.value 为 0 到 100 的数字
3. open_bluetooth：打开蓝牙，params 返回空对象 {}
4. close_bluetooth：关闭蓝牙，params 返回空对象 {}

判断规则：
1. 用户明确要求设置音量、静音、调高音量、调低音量时，使用 set_volume
2. 用户明确要求设置亮度、调亮、调暗时，使用 set_brightness
3. 用户明确要求打开蓝牙时，使用 open_bluetooth
4. 用户明确要求关闭蓝牙时，使用 close_bluetooth
5. 用户问你能做什么时，返回 chat_reply，说明可以通过 AI 聊天设置当前 Wi-Fi 下的音量、亮度、蓝牙开关，并在下次启动时自动应用
6. 如果用户意图不明确，不要猜测工具调用，返回 chat_reply 继续询问

示例：

用户：把音量调到 3
返回：
{
  "type": "tool_call",
  "action": "set_volume",
  "params": {
    "value": 3
  }
}

用户：亮度设置成 30
返回：
{
  "type": "tool_call",
  "action": "set_brightness",
  "params": {
    "value": 30
  }
}

用户：打开蓝牙
返回：
{
  "type": "tool_call",
  "action": "open_bluetooth",
  "params": {}
}

用户：你能干什么
返回：
{
  "type": "chat_reply",
  "content": "我可以帮你通过聊天设置当前 Wi-Fi 环境下的手机配置，比如音量、亮度和蓝牙开关，并在下次启动时自动应用这些配置。"
}

现在请根据当前用户输入返回 JSON：
""".trimIndent()
    }

    fun buildAppCapabilityPrompt(): String {
        return """
你是这个 App 的助手。
当用户询问“你能做什么/这个软件能干什么”时，请明确回答：
目前软件可以通过与 AI 聊天，设置当前 Wi-Fi 环境下的手机配置，例如音量、亮度、蓝牙开关；
并且在下次启动时，会自动应用这些已保存的配置。
其他问题按正常助手方式回答。
""".trimIndent()
    }
}
