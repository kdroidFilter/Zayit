package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.getOperatingSystem
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsView
import org.jetbrains.jewel.window.DecoratedWindowScope
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls

@Composable
fun DecoratedWindowScope.MainTitleBar() {
    TitleBar(modifier = Modifier.newFullscreenControls()) {
        BoxWithConstraints {
            val windowWidth = maxWidth
            val iconsNumber = 4
            val iconWidth = 40
            Row {
                Row(
                    modifier = Modifier
                        .padding(start = 0.dp)
                        .align(Alignment.Start)
                        .width(
                            windowWidth -
                                    when (getOperatingSystem()) {
                                        OperatingSystem.MACOS -> iconWidth * (iconsNumber + 2).dp
                                        OperatingSystem.WINDOWS -> iconWidth * (iconsNumber + 3.5).dp
                                        else -> (iconWidth * iconsNumber).dp
                                    }
                        )
                ) {
                    TabsView()
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TitleBarActionsButtonsView()
                }
            }
        }
    }
}
