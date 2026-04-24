package com.u3coding.shaver.action.model

data class ActionDTO(val trigger:String?, val operation:String?, val params: Map<String,Any>?){
    fun toAction(): Action{
        return Action(trigger ?: "", operation ?: "", params ?: emptyMap())
    }
}