package com.pelonot.domain.identity

/**
 * A photograph a rider chose as their face — **the name of a file, never its
 * bytes** (PLAN 20.2.4).
 *
 * The rule is [Avatar]'s and it does not bend for a photograph: `profiles.avatar`
 * holds one short string. A database carrying photographs is one that cannot be
 * exported (12.4.4) or synced (15) cheaply, and this app does both. The pixels
 * live in app-private storage, written by `AvatarPhotoStore`, and this type is
 * the reference to them.
 *
 * **The name is checked rather than trusted, and the check is a fence rather
 * than tidiness.** This value reaches a `File(directory, name)`, and the column
 * it comes from is one this project edits by hand in `sqlite3` constantly — so
 * `../../databases/pelonot_database` is a string somebody could put there, and
 * without [of] it would be a path the app then reads and draws. Nothing about
 * that is exotic; it is the ordinary reason a filename from data is not a
 * filename. [of] returns null on anything that is not a plain name this app
 * itself would have written, and [Avatar.parse] treats null the way it treats
 * every other value it does not recognise: the rider falls back to a colour.
 *
 * The pattern is deliberately narrower than "a safe filename". It is *the shape
 * this app writes*, so a name that fails it is a name nothing here created.
 */
data class AvatarPhoto(val fileName: String) {

    companion object {

        /**
         * A photograph's name, or null if it is not one this app wrote.
         *
         * Null is the same answer as an unknown colour: not an error to be
         * reported, just a value the app does not recognise. See the class KDoc
         * for why it is checked at all.
         */
        fun of(raw: String?): AvatarPhoto? {
            val name = raw?.trim().orEmpty()
            return if (SHAPE.matches(name)) AvatarPhoto(name) else null
        }

        /**
         * `avatar-<profile>-<when>.jpg`.
         *
         * The profile's row id is in the name so the files of a household of
         * four can be told apart in `adb shell ls` without opening any of them,
         * and the timestamp is what makes replacing a photograph a *new* file:
         * a stable name would be the same path with different bytes, which is
         * how an image cache comes to draw the face a rider has just replaced.
         */
        private val SHAPE = Regex("""avatar-\d+-\d+\.jpg""")

        /** The name to write for [localUserId] now. */
        fun nameFor(localUserId: Int, atEpochMs: Long): AvatarPhoto =
            AvatarPhoto("avatar-$localUserId-$atEpochMs.jpg")
    }
}
