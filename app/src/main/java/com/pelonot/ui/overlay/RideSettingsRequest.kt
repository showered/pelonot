package com.pelonot.ui.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The overlay's route into the ride's settings (11.6.10).
 *
 * The overlay is a window drawn by the service, with no navigation and no
 * back stack, so it cannot open a sheet of its own — and it must not *end* the
 * ride to reach one, which is the whole defect. So it raises this flag and
 * brings the app forward through [AppForeground]; the ride screen is already on
 * top when it arrives, sees the flag, and opens the same sheet its own gear
 * button opens.
 *
 * A process-wide flag rather than an intent extra on purpose: `bringForward`
 * deliberately sends the launcher's own `ACTION_MAIN`, because that is what
 * resumes an existing task where the rider left it. Hanging an extra off it
 * would mean either changing that intent — and 11.1a's note explains what
 * `FLAG_ACTIVITY_CLEAR_TOP` does to a ride in progress — or threading
 * `onNewIntent` through the Activity, the nav graph and the ride screen for one
 * boolean. The overlay and the ride screen are the same process by construction:
 * the service that draws one hosts the other.
 *
 * One-shot. [consume] is called by whoever acts on it, so a rider who dismisses
 * the sheet and later returns to the app does not find it reopening at them.
 */
object RideSettingsRequest {

    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun request() {
        _pending.value = true
    }

    fun consume() {
        _pending.value = false
    }
}
