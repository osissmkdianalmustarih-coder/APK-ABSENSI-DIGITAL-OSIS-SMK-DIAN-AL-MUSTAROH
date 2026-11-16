package com.example.util

import android.media.AudioManager
import android.media.ToneGenerator

object SoundFeedback {
    fun playTone(success: Boolean, enabled: Boolean) {
        if (!enabled) return
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            if (success) {
                toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 180)
            } else {
                toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 240)
            }
        } catch (_: Exception) {
        }
    }
}
