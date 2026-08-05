package com.pelonot.domain.model

import java.util.Locale

/**
 * How distances, speeds and body weights are shown to the rider.
 *
 * Only a *display* concern. Everything stored — `workouts.total_distance_km`,
 * `profiles.weight_kg`, every metric sample — stays SI, and the conversion
 * happens at the last possible moment in `Formatters`. A unit preference that
 * reaches the database is a data-corruption bug waiting for the rider to change
 * their mind, and it would silently rewrite the meaning of every row already
 * recorded under the old setting.
 *
 * Power (W), cadence (RPM), heart rate (BPM) and output (kJ) have no imperial
 * form and are deliberately untouched.
 */
enum class UnitSystem(
    val displayName: String,
    val distanceLabel: String,
    val speedLabel: String,
    val weightLabel: String
) {
    METRIC("Metric", "km", "km/h", "kg"),
    IMPERIAL("Imperial", "mi", "mph", "lb");

    fun distanceFromKm(km: Double): Double =
        if (this == IMPERIAL) km * MILES_PER_KM else km

    fun speedFromKmh(kmh: Double): Double =
        if (this == IMPERIAL) kmh * MILES_PER_KM else kmh

    fun weightFromKg(kg: Double): Double =
        if (this == IMPERIAL) kg * POUNDS_PER_KG else kg

    /**
     * The other one (PLAN 11.6.19).
     *
     * For reading a number the other way round without changing anything: the
     * ride screen's distance tile flips to this on a tap and back on the next,
     * and **nothing is written** — the stored preference has one writer and it
     * is Settings.
     */
    val other: UnitSystem get() = if (this == METRIC) IMPERIAL else METRIC

    /** The inverse, for reading a weight the rider typed in their own units. */
    fun weightToKg(value: Double): Double =
        if (this == IMPERIAL) value / POUNDS_PER_KG else value

    companion object {
        const val MILES_PER_KM = 0.621371
        const val POUNDS_PER_KG = 2.2046226218

        val DEFAULT = METRIC

        /**
         * Countries whose riders expect miles.
         *
         * The UK is the interesting case and the reason this exists: it is
         * metric for almost everything and stubbornly imperial for road
         * distance, which is exactly the number this app shows. Body weight in
         * the UK is usually stones — not offered, because a two-part unit needs
         * its own input control and "14.2 st" means fourteen stone two pounds,
         * not 14.2 of anything. Pounds is the honest simplification.
         *
         * The US territories are listed explicitly rather than inferred: a
         * `Locale` of `en-PR` is a rider in Puerto Rico, where road signs are
         * in kilometres but everything else is US customary. Miles is the
         * closer guess for a bike ride.
         */
        private val IMPERIAL_COUNTRIES = setOf(
            "US", "GB", "LR", "MM", "PR", "GU", "VI", "AS", "MP"
        )

        /**
         * The unit system to start a fresh install with.
         *
         * Defaulting to metric regardless of locale is what the app did before,
         * and it is wrong for a large fraction of the audience — including on a
         * Peloton bike, whose own display is in miles.
         */
        fun fromLocale(locale: Locale = Locale.getDefault()): UnitSystem =
            if (locale.country.uppercase(Locale.ROOT) in IMPERIAL_COUNTRIES) IMPERIAL else METRIC

        fun fromName(name: String?): UnitSystem? =
            entries.firstOrNull { it.name == name }
    }
}
