package io.github.kdroidfilter.seforimapp.features.home.widgets.earth

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.earthwidget.EarthWidgetLocation
import io.github.kdroidfilter.seforimapp.earthwidget.EarthWidgetZmanimView
import io.github.kdroidfilter.seforimapp.earthwidget.LocalWidgetAntiAliasing
import io.github.kdroidfilter.seforimapp.earthwidget.isEarthWidgetSupported
import io.github.kdroidfilter.seforimapp.earthwidget.timeZoneForLocation
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.EarthPreview
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.LocalFollowsPlay
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetCard
import io.github.kdroidfilter.seforimapp.features.home.widgets.playMillisHere
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.features.home.widgets.shownDateHere
import io.github.kdroidfilter.seforimapp.features.home.widgets.solarsystem.solarSystemOptions
import io.github.kdroidfilter.seforimapp.features.zmanim.data.worldPlaces
import org.jetbrains.jewel.foundation.theme.JewelTheme
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widget_name_earth

/** The globe at the selected moment; its orbit labels pick the date, its city list the location. */
internal object EarthWidget : HomeWidget {
    override val id = "earth"
    override val title = Res.string.home_widget_name_earth
    override val defaultSpan = CellSpan(7, 4)
    override val minSpan = CellSpan(5, 4)
    override val maxSpan = CellSpan(12, 8)
    override val isSupported get() = isEarthWidgetSupported

    @Composable
    override fun Preview(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) = EarthPreview(modifier)

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
        val playMillis = state.playMillisHere()
        // The 3D views' anti-aliasing, as picked in the solar system's options (off by default)
        val antiAliasing = solarSystemOptions(state).antiAliasing
        WidgetCard(modifier) {
            // Filament renders every vsync while composed; hidden tabs stay composed, so drop it there.
            if (LocalTabSelected.current) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val sphereBase = minOf(maxWidth, maxHeight)
                    val sphereSize = if (sphereBase < 140.dp) sphereBase else (sphereBase * 0.98f).coerceAtLeast(140.dp)
                    CompositionLocalProvider(LocalWidgetAntiAliasing provides antiAliasing) {
                        EarthWidgetZmanimView(
                            modifier = Modifier.fillMaxSize(),
                            sphereSize = sphereSize,
                            locationOverride = state.location,
                            targetTimeMillis = playMillis ?: state.targetTime?.time,
                            // While playing, the day comes with the instant (both would pull it each its way)
                            targetDateEpochDay = state.shownDateHere().toEpochDay().takeIf { playMillis == null },
                            followClock = playMillis != null,
                            onDateSelect = state::selectDate,
                            inIsrael = state.inIsrael,
                            // The widget's chrome, its date picker included, is always dark
                            accentColor = rememberAccentColor(isDark = true),
                            // In the solar system's full window, whose own picker sets the day
                            showDatePicker = !LocalFollowsPlay.current,
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
}
