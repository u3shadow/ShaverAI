package com.u3coding.shaver.action

data class Action(val trigger:String, val operation:String, val params: Map<String,Any>)