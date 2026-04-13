package com.u3coding.shaver

import android.R

class ChatRepo {
    suspend fun streamChat(input: String):List<String>{
        return listOf("这是", "AI", "的", "消息")
    }
}