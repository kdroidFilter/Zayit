package io.github.kdroidfilter.seforimapp.texteffects

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

/** Shape that caches its [Outline] — zero allocation after the first [createOutline] call. */
private class CachedRectShape(
    private val left: Float,
    private val right: Float,
) : Shape {
    private var cached: Outline? = null
    private var cachedHeight = -1f

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (size.height == cachedHeight) return cached!!
        return Outline.Rectangle(Rect(left, 0f, right, size.height)).also {
            cached = it
            cachedHeight = size.height
        }
    }
}

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

    // Shapes array set once per hint in onTextLayout; the coroutine only updates currentShape
    var shapes by remember { mutableStateOf(emptyArray<Shape>()) }

    // Single state read in graphicsLayer — the only thing that changes per frame
    var currentShape by remember { mutableStateOf<Shape?>(null) }

    LaunchedEffect(hints, enabled) {
        if (!enabled) return@LaunchedEffect

        while (true) {
            val full = hints[currentHintIndex]

            delay(preTypePauseMs.milliseconds)

            // Type
            for (len in 1..full.length) {
                currentShape = shapes.getOrNull(len - 1)
                val extra =
                    if (full[len - 1].isPunctuation()) punctuationExtraDelayMs else 0L
                delay((typingDelayMs + extra).milliseconds)
            }

            delay(holdDelayMs.milliseconds)

            // Delete
            for (len in full.length - 1 downTo 0) {
                currentShape = if (len > 0) shapes.getOrNull(len - 1) else null
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
            val origin = result.getHorizontalPosition(0, usePrimaryDirection = true)
            shapes =
                Array(text.length) { i ->
                    val edge = result.getHorizontalPosition(i + 1, usePrimaryDirection = true)
                    CachedRectShape(min(origin, edge), max(origin, edge))
                }
        },
        style = textStyle,
        maxLines = 1,
        modifier =
            modifier.graphicsLayer {
                // Single state read — minimal snapshot overhead
                val s = currentShape
                if (s == null) {
                    alpha = 0f
                } else {
                    alpha = 1f
                    clip = true
                    shape = s
                }
            },
    )
}

private fun Char.isPunctuation(): Boolean = this in ".!?…,;:"
