package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Workaround for a Compose crash when extending selection with Shift+primary across multiple
 * selectables in a virtualized list. Consumes Shift+primary presses during the Initial pass so
 * the enclosing SelectionContainer never runs the extend-selection path. Normal drag selection
 * and regular clicks are left untouched.
 */
fun Modifier.consumeShiftPrimaryPress(): Modifier =
    this.pointerInput(Unit) {
        awaitEachGesture {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val isShiftPrimaryPress =
                event.buttons.isPrimaryPressed && event.keyboardModifiers.isShiftPressed
            if (isShiftPrimaryPress) {
                event.changes.forEach { it.consume() }
            }
        }
    }
