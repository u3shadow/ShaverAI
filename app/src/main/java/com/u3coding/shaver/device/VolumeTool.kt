package com.u3coding.shaver.device

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlin.math.roundToInt

class VolumeTool(val context: Context) {
    fun setVolume(level: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val clamped = level.coerceIn(0, 100)
        if (clamped > 0) {
            runCatching {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }.onFailure {
                Log.w(TAG, "Unable to switch ringer mode to normal", it)
            }
        }

        applyAllStreams(audioManager, clamped)
        sleepQuietly(VERIFY_DELAY_MS)
        applyAllStreams(audioManager, clamped)
        logCurrentVolumes(audioManager, clamped)
    }

    private fun applyAllStreams(audioManager: AudioManager, clamped: Int) {
        val streamTypes = listOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
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
                    val value = (max * (clamped / 100f)).roundToInt().coerceIn(0, max)
                    val flags = if (stream == AudioManager.STREAM_RING) AudioManager.FLAG_PLAY_SOUND else 0
                    audioManager.setStreamVolume(stream, value, flags)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to set stream volume: $stream", e)
            }
        }
    }

    private fun logCurrentVolumes(audioManager: AudioManager, targetPercent: Int) {
        val summary = listOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_SYSTEM
        ).joinToString(", ") { stream ->
            val current = runCatching { audioManager.getStreamVolume(stream) }.getOrDefault(-1)
            val max = runCatching { audioManager.getStreamMaxVolume(stream) }.getOrDefault(-1)
            "$stream=$current/$max"
        }
        Log.i(TAG, "target=$targetPercent%, actual=[$summary]")
    }

    private fun sleepQuietly(durationMs: Long) {
        try {
            Thread.sleep(durationMs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        private const val TAG = "VolumeTool"
        private const val VERIFY_DELAY_MS = 180L
    }
}
