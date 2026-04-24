package com.u3coding.shaver.action.model

data class Action(val trigger:String, val operation:String, val params: Map<String,Any>)