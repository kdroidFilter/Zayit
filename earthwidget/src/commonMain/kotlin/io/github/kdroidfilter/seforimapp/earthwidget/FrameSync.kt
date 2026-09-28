package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

// 2D overlays (labels, glare) drawn over a FilamentSceneView must follow the state the 3D image was rendered
// with, or they slide against it while the view moves.

/**
 * Frames between a state and its 3D image on screen: filament-compose's desktop surface renders a frame with the
 * previous composition's state, reads it back asynchronously, and shows it the frame after.
 */
internal const val RENDER_LATENCY_FRAMES = 2

/** [value] as it was [frames] frames ago; catches up once it stops changing. */
@Composable
internal fun <T> rememberFrameDelayed(
    value: T,
    frames: Int,
): T {
    val current by rememberUpdatedState(value)
    var delayed by remember { mutableStateOf(value) }
    LaunchedEffect(frames) {
        val history = ArrayDeque<T>()
        while (true) {
            withFrameNanos {
                history.addLast(current)
                while (history.size > frames + 1) history.removeFirst()
                delayed = history.first()
            }
        }
    }
    return delayed
}
