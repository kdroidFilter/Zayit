package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import dev.nucleusframework.window.tao.LocalTaoWindow
import dev.nucleusframework.window.tao.TabDragOrigin
import dev.nucleusframework.window.tao.TabDragSession
import dev.nucleusframework.window.tao.TabWorkspace

/**
 * A tab drag of the strip, handed to the workspace once the pointer leaves the strip.
 *
 * The strip keeps its own in-strip reorder (ReorderableRow); past the strip the tab becomes the
 * workspace's: a drag ghost under the pointer, then a drop on another window's strip or a new
 * window. Plain fields: gesture bookkeeping, never a reason to recompose.
 */
internal class CrossWindowTabDrag {
    var stripCoordinates: LayoutCoordinates? = null
    var tabsCoordinates: LayoutCoordinates? = null
    var armedTabId: String? = null
    var session: TabDragSession? = null
    private var handedOver = false

    /** A drag of [tabId] started in the strip. */
    fun arm(tabId: String) {
        armedTabId = tabId
    }

    fun markHandedOver() {
        handedOver = true
    }

    /** True once after a drag the workspace carried out: the strip's own reorder must not apply. */
    fun consumeHandedOver(): Boolean = handedOver.also { handedOver = false }
}

@Composable
internal fun rememberCrossWindowTabDrag(): CrossWindowTabDrag {
    val drag = remember { CrossWindowTabDrag() }
    DisposableEffect(drag) { onDispose { drag.session?.cancel() } }
    return drag
}

/**
 * Observes (never consumes, Initial pass) the pointer of a tab drag started in the strip, and
 * starts / feeds / ends the workspace's session once the pointer has left the strip.
 */
@Composable
internal fun Modifier.crossWindowTabDrag(
    workspace: TabWorkspace,
    drag: CrossWindowTabDrag,
): Modifier {
    val window = LocalTaoWindow.current
    // Native Wayland cannot place windows: the tab stays in its strip there.
    if (window == null || !window.canPlaceOnScreen) return this
    val containerSize = LocalWindowInfo.current.containerSize
    return onPlaced { drag.tabsCoordinates = it }
        .pointerInput(window, containerSize) {
            val slackPx = STRIP_EXIT_SLACK_DP * density
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val tabId = drag.armedTabId ?: continue
                    val change = event.changes.firstOrNull() ?: continue
                    val inWindow = drag.tabsCoordinates?.localToWindow(change.position) ?: continue
                    val screen = window.outerBoundsPx()?.let { clientOriginPx(it, containerSize) + inWindow }
                    if (event.type == PointerEventType.Release || !change.pressed) {
                        drag.session?.let { session ->
                            if (screen != null) session.end(screen) else session.cancel()
                            drag.markHandedOver()
                        }
                        drag.session = null
                        drag.armedTabId = null
                        continue
                    }
                    if (event.type != PointerEventType.Move || screen == null) continue
                    val session = drag.session
                    if (session != null) {
                        session.update(screen)
                    } else if (isOutside(drag.stripCoordinates, inWindow, slackPx)) {
                        drag.session = workspace.beginDrag(tabId, TabDragOrigin.Strip(window), screen)?.also { it.update(screen) }
                    }
                }
            }
        }
}

private fun isOutside(
    strip: LayoutCoordinates?,
    inWindow: Offset,
    slackPx: Float,
): Boolean {
    val bounds = strip?.takeIf { it.isAttached }?.boundsInWindow() ?: return false
    return inWindow.x < bounds.left ||
        inWindow.x > bounds.right ||
        inWindow.y < bounds.top - slackPx ||
        inWindow.y > bounds.bottom + slackPx
}

/**
 * The window's client origin in physical screen px from its outer frame [outer] (x, y, w, h):
 * the side border is split evenly, the rest of the vertical chrome is on top — Win32's
 * `GetWindowRect` includes the invisible resize border (same model as Nucleus' own drag handles).
 */
private fun clientOriginPx(
    outer: LongArray,
    container: IntSize,
): Offset {
    val sideBorder = (outer[2] - container.width) / 2
    return Offset((outer[0] + sideBorder).toFloat(), (outer[1] + (outer[3] - container.height) - sideBorder).toFloat())
}

private const val STRIP_EXIT_SLACK_DP = 16
