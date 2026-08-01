package com.pelonot.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "class_templates")
data class ClassTemplateEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "duration_sec")
    val durationSec: Int,

    @ColumnInfo(name = "intervals_json")
    val intervalsJson: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * When this class stopped being part of the bundled library, or null while
     * it still is.
     *
     * The library was rebuilt in PLAN 23.2.6 under a new set of ids, and
     * `workouts.class_id` is a foreign key: a class a rider has actually
     * ridden has to keep existing or their history loses its link to what they
     * rode. Deleting it would be worse than useless — the foreign key is
     * `SET NULL`, so the ride would survive as a ride of nothing.
     *
     * A retired class is therefore kept and hidden: out of the library
     * browser, still resolvable by id from history. `ClassTemplateSeeder`
     * retires the ones a ride points at and deletes the ones nobody rode, so
     * a fresh install carries no ghosts at all.
     */
    @ColumnInfo(name = "retired_at")
    val retiredAt: Long? = null
)