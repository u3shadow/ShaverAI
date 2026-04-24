package com.u3coding.shaver.action.parser

class InputClassifier {

    companion object {
        private val commandKeywords = listOf(
            "\u5e2e\u6211", // bang wo
            "\u81ea\u52a8", // zi dong
            "\u8fdb\u5165", // jin ru
            "\u8fde\u4e0a", // lian shang
            "WiFi",
            "wifi",
            "\u8c03\u6210", // tiao cheng
            "\u8bbe\u7f6e", // she zhi
            "\u97f3\u91cf", // yin liang
            "\u6253\u5f00", // da kai
            "\u5173\u95ed", // guan bi
            "\u9759\u97f3", // jing yin
            "\u4eae\u5ea6", // liang du
            "\u84dd\u7259" // lan ya
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
