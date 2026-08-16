package com.pelonot.domain.identity

/**
 * A rider's face — the thing that makes *which of you is it* answerable at a
 * glance from two metres (PLAN 20.2).
 *
 * **A reference, never bytes** (20.2.2). This whole type serialises to one
 * short string in `profiles.avatar`; a database that carries photographs is a
 * database that cannot be exported, synced or backed up cheaply, and this app
 * already exports and syncs.
 *
 * **The set is checked in and drawn, not fetched** (20.2.1). The colours are
 * this project's own and the marks are Material icons already in the build, so
 * there is no licence question to answer and nothing to download — which
 * matters because the app starts a ride with no network and that is not
 * negotiable (19.4). The identicon route 20.2.1 offered as the alternative was
 * not taken: an identicon is a *hash*, so it cannot be chosen, and the whole
 * point of a face on a household bike is that the rider picked it.
 *
 * **Absent is a claim, and it is not the same claim as any value.** Null in
 * the column means *this rider has never chosen*, and [defaultFor] answers for
 * them from their own row id. It deliberately does not mean "colour 1": a
 * rider who has chosen the first colour and a rider who has chosen nothing are
 * different, and only the second may be re-answered if the default rule ever
 * changes. Same family as nullable `heartRateBpm` and `power_is_measured`.
 */
data class Avatar(
    val paint: AvatarPaint,
    /** The mark on the disc, or null for the rider's own initial. */
    val mark: AvatarMark? = null
) {

    /** The value written to `profiles.avatar`. See [parse] for the grammar. */
    fun store(): String = if (mark == null) paint.id else "${paint.id}:${mark.id}"

    companion object {
        /**
         * The separator between a colour and a mark.
         *
         * A future photograph avatar (20.2.4) is a **different scheme** and
         * must carry its own prefix rather than squeezing a filename in here;
         * the rule that keeps that safe is that [AvatarPaint] ids are a closed
         * set, so anything unrecognised falls through to [defaultFor] instead
         * of being mistaken for a colour.
         */
        private const val SEP = ':'

        /**
         * What a rider gets before they have chosen anything (20.2.3).
         *
         * Derived from the row id so it is stable for the life of the profile
         * and so a household of three gets three different colours without
         * anybody being asked a question on the way to their first ride. It is
         * the rule the profile selector already used, lifted out of that screen
         * and off the **power-zone palette** it was borrowing — see
         * [AvatarPaint] for why that was worth fixing rather than keeping.
         */
        fun defaultFor(localUserId: Int): Avatar {
            // Not `abs`: `abs(Int.MIN_VALUE)` is negative, and a row id is only
            // ever positive here, so the modulus is made safe rather than the
            // input assumed.
            val slot = (localUserId % AvatarPaint.entries.size + AvatarPaint.entries.size) %
                AvatarPaint.entries.size
            return Avatar(AvatarPaint.entries[slot])
        }

        /**
         * Reads the column back, falling through to [defaultFor] on anything
         * this build does not recognise.
         *
         * **It never throws and never returns null**, which is a deliberate
         * choice about a shared household tablet and a schema that will grow:
         * a value written by a newer version of the app degrades to a colour
         * rather than crashing the first screen anybody sees. The cost is that
         * a typo in `sqlite3` looks like a rider who never chose, which is a
         * far cheaper failure than the alternative.
         */
        fun parse(stored: String?, localUserId: Int): Avatar {
            val raw = stored?.trim().orEmpty()
            if (raw.isEmpty()) return defaultFor(localUserId)

            val paintId = raw.substringBefore(SEP)
            val paint = AvatarPaint.entries.firstOrNull { it.id == paintId }
                ?: return defaultFor(localUserId)

            val markId = raw.substringAfter(SEP, missingDelimiterValue = "")
            if (markId.isEmpty()) return Avatar(paint)

            // A known colour with an unknown mark keeps the colour: the rider
            // chose both, and throwing away the half we understand as well
            // would lose more than it protects.
            return Avatar(paint, AvatarMark.entries.firstOrNull { it.id == markId })
        }
    }
}

/**
 * The colours an avatar can be.
 *
 * **This palette is the avatar's own, and that is the point of it.** The
 * profile selector used to draw its discs in `PowerZone2Endurance` through
 * `PowerZone6Anaerobic` — the *zone* ramp, which this app builds meaning on
 * top of. Two consequences followed and both were live:
 *
 * 1. **A rider could be amber.** `PowerZone4Threshold` is the amber this app
 *    uses for *off target* (11.8.3), and `RiderScore`'s third rule already
 *    says out loud that a rider's identity must not wear the colour that means
 *    *you are wrong*. One in five profiles did.
 * 2. **It was the ramp making a claim it does not make.** The zone colours run
 *    cool through warm so intensity reads without the number, which is exactly
 *    why the heart-rate zones were deliberately given a *different* ramp
 *    (21.2.1): sharing a palette tells the reader the two are the same
 *    statement. A face is not an intensity.
 *
 * So none of these is a zone colour, none is a live-metric accent (those are
 * reserved for a number changing right now), and **none is amber or a
 * saturated orange**. Eight, because a household is three or four and eight
 * leaves room to be different from the housemate who chose first.
 *
 * The ids are what reach the database, so they are words rather than indices:
 * this project debugs in `sqlite3` constantly and `periwinkle` reads where
 * `c3` does not. **Never renumber or rename one** — the column stores the id,
 * and a rename silently re-colours every rider who chose it.
 */
enum class AvatarPaint(val id: String) {
    Periwinkle("periwinkle"),
    Turquoise("turquoise"),
    Leaf("leaf"),
    Rose("rose"),
    Lilac("lilac"),
    Sky("sky"),
    Sand("sand"),
    Slate("slate")
}

/**
 * The marks a rider can wear instead of their initial.
 *
 * **The initial is the default and stays the default** — it needs no choosing,
 * it is never ambiguous between two housemates with different names, and it is
 * what the screen has always drawn. A mark is for the rider who wants one, and
 * for the household where two people's names start with the same letter, which
 * is the case an initial genuinely cannot serve.
 *
 * Six, deliberately. This is a pick and not a judgement, so 26.3's argument
 * about ten answers where three will do does not apply with its full force —
 * but a grid of thirty is still a decision nobody asked for, and the honest
 * ceiling is *how many silhouettes are told apart at 30 dp from two metres*.
 *
 * Kept free of anything this app already uses to mean something: **no heart**
 * (that is the heart-rate accent and a live metric), **no flame**, which on a
 * fitness screen reads as effort rather than as a person, and **no arrows** —
 * ▲▼ belongs to the governing metric saying *go harder* (11.7).
 */
enum class AvatarMark(val id: String) {
    Bolt("bolt"),
    Mountain("mountain"),
    Paw("paw"),
    Note("note"),
    Rocket("rocket"),
    Coffee("coffee")
}
