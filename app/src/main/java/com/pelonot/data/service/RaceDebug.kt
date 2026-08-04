package com.pelonot.data.service

/**
 * Lets the live leaderboard be *seen* on the emulator (PLAN 24.3.13a).
 *
 * The measured-power gate (24.3.7, 24.4.2) is what makes the race honest, and
 * it has a consequence the plan already names: **the emulator can only produce
 * simulated rides**, so every ride on an AVD is `PowerModel`'s output and the
 * board drops itself one second in. Which is the gate working — and it also
 * means the feature cannot be looked at anywhere except on a bike with a rider
 * on it, and CLAUDE.md is right that that is a perishable resource.
 *
 * So this is the same move as `COAST`, `CORRUPT` and `SILENCE`: a lever on the
 * emulator for a condition only the hardware can otherwise create. The
 * difference, and the reason it is worth stating, is **what it does not
 * touch**. It changes nothing that is written down: `power_is_measured` still
 * records what actually happened, sample by sample, so a ride recorded under
 * this lever is still correctly excluded from every board, every FTP proposal
 * and every calibration fit afterwards. It only stops the *live* comparison
 * refusing to draw itself while the ride is happening.
 *
 * That distinction is the whole safety argument. A lever that made a simulated
 * ride *claim* to be measured would poison the record permanently and would be
 * indistinguishable afterwards from a real one — which is precisely the class
 * of bug `power_is_measured` exists to prevent.
 *
 * Set from `adb`, and only from a debug build:
 *
 * ```bash
 * adb shell am broadcast -a com.pelonot.debug.RACE \
 *   -n com.pelonot/com.pelonot.debug.DebugTelemetryReceiver --ez on true
 * ```
 *
 * Lives in the main source set for the same reason
 * `SimulatedSensorSource.corruptFor` does: the receiver that sets it is debug
 * only, so nothing in a release build can reach it.
 */
object RaceDebug {

    /**
     * When true, a race is not abandoned because this ride's watts are
     * modelled. Off, and reset by nothing — a lever that quietly turned itself
     * off would be worse than one that has to be turned off.
     */
    @Volatile
    var ignoreMeasuredGate: Boolean = false
}
