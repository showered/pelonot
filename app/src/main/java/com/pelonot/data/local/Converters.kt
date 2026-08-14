package com.pelonot.data.local

import androidx.room.TypeConverter
import com.pelonot.domain.model.MaxHeartRate
import com.pelonot.domain.model.PowerProvenance

/**
 * The two types this database stores as something other than themselves
 * (23.4.12, 21.4.2c).
 *
 * `workouts.power_provenance` is TEXT holding the enum's own name, which is not
 * an implementation detail: the cloud's copy of this column has been TEXT
 * constrained to those four words since 18.5
 * (`supabase/007_everyone_leaderboard.sql`), and `WorkoutDto` has always put
 * `PowerProvenance.name` on the wire. Storing an ordinal locally would give the
 * project two spellings of one fact and a migration to write the day somebody
 * reorders the enum.
 *
 * **A name the app no longer knows reads back as null**, rather than throwing.
 * Null already means *nobody wrote it down* and nothing may treat it as a claim
 * about the samples, so an unrecognised word degrades to the one value every
 * reader already handles. `valueOf` would instead crash whichever screen
 * happened to load that ride.
 */
object Converters {

    @TypeConverter
    fun fromPowerProvenance(provenance: PowerProvenance?): String? = provenance?.name

    @TypeConverter
    fun toPowerProvenance(name: String?): PowerProvenance? =
        name?.let { stored -> PowerProvenance.entries.firstOrNull { it.name == stored } }

    /**
     * `workouts.max_hr_source` (21.4.2c), stored the same way and for the same
     * reasons: the word rather than an ordinal, and an unknown word read back as
     * null.
     *
     * Null carries its own meaning here too — *nobody wrote it down* — and a
     * screen that cannot say where a maximum came from says nothing about it,
     * which is precisely the state this column exists to shrink rather than to
     * pretend away.
     */
    @TypeConverter
    fun fromMaxHrSource(source: MaxHeartRate.Source?): String? = source?.name

    @TypeConverter
    fun toMaxHrSource(name: String?): MaxHeartRate.Source? =
        name?.let { stored -> MaxHeartRate.Source.entries.firstOrNull { it.name == stored } }
}
