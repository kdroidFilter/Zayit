package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/** In a 3D view's place while its Filament engine is being created, which no longer holds the UI. */
@Composable
internal fun WidgetLoader(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        // Read in draw only: the spin redraws, nothing recomposes
        val angle =
            rememberInfiniteTransition(label = "widgetLoader").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(LOADER_TURN_MS, easing = LinearEasing)),
                label = "widgetLoaderAngle",
            )
        Canvas(Modifier.size(20.dp)) {
            rotate(angle.value) {
                drawArc(
                    color = Color.White.copy(alpha = 0.7f),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}

private const val LOADER_TURN_MS = 900
