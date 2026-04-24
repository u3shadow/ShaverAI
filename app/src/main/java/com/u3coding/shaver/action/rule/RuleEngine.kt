package com.u3coding.shaver.action.rule

import android.content.Context
import com.u3coding.shaver.action.executor.ActionExecutor
import com.u3coding.shaver.action.model.Action

class RuleEngine(val context: Context) {
    private val executor = ActionExecutor(context)
    private var lastSSID = ""
    private var lastActions = emptyList<Action>()
    fun run(ssid:String): RuleRunResult {
        val actions = RuleRepo.getRules(ssid)
        val sameAction = lastActions.size == actions.size&&lastActions.zip(actions).all { (a, b) ->
            a.trigger == b.trigger && a.operation == b.operation && a.params == b.params
        }
        lastActions = actions
        if (ssid == lastSSID&&sameAction) {
            return RuleRunResult.SkippedDuplicate
        }else{
            lastSSID = ssid
            if (actions.size == 0){
                return RuleRunResult.NoRule
            }else {
                actions.forEach {
                    executor.execute(it)
                }
                return RuleRunResult.Success(actions)
            }
        }
    }

}
sealed class RuleRunResult {
    object NoRule : RuleRunResult()
    object SkippedDuplicate : RuleRunResult()
    data class Success(val action: List<Action>) : RuleRunResult()
    data class Failed(val reason: String) : RuleRunResult()
}
