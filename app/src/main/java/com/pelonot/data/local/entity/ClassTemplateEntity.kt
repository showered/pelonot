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

    /**
     * What this ride is for, and what it will feel like (PLAN 23.2.7).
     *
     * The only authored prose in the library. Everything else on this row is
     * *derived* from the blocks — the title, the duration, the shape sentence,
     * the chart — so the app can say a class is "20 min · Climbs · four hard
     * efforts" and cannot say why a rider would choose it. That gap is what
     * made the Start Class screen answer *"here are 52 numbers"* to somebody
     * meeting it for the first time (22.7.5).
     *
     * Empty rather than nullable, and the distinction is deliberate: there is
     * no third claim here. A class either has a description or has not been
     * given one yet, and both read as "nothing to show" — unlike
     * `target_position` or `heart_rate_bpm`, where absent means something.
     * `build.py`'s R13 refuses to write a bundle in which any class is empty.
     */
    @ColumnInfo(name = "description", defaultValue = "")
    val description: String = "",

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