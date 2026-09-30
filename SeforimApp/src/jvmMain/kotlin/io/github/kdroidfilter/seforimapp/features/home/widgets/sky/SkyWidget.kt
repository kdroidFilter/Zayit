package io.github.kdroidfilter.seforimapp.features.home.widgets.sky

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.earthwidget.SkyWidgetView
import io.github.kdroidfilter.seforimapp.earthwidget.isEarthWidgetSupported
import io.github.kdroidfilter.seforimapp.features.home.widgets.GridSize
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.SkyPreview
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetCard
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetSize
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widget_name_sky

/** The sky seen from the selected location at the selected moment. */
internal object SkyWidget : HomeWidget {
    override val id = "sky"
    override val title = Res.string.home_widget_name_sky
    override val sizes = mapOf(WidgetSize.SMALL to GridSize(5, 1.5f), WidgetSize.MEDIUM to GridSize(8, 1.5f))
    override val defaultSize = WidgetSize.SMALL
    override val isSupported get() = isEarthWidgetSupported

    @Composable
    override fun Preview(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) = SkyPreview(modifier)

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
