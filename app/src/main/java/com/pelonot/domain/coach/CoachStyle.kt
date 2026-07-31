package com.pelonot.domain.coach

/**
 * How insistently the ride is allowed to interrupt.
 *
 * The rider is usually watching something else on the same screen, so this is
 * a real preference rather than a nicety: a voice is invaluable when your eyes
 * are on a film, and intolerable when someone else is in the room.
 *
 * Note what is *not* configurable — the countdown into the next interval, and
 * what that interval will be, are always on screen. Everything here only
 * decides how hard the app taps the rider on the shoulder about it.
 */
enum class CoachStyle(
    val displayName: String,
    val description: String
) {
    /** Voice, motion and haptics. */
    Spoken(
        displayName = "Spoken",
        description = "Calls out each interval, the countdown and sustained drift from target"
    ),

    /** Motion and haptics only — the HUD moves to catch the eye instead. */
    Silent(
        displayName = "Silent",
        description = "No voice. The strip bounces and buzzes to catch your eye instead"
    ),

    /** Nothing but the numbers. */
    Off(
        displayName = "Off",
        description = "No voice, no buzz, no movement — the countdown alone"
    );

    val speaks: Boolean get() = this == Spoken
    val vibrates: Boolean get() = this != Off

    /** Whether the HUD is allowed to animate for attention. */
    val animates: Boolean get() = this != Off

    companion object {
        /** Silent by default: a bike in a shared room should not talk unasked. */
        val DEFAULT = Silent

        fun fromName(name: String?): CoachStyle =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
