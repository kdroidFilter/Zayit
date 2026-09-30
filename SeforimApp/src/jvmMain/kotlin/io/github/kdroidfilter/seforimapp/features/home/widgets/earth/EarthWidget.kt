package io.github.kdroidfilter.seforimapp.features.home.widgets.earth

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.earthwidget.EarthWidgetLocation
import io.github.kdroidfilter.seforimapp.earthwidget.EarthWidgetZmanimView
import io.github.kdroidfilter.seforimapp.earthwidget.isEarthWidgetSupported
import io.github.kdroidfilter.seforimapp.earthwidget.timeZoneForLocation
import io.github.kdroidfilter.seforimapp.features.home.widgets.GridSize
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetCard
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetSize
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.features.zmanim.data.worldPlaces
import org.jetbrains.jewel.foundation.theme.JewelTheme

/** The globe at the selected moment; its orbit labels pick the date, its city list the location. */
internal object EarthWidget : HomeWidget {
    override val id = "earth"
    override val sizes = mapOf(WidgetSize.SMALL to GridSize(5, 2f), WidgetSize.MEDIUM to GridSize(7, 2f))
    override val defaultSize = WidgetSize.MEDIUM
    override val isSupported get() = isEarthWidgetSupported

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val accent = rememberAccentColor(JewelTheme.isDark)
        val accentRgbInt =
            ((accent.red * 255).toInt() shl 16) or
                ((accent.green * 255).toInt() shl 8) or
                (accent.blue * 255).toInt()
        val locationOptions =
            remember {
                worldPlaces.mapValues { (_, cities) ->
                    cities.mapValues { (_, place) ->
                        EarthWidgetLocation(
                            latitude = place.lat,
                            longitude = place.lng,
                            elevationMeters = place.elevation,
                            timeZone = timeZoneForLocation(place.lat, place.lng),
                        )
                    }
                }
            }
        WidgetCard(modifier) {
            // Filament renders every vsync while composed; hidden tabs stay composed, so drop it there.
            if (LocalTabSelected.current) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val sphereBase = minOf(maxWidth, maxHeight)
                    val sphereSize = if (sphereBase < 140.dp) sphereBase else (sphereBase * 0.98f).coerceAtLeast(140.dp)
                    EarthWidgetZmanimView(
                        modifier = Modifier.fillMaxSize(),
                        sphereSize = sphereSize,
                        locationOverride = state.location,
                        targetTimeMillis = state.targetTime?.time,
                        targetDateEpochDay = state.selectedDate.toEpochDay(),
                        onDateSelect = state::selectDate,
                        onLocationSelect = state::selectLocation,
                        containerBackground = Color.Transparent,
                        showOrbitLabels = true,
                        showMoonInOrbit = true,
                        earthSizeFraction = 0.6f,
                        locationLabel = state.cityLabel,
                        locationOptions = locationOptions,
                        kiddushLevanaEarliestOpinion = state.kiddushLevanaEarliest,
                        kiddushLevanaLatestOpinion = state.kiddushLevanaLatest,
                        kiddushLevanaColorRgb = accentRgbInt,
                    )
                }
            }
        }
    }
}
