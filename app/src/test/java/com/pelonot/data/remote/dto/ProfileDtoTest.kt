package com.pelonot.data.remote.dto

import com.pelonot.data.local.entity.UserEntity
import com.pelonot.domain.model.MaxHeartRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ProfileDtoTest {
    @Test
    fun onlyTheBirthYearLeavesTheTabletAndRestoresAsJanuaryFirst() {
        val original = utc(1986, Calendar.JUNE, 4)
        val dto = ProfileDto.from(
            UserEntity(name = "Rider", birthDate = original, maxHrBpm = null,
                fitnessLevel = "regular"),
            "account"
        )

        assertEquals(1986, dto.birthYear)
        assertEquals(utc(1986, Calendar.JANUARY, 1), dto.birthDateMs)
        assertEquals("regular", dto.fitnessLevel)
        assertEquals(MaxHeartRate.Source.Estimated,
            MaxHeartRate.resolve(dto.maxHrBpm, dto.birthDateMs)?.source)
    }

    @Test
    fun measuredMaximumTakesPriorityAndAbsentInputsDoNotEraseWebValues() {
        val measured = ProfileDto.from(
            UserEntity(name = "Rider", maxHrBpm = 183, birthDate = utc(1986, 0, 1)),
            "account"
        )
        assertEquals(MaxHeartRate.Source.Measured,
            MaxHeartRate.resolve(measured.maxHrBpm, measured.birthDateMs)?.source)
        assertEquals(183, measured.upsertFields()["max_hr_bpm"]?.toString()?.toInt())

        val absent = ProfileDto(id = "account", name = "Rider", ftpWatts = 150,
            weightKg = 70.0)
        assertNull(absent.birthDateMs)
        assertFalse(absent.upsertFields().containsKey("max_hr_bpm"))
        assertFalse(absent.upsertFields().containsKey("birth_year"))
        assertTrue(absent.upsertFields(includeEmptyHeartRate = true).containsKey("max_hr_bpm"))
        assertTrue(absent.upsertFields(includeEmptyHeartRate = true).containsKey("birth_year"))
    }

    private fun utc(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day)
        }.timeInMillis
}
