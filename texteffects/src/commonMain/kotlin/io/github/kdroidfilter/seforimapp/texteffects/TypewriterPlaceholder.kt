package io.github.kdroidfilter.seforimapp.texteffects

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

    var shown by remember(hints) { mutableStateOf("") }

    // Restart the entire animation when enabled changes — cancels cleanly via coroutine cancellation
    LaunchedEffect(hints, enabled) {
        if (!enabled) return@LaunchedEffect

        var idx = 0
        while (true) {
            val full = hints[idx]

            // Pre-type pause
            delay(preTypePauseMs)

            // Type characters
            for (i in 1..full.length) {
                shown = full.substring(0, i)
                val frames = typingFramesPerChar + if (full[i - 1].isPunctuation()) punctuationExtraFrames else 0
                repeat(frames) { withFrameNanos { } }
            }

            // Hold
            delay(holdDelayMs)

            // Delete characters
            for (i in full.length - 1 downTo 0) {
                shown = full.substring(0, i)
                repeat(deletingFramesPerChar) { withFrameNanos { } }
            }

            // Post-delete pause
            delay(postDeletePauseMs)

            idx = (idx + 1) % hints.size
        }
    }

    BasicText(text = shown, modifier = modifier, style = textStyle, maxLines = 1)
}

private fun Char.isPunctuation(): Boolean = this in ".!?…,;:"
