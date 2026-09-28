package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import dev.nucleusframework.application.LocalNucleusApplicationScope
import dev.nucleusframework.window.BasicTitleBar
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.TitleBarLayoutPolicy
import dev.nucleusframework.window.jewel.JewelDecoratedWindow
import dev.nucleusframework.window.newFullscreenControls
import dev.nucleusframework.window.styling.LocalTitleBarStyle
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.earthwidget.SolarSystemWidgetView
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_solar_system_title
import java.time.LocalDate

/** The Home solar system widget alone in its own maximised window, following the same [date]. */
@Composable
internal fun SolarSystemWindow(
    date: LocalDate?,
    inIsrael: Boolean,
    onClose: () -> Unit,
) {
    val title = stringResource(Res.string.home_solar_system_title)
    val state =
        remember {
            WindowState(
                placement = WindowPlacement.Maximized,
                position = WindowPosition(Alignment.Center),
                size = DpSize(1200.dp, 720.dp),
            )
        }
    with(LocalNucleusApplicationScope.current) {
        JewelDecoratedWindow(onCloseRequest = onClose, title = title, state = state) {
            val titleBarStyle = LocalTitleBarStyle.current
            BasicTitleBar(
                modifier = Modifier.newFullscreenControls(),
                gradientStartColor = ThemeUtils.titleBarGradientColor(),
                style = titleBarStyle,
                controlButtonsDirection = ControlButtonsDirection.SystemNative,
                layoutPolicy = TitleBarLayoutPolicy.Default,
            ) {
                CompositionLocalProvider(LocalContentColor provides titleBarStyle.colors.content) {
                    Text(title, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                // Its own date, starting from the Home widgets' one
                var windowDate by remember(date) { mutableStateOf(date) }
                SolarSystemWidgetView(
                    modifier = Modifier.fillMaxSize(),
                    date = windowDate,
                    inIsrael = inIsrael,
                    fullWindow = true,
                    onDateSelect = { windowDate = it },
                )
            }
        }
    }
}
