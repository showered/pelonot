package com.pelonot.domain.export

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * One ride, reduced to what an export needs.
 *
 * A separate type from `WorkoutEntity` so everything in this file stays free of
 * Room and of Android, and is therefore JVM-testable.
 */
data class ExportRide(
    val id: String,
    val title: String,
    /** Epoch millis at which the ride *started*. */
    val startedAtMillis: Long,
    val durationSec: Int,
    val totalOutputKj: Double,
    val totalDistanceKm: Double,
    val avgPower: Double?,
    val avgCadence: Double?,
    val avgHeartRate: Double?,
    /**
     * How many seconds one exported sample stands for — 1 for a ride whose
     * record is intact, 10 for one 23.4 has condensed (23.4.3).
     *
     * *"Every chart **and export** says what resolution it is showing"* is the
     * item's own sentence, and the export is the half that leaves the app. A
     * file of 300 rows for a 25-minute ride, opened in a spreadsheet or handed
     * to Strava with nothing saying it is an outline, is the record's
     * provenance thrown away at exactly the moment nobody can ask this app
     * about it any more.
     */
    val detailSec: Int = 1
)

/** One second of the ride. [heartRateBpm] null means **unknown**, never zero. */
data class ExportSample(
    val timestampSec: Int,
    val cadenceRpm: Double,
    val resistancePercent: Double,
    val powerWatts: Double,
    val heartRateBpm: Int?
)

/** What a rider can take away, and what each is for. */
enum class ExportFormat(
    val displayName: String,
    val extension: String,
    val mimeType: String,
    val description: String
) {
    Csv(
        displayName = "CSV",
        extension = "csv",
        mimeType = "text/csv",
        description = "Every recorded second, for a spreadsheet or your own analysis"
    ),
    Tcx(
        displayName = "TCX",
        extension = "tcx",
        mimeType = "application/vnd.garmin.tcx+xml",
        description = "Garmin's format — Strava, TrainingPeaks and most of the rest read it"
    )
}

/**
 * Turns a ride into a file a rider can keep (12.4.3).
 *
 * This is an open-source app: not being able to get your own data out is the
 * thing the subscription product does. Both formats are written from the
 * **stored samples**, never from the aggregates — an export that disagreed with
 * the database would be worse than none, and this project has already been
 * bitten once by an aggregate that did not match the rows it summarised.
 */
object RideExport {

    /**
     * A filename that sorts by date and says which ride it was.
     *
     * Everything outside `[A-Za-z0-9-_]` goes: class titles are free text, and
     * a stray slash is the difference between a saved file and a silent failure.
     */
    fun filename(ride: ExportRide, format: ExportFormat): String {
        val stamp = FILE_STAMP.format(Date(ride.startedAtMillis))
        val slug = ride.title
            .replace(UNSAFE, "-")
            .trim('-')
            .take(48)
            .ifBlank { "ride" }
        return "pelonot-$stamp-$slug.${format.extension}"
    }

    /**
     * Every recorded second, one row each.
     *
     * Blank rather than `0` for an absent heart rate. Zero is a reading, and
     * writing one where no strap was paired puts a fabricated sample into a file
     * the rider may well average later.
     */
    fun csv(ride: ExportRide, samples: List<ExportSample>): String = buildString {
        appendLine("# Pelonot export — ${ride.title}")
        appendLine("# Started ${ISO_SECONDS.format(Date(ride.startedAtMillis))}")
        appendLine("# ${ride.durationSec} s, ${format(ride.totalOutputKj)} kJ")
        if (ride.detailSec > 1) {
            appendLine(
                "# Condensed to a ${ride.detailSec}-second outline: the lowest and " +
                    "highest watt of each ${ride.detailSec} seconds are kept and the " +
                    "seconds between them were removed."
            )
        }
        appendLine("elapsed_sec,cadence_rpm,resistance_percent,power_watts,heart_rate_bpm")
        samples.sortedBy { it.timestampSec }.forEach { sample ->
            append(sample.timestampSec)
            append(',')
            append(format(sample.cadenceRpm))
            append(',')
            append(format(sample.resistancePercent))
            append(',')
            append(format(sample.powerWatts))
            append(',')
            // Empty, not 0. See the KDoc above.
            appendLine(sample.heartRateBpm?.toString() ?: "")
        }
    }

