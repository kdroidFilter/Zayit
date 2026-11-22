package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Where a reader pane's divider sits (a fraction of the space), and whether the user may drag it. Observable, so the UI
 * follows its changes; each platform lays its panes out from it (split panes on the desktop).
 */
@Stable
class PaneSplitState(
    initialPositionPercentage: Float = 0f,
    moveEnabled: Boolean = true,
) {
    var positionPercentage by mutableFloatStateOf(initialPositionPercentage)
    var moveEnabled by mutableStateOf(moveEnabled)
}
