package com.u3coding.shaver.data.remote

data class AgentChatRequest(
    val user_id: String,
    val message: String,
    val device_context: DeviceContext? = null
)

data class DeviceContext(
    val wifi: String? = null
)

data class AgentChatResponse(
    val answer: String = "",
    val intent: String = "UNKNOWN",
    val type: String = "answer",
    val need_android_execute: Boolean = false,
    val action_plan: BackendActionPlan? = null,
    val errors: List<String> = emptyList()
)

data class BackendActionPlan(
    val intent: String,
    val need_android_execute: Boolean,
    val actions: List<BackendActionItem>,
    val execution_target: String,
    val requires_confirmation: Boolean = false
)

data class BackendActionItem(
    val action: String,
    val params: Map<String, Any?> = emptyMap()
)

data class RuleCreateRequest(
    val user_id: String,
    val name: String,
    val trigger: Map<String, Any?>,
    val action: Map<String, Any?>
)

data class RuleCreateResponse(
    val rule_id: String,
    val name: String
)

data class DeviceEventRequest(
    val user_id: String,
    val event_type: String = "wifi_connected",
    val params: Map<String, String>
)

data class DeviceEventResponse(
    val matched: Boolean,
    val action_plan: BackendActionPlan? = null
)

data class DeviceCallbackRequest(
    val request_id: String,
    val action: String,
    val status: String,
    val result: Map<String, Any?> = emptyMap(),
    val error: String? = null
)

data class DeviceCallbackResponse(
    val callback_id: String,
    val request_id: String,
    val recorded: Boolean
)

data class BackendErrorResponse(
    val code: String? = null,
    val message: String? = null,
    val request_id: String? = null
)

data class BackendReply<T>(val value: T, val requestId: String)
