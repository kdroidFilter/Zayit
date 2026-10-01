package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.zacsweers.metro.createGraph
import io.github.kdroidfilter.seforimapp.features.home.widgets.luach.Limud
import io.github.kdroidfilter.seforimapp.features.home.widgets.luach.LimudWidget
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.Community
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.testAppSettings
import io.github.vinceglb.filekit.FileKit
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** A widget's own items atop its right-click menu: the Limud widget's opens its options page, which sets its limudim. */
@OptIn(ExperimentalTestApi::class)
class WidgetMenuUiTest {
    private val graph by lazy {
        FileKit.init("seforimapp-tests")
        createGraph<AppGraph>()
    }

    private fun ComposeUiTest.show(content: @Composable () -> Unit) =
        setContent {
            IntUiTheme(isDark = true) {
                CompositionLocalProvider(LocalAppGraph provides graph, LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
            }
        }

    @Test
    fun `the limud widget's menu opens its options`() =
        runDesktopComposeUiTest(width = 700, height = 700) {
            val state = HomeWidgetsState(HomeUserLocation.preview, Community.SEPHARADE, HomeWidgetsLayout(testAppSettings()))
            show { WidgetFrame(WidgetPlacement(LimudWidget, CellRect(0, 0, 4, 4)), state, Modifier.size(300.dp, 300.dp)) }
            onNodeWithTag("widget-limud").performMouseInput { rightClick(center) }
            onNodeWithText("אפשרויות…").performMouseInput { click(center) }
            waitForIdle()
            assertEquals(LimudWidget, state.optionsOpen)
        }

    @Test
    fun `the limud card's settings button opens its options`() =
        runDesktopComposeUiTest(width = 700, height = 700) {
            val state = HomeWidgetsState(HomeUserLocation.preview, Community.SEPHARADE, HomeWidgetsLayout(testAppSettings()))
            show { LimudWidget.Content(state, Modifier.size(300.dp, 300.dp)) }
            onNodeWithTag("limud-settings").performClick()
            waitForIdle()
            assertEquals(LimudWidget, state.optionsOpen)
        }

    @Test
    fun `the limud options pick the limudim the card shows`() =
        runDesktopComposeUiTest(width = 460, height = 900) {
            val settings = testAppSettings()
            val state = HomeWidgetsState(HomeUserLocation.preview, Community.SEPHARADE, HomeWidgetsLayout(settings))
            show { LimudWidget.Options(state) }
            onNodeWithTag("limud-option-chofetz_chaim").performClick()
            onNodeWithTag("limud-option-shmiras_halashon").performClick()
            waitForIdle()
            assertEquals(
                Limud.defaults.toSet() - Limud.CHOFETZ_CHAIM + Limud.SHMIRAS_HALASHON,
                Limud.decode(settings.homeWidgetOptionsFlow.value[LimudWidget.id]),
            )
        }
}
