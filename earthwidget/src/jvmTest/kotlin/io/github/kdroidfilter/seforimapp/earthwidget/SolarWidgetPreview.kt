package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
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
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Not a check: renders [SolarSystemWidgetView] alone at its Home-card size and saves a screenshot of that window.
 * Runs only with SOLAR_PREVIEW_OUT=<png path>.
 */
class SolarWidgetPreview {
    @Test
    fun capture() {
        val out = System.getenv("SOLAR_PREVIEW_OUT") ?: return
        // SOLAR_PREVIEW_SIZE=1200x700 previews the full-window layout
        val (cardW, cardH) =
            System
                .getenv("SOLAR_PREVIEW_SIZE")
                ?.split('x')
                ?.map { it.toFloat() }
                ?.let { it[0] to it[1] } ?: (429f to 202.5f)
        application(exitProcessOnExit = false) {
            val state = rememberWindowState(size = DpSize((cardW + 41).dp, (cardH + 58).dp), position = WindowPosition(Alignment.Center))
            Window(onCloseRequest = ::exitApplication, state = state, alwaysOnTop = true, title = "preview") {
                IntUiTheme(isDark = true) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF2B2D30)), contentAlignment = Alignment.Center) {
                        // Home card size: 3 cards (135 dp) + 2 gaps wide, 1.5 cards high
                        Box(Modifier.size(cardW.dp, cardH.dp).background(Color.Black)) {
                            SolarSystemWidgetView(
                                modifier = Modifier.fillMaxSize(),
                                // SOLAR_PREVIEW_DATE=2026-04-05 previews another day of the Home widgets
                                date = System.getenv("SOLAR_PREVIEW_DATE")?.let(java.time.LocalDate::parse),
                                inIsrael = true,
                                fullWindow = System.getenv("SOLAR_PREVIEW_SIZE") != null,
                                onDateSelect = if (System.getenv("SOLAR_PREVIEW_SIZE") != null) ({ _ -> }) else null,
                            )
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    delay(6000)
                    val w = window
                    val bounds = Rectangle(w.locationOnScreen, w.size)
                    val image = Robot().createMultiResolutionScreenCapture(bounds).resolutionVariants.maxBy { it.getWidth(null) }
                    val buffered = BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB)
                    buffered.graphics.drawImage(image, 0, 0, null)
                    ImageIO.write(buffered, "png", File(out))
                    exitApplication()
                }
            }
        }
    }
}
