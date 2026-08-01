package com.pelonot.data.sensor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 2.7.4 — a source that stops delivering has to become a source that failed.
 *
 * On the first real ride the board went quiet 86 seconds in and stayed quiet
 * for the rest of the class. Nothing threw, so `retryWhen` never fired and
 * nothing ever rebound; pedalling did not bring it back and only restarting
 * the app did. These run on virtual time, so a six-second timeout costs
 * nothing to test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryWatchdogTest {

    private val timeout = 1_000L

    private fun reading(value: Double) = SensorReading(
        powerWatts = value,
        cadenceRpm = value,
        resistancePercent = 30.0,
        timestampMs = 0L
    )

    @Test
    fun `a healthy source passes straight through`() = runTest {
        val source = flow {
            repeat(5) {
                emit(reading(it.toDouble()))
                delay(timeout / 4)
            }
        }

        val received = source.failOnSilence(timeout).toList()

        assertEquals(5, received.size)
    }

    @Test
    fun `silence becomes an error`() = runTest {
        val source = flow {
            emit(reading(1.0))
            delay(timeout * 10)
            emit(reading(2.0))
        }

        var failure: Throwable? = null
        val received = source.failOnSilence(timeout)
            .catch { failure = it }
            .toList()

        assertEquals(1, received.size)
        assertTrue("expected a TelemetrySilence, got $failure", failure is TelemetrySilence)
    }

    /**
     * The point of turning silence into an error at all: the retry policy
     * above it rebuilds the source, which for the Peloton service source means
     * a fresh bind — and, because the old flow is cancelled first, exactly one
     * live registration rather than two (2.7.1).
     */
    @Test
    fun `the retry policy rebuilds a source that has gone quiet`() = runTest {
        var subscriptions = 0
        val source = flow {
            subscriptions++
            emit(reading(subscriptions.toDouble()))
            // Never emits again, and never fails. This is what the board did.
            delay(Long.MAX_VALUE)
        }

        val received = source
            .failOnSilence(timeout)
            .retryWhen { _, _ -> true }
            .take(3)
            .toList()

        assertEquals(listOf(1.0, 2.0, 3.0), received.map { it.powerWatts })
        assertEquals(3, subscriptions)
    }

    @Test
    fun `a source that fails for its own reasons keeps its own cause`() = runTest {
        val source = flow<SensorReading> {
            emit(reading(1.0))
            throw IOException("the board unplugged itself")
        }

        var failure: Throwable? = null
        source.failOnSilence(timeout).catch { failure = it }.toList()

        assertTrue(failure is IOException)
        assertEquals("the board unplugged itself", failure?.message)
    }

    @Test
    fun `a source that simply finishes is not called silent`() = runTest {
        val source = flow {
            emit(reading(1.0))
            emit(reading(2.0))
        }

        var failure: Throwable? = null
        val received = source.failOnSilence(timeout).catch { failure = it }.toList()

        assertEquals(2, received.size)
        assertEquals(null, failure)
    }

    /**
     * The watchdog must not fire on a rider who has stopped pedalling. A bottle
     * stop still reports — cadence zero is a measurement, and 19.1.2 depends on
     * seeing it.
     */
    @Test
    fun `a rider standing still is not silence`() = runTest {
        val source = flow {
            repeat(6) {
                emit(reading(0.0))
                delay(timeout / 2)
            }
        }

        var failure: Throwable? = null
        val received = source.failOnSilence(timeout).catch { failure = it }.toList()

        assertEquals(6, received.size)
        assertEquals(null, failure)
    }
}
