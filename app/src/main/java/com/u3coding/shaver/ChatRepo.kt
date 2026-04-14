package com.u3coding.shaver

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.ResponseBody
import java.io.BufferedReader
import java.io.InputStreamReader

class ChatRepo (val api: API){
    val gson = Gson()
    suspend fun noStreamChat(input: String): String {
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
    fun streamChat(messages: List<ChatRequest.Message>): Flow<String> = flow {
        val request = ChatRequest(
            model = "deepseek-chat",
            messages = messages,
            stream = true
        )

        val responseBody: ResponseBody = api.streamChat(request)

        responseBody.use { body ->
            BufferedReader(InputStreamReader(body.byteStream())).use { reader ->
                var line: String?

                while (true) {
                    line = reader.readLine() ?: break

                    if (line.isBlank()) continue
                    if (line.startsWith(":")) continue   // keep-alive
                    if (!line.startsWith("data:")) continue

                    val data = line.removePrefix("data:").trim()

                    if (data == "[DONE]") break

                    try {
                        val chunk = gson.fromJson(data, StreamChatResponse::class.java)
                        val content = chunk.choices
                            .firstOrNull()
                            ?.delta
                            ?.content

                        if (!content.isNullOrEmpty()) {
                            emit(content)
                        }
                    } catch (_: Exception) {
                        // Skip malformed chunk and continue streaming.
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
