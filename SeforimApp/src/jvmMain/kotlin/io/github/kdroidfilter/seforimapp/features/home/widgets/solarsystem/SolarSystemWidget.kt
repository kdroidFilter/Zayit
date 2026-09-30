package io.github.kdroidfilter.seforimapp.features.home.widgets.solarsystem

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.earthwidget.SolarSystemWidgetView
import io.github.kdroidfilter.seforimapp.earthwidget.isEarthWidgetSupported
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetCard

/** The solar system on the selected day, openable in its own window. */
internal object SolarSystemWidget : HomeWidget {
    override val id = "solar_system"
    override val columns = 9
    override val rows = 1.5f
    override val isSupported get() = isEarthWidgetSupported

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
