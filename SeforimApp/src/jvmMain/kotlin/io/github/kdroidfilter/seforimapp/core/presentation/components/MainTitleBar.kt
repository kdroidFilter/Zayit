package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.TitleBarLayoutPolicy
import dev.nucleusframework.window.jewel.JewelTitleBar
import dev.nucleusframework.window.macOSLargeCornerRadius
import dev.nucleusframework.window.newFullscreenControls
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsView
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.tabStripMinWidth
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow

private enum class TitleBarSlot { Tabs, Switcher, Buttons }

@Composable
private fun CenteredRow(content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
fun DecoratedWindowScope.MainTitleBar(modifier: Modifier = Modifier) {
    JewelTitleBar(
        modifier = modifier.newFullscreenControls().macOSLargeCornerRadius(),
        gradientStartColor = ThemeUtils.titleBarGradientColor(),
        controlButtonsDirection = ControlButtonsDirection.SystemNative,
        // FillCenter gives the center child exactly the space left by the window controls,
        // so the action buttons size themselves and the tabs take the rest.
        layoutPolicy = TitleBarLayoutPolicy.FillCenter,
    ) {
        val tabCount =
            LocalOpenWindow.current.tabsViewModel.state
                .collectAsState()
                .value.tabs.size
        // The tabs take what the buttons leave. Once that is too little even for the current tab's icon and the
        // chevron to the others, the desktop switcher drops its name and hands its room over. Decided in this one
        // layout pass, from the switcher's full width, so the room it gives can't undo it.
        SubcomposeLayout(Modifier.align(Alignment.CenterHorizontally)) { constraints ->
            val loose = constraints.copy(minWidth = 0)
            val buttons = subcompose(TitleBarSlot.Buttons) { CenteredRow { TitleBarActionsButtonsView() } }.map { it.measure(loose) }
            val buttonsWidth = buttons.sumOf { it.width }
            val roomBesideFullSwitcher = constraints.maxWidth - buttonsWidth - DESKTOP_SWITCHER_WIDTH.roundToPx()
            val compact = roomBesideFullSwitcher < tabStripMinWidth(tabCount).roundToPx()
            val switcher =
                subcompose(TitleBarSlot.Switcher) { CenteredRow { DesktopSwitcher(compact = compact) } }.map { it.measure(loose) }
            val switcherWidth = switcher.sumOf { it.width }
            val tabsWidth = (constraints.maxWidth - buttonsWidth - switcherWidth).coerceAtLeast(0)
            val tabs =
                subcompose(TitleBarSlot.Tabs) { TabsView() }
                    .map { it.measure(constraints.copy(minWidth = tabsWidth, maxWidth = tabsWidth)) }
            val height = (tabs + switcher + buttons).maxOfOrNull { it.height } ?: constraints.minHeight
            layout(constraints.maxWidth, height) {
                // Start to end, as the Row did: placeRelative mirrors it in RTL.
                var x = 0
                for (placeable in tabs + switcher + buttons) {
                    placeable.placeRelative(x, (height - placeable.height) / 2)
                    x += placeable.width
                }
            }
        }
    }
}
