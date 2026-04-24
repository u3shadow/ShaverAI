package com.u3coding.shaver.action

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class AgentTaskScheduler(
    private val scope: CoroutineScope,
    private val onChatTask: suspend (AgentTask.ChatTask) -> Unit,
    private val onCommandTask: suspend (AgentTask.CommandTask) -> Unit,
    private val onSystemEventTask: suspend (AgentTask.SystemEventTask) -> Unit
) {
    private val commandChannel = Channel<AgentTask.CommandTask>(Channel.UNLIMITED)
    private val systemEventChannel = Channel<AgentTask.SystemEventTask>(Channel.UNLIMITED)

    private var currentChatJob: Job? = null

    init {
        scope.launch {
            for (task in commandChannel) {
                onCommandTask(task)
            }
        }

        scope.launch {
            for (task in systemEventChannel) {
                onSystemEventTask(task)
            }
        }
    }

    fun submit(task: AgentTask) {
        when (task) {
            is AgentTask.ChatTask -> submitChatTask(task)
            is AgentTask.CommandTask -> submitCommandTask(task)
            is AgentTask.SystemEventTask -> submitSystemEventTask(task)
        }
    }

    private fun submitChatTask(task: AgentTask.ChatTask) {
        currentChatJob?.cancel()
        currentChatJob = scope.launch {
            onChatTask(task)
        }
    }

    private fun submitCommandTask(task: AgentTask.CommandTask) {
        scope.launch {
            commandChannel.send(task)
        }
    }

    private fun submitSystemEventTask(task: AgentTask.SystemEventTask) {
        scope.launch {
            systemEventChannel.send(task)
        }
    }

    fun stopCurrentChat() {
        currentChatJob?.cancel()
        currentChatJob = null
    }
}