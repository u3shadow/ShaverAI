package com.u3coding.shaver.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface BackendApi {
    @POST("agent/chat")
    suspend fun chat(@Body request: AgentChatRequest): Response<AgentChatResponse>

    @POST("rules")
    suspend fun createRule(@Body request: RuleCreateRequest): Response<RuleCreateResponse>

    @POST("device/event")
    suspend fun reportEvent(@Body request: DeviceEventRequest): Response<DeviceEventResponse>

    @POST("device/callback")
    suspend fun reportCallback(
        @Header("X-Request-ID") requestId: String,
        @Body request: DeviceCallbackRequest
    ): Response<DeviceCallbackResponse>
}
