package com.pelonot.domain.model

import org.junit.Assert.*
import org.junit.Test

class StrapBatteryTest {
    @Test fun emptyMalformedAndReservedReadingsAreAbsent() {
        listOf(byteArrayOf(), byteArrayOf(1, 2), byteArrayOf(101), byteArrayOf(-1)).forEach {
            assertNull(StrapBattery.parse(it))
        }
    }
    @Test fun zeroIsARealReadingAndTwentyIsLow() {
        for (percent in 0..100) {
            assertEquals(percent, StrapBattery.parse(byteArrayOf(percent.toByte())))
            assertEquals(percent <= 20, StrapBattery.isLow(percent))
        }
    }
    @Test fun labelsAttributeTheReadingToTheStrap() {
        assertEquals("Strap reports 20% · Low battery", StrapBattery.label(20))
        assertEquals("Strap reports 21%", StrapBattery.label(21))
    }
}
