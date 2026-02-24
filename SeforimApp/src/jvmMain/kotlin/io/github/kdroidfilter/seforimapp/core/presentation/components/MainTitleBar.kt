package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.nucleus.window.DecoratedWindowScope
import io.github.kdroidfilter.nucleus.window.TitleBar
import io.github.kdroidfilter.nucleus.window.newFullscreenControls
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsView
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo

@Composable
fun DecoratedWindowScope.MainTitleBar() {
    TitleBar(
        modifier = Modifier.newFullscreenControls(),
        gradientStartColor = if (ThemeUtils.isIslandsStyle()) ThemeUtils.titleBarGradientColor() else Color.Transparent,
    ) {
        BoxWithConstraints {
            val windowWidth = maxWidth
            val iconsNumber = 4
            val iconWidth: Dp = 40.dp
            val iconsAreaWidth: Dp =
                when (PlatformInfo.currentOS) {
                    OperatingSystem.MACOS -> iconWidth * (iconsNumber + 2)
                    OperatingSystem.WINDOWS -> iconWidth * (iconsNumber + 3.5f)
                    else -> iconWidth * iconsNumber
                }
            val tabsAreaWidth: Dp = (windowWidth - iconsAreaWidth).coerceAtLeast(0.dp)
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
