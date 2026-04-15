package com.u3coding.shaver

import android.R
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
/*{
    "model": "deepseek-chat",
    "messages": [
    {"role": "system", "content": "You are a helpful assistant."},
    {"role": "user", "content": "Hello!"}
    ],
    "stream": false
}*/
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean = false
) {
    data class Message(
        val id: String, val role: String, val content: String, var wifiName: String? = null, val time: Long = System.currentTimeMillis()
    )
}
/*
{
    "id": "94eef12a-6993-4fc2-8d6b-99cb7012d05a",
    "object": "chat.completion",
    "created": 1776154537,
    "model": "deepseek-chat",
    "choices": [
        {
            "index": 0,
            "message": {
                "role": "assistant",
                "content": "Hello! How can I assist you today? 😊"
            },
            "logprobs": null,
            "finish_reason": "stop"
        }
    ],
    "usage": {
        "prompt_tokens": 12,
        "completion_tokens": 11,
        "total_tokens": 23,
        "prompt_tokens_details": {
            "cached_tokens": 0
        },
        "prompt_cache_hit_tokens": 0,
        "prompt_cache_miss_tokens": 12
    },
    "system_fingerprint": "fp_eaab8d114b_prod0820_fp8_kvcache_new_kvcache_20260410"
}
*/
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
data class UiMessage(val id: String, val role: String, val content: String, val wifiName: String? = null, val status: String = "done",val time: Long = System.currentTimeMillis())

