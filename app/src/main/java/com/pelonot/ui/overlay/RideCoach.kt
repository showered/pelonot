package com.pelonot.ui.overlay

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.pelonot.domain.coach.CoachStyle
import com.pelonot.domain.coach.HapticStrength
import com.pelonot.domain.coach.RideAlert
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Speaks and buzzes. Decides nothing.
 *
 * Every judgement about *when* the ride should interrupt lives in
 * [com.pelonot.domain.coach.RideCoachPolicy], which is pure and unit-tested;
 * this class is the dumb output stage. Replaces `ZoneAlertManager`, which had
 * `triggerHapticAlert()` and `speakZoneChange(zone: Int)` and no caller — the
 * decision logic it would have needed did not exist.
 *
 * Speech is published with navigation-guidance audio attributes *and* takes
 * transient audio focus for the length of each cue, so the film the rider is
 * watching dips under it rather than burying it.
 */
class RideCoach(context: Context) {

    private val appContext = context.applicationContext

    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /**
     * Guidance rather than media, so the system treats a cue as something to
     * hear over the top of a film rather than as a second film.
     */
    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

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

    private var tts: TextToSpeech? = null

    init {
        // Configuration has to happen in the init callback, not straight after
        // the constructor. TextToSpeech binds to the engine service
        // asynchronously, so the previous `.apply { language = … }` ran while
        // nothing was bound yet and was discarded with a warning the app never
        // read: "setLanguage failed: not bound to TTS engine", observed on the
        // bike's tablet. The audio attributes went the same way — which meant
        // the ducking this class exists to arrange was not actually requested.
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "Text-to-speech unavailable; coaching will be silent")
                return@TextToSpeech
            }

            runCatching {
                engine.setAudioAttributes(speechAttributes)

                // A device with no voice for the rider's locale is not a
                // reason to stay silent: the engine's own default still says
                // "Anaerobic Capacity" intelligibly, and a cue in the wrong
                // accent beats no cue at all on a surface you cannot look at.
                val locale = Locale.getDefault()
                if (engine.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) {
                    Log.w(TAG, "No TTS voice for $locale; using the engine default")
                }

                // Focus is held from the first cue until the last one finishes
                // speaking, so back-to-back utterances duck the film once
                // rather than flickering its volume between them.
                engine.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) = utteranceFinished()

                        @Deprecated("Required by the base class below API 21 semantics")
                        override fun onError(utteranceId: String?) = utteranceFinished()
                        override fun onError(utteranceId: String?, errorCode: Int) =
                            utteranceFinished()
                    }
                )
            }.onFailure { Log.w(TAG, "Could not configure text-to-speech", it) }

            ttsReady = true
        }
        tts = engine
    }

    // ── Audio focus ─────────────────────────────────────────────────

    /**
     * Nothing to do when focus changes: we are the transient requester, never
     * the one being ducked. A listener is required to make the request.
     */
    private val focusListener = AudioManager.OnAudioFocusChangeListener { }

    private val focusRequest: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(speechAttributes)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
        } else {
            null
        }

    /**
     * Utterances queued but not yet finished. Focus is taken on the way from
     * zero and given back on the way to zero.
     */
    private val speaking = AtomicInteger(0)

    /**
     * Ask the system to dip whatever else is playing.
     *
     * Audio *attributes* only describe the sound; they ask for nothing. Ducking
     * happens when someone requests AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, and
     * nothing here ever did — so on the bike, with Netflix playing, the cue was
     * spoken at full volume underneath a film at full volume and the rider
     * could not make it out. `dumpsys audio` showed Netflix holding GAIN with
     * `loss: none` throughout, and an empty ducked-players list.
     */
    private fun acquireAudioFocus() {
        val manager = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { manager.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(
                    focusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        }.onFailure { Log.w(TAG, "Could not take audio focus", it) }
    }

    private fun releaseAudioFocus() {
        val manager = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { manager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(focusListener)
            }
        }.onFailure { Log.w(TAG, "Could not release audio focus", it) }
    }

    private fun utteranceFinished() {
        // updateAndGet rather than decrementAndGet: an error callback arriving
        // after silence() has already reset the count must not drive it
        // negative and strand the film at a reduced volume for the rest of the
        // ride.
        if (speaking.updateAndGet { (it - 1).coerceAtLeast(0) } == 0) releaseAudioFocus()
    }

    /** How much the rider has asked to be interrupted. */
    @Volatile
    var style: CoachStyle = CoachStyle.DEFAULT

    /**
     * How loud the coach is, 0..1, applied per utterance (11.5.2).
     *
     * Deliberately not a stream volume. Moving a stream would fight the audio
     * focus ducking above — turning the coach down would turn the film down
     * with it — and it could never make the coach quieter *than* the film,
     * which is exactly what a rider who finds it shouty is asking for.
     */
    @Volatile
    var volume: Float = 1f

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

        val level = volume.coerceIn(0f, 1f)
        // Silenced by the slider. Returning before taking focus matters: an
        // inaudible utterance would still duck the rider's film for its whole
        // length, so they would lose the sound of the film and gain nothing.
        if (level <= 0f) return

        speaking.incrementAndGet()
        acquireAudioFocus()

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, level)
        }

        val queued = runCatching {
            // QUEUE_ADD: an interval announcement followed by its coaching cue
            // is two utterances that belong in that order.
            tts?.speak(line, TextToSpeech.QUEUE_ADD, params, "pelonot-${System.nanoTime()}")
        }.onFailure { Log.w(TAG, "Could not speak \"$line\"", it) }
            .getOrNull()

        // No utterance means no completion callback, so the count has to be
        // unwound here or the film stays ducked for the rest of the ride.
        if (queued != TextToSpeech.SUCCESS) utteranceFinished()
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
        // stop() drops the queue without reliably reporting each dropped
        // utterance, so the count is reset here rather than waited on. Leaving
        // it non-zero would hold the duck forever and quietly turn the rider's
        // film down for good.
        speaking.set(0)
        releaseAudioFocus()
    }

    fun release() {
        silence()
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
    }

    private companion object {
        const val TAG = "RideCoach"
    }
}
