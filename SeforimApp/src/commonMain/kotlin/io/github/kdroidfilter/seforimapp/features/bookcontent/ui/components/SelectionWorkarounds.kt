package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Drop-in replacement for [SelectionContainer] that works around a Compose crash when extending
 * selection with Shift+primary across multiple selectables in a virtualized list
 * (see Zayit issue #48). Shift+primary presses are consumed during the Initial pointer pass so
 * the inner [SelectionContainer] never runs the extend-selection path. Normal drag selection
 * and regular clicks are untouched.
 *
 * When upstream Compose fixes the bug, delete this file and replace call sites with
 * [SelectionContainer].
 */
@Composable
fun SafeSelectionContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val isShiftPrimaryPress =
                            event.buttons.isPrimaryPressed && event.keyboardModifiers.isShiftPressed
                        if (isShiftPrimaryPress) {
                            event.changes.forEach { it.consume() }
                        }
                    }
                },
    ) {
        SelectionContainer(content = content)
    }
}
