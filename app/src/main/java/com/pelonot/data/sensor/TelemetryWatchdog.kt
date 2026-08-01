package com.pelonot.data.sensor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * Raised when a source that is supposed to be reporting has gone quiet.
 *
 * An [IOException] rather than a bespoke type so it reads as what it is to the
 * retry policy above it: a transport that has stopped working.
 */
class TelemetrySilence(silentForMs: Long) :
    IOException("No telemetry for ${silentForMs}ms; rebuilding the source")

/**
 * Makes silence an error (2.7.4).
 *
 * On the first real ride the board stopped delivering 86 seconds in and never
 * came back. **Pedalling did not revive it; only restarting the app did.** The
 * reason is that nothing failed: the flow did not throw, it simply stopped
 * emitting, so `retryWhen` never fired and nothing ever rebound. The source's
 * own `MAX_CONSECUTIVE_TIMEOUTS` counts explicit `TIME_OUT` replies from the
 * board, and silence is not one of those — a service that has stopped
 * answering sends no reply at all, including no timeout.
 *
 * So the absence of data has to be turned into something the retry policy can
 * see. Failing the flow tears the source down — which for the Peloton service
 * source means unbinding and losing the registration — and the single retry
 * policy in [SensorRepository] then rebuilds it from scratch. That ordering
 * matters: a rebuild that left the old registration alive would add a second
 * live one, which is the leading suspect for the field rotation in 2.7.1.
 *
 * Deliberately *not* a reconnect of its own. [SensorRepository] owns the one
 * retry schedule in this app; this operator only reports.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.failOnSilence(timeoutMs: Long): Flow<T> = flow {
    coroutineScope {
        val upstream = this@failOnSilence.produceIn(this)
        try {
            while (true) {
                val received = withTimeoutOrNull(timeoutMs) { upstream.receiveCatching() }
                    ?: throw TelemetrySilence(timeoutMs)

                // The upstream finishing or failing is its own business: pass
                // it on untouched so a real transport error keeps its cause.
                if (received.isClosed) {
                    received.exceptionOrNull()?.let { throw it }
                    break
                }
                emit(received.getOrThrow())
            }
        } finally {
            upstream.cancel()
        }
    }
}
