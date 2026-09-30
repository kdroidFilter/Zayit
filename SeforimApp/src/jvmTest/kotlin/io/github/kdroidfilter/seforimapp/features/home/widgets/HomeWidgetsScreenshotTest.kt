package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.zacsweers.metro.createGraph
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.features.home.widgets.calendar.CalendarWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.temple.TempleCountdownWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.zmanim.ZmanimWidget
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.Community
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/** Renders the Home widgets to build/screenshots so their layout can be eyeballed without launching the app. */
@OptIn(ExperimentalTestApi::class)
class HomeWidgetsScreenshotTest {
    private val graph by lazy { createGraph<AppGraph>() }

    @Test
    fun `render home widgets`() {
        for (width in listOf(1100, 800)) {
            for (dark in listOf(false, true)) {
                shot("widgets", width, dark, homeWidgets)
            }
        }
    }

    @Test
    fun `render calendar widget`() {
        val layout =
            listOf(
                WidgetPlacement(CalendarWidget),
                WidgetPlacement(ZmanimWidget),
                WidgetPlacement(TempleCountdownWidget),
                WidgetPlacement(CalendarWidget, WidgetSize.LARGE),
            )
        for (dark in listOf(false, true)) shot("calendar", 1100, dark, layout)
    }

    @Test
    fun `render edit mode`() {
        for (dark in listOf(false, true)) shot("edit", 1100, dark, homeWidgets.take(3), editing = true)
    }

    private fun shot(
        name: String,
        width: Int,
        dark: Boolean,
        widgets: List<WidgetPlacement>,
        editing: Boolean = false,
    ) = runComposeUiTest {
        setContent {
            IntUiTheme(isDark = dark) {
                CompositionLocalProvider(
                    LocalAppGraph provides graph,
                    // The Filament views need a real window; leave their cards empty
                    LocalTabSelected provides false,
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                ) {
                    Box(Modifier.background(JewelTheme.globalColors.panelBackground).padding(16.dp)) {
                        HomeWidgetsHost(
                            state =
                                HomeWidgetsState(HomeUserLocation.preview, Community.SEPHARADE).also {
                                    it.editingWidgets = editing
                                },
                            modifier = Modifier.width(width.dp),
                            widgets = widgets,
                        )
                    }
                }
            }
        }
        waitForIdle()
        val out = File("build/screenshots/$name-$width-${if (dark) "dark" else "light"}.png")
        out.parentFile.mkdirs()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
    }
}
