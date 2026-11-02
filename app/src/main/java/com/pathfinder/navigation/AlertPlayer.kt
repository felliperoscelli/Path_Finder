package com.pathfinder.navigation

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Som e vibração dos alertas locais. */
class AlertPlayer(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    /** Alarme de desvio: som de alarme (toca mesmo no modo silencioso de notificações) e vibração longa. */
    fun alarm() {
        vibrate(longArrayOf(0, 800, 300, 800, 300, 800, 300, 800))
        play(RingtoneManager.TYPE_ALARM, AudioAttributes.USAGE_ALARM, durationMs = 6_000)
    }

    /** Aviso curto: ponto alcançado. */
    fun chime() {
        vibrate(longArrayOf(0, 150, 100, 150))
        play(RingtoneManager.TYPE_NOTIFICATION, AudioAttributes.USAGE_NOTIFICATION, durationMs = 2_000)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
    }

    private fun vibrate(pattern: LongArray) {
        vibrator?.takeIf { it.hasVibrator() }?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun play(type: Int, usage: Int, durationMs: Long) {
        stopSound()
        val uri = RingtoneManager.getDefaultUri(type)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        val r = RingtoneManager.getRingtone(context, uri) ?: return
        r.audioAttributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        r.play()
        ringtone = r
        handler.postDelayed(::stopSound, durationMs)
    }

    private fun stopSound() {
        handler.removeCallbacksAndMessages(null)
        ringtone?.stop()
        ringtone = null
    }
}
