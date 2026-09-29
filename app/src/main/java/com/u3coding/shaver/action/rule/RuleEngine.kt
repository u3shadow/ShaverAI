package com.u3coding.shaver.action.rule

import android.content.Context
import com.u3coding.shaver.action.executor.ActionExecutor
import com.u3coding.shaver.action.model.Action

class RuleEngine(val context: Context) {
    private val executor = ActionExecutor(context)
    private var lastSceneKey = ""
    private var lastActions = emptyList<Action>()
    fun run(
        ssid: String,
        eventType: String = RuleEventType.WIFI_CONNECTED,
        skipBluetooth: Boolean = false
    ): RuleRunResult {
        val normalizedEventType = RuleEventType.normalize(eventType)
        val actions = RuleRepo.getRules(ssid, normalizedEventType).filter { action ->
            !skipBluetooth || !action.isBluetoothAction()
        }
        val sceneKey = "$normalizedEventType:$ssid"
        val sameAction = lastActions.size == actions.size&&lastActions.zip(actions).all { (a, b) ->
            a.trigger == b.trigger &&
                a.operation == b.operation &&
                a.params == b.params &&
                RuleEventType.normalize(a.eventType) == RuleEventType.normalize(b.eventType)
        }
        if (sceneKey == lastSceneKey&&sameAction) {
            return RuleRunResult.SkippedDuplicate
        }else{
            if (actions.size == 0){
                return RuleRunResult.NoRule
            }else {
                try {
                    actions.forEach {
                        if (!executor.execute(it)) {
                            return RuleRunResult.Failed("${it.operation} 未能执行，请检查相关设备权限")
                        }
                    }
                } catch (error: Exception) {
                    return RuleRunResult.Failed(error.message ?: "设备动作执行失败")
                }
                lastSceneKey = sceneKey
                lastActions = actions
                return RuleRunResult.Success(actions)
            }
        }
    }

}
private fun Action.isBluetoothAction(): Boolean {
    return operation == "open_bluetooth" || operation == "close_bluetooth"
}
sealed class RuleRunResult {
    object NoRule : RuleRunResult()
    object SkippedDuplicate : RuleRunResult()
    data class Success(val action: List<Action>) : RuleRunResult()
    data class Failed(val reason: String) : RuleRunResult()
}
