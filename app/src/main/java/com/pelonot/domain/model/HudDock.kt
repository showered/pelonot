package com.pelonot.domain.model

/**
 * Which edge of the screen the floating HUD is pinned to.
 *
 * The HUD is not a free-floating window. A rider watching a film wants the
 * middle of the screen empty, so it spans the full width of one edge and
 * nothing else — dragging it snaps between the two rather than leaving a card
 * parked over someone's face.
 */
enum class HudDock(val displayName: String) {
    Top("Top of the screen"),
    Bottom("Bottom of the screen");

    fun opposite(): HudDock = if (this == Bottom) Top else Bottom

    companion object {
        /** Subtitles live along the bottom edge; the HUD does not fight them. */
        val DEFAULT = Top

        fun fromName(name: String?): HudDock =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
