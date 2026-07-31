package com.pelonot.domain.model

import kotlin.math.pow

/**
 * How see-through the floating HUD is allowed to be (11.1b.1, 11.1b.2).
 *
 * The rider is watching a film and the strip is sitting on top of it, so *some*
 * transparency is the whole point. The floor is the interesting part: a HUD
 * nobody can read at a glance has failed at the one thing it is for, and 8.11.82
 * made the same argument about colour.
 *
 * So the floor is **derived rather than guessed**, and derived against the
 * worst frame the film can produce rather than a convenient one. Everything
 * here is plain arithmetic on packed `0xRRGGBB` so it stays JVM-testable and
 * free of Android.
 */
object HudOpacity {

    /**
     * Translucent out of the box, and deliberately close to the floor.
     *
     * The HUD used to default to 0.99 — a solid panel — which is the right
     * answer for a control surface and the wrong one for something sitting on
     * top of a film the rider chose. They want their picture; the chips are
     * borrowing it. This leaves a little headroom above the calculated floor,
     * because that floor is derived from a still frame and a film moves.
     */
    const val DEFAULT = 0.82f

    const val OPAQUE = 1f

    /**
     * WCAG AA for **small** text.
     *
     * The big numbers would pass at [MIN_CONTRAST_LARGE], but the strip also
     * carries labels at 11sp ("CADENCE", "RPM") and a floor set by the largest
     * text on the panel would leave the smallest unreadable.
     */
    const val MIN_CONTRAST = 4.5

    /** WCAG AA for **large** text — the strip's 42sp Black metric numbers. */
    const val MIN_CONTRAST_LARGE = 3.0

    /** One colour the strip draws text in, and the contrast it has to reach. */
    data class TextSample(val rgb: Int, val target: Double = MIN_CONTRAST)

    /**
     * The worst contrast [text] can end up with, over any frame at all, when the
     * panel behind it is [panel] at [opacity].
     *
     * **The backdrop is a film, so it is every colour there is**, and the worst
     * one is not simply white. Composited through a partly-transparent panel,
     * the reachable backgrounds form a band between "over black" and "over
     * white"; if the text's own luminance falls *inside* that band then some
     * frame renders it invisible, and the answer is 1.0 — no contrast at all.
     * Outside the band, the worst frame is whichever end of it sits closer.
     *
     * Getting this wrong is not academic. Bisecting on the over-white contrast
     * alone made the coral power figure look *fine* at zero opacity, because
     * coral does read on white — it just vanishes on the mid-grey the same
     * setting produces from a mid-grey scene.
     */
    fun worstContrast(panel: Int, text: Int, opacity: Float): Double {
        val overWhite = relativeLuminance(composite(panel, opacity, WHITE))
        val overBlack = relativeLuminance(composite(panel, opacity, BLACK))
        val textLuminance = relativeLuminance(text)

        if (textLuminance in overBlack..overWhite) return 1.0

        return minOf(
            contrast(textLuminance, overWhite),
            contrast(textLuminance, overBlack)
        )
    }

    /**
     * The least opaque [panel] can be while [text] on it still reaches [target]
     * against any frame.
     *
     * Searched rather than solved: the relationship runs through sRGB's gamma
     * curve, and [worstContrast] climbs with opacity, so a bisection over 0..1
     * lands on it in a dozen iterations of arithmetic against an inverse nobody
     * would enjoy reading.
     */
    fun lowestReadable(panel: Int, text: Int, target: Double = MIN_CONTRAST): Float {
        if (worstContrast(panel, text, 0f) >= target) return 0f

        var low = 0f
        var high = OPAQUE
        repeat(24) {
            val mid = (low + high) / 2f
            if (worstContrast(panel, text, mid) >= target) high = mid else low = mid
        }
        return high
    }

    /**
     * The floor for a strip carrying **several** text colours (11.1b.2).
     *
     * The worst of them sets it. Deriving the floor from the brightest colour
     * alone — which the first version of this did — produces a number that is
     * honest about the clock and wrong about everything beside it: at 0.59 the
     * white clock passed at 4.5 and the coral power figure sat at 1.55.
     *
     * A colour that can be *moved* should be lifted instead of raising the floor
     * for everyone — see [liftToward]. That applies to the grey labels, which
     * carry no meaning in their greyness. It does not apply to the metric
     * accents: cadence is cyan and power is coral, and a rider reads those from
     * the corner of an eye.
     */
    fun lowestReadable(panel: Int, samples: List<TextSample>): Float =
        samples.maxOfOrNull { lowestReadable(panel, it.rgb, it.target) } ?: 0f

    /**
     * How far [from] must travel towards [to] to reach [target] behind a [panel]
     * at [opacity], as a 0..1 blend factor.
     *
     * Returns 1 when even [to] does not get there — the caller has run out of
     * room and it is the floor that has to move.
     */
    fun liftToward(from: Int, to: Int, panel: Int, opacity: Float, target: Double): Float {
        if (worstContrast(panel, from, opacity) >= target) return 0f
        if (worstContrast(panel, to, opacity) < target) return OPAQUE

        var low = 0f
        var high = OPAQUE
        repeat(20) {
            val mid = (low + high) / 2f
            if (worstContrast(panel, blend(from, to, mid), opacity) >= target) {
                high = mid
            } else {
                low = mid
            }
        }
        return high
    }

    /** Keeps a stored preference inside the readable range. */
    fun clamp(opacity: Float, floor: Float): Float =
        opacity.coerceIn(floor.coerceIn(0f, OPAQUE), OPAQUE)

    /** Straight-line blend between two packed colours, [t] of the way to [to]. */
    fun blend(from: Int, to: Int, t: Float): Int {
        val f = t.coerceIn(0f, OPAQUE)
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * f).toInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /**
     * [top] at [alpha] over an opaque [bottom].
     *
     * Composited in sRGB space rather than linear, because that is what the
     * renderer actually does — matching it matters more here than being
     * colourimetrically right, since the number this feeds is a promise about
     * what the rider will see.
     */
    fun composite(top: Int, alpha: Float, bottom: Int): Int {
        val a = alpha.coerceIn(0f, OPAQUE).toDouble()
        fun channel(shift: Int): Int {
            val t = (top shr shift) and 0xFF
            val b = (bottom shr shift) and 0xFF
            return (t * a + b * (1 - a)).toInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** WCAG contrast ratio, 1.0 (identical) to 21.0 (black on white). */
    fun contrastRatio(a: Int, b: Int): Double =
        contrast(relativeLuminance(a), relativeLuminance(b))

    /** WCAG relative luminance of a packed `0xRRGGBB`. */
    fun relativeLuminance(rgb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((rgb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(a: Double, b: Double): Double =
        (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)

    const val WHITE = 0xFFFFFF
    const val BLACK = 0x000000
}
