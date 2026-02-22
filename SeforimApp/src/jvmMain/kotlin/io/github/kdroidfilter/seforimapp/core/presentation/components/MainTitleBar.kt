package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsView
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.nucleus.window.DecoratedWindowScope
import io.github.kdroidfilter.nucleus.window.TitleBar
import io.github.kdroidfilter.nucleus.window.newFullscreenControls

@Composable
fun DecoratedWindowScope.MainTitleBar() {
    TitleBar(modifier = Modifier.newFullscreenControls()) {
        BoxWithConstraints {
            val windowWidth = maxWidth
            val iconsNumber = 4
            val iconWidth: Dp = 40.dp
            val density = LocalDensity.current

            // Compute the available width for the tab strip in pixel space and then
            // quantize to an integer number of pixels before converting back to Dp.
            // This avoids subtle 1px oscillations on small or non-integer window sizes.
            val tabsAreaWidth: Dp =
                with(density) {
                    val windowWidthPx = windowWidth.toPx()
                    val iconsAreaWidthDp =
                        when (PlatformInfo.currentOS) {
                            OperatingSystem.MACOS -> iconWidth * (iconsNumber + 2)
                            OperatingSystem.WINDOWS -> iconWidth * (iconsNumber + 3.5f)
                            else -> iconWidth * iconsNumber
                        }
                    val iconsAreaWidthPx = iconsAreaWidthDp.toPx()
                    val availablePx = (windowWidthPx - iconsAreaWidthPx).coerceAtLeast(0f)
                    val quantizedPx = availablePx.toInt()
                    quantizedPx.toDp()
                }
            Row {
                Row(
                    modifier =
                        Modifier
                            .padding(start = 0.dp)
                            .align(Alignment.Start)
                            .width(tabsAreaWidth),
                ) {
                    TabsView()
                }
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.End)
                            .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TitleBarActionsButtonsView()
                }
            }
        }
    }
}
