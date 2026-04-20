package com.u3coding.shaver.action


class RuleRepo {
    //使用一个一对多的结构按key为ssid,value是action列表
companion object {
     private val rulesMap = mutableMapOf<String, MutableList<Action>>()
    fun addRule(action: Action) {

        if (!rulesMap.containsKey(action.trigger)) {
            rulesMap[action.trigger] = mutableListOf()
        }
        //如果已经有相同的operation的action存在，覆盖它
        val existingActionIndex = rulesMap[action.trigger]?.indexOfFirst { it.operation == action.operation }
        if (existingActionIndex != null && existingActionIndex >= 0) {
            rulesMap[action.trigger]?.set(existingActionIndex, action)
        } else {
            rulesMap.get(action.trigger)?.add(action)
        }
    }

    fun getRules(ssid: String): List<Action> {
        if (rulesMap.containsKey(ssid)) {
            return rulesMap[ssid]?.toList() ?: emptyList()
        } else {
            return emptyList()
        }
    }
}
}