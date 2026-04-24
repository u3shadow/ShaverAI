package com.u3coding.shaver.action

sealed class AgentTask {
    data class ChatTask(
        val text: String,
        val wifiSsid: String?
    ) : AgentTask()

    data class CommandTask(
        val text: String,
        val wifiSsid: String?
    ) : AgentTask()

    data class SystemEventTask(
        val event: String,
        val wifiSsid: String?
    ) : AgentTask()
}