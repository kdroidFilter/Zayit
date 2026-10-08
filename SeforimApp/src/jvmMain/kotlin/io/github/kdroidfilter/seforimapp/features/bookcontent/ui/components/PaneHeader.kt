package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.tao.SatelliteScope
import dev.nucleusframework.window.tao.satelliteDragHandle
import io.github.kdroidfilter.seforimapp.core.presentation.components.HorizontalDivider
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalIsTouchMode
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.PathIconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.commentaries
import seforimapp.seforimapp.generated.resources.pane_dock
import seforimapp.seforimapp.generated.resources.pane_float

/**
 * The dock satellite hosting the current pane, or null outside one. [PaneHeader] is then also the
 * pane's grip: dragging it moves the pane between the dock and a window of its own.
 */
@OptIn(ExperimentalNucleusApi::class)
val LocalPaneSatellite = staticCompositionLocalOf<SatelliteScope?> { null }

@OptIn(ExperimentalNucleusApi::class)
@Composable
fun PaneHeader(
    label: String,
    onHide: () -> Unit,
    interactionSource: MutableInteractionSource? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val headerHoverSource = interactionSource ?: remember { MutableInteractionSource() }
    val isHovered by headerHoverSource.collectIsHoveredAsState()
    // Touch has no persistent hover, so reveal the actions whenever the user is
    // interacting by touch (see [LocalIsTouchMode]).
    val isTouchMode = LocalIsTouchMode.current
    val headerBackground = JewelTheme.globalColors.toolwindowBackground.copy(alpha = 0.15f)

    val satellite = LocalPaneSatellite.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(headerBackground)
                .hoverable(headerHoverSource)
                .then(if (satellite?.isDocked == true) Modifier.satelliteDragHandle(satellite) else Modifier),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }

            AnimatedVisibility(
                visible = isHovered || isTouchMode,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions?.invoke(this)
                    satellite?.let { PaneDockButton(it) }
                    IconActionButton(
                        key = AllIconsKeys.Windows.Minimize,
                        onClick = onHide,
                        contentDescription = "Hide panel",
                    )
                }
            }
        }

        HorizontalDivider()
    }
}

/** Anchors [DockToWindowIcon]'s resource lookup to this module's class loader. */
private object PaneHeaderIconAnchor

// moveToWindow's frame with the arrow turned inward, so Dock reads as the reverse of Float at the same weight.
private val DockToWindowIcon = PathIconKey("icons/dockToWindow.svg", PaneHeaderIconAnchor::class.java)

/** Pulls a docked pane out into a window of its own, or puts a floating one back where it was docked. */
@OptIn(ExperimentalNucleusApi::class)
@Composable
private fun PaneDockButton(satellite: SatelliteScope) {
    val entry = satellite.satellite
    when {
        satellite.isDocked && entry.isFloatable ->
            PaneHeaderAction(AllIconsKeys.Actions.MoveToWindow, stringResource(Res.string.pane_float), satellite::undock)
        // Floating: a click docks it where it was, a drag takes it to the side it is dropped on.
        !satellite.isDocked && entry.dockSides.isNotEmpty() ->
            PaneHeaderAction(
                key = DockToWindowIcon,
                text = stringResource(Res.string.pane_dock),
                onClick = { satellite.dock() },
                modifier = Modifier.satelliteDragHandle(satellite),
            )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaneHeaderAction(
    key: IconKey,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Tooltip({ Text(text) }) {
        IconActionButton(key = key, onClick = onClick, contentDescription = text, modifier = modifier)
    }
}

@Preview
@Composable
private fun PaneHeaderPreview() {
    PreviewContainer {
        PaneHeader(
            label = stringResource(Res.string.commentaries),
            onHide = {},
        )
    }
}

@Preview
@Composable
private fun PaneHeaderWithWarningPreview() {
    PreviewContainer {
        PaneHeader(
            label = stringResource(Res.string.commentaries),
            onHide = {},
        )
    }
}
