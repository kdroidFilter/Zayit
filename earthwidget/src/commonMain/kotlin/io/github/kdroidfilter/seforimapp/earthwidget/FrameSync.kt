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
        withFrameNanos {}
        rendering = false
    }
    return rendering
}
