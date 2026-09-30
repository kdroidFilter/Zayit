package io.github.kdroidfilter.seforimapp.features.home.widgets.sky

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.earthwidget.SkyWidgetView
import io.github.kdroidfilter.seforimapp.earthwidget.isEarthWidgetSupported
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetCard

/** The sky seen from the selected location at the selected moment. */
internal object SkyWidget : HomeWidget {
    override val id = "sky"
    override val columns = 5
    override val rows = 1.5f
    override val isSupported get() = isEarthWidgetSupported

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        WidgetCard(modifier) {
            if (LocalTabSelected.current) {
                SkyWidgetView(
                    latitude = state.location.latitude,
                    longitude = state.location.longitude,
                    timeMillis = state.skyTimeMillis,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
