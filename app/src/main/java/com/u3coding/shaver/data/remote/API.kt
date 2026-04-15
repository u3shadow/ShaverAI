package com.u3coding.shaver.data.remote

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Streaming

interface API {
    @Headers("Content-Type: application/json")
    @POST("chat/completions")
    suspend fun chat(@Body request: ChatRequest): ChatResponse

    @Streaming
    @Headers("Content-Type: application/json")
    @POST("chat/completions")
    suspend fun streamChat(@Body request: ChatRequest): ResponseBody
}

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage,
    val system_fingerprint: String
) {
    data class Choice(
        val index: Int,
        val message: Message,
        val logprobs: Any?,
        val finish_reason: String
    ) {
        data class Message(
            val role: String,
            val content: String
        )
    }

    data class Usage(
        val prompt_tokens: Int,
        val completion_tokens: Int,
        val total_tokens: Int,
        val prompt_tokens_details: PromptTokensDetails,
        val prompt_cache_hit_tokens: Int,
        val prompt_cache_miss_tokens: Int
    ) {
        data class PromptTokensDetails(
            val cached_tokens: Int
        )
    }
}

data class StreamChatResponse(
    val id: String? = null,
    val `object`: String? = null,
    val created: Long? = null,
    val model: String? = null,
    val choices: List<Choice> = emptyList()
) {
    data class Choice(
        val index: Int? = null,
        val delta: Delta? = null,
        val finish_reason: String? = null
    )

    data class Delta(
        val role: String? = null,
        val content: String? = null,
        val reasoning_content: String? = null
    )
}
