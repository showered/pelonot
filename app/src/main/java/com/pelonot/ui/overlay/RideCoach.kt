package com.pelonot.ui.overlay

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import com.pelonot.domain.coach.CoachStyle
import com.pelonot.domain.coach.HapticStrength
import com.pelonot.domain.coach.RideAlert
import java.util.Locale

/**
 * Speaks and buzzes. Decides nothing.
 *
 * Every judgement about *when* the ride should interrupt lives in
 * [com.pelonot.domain.coach.RideCoachPolicy], which is pure and unit-tested;
 * this class is the dumb output stage. Replaces `ZoneAlertManager`, which had
 * `triggerHapticAlert()` and `speakZoneChange(zone: Int)` and no caller — the
 * decision logic it would have needed did not exist.
 *
 * Speech is published with navigation-guidance audio attributes so the system
 * ducks whatever the rider is watching rather than talking over the top of it.
 */
class RideCoach(context: Context) {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    @Volatile
    private var ttsReady = false

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
        if (!ttsReady) Log.w(TAG, "Text-to-speech unavailable; coaching will be silent")
    }.apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                // Guidance rather than media, so the film ducks under it
                // instead of the cue being drowned by it.
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        language = Locale.getDefault()
    }

    /** How much the rider has asked to be interrupted. */
    @Volatile
    var style: CoachStyle = CoachStyle.DEFAULT

    fun deliver(alerts: List<RideAlert>) {
        if (alerts.isEmpty()) return

        alerts.forEach { alert ->
            if (style.vibrates) buzz(alert.haptic)
            val line = alert.speech
            if (style.speaks && !line.isNullOrBlank()) speak(line)
        }
    }

    private fun speak(line: String) {
        if (!ttsReady) return
        runCatching {
            // QUEUE_ADD: an interval announcement followed by its coaching cue
            // is two utterances that belong in that order.
            tts?.speak(line, TextToSpeech.QUEUE_ADD, null, "pelonot-${System.nanoTime()}")
        }.onFailure { Log.w(TAG, "Could not speak \"$line\"", it) }
    }

    private fun buzz(strength: HapticStrength) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when (strength) {
                    HapticStrength.Light -> VibrationEffect.createOneShot(35, 90)
                    // Two short pulses read as "something changed" far better
                    // than one long one, which reads as a notification.
                    HapticStrength.Firm -> VibrationEffect.createWaveform(
                        longArrayOf(0, 60, 70, 90), -1
                    )
                }
                device.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                when (strength) {
                    HapticStrength.Light -> device.vibrate(35)
                    HapticStrength.Firm -> device.vibrate(longArrayOf(0, 60, 70, 90), -1)
                }
            }
        }.onFailure { Log.w(TAG, "Could not vibrate", it) }
    }

    /** Cuts speech off immediately — used when a ride is stopped mid-sentence. */
    fun silence() {
        runCatching { tts?.stop() }
    }

    fun release() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ttsReady = false
    }

    private companion object {
        const val TAG = "RideCoach"
    }
}
