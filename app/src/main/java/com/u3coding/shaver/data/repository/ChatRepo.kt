package com.u3coding.shaver.data.repository

import com.google.gson.Gson
import com.u3coding.shaver.data.remote.API
import com.u3coding.shaver.data.remote.ChatMessage
import com.u3coding.shaver.data.remote.ChatRequest
import com.u3coding.shaver.data.remote.StreamChatResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.ResponseBody
import java.io.BufferedReader
import java.io.InputStreamReader

class ChatRepo(private val api: API) {

    private val gson = Gson()

    fun streamChat(messages: List<ChatMessage>): Flow<String> = flow {
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
                    currentCoroutineContext().ensureActive()
                    line = reader.readLine() ?: break
                    currentCoroutineContext().ensureActive()
                    if (line.isBlank()) continue
                    if (line.startsWith(":")) continue
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
                            currentCoroutineContext().ensureActive()
                            emit(content)
                        }
                    } catch (_: Exception) {
                        // Ignore malformed chunk and keep streaming.
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
