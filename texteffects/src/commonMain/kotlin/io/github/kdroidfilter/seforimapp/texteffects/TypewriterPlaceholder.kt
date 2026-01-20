package io.github.kdroidfilter.seforimapp.texteffects

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

enum class Phase { PreTypePause, Typing, Holding, Deleting, PostDeletePause }

@Composable
fun TypewriterPlaceholder(
    hints: List<String>,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    typingDelayPerChar: Long = 75L, // slower typing
    deletingDelayPerChar: Long = 45L, // slower deleting
    holdDelayMs: Long = 1600L, // hold full text before deleting
    preTypePauseMs: Long = 500L, // pause before starting to type
    postDeletePauseMs: Long = 450L, // pause after deleting all
    speedMultiplier: Float = 1.0f, // global speed control ( >1 = slower )
    enabled: Boolean = true, // when false, freezes the animation
) {
    require(hints.isNotEmpty())

    // States
    var idx by remember(hints) { mutableStateOf(0) }
    var shown by remember(hints) { mutableStateOf("") }
    var phase by remember(hints) { mutableStateOf(Phase.PreTypePause) }

    val full = hints[idx]

    // Extra hold on punctuation (adds a small "breath" after typing punctuation)
    fun punctuationHold(c: Char): Long =
        when (c) {
            '.', '!', '?', '…' -> 180L
            ',', ';', ':' -> 120L
            else -> 0L
        }

    // Drive the animation
    LaunchedEffect(full, phase, shown, enabled) {
        if (!enabled) return@LaunchedEffect
        when (phase) {
            Phase.PreTypePause -> {
                delay((preTypePauseMs * speedMultiplier).toLong())
                phase = Phase.Typing
            }
            Phase.Typing -> {
                if (shown.length < full.length) {
                    val nextLen = shown.length + 1
                    val nextChar = full[nextLen - 1]
                    shown = full.substring(0, nextLen)

                    val base = typingDelayPerChar + punctuationHold(nextChar)
                    delay((base * speedMultiplier).toLong())
                } else {
                    phase = Phase.Holding
                }
            }
            Phase.Holding -> {
                delay((holdDelayMs * speedMultiplier).toLong())
                phase = Phase.Deleting
            }
            Phase.Deleting -> {
                if (shown.isNotEmpty()) {
                    shown = shown.dropLast(1)
                    delay((deletingDelayPerChar * speedMultiplier).toLong())
                } else {
                    phase = Phase.PostDeletePause
                }
            }
            Phase.PostDeletePause -> {
                delay((postDeletePauseMs * speedMultiplier).toLong())
                idx = (idx + 1) % hints.size
                phase = Phase.PreTypePause
            }
        }
    }

    BasicText(text = shown, modifier = modifier, style = textStyle, maxLines = 1)
}
