package com.pelonot.domain.coach

/**
 * What the coach just said, on screen for a few seconds (PLAN 11.8.4).
 *
 * The owner offered the idea as *"a designated space for 'subtitles'"* and
 * flagged it as possibly a bad one. 11.8.4's answer is that it is a good idea
 * **as captions** and a bad one as a motivational feed, and the distinction is
 * the whole of this file: everything here is a rendering of a cue the app was
 * already delivering out loud, at the moment it delivered it, and there is no
 * source of text that exists to fill the line. A slot that must be filled is
 * what produces sentences nobody needed.
 *
 * ### Why it is worth having at all
 *
 * `RideCoach` speaks over a film, and 11.5 exists because that is a fight the
 * voice does not always win. A rider with the tablet muted, riding at 6 am, or
 * deaf, gets nothing at all from a channel the app has already computed and
 * timed. So this is mostly a *rendering* of something that exists rather than a
 * feature — and everything it can say is something the rider has agreed to be
 * told, because it is exactly the set `RideCoachPolicy` already decided to say.
 *
 * ### Why it goes away
 *
 * [SHOW_SEC] rather than a line that persists to the end of the block. A text
 * line that is always on is a permanent draw on a screen read at two metres by
 * somebody at threshold, and this screen has been decluttered twice on the
 * owner's own reports. The policy is debounced to roughly one cue a block, so a
 * caption that shows for six seconds and then leaves is silent between cues by
 * construction rather than by a second rule.
 *
 * ### Why it is not on the overlay
 *
 * 24.1.5, and 11.8.4 is explicit that 24.3.16's overrule does not reach it: the
 * board that went back onto the strip is a *static* card, and this is moving
 * text over somebody's film. Nothing here is published to the HUD.
 */
data class RideCaption(
    /** What to draw. Never the speech string — see [RideAlert.caption]. */
    val text: String,
    /** The ride second it was raised, which is what [isVisibleAt] measures. */
    val raisedAtSec: Int
) {
    /**
     * Still worth drawing at [elapsedSec].
     *
     * Asked rather than scheduled: the ride's own ticker is the clock, so a
     * caption cannot outlive a pause and cannot be left on screen by a
     * cancelled timer.
     */
    fun isVisibleAt(elapsedSec: Int): Boolean = elapsedSec - raisedAtSec < SHOW_SEC

    companion object {
        /**
         * Six seconds — long enough to read *"Zone 4 · Threshold"* while
         * breathing hard, short enough that the line is absent for most of a
         * block.
         */
        const val SHOW_SEC = 6

        /**
         * The one worth showing out of a tick's alerts, or null.
         *
         * **The last one wins**, which matters on the tick where a block ends:
         * the policy can raise the change warning and the interval change in
         * the same second, and the caption a rider needs then is the block they
         * are now in rather than the one they were warned about. Alerts with no
         * caption — the silent countdown ticks — are skipped rather than
         * clearing the line, so a buzz does not wipe a sentence mid-read.
         */
        fun latest(alerts: List<RideAlert>, elapsedSec: Int): RideCaption? =
            alerts.lastOrNull { it.caption != null }
                ?.let { RideCaption(it.caption!!, elapsedSec) }
    }
}