    /**
     * Garmin Training Center XML, which is what Strava and the rest read.
     *
     * **No per-trackpoint `DistanceMeters`, deliberately.** `workout_metrics`
     * stores cadence, resistance, power and heart rate — there is no speed or
     * distance column, so a per-second distance could only be invented by
     * spreading the ride's total evenly across its seconds, which would describe
     * a ride nobody did. The lap carries the real total and the trackpoints
     * carry what was actually measured. Same family as 16.1.6: a missing column,
     * not a missing calculation. See 12.4.3a.
     */
    fun tcx(ride: ExportRide, samples: List<ExportSample>): String = buildString {
        val ordered = samples.sortedBy { it.timestampSec }
        val start = ride.startedAtMillis

        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine(
            """<TrainingCenterDatabase """ +
                """xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2" """ +
                """xmlns:ns3="http://www.garmin.com/xmlschemas/ActivityExtension/v2">"""
        )
        appendLine("  <Activities>")
        appendLine("""    <Activity Sport="Biking">""")
        appendLine("      <Id>${ISO_SECONDS.format(Date(start))}</Id>")
        appendLine("""      <Lap StartTime="${ISO_SECONDS.format(Date(start))}">""")
        appendLine("        <TotalTimeSeconds>${ride.durationSec}</TotalTimeSeconds>")
        appendLine("        <DistanceMeters>${format(ride.totalDistanceKm * 1000)}</DistanceMeters>")
        ride.avgHeartRate?.let {
            appendLine("        <AverageHeartRateBpm><Value>${it.toInt()}</Value>" +
                "</AverageHeartRateBpm>")
        }
        appendLine("        <Intensity>Active</Intensity>")
        appendLine("        <TriggerMethod>Manual</TriggerMethod>")
        appendLine("        <Track>")

        ordered.forEach { sample ->
            val at = ISO_SECONDS.format(Date(start + sample.timestampSec * 1000L))
            appendLine("          <Trackpoint>")
            appendLine("            <Time>$at</Time>")
            sample.heartRateBpm?.let {
                appendLine("            <HeartRateBpm><Value>$it</Value></HeartRateBpm>")
            }
            appendLine("            <Cadence>${sample.cadenceRpm.toInt()}</Cadence>")
            appendLine("            <Extensions>")
            appendLine("              <ns3:TPX>")
            appendLine("                <ns3:Watts>${sample.powerWatts.toInt()}</ns3:Watts>")
            appendLine("              </ns3:TPX>")
            appendLine("            </Extensions>")
            appendLine("          </Trackpoint>")
        }

        appendLine("        </Track>")
        appendLine("      </Lap>")
        // The one place a TCX can carry a sentence, so the outline says so
        // there rather than arriving at Strava looking like a sparse ride.
        val note = if (ride.detailSec > 1) {
            "${ride.title} — condensed to a ${ride.detailSec}-second outline"
        } else {
            ride.title
        }
        appendLine("      <Notes>${escape(note)}</Notes>")
        appendLine("    </Activity>")
        appendLine("  </Activities>")
        appendLine("</TrainingCenterDatabase>")
    }

    /**
     * Two decimal places, always with a dot.
     *
     * [Locale.US] explicitly: a French or German device formats 91.5 as "91,5",
     * which in a comma-separated file is two columns and in XML is a parse
     * error at the far end. Nothing about a rider's locale should reach a file
     * format's grammar.
     */
    private fun format(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private val ISO_SECONDS = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val FILE_STAMP = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US)

    private val UNSAFE = Regex("[^A-Za-z0-9-_]+")
}
