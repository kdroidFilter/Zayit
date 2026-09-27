package io.github.kdroidfilter.seforimapp.framework.desktop

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import io.github.kdroidfilter.seforimapp.framework.session.SavedGeometry
import java.awt.GraphicsEnvironment
import kotlin.math.min
import kotlin.math.roundToInt

internal fun defaultWindowState(): WindowState =
    WindowState(
        placement = WindowPlacement.Maximized,
        position = WindowPosition.Aligned(Alignment.Center),
        size = DpSize(1280.dp, 800.dp),
    )

internal fun WindowState.toSavedGeometry(): SavedGeometry {
    val pos = position
    val (x, y) =
        if (pos is WindowPosition.Absolute) {
            pos.x.value.roundToInt() to pos.y.value.roundToInt()
        } else {
            SavedGeometry.UNSPECIFIED to SavedGeometry.UNSPECIFIED
        }
    val (w, h) =
        if (size.isSpecified) {
            size.width.value.roundToInt() to size.height.value.roundToInt()
        } else {
            1280 to 800
        }
    return SavedGeometry(x = x, y = y, width = w, height = h, placement = placement.name)
}

internal fun SavedGeometry?.toWindowState(): WindowState {
    if (this == null) return defaultWindowState()
    val placement = runCatching { WindowPlacement.valueOf(placement) }.getOrDefault(WindowPlacement.Floating)
    val size = DpSize(width.coerceIn(400, 10_000).dp, height.coerceIn(300, 10_000).dp)
    val position =
        if (x == SavedGeometry.UNSPECIFIED || !isVisibleOnAnyScreen(x, y, width, height)) {
            WindowPosition.Aligned(Alignment.Center)
        } else {
            WindowPosition.Absolute(x.dp, y.dp)
        }
    return WindowState(placement = placement, position = position, size = size)
}

internal fun applyGeometry(
    state: WindowState,
    geometry: SavedGeometry?,
) {
    val restored = geometry.toWindowState()
    state.size = restored.size
    state.position = restored.position
    state.placement = restored.placement
}

/**
 * A monitor may have been unplugged since the session was saved; a window restored to its old
 * bounds would then be invisible and undraggable. Require a minimal visible strip on some screen.
 */
private fun isVisibleOnAnyScreen(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
): Boolean =
    // ponytail: logical-dp vs device-px mismatch on mixed-DPI multi-monitor setups at worst
    // recenters the window; per-screen scale math if users report bad restores.
    runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any { device ->
            val b = device.defaultConfiguration.bounds
            val visibleW = min(x + width, b.x + b.width) - maxOf(x, b.x)
            val visibleH = min(y + height, b.y + b.height) - maxOf(y, b.y)
            visibleW >= 120 && visibleH >= 60
        }
    }.getOrDefault(true)
