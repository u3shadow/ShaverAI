package com.u3coding.shaver

data class Message(val id: String, val role: String, val content: String,val wifiName: String? = null,val time: Long = System.currentTimeMillis())