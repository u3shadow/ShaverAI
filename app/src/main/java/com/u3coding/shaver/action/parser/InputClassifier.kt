package com.u3coding.shaver.action.parser


class InputClassifier {

    companion object {
        private val commandKeywords = listOf(
            "帮我",
            "自动",
            "进入",
            "连上",
            "WiFi",
            "wifi",
            "调成",
            "设置",
            "音量",
            "打开",
            "关闭",
            "静音",
            "亮度",
            "蓝牙"
        )

        fun classify(input: String): InputType {
            val isCommand = commandKeywords.any { keyword ->
                input.contains(keyword, ignoreCase = true)
            }
            return if (isCommand) InputType.CommandChat else InputType.NormalChat
        }
    }
}

sealed class InputType {
    object NormalChat : InputType()
    object CommandChat : InputType()
}