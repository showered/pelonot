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
    val ftpWatts: Int = 150,

    @ColumnInfo(name = "theme_preference")
    val themePreference: String = "DARK",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)