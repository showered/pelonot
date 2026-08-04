package com.pelonot.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "local_user_id")
    val localUserId: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "weight_kg")
    val weightKg: Double = 70.0,

    @ColumnInfo(name = "ftp_watts")
    val ftpWatts: Int = DEFAULT_FTP,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * The Supabase account this profile is signed in to, or null.
     *
     * **This is the app's consent gate** (PLAN 23.1.1). Null means the rider is
     * on the middle rung of the identity ladder — a real profile with a real
     * history, and no cloud at all. Not a degraded rider: the default one.
     *
     * It is deliberately per profile rather than per device. A household tablet
     * may hold two accounts, or one account and one rider who never wanted one,
     * and nothing may assume a single signed-in user per device (15.2.4).
     *
     * Nothing sets it yet — Phase 15 is where signing in gets built — so today
     * every profile is offline, which is what rule 1 of the connectivity model
     * asks for.
     */
    @ColumnInfo(name = "auth_user_id")
    val authUserId: String? = null,

    /**
     * Whether this rider's numbers appear on the screens the rest of the house
     * sees (PLAN 24.2.3).
     *
     * Privacy inside a household is still privacy, and it is the kind that gets
     * forgotten because everyone involved knows each other. One switch with one
     * meaning: a rider who turns it off is out of household social entirely —
     * the per-class leaderboard (24.1) as well as the dashboard's week (24.2) —
     * because "out of some of it" is not what anybody is asking for.
     *
     * It takes nothing away from them: their own history, their own dashboard
     * and their own trends are untouched. It is about what other people see.
     *
     * Defaults to true because every profile that already exists was created
     * when the household leaderboard already showed them, and defaulting to
     * false would quietly remove them from a board they are on today — a
     * migration deciding a rider's preference for them, in either direction, is
     * the thing to avoid.
     */
    @ColumnInfo(name = "household_visible")
    val householdVisible: Boolean = true,

    /**
     * The rider's own maximum heart rate, if they know it (21.1.3).
     *
     * **This is the primary input and the date below is the fallback**, not
     * the other way round: any age formula has a 10–12 bpm spread between
     * individuals at the same age, which is wider than a zone, so for a
     * meaningful fraction of riders an estimate produces the wrong zones
     * outright.
     *
     * Nullable, and null means *not given* — never a default. A rider with
     * neither this nor a date of birth gets **no** heart-rate zones, which is
     * an honest state with a screen of its own (21.3.3), and it is what stops
     * the app inventing a denominator the way it must not invent a heart rate.
     */
    @ColumnInfo(name = "max_hr_bpm")
    val maxHrBpm: Int? = null,

    /**
     * Date of birth, epoch milliseconds UTC — the fallback input for zones
     * (21.1.1, 21.1.2).
     *
     * A **full date stored as a date**, not an age: an age integer goes
     * silently stale on the rider's birthday, and a date picker is a control
     * everyone already knows where "what year were you born" is a field people
     * stop and think about.
     *
     * The app does not want anybody's birthday; it wants a maximum heart rate,
     * and age is only a proxy for one. Nullable for the rider who would rather
     * not say — and with [maxHrBpm] asked first, many riders are never asked
     * for it at all. Only the **year** may ever leave the tablet (21.1.1a): on
     * a bike this is a fitness input, and in a cloud row beside a display name
     * it is an identity field.
     */
    @ColumnInfo(name = "birth_date")
    val birthDate: Long? = null,

    /**
     * How the rider described their own riding when the profile was made
     * (20.3.2, Route B) — the `id` of a `FitnessLevel`, or null.
     *
     * The one input to the FTP estimate that is not collected for some other
     * reason, which is what made Route B affordable: weight was already here
     * and [birthDate] arrived for heart-rate zones (20.3.9).
     *
     * **Nullable, and stored rather than only used**, which are two separate
     * decisions. Nullable because every profile that already exists was never
     * asked and a backfilled guess is indistinguishable from an answer —
     * 11 → 12's argument repeated. Stored because 20.3.4 requires the app to be
     * able to say where an estimated FTP came from, and *"you told us you ride
     * regularly"* is that sentence; an estimate whose inputs were thrown away
     * is a number the rider cannot argue with.
     *
     * It is **not** re-read to move the FTP later. The estimate happens once,
     * at creation, and from then on the riding corrects it (20.3.5). Anything
     * that recomputed an FTP from this column would be deriving a rider's
     * recorded denominator from a self-assessment months old.
     */
    @ColumnInfo(name = "fitness_level")
    val fitnessLevel: String? = null
) {

    /** True when this rider has an account, and therefore a cloud. */
    val hasAccount: Boolean get() = authUserId != null

    companion object {
        const val DEFAULT_FTP = 150
        const val DEFAULT_WEIGHT_KG = 70.0
    }
}