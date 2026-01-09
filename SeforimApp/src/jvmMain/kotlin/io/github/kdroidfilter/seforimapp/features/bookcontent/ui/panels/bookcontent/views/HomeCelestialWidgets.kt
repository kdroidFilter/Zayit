package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import io.github.kdroidfilter.seforimapp.core.presentation.theme.AppColors
import io.github.kdroidfilter.seforimapp.earthwidget.EarthWidgetLocation
import io.github.kdroidfilter.seforimapp.earthwidget.EarthWidgetMoonSkyView
import io.github.kdroidfilter.seforimapp.earthwidget.EarthWidgetZmanimView
import io.github.kdroidfilter.seforimapp.earthwidget.KiddushLevanaEarliestOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.KiddushLevanaLatestOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.computeZmanimTimes
import io.github.kdroidfilter.seforimapp.earthwidget.timeZoneForLocation
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.Community
import io.github.kdroidfilter.seforimapp.features.zmanim.data.worldPlaces
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_cycle_card_subtitle
import seforimapp.seforimapp.generated.resources.home_cycle_card_title
import seforimapp.seforimapp.generated.resources.home_cycle_chip_solar_noon
import seforimapp.seforimapp.generated.resources.home_cycle_chip_twilight
import seforimapp.seforimapp.generated.resources.home_lunar_card_subtitle
import seforimapp.seforimapp.generated.resources.home_lunar_card_title
import seforimapp.seforimapp.generated.resources.home_lunar_chip_day
import seforimapp.seforimapp.generated.resources.home_lunar_label_illumination
import seforimapp.seforimapp.generated.resources.home_lunar_label_moonrise
import seforimapp.seforimapp.generated.resources.home_lunar_label_moonset
import seforimapp.seforimapp.generated.resources.home_lunar_next_full_moon_label
import seforimapp.seforimapp.generated.resources.home_lunar_next_full_moon_value
import seforimapp.seforimapp.generated.resources.home_widget_card_first_light_title
import seforimapp.seforimapp.generated.resources.home_widget_card_noon_title
import seforimapp.seforimapp.generated.resources.home_widget_card_sunrise_title
import seforimapp.seforimapp.generated.resources.home_widget_card_sunset_title
import seforimapp.seforimapp.generated.resources.home_widget_shema_gra_label
import seforimapp.seforimapp.generated.resources.home_widget_shema_mga_label
import seforimapp.seforimapp.generated.resources.home_widget_shema_title
import seforimapp.seforimapp.generated.resources.home_widget_tefila_title
import seforimapp.seforimapp.generated.resources.home_widget_label_astronomical_dawn
import seforimapp.seforimapp.generated.resources.home_widget_label_night
import seforimapp.seforimapp.generated.resources.home_widget_label_noon
import seforimapp.seforimapp.generated.resources.home_widget_label_sunrise
import seforimapp.seforimapp.generated.resources.home_widget_label_sunset
import seforimapp.seforimapp.generated.resources.home_widget_tzais_geonim_label
import seforimapp.seforimapp.generated.resources.home_widget_tzais_rabbeinu_tam_label
import seforimapp.seforimapp.generated.resources.home_widget_visible_stars_title
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date

private const val ZMANIM_LAYOUT_SCALE = 1.5f
private val ZMANIM_CARD_HEIGHT = 90.dp * ZMANIM_LAYOUT_SCALE
private val ZMANIM_VERTICAL_SPACING = 12.dp * ZMANIM_LAYOUT_SCALE
private val ZMANIM_HORIZONTAL_SPACING = 12.dp

private data class DayMarker(
    val label: StringResource,
    val time: String,
    val position: Float,
    val color: Color
)

private data class DayMomentCardData(
    val title: StringResource,
    val time: String,
    val timeValue: Date?,
    val accentStart: Color,
    val accentEnd: Color
)

private data class LunarCycleData(
    val dayValue: String,
    val illuminationPercent: Int,
    val moonriseTime: String,
    val moonsetTime: String,
    val nextFullMoonIn: String
)

private sealed class ZmanimGridItem {
    data class Moment(
        val data: DayMomentCardData,
        val onClick: (() -> Unit)?,
    ) : ZmanimGridItem()

    data class Shema(
        val title: StringResource,
        val graLabel: StringResource,
        val graTime: String,
        val graTimeValue: Date?,
        val mgaLabel: StringResource,
        val mgaTime: String,
        val mgaTimeValue: Date?,
        val onGraClick: (() -> Unit)?,
        val onMgaClick: (() -> Unit)?,
    ) : ZmanimGridItem()

    data class Tefila(
        val title: StringResource,
        val graLabel: StringResource,
        val graTime: String,
        val graTimeValue: Date?,
        val mgaLabel: StringResource,
        val mgaTime: String,
        val mgaTimeValue: Date?,
        val onGraClick: (() -> Unit)?,
        val onMgaClick: (() -> Unit)?,
    ) : ZmanimGridItem()

