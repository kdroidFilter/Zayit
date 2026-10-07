package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.TitleBarLayoutPolicy
import dev.nucleusframework.window.jewel.JewelTitleBar
import dev.nucleusframework.window.macOSLargeCornerRadius
import dev.nucleusframework.window.newFullscreenControls
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsView
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils

@Composable
fun DecoratedWindowScope.MainTitleBar(modifier: Modifier = Modifier) {
    JewelTitleBar(
        modifier = modifier.newFullscreenControls().macOSLargeCornerRadius(),
        gradientStartColor = ThemeUtils.titleBarGradientColor(),
        controlButtonsDirection = ControlButtonsDirection.SystemNative,
        // FillCenter gives the center child exactly the space left by the window controls,
        // so the action buttons size themselves and the tabs take the rest through weight().
        layoutPolicy = TitleBarLayoutPolicy.FillCenter,
    ) {
        Row(modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Box(modifier = Modifier.weight(1f)) {
                TabsView()
            }
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DesktopSwitcher()
                TitleBarActionsButtonsView()
            }
        }
    }
}
