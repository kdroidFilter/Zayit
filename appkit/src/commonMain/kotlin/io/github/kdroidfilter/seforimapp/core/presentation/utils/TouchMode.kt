package io.github.kdroidfilter.seforimapp.core.presentation.utils

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastAll

/**
 * Whether the user is currently interacting by touch rather than with a mouse.
 *
 * Mirrors foundation's internal `isInTouchMode`: it turns `true` on a touch /
 * stylus event and flips back to `false` on the next mouse event. Hover-gated
 * affordances (e.g. the pane close buttons that only appear on mouse hover) read
 * this to stay visible under touch, where there is no persistent hover.
 *
 * The value is detected once near the window root via [Modifier.detectTouchMode]
 * and published here; read it anywhere with `LocalIsTouchMode.current`.
 */
val LocalIsTouchMode = staticCompositionLocalOf { false }

/**
 * Observes the active pointer modality on the [PointerEventPass.Initial] pass
 * (without consuming events, so children still receive them) and reports it via
 * [onChange]. Apply once on a window-spanning layout, then provide the value
 * through [LocalIsTouchMode].
 */
fun Modifier.detectTouchMode(onChange: (Boolean) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                // "Touch" = not an all-mouse event, exactly like foundation's
                // PointerEvent.isMouseOrTouchPad() negated.
                onChange(!event.changes.fastAll { it.type == PointerType.Mouse })
            }
        }
    }
