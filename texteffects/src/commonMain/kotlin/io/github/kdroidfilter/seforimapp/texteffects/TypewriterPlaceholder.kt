package io.github.kdroidfilter.seforimapp.texteffects

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun TypewriterPlaceholder(
    hints: List<String>,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    typingDelayMs: Long = 50L,
    deletingDelayMs: Long = 30L,
    holdDelayMs: Long = 1600L,
    preTypePauseMs: Long = 300L,
    postDeletePauseMs: Long = 250L,
    punctuationExtraDelayMs: Long = 100L,
    enabled: Boolean = true,
) {
    require(hints.isNotEmpty())

    var currentHintIndex by remember(hints) { mutableIntStateOf(0) }

    // Read ONLY in graphicsLayer — mutations invalidate the layer, not composition or layout
    val visibleLength = remember(hints) { mutableIntStateOf(0) }

    // Pre-computed horizontal positions per character, updated once per hint after layout
    var textOrigin by remember { mutableFloatStateOf(0f) }
    var clipEdges by remember { mutableStateOf(FloatArray(0)) }

    LaunchedEffect(hints, enabled) {
        if (!enabled) return@LaunchedEffect

        while (true) {
            val full = hints[currentHintIndex]

            delay(preTypePauseMs.milliseconds)

            // Type
            for (len in 1..full.length) {
                visibleLength.intValue = len
                val extra =
                    if (full[len - 1].isPunctuation()) punctuationExtraDelayMs else 0L
                delay((typingDelayMs + extra).milliseconds)
            }

            delay(holdDelayMs.milliseconds)

            // Delete
            for (len in full.length - 1 downTo 0) {
                visibleLength.intValue = len
                delay(deletingDelayMs.milliseconds)
            }

            delay(postDeletePauseMs.milliseconds)

            currentHintIndex = (currentHintIndex + 1) % hints.size
        }
    }

    BasicText(
        text = hints[currentHintIndex],
        onTextLayout = { result: TextLayoutResult ->
            val text = hints[currentHintIndex]
            textOrigin = result.getHorizontalPosition(0, usePrimaryDirection = true)
            clipEdges =
                FloatArray(text.length) { i ->
                    result.getHorizontalPosition(i + 1, usePrimaryDirection = true)
                }
        },
        style = textStyle,
        maxLines = 1,
        modifier =
            modifier.graphicsLayer {
                val edges = clipEdges
                val len = visibleLength.intValue
                if (len <= 0 || edges.isEmpty()) {
                    alpha = 0f
                } else {
                    alpha = 1f
                    clip = true
                    val origin = textOrigin
                    val edge = edges[min(len, edges.size) - 1]
                    shape =
                        object : Shape {
                            override fun createOutline(
                                size: Size,
                                layoutDirection: LayoutDirection,
                                density: Density,
                            ): Outline =
                                Outline.Rectangle(
                                    Rect(min(origin, edge), 0f, max(origin, edge), size.height),
                                )
                        }
                }
            },
    )
}

private fun Char.isPunctuation(): Boolean = this in ".!?…,;:"
