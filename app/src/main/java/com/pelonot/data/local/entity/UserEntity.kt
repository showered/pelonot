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
    val authUserId: String? = null
) {

    /** True when this rider has an account, and therefore a cloud. */
    val hasAccount: Boolean get() = authUserId != null

    companion object {
        const val DEFAULT_FTP = 150
        const val DEFAULT_WEIGHT_KG = 70.0
    }
}