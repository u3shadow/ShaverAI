package com.u3coding.shaver.data.repository

import com.google.gson.Gson
import com.u3coding.shaver.data.remote.AgentChatRequest
import com.u3coding.shaver.data.remote.AgentChatResponse
import com.u3coding.shaver.data.remote.BackendApi
import com.u3coding.shaver.data.remote.BackendErrorResponse
import com.u3coding.shaver.data.remote.BackendReply
import com.u3coding.shaver.data.remote.DeviceCallbackRequest
import com.u3coding.shaver.data.remote.DeviceCallbackResponse
import com.u3coding.shaver.data.remote.DeviceEventRequest
import com.u3coding.shaver.data.remote.DeviceEventResponse
import com.u3coding.shaver.data.remote.RuleCreateRequest
import com.u3coding.shaver.data.remote.RuleCreateResponse
import retrofit2.Response
import java.io.IOException

class BackendRepository(private val api: BackendApi) {
    private val gson = Gson()

    suspend fun chat(request: AgentChatRequest): BackendReply<AgentChatResponse> =
        requireBody(api.chat(request))

    suspend fun createRule(request: RuleCreateRequest): BackendReply<RuleCreateResponse> =
        requireBody(api.createRule(request))

    suspend fun reportEvent(request: DeviceEventRequest): BackendReply<DeviceEventResponse> =
        requireBody(api.reportEvent(request))

    suspend fun reportCallback(request: DeviceCallbackRequest): BackendReply<DeviceCallbackResponse> =
        requireBody(api.reportCallback(request.request_id, request))

    private fun <T> requireBody(response: Response<T>): BackendReply<T> {
        val requestId = response.headers()["X-Request-ID"].orEmpty()
        if (!response.isSuccessful) {
            val error = runCatching {
                gson.fromJson(response.errorBody()?.string(), BackendErrorResponse::class.java)
            }.getOrNull()
            val message = error?.message?.takeIf { it.isNotBlank() }
                ?: "服务器请求失败（HTTP ${response.code()}）"
            throw BackendApiException(message, error?.request_id ?: requestId)
        }
        val value = response.body() ?: throw BackendApiException("服务器返回空响应", requestId)
        return BackendReply(value, requestId)
    }
}

class BackendApiException(message: String, val requestId: String) : IOException(
    if (requestId.isBlank()) message else "$message (request_id=$requestId)"
)
