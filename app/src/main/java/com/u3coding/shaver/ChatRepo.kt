package com.u3coding.shaver

class ChatRepo (val api: API){
    suspend fun streamChat(input: String): String {
        val request = ChatRequest(
            model = "deepseek-chat",
            messages = listOf(
                ChatRequest.Message(role = "system", content = "You are a helpful assistant.")
                ,ChatRequest.Message(role = "user", content = input)
            ),
            stream = false
        )
        val response = api.chat(request)
        return response.choices.firstOrNull()?.message?.content.orEmpty()
    }
}
