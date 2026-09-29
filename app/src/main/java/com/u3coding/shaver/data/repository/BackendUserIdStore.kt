package com.u3coding.shaver.data.repository

import android.content.Context
import java.util.UUID

class BackendUserIdStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "backend_identity",
        Context.MODE_PRIVATE
    )

    fun getOrCreate(): String {
        preferences.getString("user_id", null)?.let { return it }
        val userId = "android_${UUID.randomUUID().toString().replace("-", "")}"
        preferences.edit().putString("user_id", userId).apply()
        return userId
    }
}
