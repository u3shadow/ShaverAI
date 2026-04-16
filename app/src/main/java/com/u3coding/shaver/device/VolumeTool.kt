package com.u3coding.shaver.device

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlin.math.log

class VolumeTool(val context: Context) {
    fun setVolume(level: Int) {
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val soundlevel =
            (audioManager.getStreamMaxVolume(AudioManager.STREAM_RING) * (level.toFloat()  / 100)).toInt()
        audioManager.setStreamVolume(
            AudioManager.STREAM_RING,
            soundlevel,
            AudioManager.FLAG_PLAY_SOUND
        )
        Log.e("VolumeTool", "set volume to $level, actual level: $soundlevel")
    }
}