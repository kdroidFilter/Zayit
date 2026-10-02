package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * Whether a FilamentSceneView showing [scene] (everything its image depends on) should render: for the frame [scene]
 * changes in, paused otherwise rather than re-rendering an unchanged picture every display frame. A paused view still
 * renders until its last scene is on screen.
 */
@Composable
internal fun rememberRenderOnChange(scene: Any?): Boolean {
    var rendering by remember { mutableStateOf(true) }
    LaunchedEffect(scene) {
        rendering = true
        // Two quiet frames before pausing: a scene changing every frame (a play, a drag) restarts this before then,
        // so the view keeps rendering on and off never flips (each flip restarted its frame loop, dropping frames)
        withFrameNanos {}
        withFrameNanos {}
        rendering = false
    }
    return rendering
}
