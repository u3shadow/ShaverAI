package com.u3coding.shaver

class ChatRepo {
    suspend fun streamChat(input: String): List<String> {
        return listOf("This ", "is ", "AI ", "message")
    }
}
