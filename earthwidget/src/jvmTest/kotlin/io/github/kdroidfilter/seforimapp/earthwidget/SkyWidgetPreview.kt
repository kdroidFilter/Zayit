package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import java.io.File
import java.util.TimeZone
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Not a check: renders [SkyWidgetView] alone at its Home-card size (Jerusalem) and saves a screenshot of that window.
 * Runs only with SKY_PREVIEW_OUT=<png path>; SKY_PREVIEW_LIGHT=1 for the light theme, SKY_PREVIEW_HOURS=<h> to start that far from now.
 */
class SkyWidgetPreview {
    @Test
    fun capture() {
        val out = System.getenv("SKY_PREVIEW_OUT") ?: return
        val dark = System.getenv("SKY_PREVIEW_LIGHT") == null
        application(exitProcessOnExit = false) {
            val state =
                rememberWindowState(
                    size = DpSize(764.dp, 262.dp),
                    position = WindowPosition(Alignment.Center),
                )
            Window(onCloseRequest = ::exitApplication, state = state, alwaysOnTop = true, title = "sky") {
                IntUiTheme(isDark = dark) {
                    val panel = org.jetbrains.jewel.foundation.theme.JewelTheme.globalColors.panelBackground
                    Box(Modifier.fillMaxSize().background(panel), contentAlignment = Alignment.Center) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Box(
                                Modifier
                                    .size(265.dp, 202.5.dp)
                                    .background(panel.copy(alpha = 0.3f), RoundedCornerShape(18.dp)),
                            ) {
                                SkyWidgetView(
                                    latitude = 31.7683,
                                    longitude = 35.2137,
                                    timeZone = TimeZone.getTimeZone("Asia/Jerusalem"),
                                    modifier = Modifier.fillMaxSize(),
                                    timeMillis =
                                        System.getenv("SKY_PREVIEW_HOURS")?.let {
                                            System.currentTimeMillis() +
                                                (it.toDouble() * 3_600_000).toLong()
                                        },
                                )
                            }
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    delay(6000)
                    val w = window
                    val image =
                        Robot()
                            .createMultiResolutionScreenCapture(Rectangle(w.locationOnScreen, w.size))
                            .resolutionVariants
                            .maxBy { it.getWidth(null) }
                    val buffered = BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB)
                    buffered.graphics.drawImage(image, 0, 0, null)
                    ImageIO.write(buffered, "png", File(out))
                    exitApplication()
                }
            }
        }
    }
}