    data class VisibleStars(
        val title: StringResource,
        val geonimLabel: StringResource,
        val geonimTime: String,
        val geonimTimeValue: Date?,
        val rabbeinuTamLabel: StringResource,
        val rabbeinuTamTime: String,
        val rabbeinuTamTimeValue: Date?,
        val onGeonimClick: (() -> Unit)?,
        val onRabbeinuTamClick: (() -> Unit)?,
    ) : ZmanimGridItem()

    data class MoonSky(
        val referenceTime: Date,
        val location: EarthWidgetLocation,
    ) : ZmanimGridItem()
}

@Composable
fun HomeCelestialWidgets(
    modifier: Modifier = Modifier,
    userCommunityCode: String? = null,
    locationState: HomeCelestialWidgetsState,
) {
    val userPlace = locationState.userPlace
    val userCityLabel = locationState.userCityLabel
    val userCommunity = remember(userCommunityCode) {
        userCommunityCode?.let { code -> runCatching { Community.valueOf(code) }.getOrNull() }
    }
    val (kiddushLevanaEarliestOpinion, kiddushLevanaLatestOpinion) = remember(userCommunity) {
        if (userCommunity == Community.SEPHARADE) {
            KiddushLevanaEarliestOpinion.DAYS_7 to KiddushLevanaLatestOpinion.DAYS_15
        } else {
            KiddushLevanaEarliestOpinion.DAYS_3 to KiddushLevanaLatestOpinion.BETWEEN_MOLDOS
        }
    }
    val locationOptions = remember {
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

    // Temporary location selection (does not affect user settings)
    var temporaryLocation by remember { mutableStateOf<EarthWidgetLocation?>(null) }
    var temporaryCityLabel by remember { mutableStateOf<String?>(null) }

    // Use temporary location if selected, otherwise fall back to user's saved location
    val effectiveLocation = temporaryLocation ?: remember(userPlace) {
        val tz = timeZoneForLocation(userPlace.lat, userPlace.lng)
        EarthWidgetLocation(
            latitude = userPlace.lat,
            longitude = userPlace.lng,
            elevationMeters = userPlace.elevation,
            timeZone = tz
        )
    }
    val effectiveCityLabel = temporaryCityLabel ?: userCityLabel
    val timeZone = effectiveLocation.timeZone

    // Shared date state - controls both the Earth widget and zmanim cards
    val todayDate = remember(timeZone) { LocalDate.now(timeZone.toZoneId()) }
    var selectedDate by remember(todayDate) { mutableStateOf(todayDate) }

    // Compute zmanim times based on selected date and effective location
    val zmanimTimes = remember(selectedDate, effectiveLocation) { computeZmanimTimes(selectedDate, effectiveLocation) }
    val timeFormatter = remember(timeZone) {
        SimpleDateFormat("HH:mm").apply { this.timeZone = timeZone }
    }
    fun formatTime(date: Date?): String = date?.let { "\u2066${timeFormatter.format(it)}\u2069" } ?: ""
    var earthWidgetTargetTime by remember { mutableStateOf<Date?>(null) }
    val fallbackMoonTime = remember(selectedDate, timeZone) {
        Calendar.getInstance(timeZone).apply {
            set(Calendar.YEAR, selectedDate.year)
            set(Calendar.MONTH, selectedDate.monthValue - 1)
            set(Calendar.DAY_OF_MONTH, selectedDate.dayOfMonth)
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }
    val moonReferenceTime = earthWidgetTargetTime ?: zmanimTimes.tzais ?: fallbackMoonTime
    val selectedTimeMillis = earthWidgetTargetTime?.time

    // When clicking a zmanim card, update the Earth widget's target time
    val onZmanimClick: (Date?) -> Unit = { date ->
        date?.let { earthWidgetTargetTime = Date(it.time) }
    }

    // When clicking an orbit label in the Earth widget, update the shared date
    val onDateSelected: (LocalDate) -> Unit = { date ->
        selectedDate = date
        // Reset target time when date changes so widget shows noon of new date
        earthWidgetTargetTime = null
    }

    // When selecting a location in the Earth widget, update temporary location (without changing user settings)
    val onLocationSelectedHandler: (String, String, EarthWidgetLocation) -> Unit = { _, city, location ->
        temporaryLocation = location
        temporaryCityLabel = city
        // Reset target time when location changes
        earthWidgetTargetTime = null
    }

    val markers = listOf(
        DayMarker(
            Res.string.home_widget_label_astronomical_dawn,
            formatTime(zmanimTimes.alosHashachar),
            0.05f,
            Color(0xFFC084FC)
        ),
        DayMarker(
            Res.string.home_widget_label_sunrise,
            formatTime(zmanimTimes.sunrise),
            0.18f,
            Color(0xFFFFD166)
        ),
        DayMarker(
            Res.string.home_widget_label_noon,
            formatTime(zmanimTimes.chatzosHayom),
            0.52f,
            Color(0xFFFFAD61)
        ),
        DayMarker(
            Res.string.home_widget_label_sunset,
            formatTime(zmanimTimes.sunset),
            0.78f,
            Color(0xFF7CB7FF)
        ),
        DayMarker(
            Res.string.home_widget_label_night,
            formatTime(zmanimTimes.tzais),
            0.94f,
            Color(0xFFAEB8FF)
        )
    )

    val momentCards = listOf(
        DayMomentCardData(
            title = Res.string.home_widget_card_first_light_title,
            time = formatTime(zmanimTimes.alosHashachar),
            timeValue = zmanimTimes.alosHashachar,
            accentStart = Color(0xFF8AB4F8),
            accentEnd = Color(0xFFC3DAFE)
        ),
        DayMomentCardData(
            title = Res.string.home_widget_card_sunrise_title,
            time = formatTime(zmanimTimes.sunrise),
            timeValue = zmanimTimes.sunrise,
            accentStart = Color(0xFFFFCA7A),
            accentEnd = Color(0xFFFFE0A3)
        ),
        DayMomentCardData(
            title = Res.string.home_widget_card_noon_title,
            time = formatTime(zmanimTimes.chatzosHayom),
            timeValue = zmanimTimes.chatzosHayom,
            accentStart = Color(0xFFFFA94D),
            accentEnd = Color(0xFFFFC58A)
        ),
        DayMomentCardData(
            title = Res.string.home_widget_card_sunset_title,
            time = formatTime(zmanimTimes.sunset),
            timeValue = zmanimTimes.sunset,
            accentStart = Color(0xFF9CB9FF),
            accentEnd = Color(0xFFB6D4FF)
        )
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val horizontalSpacing = ZMANIM_HORIZONTAL_SPACING
        val verticalSpacing = ZMANIM_VERTICAL_SPACING
        val maxContentWidth = 1000.dp
        val effectiveWidth = maxContentWidth.coerceAtMost(maxWidth)
        val availableWidth = effectiveWidth - horizontalSpacing
        val leftColumnWidth = availableWidth * 0.65f
        val rightColumnWidth = availableWidth * 0.35f
        val minCardWidth = 150.dp
        val maxColumnsLimit = 4

        val zmanimItems = buildList {
            momentCards.forEachIndexed { index, card ->
                val onClick = if (card.timeValue != null) {
                    { onZmanimClick(card.timeValue) }
                } else {
                    null
                }
                add(ZmanimGridItem.Moment(card, onClick))
                if (index == 1) {
                    val graTimeValue = zmanimTimes.sofZmanShmaGra
                    val mgaTimeValue = zmanimTimes.sofZmanShmaMga
                    add(
                        ZmanimGridItem.Shema(
                            title = Res.string.home_widget_shema_title,
                            graLabel = Res.string.home_widget_shema_gra_label,
                            graTime = formatTime(graTimeValue),
                            graTimeValue = graTimeValue,
                            mgaLabel = Res.string.home_widget_shema_mga_label,
                            mgaTime = formatTime(mgaTimeValue),
                            mgaTimeValue = mgaTimeValue,
                            onGraClick = graTimeValue?.let { { onZmanimClick(it) } },
                            onMgaClick = mgaTimeValue?.let { { onZmanimClick(it) } },
                        )
                    )
                    val tefilaGraTime = zmanimTimes.sofZmanTfilaGra
                    val tefilaMgaTime = zmanimTimes.sofZmanTfilaMga
                    add(
                        ZmanimGridItem.Tefila(
                            title = Res.string.home_widget_tefila_title,
                            graLabel = Res.string.home_widget_shema_gra_label,
                            graTime = formatTime(tefilaGraTime),
                            graTimeValue = tefilaGraTime,
                            mgaLabel = Res.string.home_widget_shema_mga_label,
                            mgaTime = formatTime(tefilaMgaTime),
                            mgaTimeValue = tefilaMgaTime,
                            onGraClick = tefilaGraTime?.let { { onZmanimClick(it) } },
                            onMgaClick = tefilaMgaTime?.let { { onZmanimClick(it) } },
                        )
                    )
                }
            }
            val tzaisGeonim = zmanimTimes.tzais
            val tzaisRabbeinuTam = zmanimTimes.tzaisRabbeinuTam
            add(
                ZmanimGridItem.VisibleStars(
                    title = Res.string.home_widget_visible_stars_title,
                    geonimLabel = Res.string.home_widget_tzais_geonim_label,
                    geonimTime = formatTime(tzaisGeonim),
                    geonimTimeValue = tzaisGeonim,
                    rabbeinuTamLabel = Res.string.home_widget_tzais_rabbeinu_tam_label,
                    rabbeinuTamTime = formatTime(tzaisRabbeinuTam),
                    rabbeinuTamTimeValue = tzaisRabbeinuTam,
                    onGeonimClick = tzaisGeonim?.let { { onZmanimClick(it) } },
                    onRabbeinuTamClick = tzaisRabbeinuTam?.let { { onZmanimClick(it) } },
                )
            )
            add(
                ZmanimGridItem.MoonSky(
                    referenceTime = moonReferenceTime,
                    location = effectiveLocation,
                )
            )
        }
        val zmanimItemCount = zmanimItems.size
        val maxColumns = ((leftColumnWidth + horizontalSpacing) / (minCardWidth + horizontalSpacing))
            .toInt()
            .coerceAtLeast(1)
        val columns = maxColumns.coerceAtMost(maxColumnsLimit).coerceAtMost(zmanimItemCount)
        val rowCount = ((zmanimItemCount + columns - 1) / columns).coerceAtLeast(1)
        val leftColumnHeight = (ZMANIM_CARD_HEIGHT * rowCount) +
            (verticalSpacing * (rowCount - 1).coerceAtLeast(0))
        val heightForSphere = leftColumnHeight
        val sphereBase = minOf(rightColumnWidth, heightForSphere)
        val rawSphereSize = sphereBase * 0.98f
        val sphereSize = if (sphereBase < 140.dp) sphereBase else rawSphereSize.coerceAtLeast(140.dp)
        val rightColumnHeightModifier = Modifier.height(leftColumnHeight)

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.width(effectiveWidth),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.65f)
                        .height(leftColumnHeight),
                    verticalArrangement = Arrangement.spacedBy(verticalSpacing)
                ) {
                    ZmanimCardsGrid(
                        items = zmanimItems,
                        columns = columns,
                        horizontalSpacing = horizontalSpacing,
                        verticalSpacing = verticalSpacing,
                        selectedTimeMillis = selectedTimeMillis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(
                    modifier = Modifier.weight(0.35f),
                    verticalArrangement = Arrangement.spacedBy(verticalSpacing)
                ) {
                    CelestialWidgetCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(rightColumnHeightModifier),
                        backgroundColor = Color.Black,
                    ) {
                        EarthWidgetZmanimView(
                            modifier = Modifier.fillMaxSize(),
                            sphereSize = sphereSize,
                            locationOverride = effectiveLocation,
                            targetTime = earthWidgetTargetTime,
                            targetDate = selectedDate,
                            onDateSelected = onDateSelected,
                            onLocationSelected = onLocationSelectedHandler,
                            allowLocationSelection = true,
                            containerBackground = Color.Transparent,
                            contentPadding = 0.dp,
                            showControls = false,
                            showOrbitLabels = true,
                            showMoonInOrbit = true,
                            initialShowMoonFromMarker = false,
                            useScroll = false,
                            earthSizeFraction = 0.6f,
                            locationLabel = effectiveCityLabel,
                            locationOptions = locationOptions,
                            kiddushLevanaEarliestOpinion = kiddushLevanaEarliestOpinion,
                            kiddushLevanaLatestOpinion = kiddushLevanaLatestOpinion,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LunarCycleCard(data: LunarCycleData, modifier: Modifier = Modifier) {
    val isDark = JewelTheme.isDark
    val shape = RoundedCornerShape(22.dp)
    val panelBackground = JewelTheme.globalColors.panelBackground
    val accent = JewelTheme.globalColors.text.info
    val background = if (isDark) {
        Brush.verticalGradient(
            listOf(
                panelBackground.blendTowards(Color.White, 0.06f),
                panelBackground.blendTowards(Color.Black, 0.18f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                panelBackground.blendTowards(Color.White, 0.12f),
                panelBackground.blendTowards(accent, 0.08f)
            )
        )
    }
    val borderColor = if (isDark) {
        JewelTheme.globalColors.borders.disabled
    } else {
        JewelTheme.globalColors.borders.normal
    }
    val chipBackground = if (isDark) {
        panelBackground.blendTowards(Color.White, 0.16f)
    } else {
        panelBackground.blendTowards(Color.White, 0.85f)
    }
    val chipBorder = if (isDark) {
        JewelTheme.globalColors.borders.disabled
    } else {
        JewelTheme.globalColors.borders.normal
    }
    val textColor = JewelTheme.globalColors.text.normal
    val labelColor = textColor.copy(alpha = 0.78f)
    val secondary = textColor.copy(alpha = 0.76f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MoonPhaseIcon(isDark = isDark)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(Res.string.home_lunar_card_title),
                            color = textColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(Res.string.home_lunar_card_subtitle),
                            color = secondary,
                            fontSize = 12.sp
                        )
                    }
                }
                PillChip(
                    text = stringResource(Res.string.home_lunar_chip_day, data.dayValue),
                    backgroundColor = chipBackground,
                    borderColor = chipBorder,
                    dotStart = Color(0xFF7C8FF5),
                    dotEnd = Color(0xFF4F6BDE)
                )
            }

            MoonIllustration(isDark = isDark)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.home_lunar_label_illumination),
                        color = secondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${data.illuminationPercent}%",
                        color = textColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
                IlluminationBar(
                    progress = data.illuminationPercent / 100f,
                    isDark = isDark
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MoonEventCard(
                    label = Res.string.home_lunar_label_moonrise,
                    value = data.moonriseTime,
                    accentStart = Color(0xFFA5C6FF),
                    accentEnd = Color(0xFFCBE0FF),
                    modifier = Modifier.weight(1f)
                )
                MoonEventCard(
                    label = Res.string.home_lunar_label_moonset,
                    value = data.moonsetTime,
                    accentStart = Color(0xFFB5B2FF),
                    accentEnd = Color(0xFFD7D4FF),
                    modifier = Modifier.weight(1f)
                )
            }

            NextFullMoonBar(
                label = stringResource(Res.string.home_lunar_next_full_moon_label),
                value = stringResource(Res.string.home_lunar_next_full_moon_value, data.nextFullMoonIn),
                isDark = isDark
            )
        }
    }
}

@Composable
private fun MoonIllustration(isDark: Boolean, modifier: Modifier = Modifier) {
    val panelBackground = JewelTheme.globalColors.panelBackground
    val accent = JewelTheme.globalColors.text.info
    val glowGradient = if (isDark) {
        Brush.radialGradient(
            listOf(
                accent.copy(alpha = 0.2f),
                panelBackground.blendTowards(Color.Black, 0.25f)
            )
        )
    } else {
        Brush.radialGradient(
            listOf(
                accent.copy(alpha = 0.18f),
                panelBackground.blendTowards(accent, 0.08f)
            )
        )
    }
    val moonGradient = if (isDark) {
        Brush.radialGradient(
            listOf(
                Color.White.copy(alpha = 0.85f),
                panelBackground.blendTowards(Color.Black, 0.3f)
            )
        )
    } else {
        Brush.radialGradient(
            listOf(
                panelBackground.blendTowards(Color.White, 0.7f),
                panelBackground.blendTowards(accent, 0.18f)
            )
        )
    }
    val shadowColor = if (isDark) {
        panelBackground.blendTowards(Color.Black, 0.45f)
    } else {
        panelBackground.blendTowards(Color.Black, 0.08f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(170.dp)
                .background(glowGradient, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(122.dp)
                .background(moonGradient, CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(122.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, shadowColor.copy(alpha = 0.9f))
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(150.dp)
                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(Color.White.copy(alpha = 0.35f), CircleShape)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color.White.copy(alpha = 0.28f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color.White.copy(alpha = 0.32f), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun IlluminationBar(progress: Float, isDark: Boolean, modifier: Modifier = Modifier) {
    val clamped = progress.coerceIn(0f, 1f)
    val baseTrack = JewelTheme.globalColors.borders.disabled
    val trackColor = if (isDark) {
        baseTrack.copy(alpha = 0.8f)
    } else {
        JewelTheme.globalColors.borders.normal.copy(alpha = 0.45f)
    }
    val accent = JewelTheme.globalColors.text.info
    val fillGradient = if (isDark) {
        Brush.horizontalGradient(
            listOf(
                accent.blendTowards(Color.White, 0.25f),
                accent
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                accent.blendTowards(Color.White, 0.35f),
                accent
            )
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clamped)
                .background(fillGradient)
        )
    }
}

@Composable
private fun MoonEventCard(
    label: StringResource,
    value: String,
    accentStart: Color,
    accentEnd: Color,
    modifier: Modifier = Modifier
) {
    val isDark = JewelTheme.isDark
    val shape = RoundedCornerShape(16.dp)
    val panelBackground = JewelTheme.globalColors.panelBackground
    val background = if (isDark) {
        Brush.verticalGradient(
            listOf(
                panelBackground.blendTowards(Color.White, 0.06f),
                panelBackground.blendTowards(Color.Black, 0.18f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                panelBackground.blendTowards(Color.White, 0.10f),
                panelBackground.blendTowards(JewelTheme.globalColors.text.info, 0.05f)
            )
        )
    }
    val borderColor = if (isDark) {
        JewelTheme.globalColors.borders.disabled
    } else {
        JewelTheme.globalColors.borders.normal
    }
    val textColor = JewelTheme.globalColors.text.normal
    val secondary = textColor.copy(alpha = 0.75f)

    Column(
        modifier = modifier
            .height(90.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientDot(accentStart, accentEnd, size = 10.dp)
            Text(
                text = stringResource(label),
                color = secondary,
                fontSize = 12.sp
            )
        }
        Text(
            text = value,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
    }
}

@Composable
private fun NextFullMoonBar(label: String, value: String, isDark: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val panelBackground = JewelTheme.globalColors.panelBackground
    val accent = JewelTheme.globalColors.text.info
    val background = if (isDark) {
        Brush.horizontalGradient(
            listOf(
                panelBackground.blendTowards(accent, 0.28f),
                panelBackground.blendTowards(accent, 0.14f)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                panelBackground.blendTowards(accent, 0.18f),
                panelBackground.blendTowards(accent, 0.08f)
            )
        )
    }
    val labelColor = if (isDark) {
        JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
    } else {
        JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
    }
    val valueColor = if (isDark) {
        accent
    } else {
        accent
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = valueColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun MoonPhaseIcon(isDark: Boolean, modifier: Modifier = Modifier) {
    val panelBackground = JewelTheme.globalColors.panelBackground
    val accent = JewelTheme.globalColors.text.info
    val gradient = if (isDark) {
        Brush.radialGradient(
            listOf(
                accent.blendTowards(Color.White, 0.3f),
                panelBackground.blendTowards(accent, 0.6f)
            )
        )
    } else {
        Brush.radialGradient(
            listOf(
                accent.blendTowards(Color.White, 0.4f),
                panelBackground.blendTowards(accent, 0.5f)
            )
        )
    }
    val shadow = if (isDark) {
        panelBackground.blendTowards(Color.Black, 0.5f)
    } else {
        panelBackground.blendTowards(Color.Black, 0.12f)
    }

    Box(
        modifier = modifier
            .size(28.dp)
            .background(gradient, CircleShape)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(12.dp)
                .clip(CircleShape)
                .background(shadow)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(28.dp)
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        )
    }
}

@Composable
private fun DayCycleCard(markers: List<DayMarker>, modifier: Modifier = Modifier) {
    if (markers.isEmpty()) return
    val isDark = JewelTheme.isDark
    val shape = RoundedCornerShape(22.dp)
    val panelBackground = JewelTheme.globalColors.panelBackground
    val background = if (isDark) {
        Brush.verticalGradient(
            listOf(
                panelBackground.blendTowards(Color.White, 0.06f),
                panelBackground.blendTowards(Color.Black, 0.18f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                panelBackground.blendTowards(Color.White, 0.10f),
                panelBackground.blendTowards(JewelTheme.globalColors.text.info, 0.05f)
            )
        )
    }
    val borderColor = if (isDark) {
        JewelTheme.globalColors.borders.disabled
    } else {
        JewelTheme.globalColors.borders.normal
    }
    val chipBackground = if (isDark) {
        panelBackground.blendTowards(Color.White, 0.16f)
    } else {
        panelBackground.blendTowards(Color.White, 0.9f)
    }
    val chipBorder = if (isDark) {
        JewelTheme.globalColors.borders.disabled
    } else {
        JewelTheme.globalColors.borders.normal
    }
    val barGradient = if (isDark) {
        Brush.horizontalGradient(
            listOf(
                Color(0xFF161C46),
                Color(0xFF1E3A8A),
                Color(0xFFFFC857),
                Color(0xFFD97706),
                Color(0xFF0B132B)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                panelBackground.blendTowards(Color.Black, 0.15f),
                JewelTheme.globalColors.text.info,
                panelBackground.blendTowards(Color.White, 0.35f)
            )
        )
    }
    val textColor = JewelTheme.globalColors.text.normal

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GradientDot(
                        colorStart = JewelTheme.globalColors.text.info,
                        colorEnd = panelBackground.blendTowards(JewelTheme.globalColors.text.info, 0.4f),
                        size = 12.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(Res.string.home_cycle_card_title),
                            color = textColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(Res.string.home_cycle_card_subtitle),
                            color = textColor.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillChip(
                        text = stringResource(Res.string.home_cycle_chip_twilight, markers.first().time),
                        backgroundColor = chipBackground,
                        borderColor = chipBorder,
                        dotStart = JewelTheme.globalColors.text.info,
                        dotEnd = panelBackground.blendTowards(JewelTheme.globalColors.text.info, 0.4f)
                    )
                    val noonTime = markers.getOrNull(2)?.time ?: ""
                    PillChip(
                        text = stringResource(Res.string.home_cycle_chip_solar_noon, noonTime),
                        backgroundColor = chipBackground,
                        borderColor = chipBorder,
                        dotStart = JewelTheme.globalColors.text.info,
                        dotEnd = panelBackground.blendTowards(Color.White, 0.7f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(barGradient)
                    .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sorted = markers.sortedBy { it.position }
                    var previous = 0f
                    sorted.forEachIndexed { index, marker ->
                        val gap = (marker.position - previous).coerceAtLeast(0f)
                        if (gap > 0f) {
                            Spacer(Modifier.weight(gap))
                        }
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(marker.color.copy(alpha = 0.95f))
                        )
                        previous = marker.position
                        if (index == sorted.lastIndex) {
                            val tail = (1f - previous).coerceAtLeast(0f)
                            if (tail > 0f) {
                                Spacer(Modifier.weight(tail))
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                markers.forEach { marker ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        GradientDot(
                            colorStart = marker.color,
                            colorEnd = marker.color.copy(alpha = 0.9f),
                            size = 10.dp
                        )
                        Text(
                            text = marker.time,
                            color = textColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = stringResource(marker.label),
                            color = textColor.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZmanimCardsGrid(
    items: List<ZmanimGridItem>,
    columns: Int,
    horizontalSpacing: Dp,
    verticalSpacing: Dp,
    selectedTimeMillis: Long?,
    modifier: Modifier = Modifier,
) {
    val safeColumns = columns.coerceAtLeast(1)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        items.chunked(safeColumns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)
            ) {
                rowItems.forEach { item ->
                    when (item) {
                        is ZmanimGridItem.Moment -> {
                            val isSelected = selectedTimeMillis != null &&
                                item.data.timeValue?.time == selectedTimeMillis
                            DayMomentCard(
                                data = item.data,
                                isSelected = isSelected,
                                modifier = Modifier.weight(1f),
                                onClick = item.onClick
                            )
                        }
                        is ZmanimGridItem.Shema -> {
                            val isLeftSelected = selectedTimeMillis != null &&
                                item.mgaTimeValue?.time == selectedTimeMillis
                            val isRightSelected = selectedTimeMillis != null &&
                                item.graTimeValue?.time == selectedTimeMillis
                            DualTimeCard(
                                title = item.title,
                                leftLabel = item.mgaLabel,
                                leftTime = item.mgaTime,
                                leftTimeValue = item.mgaTimeValue,
                                leftSelected = isLeftSelected,
                                rightLabel = item.graLabel,
                                rightTime = item.graTime,
                                rightTimeValue = item.graTimeValue,
                                rightSelected = isRightSelected,
                                onLeftClick = item.onMgaClick,
                                onRightClick = item.onGraClick,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        is ZmanimGridItem.Tefila -> {
                            val isLeftSelected = selectedTimeMillis != null &&
                                item.mgaTimeValue?.time == selectedTimeMillis
                            val isRightSelected = selectedTimeMillis != null &&
                                item.graTimeValue?.time == selectedTimeMillis
                            DualTimeCard(
                                title = item.title,
                                leftLabel = item.mgaLabel,
                                leftTime = item.mgaTime,
                                leftTimeValue = item.mgaTimeValue,
                                leftSelected = isLeftSelected,
                                rightLabel = item.graLabel,
                                rightTime = item.graTime,
                                rightTimeValue = item.graTimeValue,
                                rightSelected = isRightSelected,
                                onLeftClick = item.onMgaClick,
                                onRightClick = item.onGraClick,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        is ZmanimGridItem.VisibleStars -> {
                            val isLeftSelected = selectedTimeMillis != null &&
                                item.geonimTimeValue?.time == selectedTimeMillis
                            val isRightSelected = selectedTimeMillis != null &&
                                item.rabbeinuTamTimeValue?.time == selectedTimeMillis
                            DualTimeCard(
                                title = item.title,
                                leftLabel = item.geonimLabel,
                                leftTime = item.geonimTime,
                                leftTimeValue = item.geonimTimeValue,
                                leftSelected = isLeftSelected,
                                rightLabel = item.rabbeinuTamLabel,
                                rightTime = item.rabbeinuTamTime,
                                rightTimeValue = item.rabbeinuTamTimeValue,
                                rightSelected = isRightSelected,
                                onLeftClick = item.onGeonimClick,
                                onRightClick = item.onRabbeinuTamClick,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        is ZmanimGridItem.MoonSky -> {
                            MoonSkyCard(
                                referenceTime = item.referenceTime,
                                location = item.location,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                repeat(safeColumns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayMomentCard(
    data: DayMomentCardData,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val isDark = JewelTheme.isDark
    val shape = RoundedCornerShape(18.dp)
    val panelBackground = JewelTheme.globalColors.panelBackground
    val background = if (isDark) {
        Brush.verticalGradient(
            listOf(
                panelBackground.blendTowards(Color.White, 0.06f),
                panelBackground.blendTowards(Color.Black, 0.18f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                panelBackground.blendTowards(Color.White, 0.10f),
                panelBackground.blendTowards(JewelTheme.globalColors.text.info, 0.05f)
            )
        )
    }
    val borderColor = if (isDark) {
        JewelTheme.globalColors.borders.disabled
    } else {
        JewelTheme.globalColors.borders.normal
    }
    val labelColor = JewelTheme.globalColors.text.normal.copy(alpha = 0.78f)
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val isClickable = onClick != null
    val showHover = isClickable && isHovered
    val selectionOverlay = JewelTheme.globalColors.text.selected.copy(alpha = if (isDark) 0.2f else 0.12f)
    val selectionBorder = JewelTheme.globalColors.borders.focused
    val hoverModifier = if (isClickable) {
        Modifier.hoverable(hoverSource).pointerHoverIcon(PointerIcon.Hand)
    } else {
        Modifier
    }
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .height(ZMANIM_CARD_HEIGHT)
            .clip(shape)
            .then(hoverModifier)
            .then(clickModifier)
            .background(background)
            .border(1.dp, borderColor, shape)
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(selectionOverlay)
            )
        } else if (showHover) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.HOVER_HIGHLIGHT)
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, selectionBorder, shape)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GradientDot(data.accentStart, data.accentEnd, size = 13.dp)
                Text(
                    text = stringResource(data.title),
                    color = labelColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = data.time,
                color = JewelTheme.globalColors.text.normal,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DualTimeCard(
    title: StringResource,
    leftLabel: StringResource,
    leftTime: String,
    leftTimeValue: Date?,
    leftSelected: Boolean,
    rightLabel: StringResource,
    rightTime: String,
    rightTimeValue: Date?,
    rightSelected: Boolean,
    modifier: Modifier = Modifier,
    onLeftClick: (() -> Unit)? = null,
    onRightClick: (() -> Unit)? = null,
) {
    val isDark = JewelTheme.isDark
    val shape = RoundedCornerShape(18.dp)
    val panelBackground = JewelTheme.globalColors.panelBackground
    val background = if (isDark) {
        Brush.verticalGradient(
            listOf(
                panelBackground.blendTowards(Color.White, 0.06f),
                panelBackground.blendTowards(Color.Black, 0.18f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                panelBackground.blendTowards(Color.White, 0.10f),
                panelBackground.blendTowards(JewelTheme.globalColors.text.info, 0.05f)
            )
        )
    }
    val borderColor = if (isDark) {
        JewelTheme.globalColors.borders.disabled
    } else {
        JewelTheme.globalColors.borders.normal
    }
    val labelColor = JewelTheme.globalColors.text.normal.copy(alpha = 0.78f)
    val accentStart = Color(0xFF9AE7E7)
    val accentEnd = Color(0xFFC7F5F0)
    val leftClick = onLeftClick
    val rightClick = onRightClick
    val leftClickable = leftClick != null && leftTimeValue != null
    val rightClickable = rightClick != null && rightTimeValue != null
    val leftHoverSource = remember { MutableInteractionSource() }
    val rightHoverSource = remember { MutableInteractionSource() }
    val isLeftHovered by leftHoverSource.collectIsHoveredAsState()
    val isRightHovered by rightHoverSource.collectIsHoveredAsState()
    val showHover = (leftClickable && isLeftHovered) || (rightClickable && isRightHovered)
    val isSelected = leftSelected || rightSelected
    val selectionOverlay = JewelTheme.globalColors.text.selected.copy(alpha = if (isDark) 0.2f else 0.12f)
    val selectionBorder = JewelTheme.globalColors.borders.focused
    val leftModifier = if (leftClick != null && leftTimeValue != null) {
        Modifier
            .hoverable(leftHoverSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = leftClick)
    } else {
        Modifier
    }
    val rightModifier = if (rightClick != null && rightTimeValue != null) {
        Modifier
            .hoverable(rightHoverSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = rightClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .height(ZMANIM_CARD_HEIGHT)
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(selectionOverlay)
            )
        } else if (showHover) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.HOVER_HIGHLIGHT)
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, selectionBorder, shape)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GradientDot(accentStart, accentEnd, size = 13.dp)
                Text(
                    text = stringResource(title),
                    color = labelColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(leftLabel),
                        color = labelColor,
                        fontSize = 12.sp
                    )
                    Text(
                        text = leftTime,
                        color = JewelTheme.globalColors.text.normal,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(rightLabel),
                        color = labelColor,
                        fontSize = 12.sp
                    )
                    Text(
                        text = rightTime,
                        color = JewelTheme.globalColors.text.normal,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(leftModifier)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(rightModifier)
            )
        }
    }
}

@Composable
private fun MoonSkyCard(
    referenceTime: Date,
    location: EarthWidgetLocation,
    modifier: Modifier = Modifier,
) {
    val isDark = JewelTheme.isDark
    val shape = RoundedCornerShape(18.dp)
    val borderColor = if (isDark) {
        JewelTheme.globalColors.borders.disabled
    } else {
        JewelTheme.globalColors.borders.normal
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ZMANIM_CARD_HEIGHT)
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, borderColor, shape)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val moonSize = minOf(maxWidth, maxHeight)
            EarthWidgetMoonSkyView(
                modifier = Modifier.size(moonSize),
                sphereSize = moonSize,
                location = location,
                referenceTime = referenceTime,
                showBackground = true,
                earthSizeFraction = 0.6f,
            )
        }
    }
}

@Composable
private fun CelestialWidgetCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = JewelTheme.isDark
    val shape = RoundedCornerShape(22.dp)
    val panelBackground = JewelTheme.globalColors.panelBackground
    val accent = JewelTheme.globalColors.text.info
    val background = if (backgroundColor == null) {
        if (isDark) {
            Brush.verticalGradient(
                listOf(
                    panelBackground.blendTowards(Color.White, 0.06f),
                    panelBackground.blendTowards(Color.Black, 0.18f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    panelBackground.blendTowards(Color.White, 0.10f),
                    panelBackground.blendTowards(accent, 0.05f)
                )
            )
        }
    } else {
        null
    }
    val borderColor = if (isDark) {
        JewelTheme.globalColors.borders.disabled
    } else {
        JewelTheme.globalColors.borders.normal
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (backgroundColor != null) {
                    Modifier.background(backgroundColor, shape)
                } else {
                    Modifier.background(background!!, shape)
                }
            )
            .border(1.dp, borderColor, shape),
        content = content
    )
}

@Composable
private fun PillChip(
    text: String,
    backgroundColor: Color,
    borderColor: Color,
    dotStart: Color,
    dotEnd: Color,
    modifier: Modifier = Modifier
) {
    val chipTextColor = if (JewelTheme.isDark) Color(0xFFE5E7EB) else Color(0xFF1F2937)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GradientDot(dotStart, dotEnd, size = 10.dp)
        Text(
            text = text,
            color = chipTextColor,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GradientDot(
    colorStart: Color,
    colorEnd: Color,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                brush = Brush.radialGradient(listOf(colorStart, colorEnd)),
                shape = CircleShape
            )
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
    )
}

private fun Color.blendTowards(target: Color, ratio: Float): Color {
    val clamped = ratio.coerceIn(0f, 1f)
    val inverse = 1f - clamped
    return Color(
        red = red * inverse + target.red * clamped,
        green = green * inverse + target.green * clamped,
        blue = blue * inverse + target.blue * clamped,
        alpha = alpha * inverse + target.alpha * clamped
    )
}

@Preview
@Composable
private fun HomeCelestialWidgetsPreview() {
    PreviewContainer {
        HomeCelestialWidgets(locationState = HomeCelestialWidgetsState.preview)
    }
}
