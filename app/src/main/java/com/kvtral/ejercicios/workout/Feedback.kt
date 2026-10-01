package com.kvtral.ejercicios.workout

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build

/** Pitidos y vibración para avisar de los cambios de fase sin mirar la pantalla. */
class Feedback(context: Context) {
    private val tone: ToneGenerator? =
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull()

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    fun countdown(sound: Boolean) {
        if (sound) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
    }

    fun phaseStart(type: PhaseType, sound: Boolean, vibrate: Boolean) {
        if (sound) {
            val t = if (type == PhaseType.WORK) ToneGenerator.TONE_CDMA_HIGH_L else ToneGenerator.TONE_PROP_BEEP2
            tone?.startTone(t, 450)
        }
        if (vibrate) vibrator?.vibrate(VibrationEffect.createOneShot(if (type == PhaseType.WORK) 400 else 200, 200))
    }

    fun finished(sound: Boolean, vibrate: Boolean) {
        if (sound) tone?.startTone(ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_PING_RING, 900)
        if (vibrate) vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 500), -1))
    }

    fun release() {
        tone?.release()
    }
}
