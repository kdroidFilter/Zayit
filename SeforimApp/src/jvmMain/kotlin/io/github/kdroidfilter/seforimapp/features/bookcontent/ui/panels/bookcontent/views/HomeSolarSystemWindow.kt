package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import dev.nucleusframework.application.LocalNucleusApplicationScope
import dev.nucleusframework.window.BasicTitleBar
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.LocalWindowChromeInsets
import dev.nucleusframework.window.TitleBarPlacement
import dev.nucleusframework.window.WindowBackground
import dev.nucleusframework.window.WindowScaffold
import dev.nucleusframework.window.jewel.JewelDecoratedWindow
import dev.nucleusframework.window.newFullscreenControls
import dev.nucleusframework.window.styling.LocalTitleBarStyle
import dev.nucleusframework.window.windowDragArea
import io.github.kdroidfilter.seforimapp.earthwidget.KiddushLevanaEarliestOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.KiddushLevanaLatestOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.SolarSystemWidgetView
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_solar_system_title
import java.time.LocalDate

/**
 * Height of the title bar band: the traffic lights are centred in it, so it matches the widget header's centre in a
 * full window (10 dp padding + a 28 dp row, both ×1.4 text scale → ~34 dp).
 */
private val OVERLAY_BAR_HEIGHT = 68.dp

/** The Home solar system widget alone in its own maximised window, following the same [date]. */
@Composable
internal fun SolarSystemWindow(
    date: LocalDate?,
    inIsrael: Boolean,
    kiddushLevanaEarliest: KiddushLevanaEarliestOpinion,
    kiddushLevanaLatest: KiddushLevanaLatestOpinion,
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
            // No title bar chrome: the scene fills the whole window and the widget's own header sits in the title bar
            // band — an empty, click-through overlay whose height centres the traffic lights on that header. The
            // header row drags the window; black behind, so a live resize never flashes white.
            WindowBackground(Color.Black)
            val baseBarStyle = LocalTitleBarStyle.current
            // Invisible bar (the scene shows through) kept only for the window controls — also in macOS fullscreen,
            // where newFullscreenControls brings the traffic lights back
            val transparentBarStyle =
                remember(baseBarStyle) {
                    baseBarStyle.copy(
                        colors =
                            baseBarStyle.colors.copy(
                                background = Color.Transparent,
                                inactiveBackground = Color.Transparent,
                                border = Color.Transparent,
                            ),
                        metrics = baseBarStyle.metrics.copy(height = OVERLAY_BAR_HEIGHT),
                    )
                }
            WindowScaffold(
                titleBar = {
                    BasicTitleBar(
                        modifier = Modifier.newFullscreenControls(),
                        style = transparentBarStyle,
                        controlButtonsDirection = ControlButtonsDirection.SystemNative,
                    )
                },
                titleBarPlacement = TitleBarPlacement.Overlay(autoHideInFullscreen = false, passThroughToContent = true),
                // Same side as the main window's traffic lights
                controlButtonsDirection = ControlButtonsDirection.SystemNative,
            ) { _ ->
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    // Its own date, starting from the Home widgets' one
                    var windowDate by remember(date) { mutableStateOf(date) }
                    SolarSystemWidgetView(
                        modifier = Modifier.fillMaxSize(),
                        date = windowDate,
                        inIsrael = inIsrael,
                        fullWindow = true,
                        onDateSelect = { windowDate = it },
                        kiddushLevanaEarliestOpinion = kiddushLevanaEarliest,
                        kiddushLevanaLatestOpinion = kiddushLevanaLatest,
                        // Beside the traffic lights, not below them. controlsInsets is start/end in the controls'
                        // own direction — read it as LTR to get the physical side, which the RTL page would flip
                        chromePadding =
                            LocalWindowChromeInsets.current.controlsInsets.let {
                                PaddingValues.Absolute(
                                    left = it.calculateLeftPadding(LayoutDirection.Ltr),
                                    right = it.calculateRightPadding(LayoutDirection.Ltr),
                                )
                            },
                        headerModifier = Modifier.windowDragArea(),
                    )
                }
            }
        }
    }
}
