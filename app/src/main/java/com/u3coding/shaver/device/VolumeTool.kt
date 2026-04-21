package com.u3coding.shaver.device

import android.content.Context
import android.media.AudioManager
import android.util.Log

class VolumeTool(val context: Context) {
    fun setVolume(level: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val clamped = level.coerceIn(0, 100)
        val streamTypes = listOf(
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_DTMF,
            AudioManager.STREAM_ACCESSIBILITY
        )
        streamTypes.forEach { stream ->
            try {
                val max = audioManager.getStreamMaxVolume(stream)
                if (max > 0) {
                    val value = (max * (clamped / 100f)).toInt().coerceIn(0, max)
                    val flags = if (stream == AudioManager.STREAM_RING) AudioManager.FLAG_PLAY_SOUND else 0
                    audioManager.setStreamVolume(stream, value, flags)
                }
            } catch (_: Exception) {
                // Ignore unsupported stream types on some devices.
            }
        }
        Log.e("VolumeTool", "set all stream volumes to $clamped%")
    }
}
