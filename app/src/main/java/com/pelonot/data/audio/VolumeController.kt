package com.pelonot.data.audio

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The media volume, because on this tablet nothing else offers it.
 *
 * The bike's tablet runs with **no status bar**, so there is no shade to pull
 * down and no system volume panel — and the owner reports no physical rocker
 * either (`HARDWARE.md`). A rider watching Netflix with the coach speaking over
 * it has no way at all to change either level without leaving what they are
 * doing. That is why PLAN 11.5 is closer to fundamental than to polish.
 *
 * This governs `STREAM_MUSIC` only, which is what Netflix and everything else
 * plays on. The coach's own level is **not** here: it is a per-utterance
 * scalar on the `TextToSpeech` call, so that turning the coach down cannot
 * fight the ducking in 11.1.6 and can make the coach quieter *than* the film —
 * which a stream-level control could not do.
 */
class VolumeController(context: Context) {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val maxVolume: Int =
        (audioManager?.getStreamMaxVolume(STREAM) ?: 0).coerceAtLeast(1)

    private val _mediaVolume = MutableStateFlow(readMediaVolume())

    /** Media volume as 0..1, so the UI never has to know the device's step count. */
    val mediaVolume: StateFlow<Float> = _mediaVolume.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)

    /**
     * Why the last change did not take, or null.
     *
     * 11.5.7: `setStreamVolume` throws `SecurityException` while a Do Not
     * Disturb policy is active and the app has no notification-policy access.
     * Swallowing that would leave the slider sitting where the rider dragged it
     * while the volume had not moved at all — a control that lies about having
     * worked is worse than one that is absent.
     */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    val isAvailable: Boolean get() = audioManager != null

    /** Re-reads the system value, which anything else on the device may change. */
    fun refresh() {
        _mediaVolume.value = readMediaVolume()
    }

    fun setMediaVolume(fraction: Float) {
        val manager = audioManager ?: run {
            _lastError.value = "This device has no audio service."
            return
        }

        val steps = (fraction.coerceIn(0f, 1f) * maxVolume).toInt().coerceIn(0, maxVolume)
        try {
            // No FLAG_SHOW_UI: on a tablet with no status bar the system panel
            // is exactly the thing that does not appear, and asking for it on a
            // device that does have one would put a second slider over the
            // rider's film while they are already looking at ours.
            manager.setStreamVolume(STREAM, steps, 0)
            _lastError.value = null
        } catch (e: SecurityException) {
            Log.w(TAG, "Refused permission to set the media volume", e)
            _lastError.value =
                "Android would not let Pelonot change the volume — Do Not Disturb is " +
                    "usually the reason."
        }
        // Read back rather than trusting the write: the value that matters is
        // the one the system now holds, and it clamps and rounds its own way.
        refresh()
    }

    private fun readMediaVolume(): Float {
        val current = audioManager?.getStreamVolume(STREAM) ?: return 0f
        return (current.toFloat() / maxVolume).coerceIn(0f, 1f)
    }

    private companion object {
        const val TAG = "VolumeController"
        const val STREAM = AudioManager.STREAM_MUSIC
    }
}
