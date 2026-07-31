package com.pelonot.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.pelonot.domain.model.PowerZone
import com.pelonot.ui.theme.color

/**
 * The badge that says which zone the rider is in — as a shape, not just a
 * number.
 *
 * Material 3 Expressive treats shape as a channel that carries meaning rather
 * than decoration, and this is the one place in the app where that pays for
 * itself: the HUD sits at the edge of a screen the rider is mostly not looking
 * at. Intensity is encoded three times over — colour, the digit, and how spiky
 * the badge is — so it reads from peripheral vision without being read.
 *
 * Zone 1 is a calm circle. Each zone up adds points and sharpens them, until
 * Zone 7 is a twelve-point star. Changing zone *morphs* between the two shapes
 * rather than swapping them, which is what makes the change catch the eye
 * with no sound and no text.
 */
@Composable
fun ZoneGlyph(
    zone: PowerZone,
    modifier: Modifier = Modifier,
    color: Color = zone.color,
    /** Spins slowly while the rider is in Zone 5+. Off for calmer zones. */
    rotating: Boolean = false,
    rotationDegrees: Float = 0f,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "ZoneGlyphColor"
    )

    // Morph progress is the zone number itself, so the shape travels through
    // the intermediate zones when the class jumps from Z2 to Z6.
    val morphTarget by animateFloatAsState(
        targetValue = zone.number.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ZoneGlyphShape"
    )

    val morphs = remember { ZoneShapes.morphs() }
    val spin = if (rotating) rotationDegrees else 0f

    Box(
        modifier = modifier.drawBehind {
            val path = ZoneShapes.pathFor(morphs, morphTarget, size)
            rotate(spin) { drawPath(path, animatedColor) }
        },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * A static [Shape] for one zone, for surfaces that want the zone silhouette
 * without the morph — the next-up preview, for instance.
 */
fun zoneShape(zone: PowerZone): Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = Outline.Generic(
        ZoneShapes.polygonPath(ZoneShapes.forZone(zone.number), size)
    )
}

/**
 * The polygon per zone, and the machinery to scale one into a layout box.
 *
 * The polygons are built once at unit radius around the origin and rescaled per
 * draw, because building a [RoundedPolygon] allocates and the HUD redraws
 * several times a second.
 */
private object ZoneShapes {

    /** Zone 1 through 7, calm to violent. */
    private val polygons: List<RoundedPolygon> by lazy {
        listOf(
            // Z1 — a circle. Nothing is being asked of the rider.
            RoundedPolygon.circle(numVertices = 12, radius = 1f),
            // Z2 — a soft hexagon.
            RoundedPolygon(numVertices = 6, radius = 1f, rounding = CornerRounding(0.5f, 1f)),
            // Z3 — a pentagon, corners starting to show.
            RoundedPolygon(numVertices = 5, radius = 1f, rounding = CornerRounding(0.35f, 0.8f)),
            // Z4 — threshold: a squircle, deliberately stable-looking.
            RoundedPolygon(numVertices = 4, radius = 1f, rounding = CornerRounding(0.3f, 0.6f)),
            // Z5 — points appear.
            RoundedPolygon.star(
                numVerticesPerRadius = 8,
                radius = 1f,
                innerRadius = 0.72f,
                rounding = CornerRounding(0.18f, 0.5f)
            ),
            // Z6 — sharper, more of them.
            RoundedPolygon.star(
                numVerticesPerRadius = 10,
                radius = 1f,
                innerRadius = 0.62f,
                rounding = CornerRounding(0.1f, 0.3f)
            ),
            // Z7 — neuromuscular. A saw blade.
            RoundedPolygon.star(
                numVerticesPerRadius = 12,
                radius = 1f,
                innerRadius = 0.5f,
                rounding = CornerRounding(0.05f, 0.1f)
            )
        // normalized() rescales each polygon into the unit square, so shapes
        // built at different radii all fill the same layout box.
        ).map { it.normalized() }
    }

    fun forZone(zoneNumber: Int): RoundedPolygon =
        polygons[(zoneNumber - 1).coerceIn(polygons.indices)]

    /** One morph per adjacent pair, so a jump of several zones travels through. */
    fun morphs(): List<Morph> =
        polygons.zipWithNext { from, to -> Morph(from, to) }

    /**
     * [zoneProgress] is a zone number as a float — 4.6 is most of the way from
     * Zone 4's squircle to Zone 5's star.
     */
    fun pathFor(morphs: List<Morph>, zoneProgress: Float, size: Size): Path {
        val clamped = zoneProgress.coerceIn(1f, polygons.size.toFloat())
        val index = (clamped.toInt() - 1).coerceIn(0, morphs.size - 1)
        val fraction = (clamped - (index + 1)).coerceIn(0f, 1f)
        val path = morphs[index].toPath(progress = fraction).asComposePath()
        return path.fitTo(size)
    }

    fun polygonPath(polygon: RoundedPolygon, size: Size): Path =
        polygon.toPath().asComposePath().fitTo(size)

    /**
     * Shapes are authored in a normalised 0..1 box; this stretches one to the
     * layout box it has actually been given.
     */
    private fun Path.fitTo(size: Size): Path {
        transform(Matrix().apply { scale(size.width, size.height) })
        return this
    }
}
