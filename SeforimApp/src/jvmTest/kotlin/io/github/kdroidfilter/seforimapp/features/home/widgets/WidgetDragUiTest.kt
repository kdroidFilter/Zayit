package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.zacsweers.metro.createGraph
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.Community
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** Moves a widget by its hover grip, outside edit mode, as a mouse would. */
@OptIn(ExperimentalTestApi::class)
class WidgetDragUiTest {
    private val graph by lazy { createGraph<AppGraph>() }

    @Test
    fun `a widget dragged by its grip takes the place of the one it's dropped on`() {
        val saved = AppSettings.homeWidgetsLayoutFlow.value
        try {
            // Temple, then zmanim: both fit one row, the Temple at the start
            AppSettings.setHomeWidgetsLayout("temple_countdown:MEDIUM,zmanim:LARGE")
            runComposeUiTest {
                setContent {
                    IntUiTheme {
                        CompositionLocalProvider(LocalAppGraph provides graph, LocalTabSelected provides false) {
                            val raw by AppSettings.homeWidgetsLayoutFlow.collectAsState()
                            Box(Modifier.testTag("grid").width(1000.dp).height(700.dp)) {
                                HomeWidgetsGrid(
                                    state = HomeWidgetsState(HomeUserLocation.preview, Community.SEPHARADE),
                                    widgets = decodeLayout(raw),
                                    gridState = rememberLazyGridState(),
                                )
                            }
                        }
                    }
                }
                val grid = onNodeWithTag("grid")
                grid.performMouseInput {
                    // Hover the Temple (start: left in LTR) so its grip shows at its top centre
                    moveTo(Offset(150f, 100f))
                }
                waitForIdle()
                onNodeWithTag("widget-grip-temple_countdown").performMouseInput {
                    moveTo(center)
                    press()
                    // Onto the zmanim widget, in steps so the drag passes its slop
                    repeat(10) { moveBy(Offset(50f, 5f)) }
                    release()
                }
                waitForIdle()
            }
            assertEquals("zmanim:LARGE,temple_countdown:MEDIUM", AppSettings.homeWidgetsLayoutFlow.value)
        } finally {
            AppSettings.setHomeWidgetsLayout(saved)
        }
    }

    @Test
    fun `a widget dropped on the trash is removed, with an undo`() {
        val saved = AppSettings.homeWidgetsLayoutFlow.value
        try {
            AppSettings.setHomeWidgetsLayout("temple_countdown:MEDIUM,zmanim:LARGE")
            val state = HomeWidgetsState(HomeUserLocation.preview, Community.SEPHARADE)
            runComposeUiTest {
                setContent {
                    IntUiTheme {
                        CompositionLocalProvider(LocalAppGraph provides graph, LocalTabSelected provides false) {
                            val raw by AppSettings.homeWidgetsLayoutFlow.collectAsState()
                            Box(Modifier.testTag("grid").width(1000.dp).height(700.dp)) {
                                HomeWidgetsGrid(state = state, widgets = decodeLayout(raw), gridState = rememberLazyGridState())
                                HomeWidgetsOverlay(state, decodeLayout(raw))
                            }
                        }
                    }
                }
                onNodeWithTag("grid").performMouseInput { moveTo(Offset(150f, 100f)) }
                waitForIdle()
                val grip = onNodeWithTag("widget-grip-temple_countdown")
                val gripCentre = grip.fetchSemanticsNode().boundsInRoot.center
                grip.performMouseInput {
                    moveTo(center)
                    press()
                    repeat(3) { moveBy(Offset(0f, 10f)) }
                }
                waitForIdle()
                // The trash shows once the move starts: aim at it
                val trash = onNodeWithTag("widget-trash").fetchSemanticsNode().boundsInRoot.center
                grip.performMouseInput {
                    val steps = 10
                    val step = (trash - gripCentre - Offset(0f, 30f)) / steps.toFloat()
                    repeat(steps) { moveBy(step) }
                    release()
                }
                waitForIdle()
            }
            assertEquals("zmanim:LARGE", AppSettings.homeWidgetsLayoutFlow.value)
            assertEquals(
                "temple_countdown",
                state.lastRemoved
                    ?.placement
                    ?.widget
                    ?.id,
            )
        } finally {
            AppSettings.setHomeWidgetsLayout(saved)
        }
    }
}
