package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The HUD's own colours, which is what the floor is actually derived from. */
private const val PANEL = 0x0C0C0E
private const val TEXT = 0xF4F4F5
private const val LABEL = 0xA1A1AA
private const val CORAL = 0xFF5252

class HudOpacityTest {

    @Test
    fun `luminance and contrast match the published anchors`() {
        assertEquals(0.0, HudOpacity.relativeLuminance(0x000000), 1e-9)
        assertEquals(1.0, HudOpacity.relativeLuminance(0xFFFFFF), 1e-9)
        assertEquals(21.0, HudOpacity.contrastRatio(0x000000, 0xFFFFFF), 1e-6)
        assertEquals(1.0, HudOpacity.contrastRatio(0x777777, 0x777777), 1e-9)
    }

    @Test
    fun `compositing runs from the backdrop to the panel`() {
        assertEquals(0xFFFFFF, HudOpacity.composite(PANEL, alpha = 0f, bottom = 0xFFFFFF))
        assertEquals(PANEL, HudOpacity.composite(PANEL, alpha = 1f, bottom = 0xFFFFFF))
        // Half way between 0x0C and 0xFF is 0x85, give or take the rounding.
        val half = HudOpacity.composite(PANEL, alpha = 0.5f, bottom = 0xFFFFFF)
        assertTrue(Integer.toHexString(half), ((half shr 16) and 0xFF) in 0x84..0x86)
    }

    /**
     * 11.1b.2. The point of the floor is that it is *derived*: whatever the
     * slider is dragged to, the strip's own text still passes AA against the
     * brightest frame a film can throw at it.
     */
    @Test
    fun `the floor is the least opaque the strip can be and still be readable`() {
        val floor = HudOpacity.lowestReadable(PANEL, TEXT)

        assertTrue("floor was $floor", floor in 0.5f..0.7f)

        val atFloor = HudOpacity.worstContrast(PANEL, TEXT, floor)
        assertTrue("contrast at the floor was $atFloor", atFloor >= HudOpacity.MIN_CONTRAST)

        // And a hair below it does not pass, or the floor is not the floor.
        val below = HudOpacity.worstContrast(PANEL, TEXT, floor - 0.02f)
        assertTrue("contrast just below the floor was $below", below < HudOpacity.MIN_CONTRAST)
    }

    /**
     * The backdrop is a film, so it is every colour there is. A colour that
     * reads beautifully on white is invisible on the mid-grey the same setting
     * produces from a mid-grey scene, and the floor has to know that.
     */
    @Test
    fun `a transparent strip is judged against the worst frame, not a white one`() {
        // Coral reads on white at better than 3:1 all by itself...
        assertTrue(HudOpacity.contrastRatio(CORAL, HudOpacity.WHITE) > 3.0)
        // ...and with no panel behind it there is a frame it disappears into.
        assertEquals(1.0, HudOpacity.worstContrast(PANEL, CORAL, 0f), 1e-9)
        assertTrue(HudOpacity.lowestReadable(PANEL, CORAL, 3.0) > 0.5f)
    }

    @Test
    fun `contrast only improves as the panel fills in`() {
        var previous = 0.0
        (0..20).forEach { step ->
            val current = HudOpacity.worstContrast(PANEL, TEXT, step / 20f)
            assertTrue("dipped at $step", current >= previous - 1e-9)
            previous = current
        }
    }

    /**
     * The defect this whole calculation exists to catch, and it was live for
     * one build: a floor set by the brightest colour on the strip says nothing
     * about the rest of it. At 0.585 the white clock passed at 4.50 and the
     * coral power figure sat at 1.55.
     */
    @Test
    fun `the worst colour on the strip sets the floor, not the brightest`() {
        val brightestOnly = HudOpacity.lowestReadable(PANEL, TEXT)
        val allOfThem = HudOpacity.lowestReadable(
            panel = PANEL,
            samples = listOf(
                HudOpacity.TextSample(TEXT),
                HudOpacity.TextSample(CORAL, HudOpacity.MIN_CONTRAST_LARGE)
            )
        )

        assertTrue("$allOfThem should be above $brightestOnly", allOfThem > brightestOnly)

        val coralAtFloor = HudOpacity.worstContrast(PANEL, CORAL, allOfThem)
        assertTrue("coral was $coralAtFloor", coralAtFloor >= HudOpacity.MIN_CONTRAST_LARGE)
    }

    /**
     * The grey labels have no meaning in their greyness, so they are lifted
     * rather than made everybody else's problem.
     */
    @Test
    fun `the label colour is lifted only as far as contrast needs`() {
        assertEquals(
            "an opaque strip needs no lift at all",
            0f,
            HudOpacity.liftToward(LABEL, TEXT, PANEL, 1f, HudOpacity.MIN_CONTRAST),
            1e-6f
        )

        val lift = HudOpacity.liftToward(LABEL, TEXT, PANEL, 0.76f, HudOpacity.MIN_CONTRAST)
        assertTrue("lift was $lift", lift > 0f)

        val lifted = HudOpacity.blend(LABEL, TEXT, lift)
        assertTrue(HudOpacity.worstContrast(PANEL, lifted, 0.76f) >= HudOpacity.MIN_CONTRAST)
        // And it stops short of simply becoming the primary colour, which would
        // flatten the strip's hierarchy for no gain.
        assertTrue("lift was $lift", lift < 1f)
    }

    @Test
    fun `blending runs end to end`() {
        assertEquals(LABEL, HudOpacity.blend(LABEL, TEXT, 0f))
        assertEquals(TEXT, HudOpacity.blend(LABEL, TEXT, 1f))
    }

    @Test
    fun `a stored preference is pulled back inside the readable range`() {
        val floor = HudOpacity.lowestReadable(PANEL, TEXT)

        assertEquals(floor, HudOpacity.clamp(0.1f, floor), 1e-6f)
        assertEquals(1f, HudOpacity.clamp(1.4f, floor), 1e-6f)
        assertEquals(0.8f, HudOpacity.clamp(0.8f, floor), 1e-6f)
    }
}
