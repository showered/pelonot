package com.pelonot.domain.model

/**
 * Which edge of the screen the floating HUD is pinned to.
 *
 * The HUD is not a free-floating window. A rider watching a film wants the
 * middle of the screen empty, so it spans one whole edge and nothing else —
 * dragging it snaps between the four rather than leaving a card parked over
 * someone's face.
 *
 * **Four edges rather than two (11.1b.4), asked for by the owner directly:**
 * *"I would like a version of the HUD on the right and left too, should be able
 * to drag it where you want."* On a 16:9 tablet in landscape a vertical strip
 * down one side is very likely the better default — it leaves subtitles *and*
 * faces clear, where a horizontal band can only ever miss one of them.
 *
 * A vertical dock is a genuinely different layout and not a rotation
 * (11.1b.5): the strip is [VERTICAL_WIDTH_DP] wide, and everything on it has a
 * tall arrangement of its own.
 */
enum class HudDock(val displayName: String) {
    Top("Top"),
    Bottom("Bottom"),
    Left("Left"),
    Right("Right");

    /** True for the two docks that run down a side rather than across an edge. */
    val isVertical: Boolean get() = this == Left || this == Right

    /**
     * The edge the class timeline takes, which is never the strip's own.
     *
     * For a horizontal dock that is the far side. For a vertical one it is the
     * **top**: time runs left to right, so the timeline stays a horizontal bar
     * rather than being stood on its end, and the bottom edge is where
     * subtitles live.
     */
    fun timelineEdge(): HudDock = when (this) {
        Top -> Bottom
        Bottom -> Top
        Left, Right -> Top
    }

    fun opposite(): HudDock = when (this) {
        Top -> Bottom
        Bottom -> Top
        Left -> Right
        Right -> Left
    }

    companion object {
        /** Subtitles live along the bottom edge; the HUD does not fight them. */
        val DEFAULT = Top

        /**
         * How wide the strip is when it runs down a side.
         *
         * Fixed rather than wrapped, and that is what makes the vertical layout
         * buildable at all: the re-flow has a width to design against, and the
         * timeline bar above it has a number to inset itself by so the two
         * never overlap in the corner.
         */
        const val VERTICAL_WIDTH_DP = 244

        /**
         * And how wide it is once it is collapsed (11.1b.11).
         *
         * **The owner's report on 11.1b.4, the morning after it landed:**
         * *"Compact mode when on left/right needs some work. Should be much more
         * compact width-wise."* [VERTICAL_WIDTH_DP] was one number doing two
         * jobs — it is the width the *expanded* re-flow was designed against,
         * and collapsed the widest thing on the strip was three 52 dp transport
         * buttons in a row. Stacking them is the other half of the note and is
         * what makes a second constant a genuinely narrower strip rather than a
         * tighter squeeze on the same layout.
         *
         * Still a constant, for [VERTICAL_WIDTH_DP]'s reason: `WRAP_CONTENT`
         * would hand the width of a rider's film back to whichever number the
         * strip happens to be showing. This one is set by the metric readouts —
         * a three-digit value beside its unit — and not by the buttons, which is
         * the measurement that decides it rather than an aesthetic.
         */
        const val VERTICAL_COLLAPSED_WIDTH_DP = 132

        /** Which of the two widths is live, given the state the strip is in. */
        fun widthDp(dock: HudDock, collapsed: Boolean): Int = when {
            !dock.isVertical -> 0
            collapsed -> VERTICAL_COLLAPSED_WIDTH_DP
            else -> VERTICAL_WIDTH_DP
        }

        fun fromName(name: String?): HudDock =
            entries.firstOrNull { it.name == name } ?: DEFAULT

        /**
         * Which edge a drag off the handle is asking for, or null for a drag
         * that is not asking for anything.
         *
         * Pure, so the rule can be tested without a window: the **dominant
         * axis** decides, so a drag that wanders is read as the direction it
         * mostly went rather than as whichever axis crossed first, and a drag
         * shorter than [threshold] moves nothing — brushing the handle mid-ride
         * must not send the strip across the screen. A drag back towards the
         * edge the strip is already on returns null rather than the current
         * dock, so the caller never re-lays-out a window for no reason.
         */
        fun dragTarget(current: HudDock, dx: Float, dy: Float, threshold: Float): HudDock? {
            val horizontal = kotlin.math.abs(dx) >= kotlin.math.abs(dy)
            val travel = if (horizontal) kotlin.math.abs(dx) else kotlin.math.abs(dy)
            if (travel < threshold) return null
            val target = when {
                horizontal && dx > 0 -> Right
                horizontal -> Left
                dy > 0 -> Bottom
                else -> Top
            }
            return target.takeIf { it != current }
        }
    }
}
