package io.github.kdroidfilter.seforimapp.features.home.widgets.solarsystem

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.earthwidget.SolarSystemWidgetView
import io.github.kdroidfilter.seforimapp.earthwidget.isEarthWidgetSupported
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.HOME_GRID_COLUMNS
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.SolarSystemPreview
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetCard
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_solar_system_title

/** The solar system on the selected day, openable in its own window. */
internal object SolarSystemWidget : HomeWidget {
    override val id = "solar_system"
    override val title = Res.string.home_solar_system_title
    override val defaultSpan = CellSpan(9, 3)
    override val minSpan = CellSpan(6, 3)
    override val maxSpan = CellSpan(HOME_GRID_COLUMNS, 8)
    override val isSupported get() = isEarthWidgetSupported
    override val toolWindowSize = DpSize(1200.dp, 720.dp)
    override val toolSymbol = "sun.max"

    @Composable
    override fun Preview(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) = SolarSystemPreview(modifier)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        WidgetCard(modifier) {
            // Filament renders every vsync while composed; hidden tabs stay composed, so drop it there.
            if (LocalTabSelected.current) {
                SolarSystemWidgetView(
                    modifier = Modifier.fillMaxSize(),
                    date = state.selectedDate,
                    timeMillis = state.skyTimeMillis,
                    onFullscreen = { state.solarSystemFullscreen = true },
                    inIsrael = state.userInIsrael,
                    kiddushLevanaEarliestOpinion = state.kiddushLevanaEarliest,
                    kiddushLevanaLatestOpinion = state.kiddushLevanaLatest,
                )
            }
        }
    }

    /** As a tool, its full-window view, not the card. */
    @Composable
    override fun ToolWindow(
        state: HomeWidgetsState,
        onClose: () -> Unit,
    ) = SolarSystemWindow(
        date = state.selectedDate,
        inIsrael = state.userInIsrael,
        kiddushLevanaEarliest = state.kiddushLevanaEarliest,
        kiddushLevanaLatest = state.kiddushLevanaLatest,
        onClose = onClose,
    )

    @Composable
    override fun Detached(state: HomeWidgetsState) {
        if (state.solarSystemFullscreen) {
            SolarSystemWindow(
                date = state.selectedDate,
                inIsrael = state.userInIsrael,
                kiddushLevanaEarliest = state.kiddushLevanaEarliest,
                kiddushLevanaLatest = state.kiddushLevanaLatest,
                onClose = { state.solarSystemFullscreen = false },
            )
        }
    }
}
