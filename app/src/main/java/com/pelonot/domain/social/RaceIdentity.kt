package com.pelonot.domain.social

import com.pelonot.domain.identity.Avatar
import com.pelonot.domain.progress.RiderLevel

/**
 * Who a leaderboard row **is**, for the rows that are a person (PLAN 24.3.19).
 *
 * The owner sent a picture of Peloton's own board and asked for its row:
 * *"something like that for leaderboard please!"* — a face inside a progress
 * ring with a level on it, the name, the FTP under the name, and the output as
 * the large number on the right. Three of those four the board already had; the
 * face and the FTP are what this type carries.
 *
 * **It is nullable on every row that holds one, and null is the ordinary
 * case.** A live board is mostly *not* people: three of 24.3.12's four kinds of
 * row are the rider's own past rides, and 24.3.18's ghosts are numbers this app
 * made up. The rule 24.3.19a settles is that **a ghost is not a person, so it
 * gets no face and no level** — give it one and the board claims somebody who
 * does not exist. Two visibly different classes of row is the honest outcome
 * and is 24.3.18a's rule applied to a picture instead of a number.
 *
 * The rider's own past rides are the second half of that rule and it is the
 * same half: `Your best` is not a person either, and a face repeated four times
 * down a board is what 20.2.6a called decoration.
 *
 * **The FTP travels here and nowhere else on a social surface.** 26.4.8 keeps
 * the level and the FTP apart everywhere except two screens, and this is the
 * second of them — the argument at 24.3.19b is that publishing a *housemate's*
 * measurement needs their consent, and `household_visible` already is it
 * (24.2.3). A rider who is off the household panel is off this board too, in
 * the query rather than here.
 *
 * Pure and JVM-testable, like everything else in `domain/social`.
 */
data class RaceIdentity(
    /** The profile this row belongs to. Guests have none and get no identity. */
    val localUserId: Int,
    /**
     * **The rider's own name, which is not the row's label.**
     *
     * Carried because the face falls back to an initial when a rider has no
     * picture, and the row's label is not always their name: the rider's own
     * row says `YOU` (deliberately — `LiveStanding.YOU` is not a name so it can
     * never collide with one) and a housemate's activity row says *"Alex's last
     * ride"*. Drawing the label's initial put a **`Y`** on Robin's disc, which
     * is the same rider drawn two different ways on two screens. Seen on the
     * tablet AVD, and it could not have been found any other way — the row was
     * correct in every other respect.
     */
    val name: String,
    val avatar: Avatar,
    val level: RiderLevel,
    /**
     * Their functional threshold power, or null when there is nothing honest to
     * draw.
     *
     * Nullable rather than defaulted for the usual reason in this project:
     * `150` is `UserEntity.DEFAULT_FTP` and also a real answer, so a row that
     * cannot say would otherwise publish the default as a measurement of
     * somebody. Absent is a claim and it is a different claim from 150.
     */
    val ftpWatts: Int? = null
)
