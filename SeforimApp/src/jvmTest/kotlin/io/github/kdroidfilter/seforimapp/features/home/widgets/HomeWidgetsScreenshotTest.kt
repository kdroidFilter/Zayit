package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import io.github.kdroidfilter.seforimapp.testAppSettings
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/** Renders the Home widgets to build/screenshots so their layout can be eyeballed without launching the app. */
@OptIn(ExperimentalTestApi::class)
class HomeWidgetsScreenshotTest {
    private val appSettings = testAppSettings()
    private val layout = HomeWidgetsLayout(appSettings)

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
        // One placement per widget: the grid keys them by id
        val medium = listOf(WidgetPlacement(CalendarWidget, CellRect(0, 0, 7, 5)), WidgetPlacement(ZmanimWidget, CellRect(7, 0, 13, 4)))
        val large =
            listOf(WidgetPlacement(CalendarWidget, CellRect(0, 0, 10, 6)), WidgetPlacement(TempleCountdownWidget, CellRect(10, 0, 6, 3)))
        for (dark in listOf(false, true)) {
            shot("calendar-medium", 1100, dark, medium)
            shot("calendar-large", 1100, dark, large)
        }
    }

    @Test
    fun `render edit mode`() {
        for (dark in listOf(false, true)) shot("edit", 1100, dark, homeWidgets.take(3), editing = true)
        // The Temple selected: its resize frame, a handle on each side
        shot("edit-resize", 1100, false, homeWidgets.take(3), editing = true) { it.selectedWidget = TempleCountdownWidget.id }
    }

    private fun shot(
        name: String,
        width: Int,
        dark: Boolean,
        widgets: List<WidgetPlacement>,
        editing: Boolean = false,
        setUp: (HomeWidgetsState) -> Unit = {},
    ) = runComposeUiTest {
        setContent {
            IntUiTheme(isDark = dark) {
                CompositionLocalProvider(
                    LocalAppGraph provides graph,
                    // The Filament views need a real window; leave their cards empty
                    LocalTabSelected provides false,
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                ) {
                    val state =
                        HomeWidgetsState(HomeUserLocation.preview, Community.SEPHARADE, layout).also {
                            it.editingWidgets = editing
                            setUp(it)
                        }
                    Box(
                        Modifier
                            .background(JewelTheme.globalColors.panelBackground)
                            .padding(16.dp)
                            .width(width.dp)
                            .height(if (editing) 760.dp else 900.dp),
                    ) {
                        HomeWidgetsGrid(state = state, widgets = widgets, scrollState = rememberScrollState())
                        HomeWidgetsOverlay(state, widgets)
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
