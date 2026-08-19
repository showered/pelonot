package com.pelonot.domain.model

/**
 * What two rides are being compared *by* (PLAN 24.3.14).
 *
 * The owner settled the score itself — **total kilojoules for the class, the
 * number the real Peloton gives you** — and then asked for the shape rather
 * than only the answer: *"let's not rule out racing by OTHER metrics too, such
 * as distance, perhaps structure the data in an agnostic way like that."*
 *
 * Which is cheap, because both candidates are the same operation. A race is
 * always **one cumulative series against another, aligned by elapsed second**;
 * only the thing being accumulated differs, and [WorkoutAggregates.from]
 * already integrates both of these in one pass over the same samples with the
 * same five-second gap clamp.
 *
 * Two things worth knowing before adding a third value here.
 *
 * **The quantity must be monotonic in elapsed time**, or "who is ahead" stops
 * meaning anything: you cannot race by average power, because a rider can go
 * backwards on it by resting. Anything integrated over the ride qualifies;
 * anything averaged over it does not.
 *
 * **And [Distance] is a fiction, but a fair one.** It is [RoadSpeed] integrated
 * rather than measured — a stationary bike covers no ground. That is not the
 * `PowerModel` problem: there a *modelled* watt gets compared against a
 * *measured* one. Here both sides of the comparison run the same curve on the
 * same nominal rider, so the ranking is precisely as trustworthy as the watts
 * behind it. Which is now the whole of the caveat — see [requiresMeasuredPower].
 */
enum class RaceMetric {

    /** Cumulative kilojoules. The class score, and the default. */
    Output,

    /** Cumulative kilometres. */
    Distance;

    /**
     * Whether a ride has to have *measured* power to appear in this race
     * (24.4.2).
     *
     * [Output] is integrated power, so a ride whose watts came out of
     * `PowerModel` cannot be ranked beside one whose watts came off the board
     * — that is the whole of `PowerProvenance.isTrustworthyAsMeasured`, and it
     * is why most rides in the library have no ghost at all today.
     *
     * **[Distance] used to answer false, and 2.5a is why it no longer can.**
     * It was integrated *cadence*, measured on every ride this app has ever
     * recorded, so a distance race worked on rides the output race had to
     * exclude — which was the non-obvious reason this enum earned its place.
     * Distance is now a monotone function of power, so a modelled watt reaches
     * it exactly as it reaches [Output], and a board that let it through would
     * be ranking a `PowerModel` guess against the board's own watts.
     *
     * That is a real loss and 2.5a takes it deliberately: the property was
     * bought with a distance the owner measured as wrong by two and a half
     * times, and a populated board of wrong distances is worse than an empty
     * one. It stays a property rather than becoming a constant because a third
     * metric has to answer it, and because *why* both answer true is different
     * for each.
     */
    val requiresMeasuredPower: Boolean get() = true
}
