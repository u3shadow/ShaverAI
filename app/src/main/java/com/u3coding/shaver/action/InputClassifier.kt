package com.u3coding.shaver.action

class InputClassifier {

    companion object{
      private  val commandKeywords = listOf("帮我"
            ,"自动"
            ,"进入"
            ,"连上"
            ,"在xxWiFi下"
            ,"调成"
            ,"设置"
            ,"音量"
              ,"打开"
          ,"关闭"
            ,"静音"
            ,"亮度")
        fun classify(input: String): InputType {
            //判断input中是否有commandKeywords中的关键词，如果有，就认为是一个合法的trigger
            var isCommand = false
             commandKeywords.forEach { keyword ->
                if (input.contains(keyword)) {
                    isCommand = true
                    return@forEach
                }
            }
           return if(isCommand) InputType.CommandChat else InputType.NormalChat
        }
    }
}
sealed class InputType {
    object NormalChat : InputType()
    object CommandChat : InputType()
}