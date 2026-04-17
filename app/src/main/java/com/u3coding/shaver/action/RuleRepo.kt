package com.u3coding.shaver.action


class RuleRepo {
    //使用一个一对多的结构按key为ssid,value是action列表
companion object {
     private val rulesMap = mutableMapOf<String, MutableList<Action>>()
    fun addRule(action: Action) {
        if (!rulesMap.containsKey(action.trigger)) {
            rulesMap[action.trigger] = mutableListOf()
        }
        rulesMap.get(action.trigger)?.add(action)
    }

    fun getRules(action: Action): List<Action> {
        if (rulesMap.containsKey(action.trigger)) {
            return rulesMap[action.trigger]?.toList() ?: emptyList()
        } else {
            return emptyList()
        }
    }
}
}