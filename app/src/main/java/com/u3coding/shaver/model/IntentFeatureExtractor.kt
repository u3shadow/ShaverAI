package com.u3coding.shaver.model

class IntentFeatureExtractor {

    fun extract(text: String): FloatArray {
        return floatArrayOf(
            if (text.contains("音量")) 1f else 0f,
            if (text.contains("亮度")) 1f else 0f,
            if (text.contains("WiFi") || text.contains("wifi")) 1f else 0f,
            if (text.contains("帮我") || text.contains("设置") || text.contains("打开") || text.contains("关闭")) 1f else 0f,
            if (text.contains("自动") || text.contains("进入") || text.contains("连接")) 1f else 0f
        )
    }
}