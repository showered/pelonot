package com.pelonot.ui.overlay

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Manages haptic feedback and TTS audio cues for zone alerts.
 */
class ZoneAlertManager(private val context: Context) {
    
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    
    private var tts: TextToSpeech? = null
    
    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
    }
    
    /**
     * Trigger haptic feedback for zone alert.
     */
    fun triggerHapticAlert() {
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(
                    100, // 100ms
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        }
    }
    
    /**
     * Speak zone change via TTS.
     */
    fun speakZoneChange(zone: Int) {
        tts?.speak(
            "Zone $zone",
            TextToSpeech.QUEUE_ADD,
            null,
            "zone_$zone"
        )
    }
    
    /**
     * Speak alert message via TTS.
     */
    fun speakAlert(message: String) {
        tts?.speak(
            message,
            TextToSpeech.QUEUE_ADD,
            null,
            "alert_${System.currentTimeMillis()}"
        )
    }
    
    fun release() {
        tts?.shutdown()
    }
}