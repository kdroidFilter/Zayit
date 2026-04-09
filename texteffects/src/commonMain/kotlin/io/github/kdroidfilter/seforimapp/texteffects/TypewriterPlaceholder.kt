package io.github.kdroidfilter.seforimapp.texteffects

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

@Composable
fun TypewriterPlaceholder(
    hints: List<String>,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    typingFramesPerChar: Int = 1,
    deletingFramesPerChar: Int = 1,
    holdDelayMs: Long = 1600L,
    preTypePauseMs: Long = 300L,
    postDeletePauseMs: Long = 250L,
    punctuationExtraFrames: Int = 4,
    enabled: Boolean = true,
) {
    require(hints.isNotEmpty())

    // Track current hint + visible length to avoid per-character String allocations in the loop
    var currentHintIndex by remember(hints) { mutableIntStateOf(0) }
    var visibleLength by remember(hints) { mutableIntStateOf(0) }

    LaunchedEffect(hints, enabled) {
        if (!enabled) return@LaunchedEffect

        while (true) {
            val full = hints[currentHintIndex]

            delay(preTypePauseMs)

            // Type
            for (len in 1..full.length) {
                visibleLength = len
                val frames = typingFramesPerChar + if (full[len - 1].isPunctuation()) punctuationExtraFrames else 0
                repeat(frames) { withFrameNanos { } }
            }

            delay(holdDelayMs)

            // Delete
            for (len in full.length - 1 downTo 0) {
                visibleLength = len
                repeat(deletingFramesPerChar) { withFrameNanos { } }
            }

            delay(postDeletePauseMs)

            currentHintIndex = (currentHintIndex + 1) % hints.size
        }
    }

    // Single substring allocation only on recomposition
    val text =
        remember(currentHintIndex, visibleLength) {
            hints[currentHintIndex].substring(0, visibleLength)
        }

    BasicText(text = text, modifier = modifier, style = textStyle, maxLines = 1)
}

private fun Char.isPunctuation(): Boolean = this in ".!?…,;:"
