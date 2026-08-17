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
 * **The set is checked in and drawn, not fetched** (20.2.1, 20.6.1). The
 * colours are this project's own and the twenty faces are **Open Peeps by Pablo
 * Stanley, CC0**, vendored into `res/drawable-nodpi` by `avatars/fetch.sh` — a
 * script that runs by hand, once, and whose output is committed. Nothing in the
 * app calls an API, because it starts a ride with no network and that is not
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
    /** The face on the disc, or null for the rider's own initial. */
    val face: AvatarFace? = null,
    /**
     * A photograph of the rider, which wins over both of the above (20.2.4).
     *
     * **[paint] survives beside it rather than being replaced by it**, and that
     * is the whole reason this is a third field instead of a sealed type: the
     * file lives in app-private storage and the column lives in the database,
     * so the two can come apart — a database exported and imported on another
     * tablet (12.4.4) names a photograph that is not there. When it is missing
     * the rider falls back to the colour and initial they would otherwise have
     * had, which needs a colour to fall back *to*.
     */
    val photo: AvatarPhoto? = null
) {

    /** The value written to `profiles.avatar`. See [parse] for the grammar. */
    fun store(): String = when {
        photo != null -> "$PHOTO_PREFIX${photo.fileName}"
        face == null -> paint.id
        else -> "${paint.id}$SEP${face.id}"
    }

    companion object {
        /**
         * The separator between a colour and a face.
         *
         * The photograph avatar (20.2.4) carries its own prefix rather than
         * squeezing a filename in here; the rule that makes that safe is that
         * [AvatarPaint] ids are a closed set, so anything unrecognised falls
         * through to [defaultFor] instead of being mistaken for a colour.
         */
        private const val SEP = ':'

        /**
         * The photograph scheme's prefix — deliberately a word no colour is.
         *
         * `AvatarTest` asserts that, because the collision would be silent: a
         * paint called `photo` would make every rider who chose it read back as
         * a photograph nobody took, and the file lookup would then fail into
         * the default. `parse` reads this branch **first**, so the prefix wins
         * over the colour lookup rather than racing it.
         */
        internal const val PHOTO_PREFIX = "photo$SEP"

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

            if (raw.startsWith(PHOTO_PREFIX)) {
                // The colour under a photograph is the one the rider would have
                // had anyway, so a photograph that has gone missing degrades to
                // exactly the face of somebody who never chose — not to a
                // stranger's colour and not to a broken image.
                val photo = AvatarPhoto.of(raw.removePrefix(PHOTO_PREFIX))
                    ?: return defaultFor(localUserId)
                return defaultFor(localUserId).copy(photo = photo)
            }

            val paintId = raw.substringBefore(SEP)
            val paint = AvatarPaint.entries.firstOrNull { it.id == paintId }
                ?: return defaultFor(localUserId)

            val faceId = raw.substringAfter(SEP, missingDelimiterValue = "")
            if (faceId.isEmpty()) return Avatar(paint)

            // A known colour with an unknown face keeps the colour: the rider
            // chose both, and throwing away the half we understand as well
            // would lose more than it protects.
            //
            // **This clause is cashed in for the first time by 20.6.3.** It was
            // written for a *newer* app's value reaching an older build, and
            // the six icon marks it now catches are the opposite case — a
            // retired set being read by the build that retired it. A rider who
            // chose `rose:bolt` becomes rose with their own initial, and their
            // column is left exactly as they wrote it.
            return Avatar(paint, AvatarFace.entries.firstOrNull { it.id == faceId })
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
 * The faces a rider can wear instead of their initial (20.6.1).
 *
 * **Twenty drawn people, and they replace six Material icons.** The owner's
 * note on the set the fifty-eighth sitting built was *"the ones we have are not
 * good"* — a judgement about how the first screen anybody sees actually looks,
 * which is the one thing a session cannot take from a diff. The set is **Open
 * Peeps by Pablo Stanley, CC0**, chosen from five CC0 candidates by rendering
 * each on this app's own disc at **32 dp** as well as large: that is the
 * household row and the dashboard greeting, and it is where the line-art sets
 * collapse to a hairline. `avatars/README.md` has the comparison and the
 * licence.
 *
 * **The initial is still the default and still first in the picker.** It needs
 * no choosing, it is never ambiguous between two housemates with different
 * names, and it is what a rider who has never opened the dialog already has.
 * A face is for the rider who wants one — and for the household where two
 * names start with the same letter, the one case an initial genuinely cannot
 * serve.
 *
 * **Twenty, at the owner's own choice**, and it is more than 26.3's instinct
 * would allow because this is a *pick* rather than a judgement: the ceiling on
 * a pick is how many are told apart at a glance, and twenty different haircuts
 * are told apart where twenty shades of one colour are not.
 *
 * **The ids are the seeds that generated the images and they reach the
 * database.** Deliberately neutral words rather than indices or people's names:
 * this project debugs in `sqlite3` and `rose:lark` reads where `rose:f11` does
 * not, while a seed that is somebody's name puts a name on a stranger's face.
 * **Never rename or reorder one** — the column stores the id, and a rename
 * silently re-faces every rider who chose it.
 *
 * **`AvatarMark` is gone rather than renamed** (20.6.3). The six icon ids stop
 * being recognised, and [Avatar.parse]'s existing fallback is what makes that
 * safe: a rider who chose `rose:bolt` becomes rose with their own initial.
 * Nothing is migrated and no column is rewritten.
 */
enum class AvatarFace(val id: String) {
    Ash("ash"),
    Bay("bay"),
    Cove("cove"),
    Dune("dune"),
    Elm("elm"),
    Fern("fern"),
    Glen("glen"),
    Haze("haze"),
    Isle("isle"),
    Kite("kite"),
    Lark("lark"),
    Moss("moss"),
    Nova("nova"),
    Opal("opal"),
    Pine("pine"),
    Quill("quill"),
    Reed("reed"),
    Sage("sage"),
    Tide("tide"),
    Vale("vale")
}
