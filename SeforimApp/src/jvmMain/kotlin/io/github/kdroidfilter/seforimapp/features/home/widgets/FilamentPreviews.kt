package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// The gallery pictures of the Filament widgets, which a scaled-down thumbnail can't render live. Drawn, not
// screenshots: they stay sharp at any size and follow no texture that could go stale.

private val Space = Color(0xFF05070D)

/** The globe on its orbit, lit from the sun's side, the moon beside it. */
@Composable
internal fun EarthPreview(modifier: Modifier = Modifier) {
    WidgetCard(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Space)
            drawStars(seed = 1, count = 40)
            val r = min(size.width, size.height) * 0.3f
            val c = center
            drawOval(
                color = Color.White.copy(alpha = 0.18f),
                topLeft = Offset(c.x - r * 1.9f, c.y - r * 0.55f),
                size = Size(r * 3.8f, r * 1.1f),
                style = Stroke(width = 1.5f),
            )
            drawCircle(
                brush =
                    Brush.radialGradient(
                        listOf(Color(0xFF7FC4FF), Color(0xFF1E6FD9), Color(0xFF0B2A5B)),
                        center = Offset(c.x - r * 0.35f, c.y - r * 0.35f),
                        radius = r * 1.4f,
                    ),
                radius = r,
                center = c,
            )
            drawCircle(Color(0xFF3FA36B).copy(alpha = 0.55f), radius = r * 0.32f, center = Offset(c.x + r * 0.2f, c.y - r * 0.1f))
            drawCircle(Color(0xFFD9D9D9), radius = r * 0.16f, center = Offset(c.x + r * 1.7f, c.y + r * 0.2f))
        }
    }
}

/** A night sky from the ground: stars over a dusk horizon, a crescent moon. */
@Composable
internal fun SkyPreview(modifier: Modifier = Modifier) {
    WidgetCard(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF040816), Color(0xFF16204A), Color(0xFF6A4A7A))))
            drawStars(seed = 2, count = 60, maxY = 0.7f)
            val r = min(size.width, size.height) * 0.1f
            val moon = Offset(size.width * 0.72f, size.height * 0.28f)
            drawCircle(Color(0xFFF4EBC8), radius = r, center = moon)
            drawCircle(Color(0xFF0B1230), radius = r, center = moon + Offset(r * 0.45f, -r * 0.2f))
        }
    }
}

/** The sun and the orbits of the inner planets. */
@Composable
internal fun SolarSystemPreview(modifier: Modifier = Modifier) {
    WidgetCard(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Space)
            drawStars(seed = 3, count = 50)
            val unit = min(size.width, size.height) * 0.12f
            drawCircle(
                brush =
                    Brush.radialGradient(
                        listOf(Color(0xFFFFF2B0), Color(0xFFFFB12E), Color(0x00FF8A00)),
                        center = center,
                        radius =
                            unit * 1.6f,
                    ),
                radius = unit * 1.6f,
            )
            listOf(1.8f to Color(0xFFB0A9A0), 2.6f to Color(0xFFE8C07A), 3.5f to Color(0xFF4F9BFF), 4.4f to Color(0xFFD2694B))
                .forEachIndexed { i, (orbit, planet) ->
                    drawOval(
                        color = Color.White.copy(alpha = 0.16f),
                        topLeft = Offset(center.x - unit * orbit * 1.6f, center.y - unit * orbit * 0.7f),
                        size = Size(unit * orbit * 3.2f, unit * orbit * 1.4f),
                        style = Stroke(width = 1.2f),
                    )
                    val angle = 0.9f + i * 1.7f
                    val at =
                        Offset(
                            center.x + unit * orbit * 1.6f * cos(angle),
                            center.y + unit * orbit * 0.7f * sin(angle),
                        )
                    drawCircle(planet, radius = unit * 0.22f, center = at)
                }
        }
    }
}

private fun DrawScope.drawStars(
    seed: Int,
    count: Int,
    maxY: Float = 1f,
) {
    val random = Random(seed)
    repeat(count) {
        drawCircle(
            Color.White.copy(alpha = 0.3f + random.nextFloat() * 0.6f),
            radius = 0.6f + random.nextFloat() * 1.4f,
            center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height * maxY),
        )
    }
}
