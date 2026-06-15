package com.u3coding.shaver.action.parser

class InputClassifier {

    companion object {
        private val commandKeywords = listOf(
            "\u5e2e\u6211",
            "\u81ea\u52a8",
            "\u8fdb\u5165",
            "\u8fde\u4e0a",
            "WiFi",
            "wifi",
            "\u8c03\u6210",
            "\u8bbe\u7f6e",
            "\u97f3\u91cf",
            "\u6253\u5f00",
            "\u5173\u95ed",
            "\u9759\u97f3",
            "\u4eae\u5ea6",
            "\u84dd\u7259",
            "\u522b\u540d",
            "\u53eb\u505a",
            "\u53eb\u4f5c",
            "\u547d\u540d",
            "\u53d6\u540d"
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
