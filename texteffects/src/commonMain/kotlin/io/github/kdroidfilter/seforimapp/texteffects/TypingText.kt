/*
 * Copyright (C) 2024 Arjun Jadeja (arjunjadeja.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.kdroidfilter.seforimapp.texteffects

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay

/**
 * A composable function that simulates typing of the text with a delay between each character.
 *
 * @param text The text to be displayed with typing animation.
 * @param typingDelayPerChar The delay between each character typed, in milliseconds.
 * @param modifier The modifier to be applied to the text.
 * @param textStyle Style configuration for the text.
 * @param onTextLayout Optional callback for text layout information.
 * @param overflow How text overflow should be handled.
 * @param softWrap Whether text should wrap at soft line breaks.
 * @param maxLines The maximum number of lines to display.
 * @param minLines The minimum number of lines to display.
 * @param color Optional producer for text color.
 * @param onTextDisplay A callback triggered when the text is fully displayed.
 */
@Composable
fun TypingText(
    text: String,
    typingDelayPerChar: Long,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    color: ColorProducer? = null,
    onTextDisplay: () -> Unit = {},
) {
    var displayedText by remember { mutableStateOf("") }
    val currentOnTextDisplay by rememberUpdatedState(onTextDisplay)

    LaunchedEffect(text) {
        val stringBuilder = StringBuilder()
        text.forEach { char ->
            stringBuilder.append(char)
            displayedText = stringBuilder.toString()
            delay(typingDelayPerChar)
        }
        delay(typingDelayPerChar)
        currentOnTextDisplay()
    }

    BasicText(
        text = displayedText,
        modifier = modifier,
        style = textStyle,
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        color = color,
    )
}
