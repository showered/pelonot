package com.pelonot.domain.model

/** The strap's reported percentage, never an estimate of remaining ride time. */
object StrapBattery {
    fun parse(bytes: ByteArray): Int? = bytes.singleOrNull()?.toInt()?.and(0xff)?.takeIf { it <= 100 }
    fun isLow(percent: Int): Boolean = percent in 0..20
    fun label(percent: Int): String = if (isLow(percent)) "Strap reports $percent% · Low battery" else "Strap reports $percent%"
}
