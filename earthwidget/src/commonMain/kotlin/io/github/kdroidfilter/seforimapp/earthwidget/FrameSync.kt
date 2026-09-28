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
    val history = remember { ArrayDeque<T>() }
    // Ticks only until caught up: an endless frame loop would keep the frame clock, and the whole app, awake.
    LaunchedEffect(value, frames) {
        do {
            withFrameNanos {
                history.addLast(current)
                while (history.size > frames + 1) history.removeFirst()
                delayed = history.first()
            }
        } while (history.size <= frames || history.any { it != current })
    }
    return delayed
}

/**
 * Whether a FilamentSceneView showing [scene] (everything its image depends on) should render: while [scene]
 * changes, and [RENDER_LATENCY_FRAMES] + 2 frames after so its last image reaches the screen. Paused otherwise,
 * rather than re-rendering an unchanged picture every display frame.
 */
@Composable
internal fun rememberRenderOnChange(scene: Any?): Boolean {
    var rendering by remember { mutableStateOf(true) }
    LaunchedEffect(scene) {
        rendering = true
        repeat(RENDER_LATENCY_FRAMES + 2) { withFrameNanos {} }
        rendering = false
    }
    return rendering
}
