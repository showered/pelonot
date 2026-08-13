package com.pelonot.data.sensor

import java.io.IOException

/**
 * Raised when the sensor service is answering but the board behind it is not
 * (2.7d).
 *
 * This is the *informative* failure. Peloton's `SensorService` opens the
 * exclusive UART inside `onBind`, and when that open fails — because another
 * bike app has the port, or because the port leaked and nothing has released it
 * — the service still binds, still accepts our registration, and then answers
 * every poll with `TIME_OUT`. A steady stream of those means the service is
 * alive and the *board* is not reachable through it, which is a different
 * condition from a service that has died, and it has a different remedy.
 */
class SensorBoardNotAnswering(quietForMs: Long) :
    IOException("The sensor board answered nothing for ${quietForMs}ms")

/** Raised when there is no sensor service on this device to bind at all. */
class SensorServiceMissing(message: String) : IOException(message)

/**
 * Why the pipeline gave up, in terms a rider can act on.
 *
 * Deliberately a small enum rather than a string: the wording belongs to the
 * screen showing it, and the ride screen and the overlay have very different
 * amounts of room for it.
 */
enum class SensorUnavailableReason {
    /** The service is answering; the board behind it is not. */
    BoardNotAnswering,

    /** No Peloton sensor service on this device. */
    ServiceMissing,

    /** The source failed repeatedly without ever delivering a reading. */
    NeverStarted
}

/** What the retry policy decided to do about one failure. */
sealed interface Reconnect {
    /** Rebuild the source after [delayMs]. [attempt] counts from 1. */
    data class After(val delayMs: Long, val attempt: Int) : Reconnect

    /** Stop. Rebinding is not going to help, and each one costs something. */
    data class GiveUp(val attempts: Int, val reason: SensorUnavailableReason) : Reconnect
}

/**
 * The one retry schedule in this app, as pure arithmetic (2.7.7, 2.7.8).
 *
 * Split out of [SensorRepository] so it can be tested on the JVM — the
 * repository itself cannot be, because it logs — and because the interesting
 * part is a decision rather than a coroutine.
 *
 * **Two schedules, and which one applies is the whole point.** A bind that
 * delivered readings and then went quiet is 2.7.4's case: the board dropped
 * out mid-ride, it recovered on the bike at 122 s, and the right response is to
 * come back quickly and keep coming back for as long as the rider is pedalling.
 * A bind that delivered **nothing at all** is 2.7d's case: the service is
 * there, the port is not, and *every rebind reopens that port* — which is the
 * one thing that makes a leaked port worse rather than better. So a barren
 * attempt waits longer before the next try and there is a limited number of
 * them.
 *
 * **Giving up is the honest answer, not a shortcut.** On the bike this was
 * measured: after the second app was force-stopped, Pelonot sat dead on retry
 * attempt **141**, and force-stopping every client still did not release the
 * port — `SerialService` lives in `system_server`, so the tablet had to be
 * rebooted. A counter climbing past a hundred while the screen says
 * "reconnecting" is the app telling the rider something that is not true.
 */
class ReconnectPolicy(
    private val baseDelayMs: Long = BASE_DELAY_MS,
    private val barrenBaseDelayMs: Long = BARREN_BASE_DELAY_MS,
    private val maxDelayMs: Long = MAX_DELAY_MS,
    private val maxBarrenAttempts: Int = MAX_BARREN_ATTEMPTS
) {

    private var failures = 0
    private var barrenFailures = 0

    /** How many consecutive attempts have produced no reading at all. */
    val barrenAttempts: Int get() = barrenFailures

    /**
     * Decides what to do about a failed attempt.
     *
     * @param producedReadings whether *this* attempt delivered at least one
     *   reading before it failed. A productive attempt clears the history: a
     *   board that dropped out once and came back should not be met with a
     *   30-second wait the next time because the counter never reset.
     */
    fun onFailure(cause: Throwable, producedReadings: Boolean): Reconnect {
        if (producedReadings) {
            failures = 0
            barrenFailures = 0
        } else {
            barrenFailures++
        }
        failures++

        // Nothing to bind is not a thing waiting fixes, and it is the one
        // failure the rider cannot act on at all — say so at once rather than
        // after forty seconds of pretending.
        if (cause is SensorServiceMissing) {
            return Reconnect.GiveUp(failures, SensorUnavailableReason.ServiceMissing)
        }

        if (barrenFailures >= maxBarrenAttempts) {
            val reason = if (cause is SensorBoardNotAnswering) {
                SensorUnavailableReason.BoardNotAnswering
            } else {
                SensorUnavailableReason.NeverStarted
            }
            return Reconnect.GiveUp(failures, reason)
        }

        val base = if (producedReadings) baseDelayMs else barrenBaseDelayMs
        val shift = (failures - 1).coerceAtMost(MAX_SHIFT)
        return Reconnect.After((base shl shift).coerceAtMost(maxDelayMs), failures)
    }

    /** Forgets everything. Called when the pipeline is started afresh. */
    fun reset() {
        failures = 0
        barrenFailures = 0
    }

    private companion object {
        const val BASE_DELAY_MS = 1_000L
        const val MAX_DELAY_MS = 30_000L

        /**
         * The first wait after an attempt that produced nothing (2.7.8).
         *
         * Three times the productive one, because the failure it belongs to is
         * the one where trying again is actively harmful. Four barren attempts
         * on this schedule cost 3 + 6 + 12 + 24 s, so the app gives up after
         * about three quarters of a minute rather than at attempt 141.
         */
        const val BARREN_BASE_DELAY_MS = 3_000L

        const val MAX_BARREN_ATTEMPTS = 5
        const val MAX_SHIFT = 5
    }
}
