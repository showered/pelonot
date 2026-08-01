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
    val householdVisible: Boolean = true
) {

    /** True when this rider has an account, and therefore a cloud. */
    val hasAccount: Boolean get() = authUserId != null

    companion object {
        const val DEFAULT_FTP = 150
        const val DEFAULT_WEIGHT_KG = 70.0
    }
}