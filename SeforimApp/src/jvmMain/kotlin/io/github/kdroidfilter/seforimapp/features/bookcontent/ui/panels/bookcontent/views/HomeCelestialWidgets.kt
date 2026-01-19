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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import io.github.kdroidfilter.seforimapp.earthwidget.EarthWidgetLocation
import io.github.kdroidfilter.seforimapp.earthwidget.EarthWidgetMoonSkyView
import io.github.kdroidfilter.seforimapp.earthwidget.EarthWidgetZmanimView
import io.github.kdroidfilter.seforimapp.earthwidget.KiddushLevanaEarliestOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.KiddushLevanaLatestOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.ROZmanimCalendar
import io.github.kdroidfilter.seforimapp.earthwidget.ZmanimOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.computeZmanimTimes
import io.github.kdroidfilter.seforimapp.earthwidget.timeZoneForLocation
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.Community
import io.github.kdroidfilter.seforimapp.features.zmanim.data.worldPlaces
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import kotlin.math.abs
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Divider
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
import seforimapp.seforimapp.generated.resources.home_widget_card_first_light_abbrev
import seforimapp.seforimapp.generated.resources.home_widget_card_first_light_title
import seforimapp.seforimapp.generated.resources.home_widget_card_midnight_abbrev
import seforimapp.seforimapp.generated.resources.home_widget_card_midnight_title
import seforimapp.seforimapp.generated.resources.home_widget_card_noon_abbrev
import seforimapp.seforimapp.generated.resources.home_widget_card_noon_title
import seforimapp.seforimapp.generated.resources.home_widget_card_sunrise_abbrev
import seforimapp.seforimapp.generated.resources.home_widget_card_sunrise_title
import seforimapp.seforimapp.generated.resources.home_widget_card_sunset_abbrev
import seforimapp.seforimapp.generated.resources.home_widget_card_sunset_title
import seforimapp.seforimapp.generated.resources.home_widget_shema_gra_label
import seforimapp.seforimapp.generated.resources.home_widget_shema_mga_label
import seforimapp.seforimapp.generated.resources.home_widget_shema_title
import seforimapp.seforimapp.generated.resources.home_widget_shema_title_abbrev
import seforimapp.seforimapp.generated.resources.home_widget_shabbat_entry_label
import seforimapp.seforimapp.generated.resources.home_widget_shabbat_exit_label
import seforimapp.seforimapp.generated.resources.home_widget_tefila_title
import seforimapp.seforimapp.generated.resources.home_widget_tefila_title_abbrev
import seforimapp.seforimapp.generated.resources.home_widget_label_astronomical_dawn
import seforimapp.seforimapp.generated.resources.home_widget_label_night
import seforimapp.seforimapp.generated.resources.home_widget_label_noon
import seforimapp.seforimapp.generated.resources.home_widget_label_sunrise
import seforimapp.seforimapp.generated.resources.home_widget_label_sunset
import seforimapp.seforimapp.generated.resources.home_widget_tzais_geonim_label
import seforimapp.seforimapp.generated.resources.home_widget_tzais_geonim_label_abbrev
import seforimapp.seforimapp.generated.resources.home_widget_tzais_rabbeinu_tam_label
import seforimapp.seforimapp.generated.resources.home_widget_visible_stars_title
import seforimapp.seforimapp.generated.resources.home_widget_visible_stars_title_abbrev
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Date

private const val ZMANIM_LAYOUT_SCALE = 1.5f
private val ZMANIM_CARD_HEIGHT = 90.dp * ZMANIM_LAYOUT_SCALE
private val ZMANIM_VERTICAL_SPACING = 12.dp * ZMANIM_LAYOUT_SCALE
private val ZMANIM_HORIZONTAL_SPACING = 12.dp
private val MIN_ZMANIM_CARD_WIDTH = 99.dp
private val MIN_EARTH_WIDGET_WIDTH = 230.dp
private val MIN_WIDTH_FOR_EXTRA_CARDS = 443.dp

@Immutable
private data class DayMarker(
    val label: StringResource,
    val time: String,
    val position: Float,
    val color: Color
)

@Immutable
private data class DayMomentCardData(
    val title: StringResource,
    val titleAbbrev: StringResource? = null,
    val time: String,
    val timeValue: Date?,
    val accentStart: Color,
    val accentEnd: Color
)

@Immutable
private data class LunarCycleData(
    val dayValue: String,
    val illuminationPercent: Int,
    val moonriseTime: String,
    val moonsetTime: String,
    val nextFullMoonIn: String
)

@Immutable
private data class ShabbatTimes(
    val parashaName: String,
    val entryTime: Date?,
    val exitTime: Date?,
)

@Immutable
private sealed class ZmanimGridItem {
    @Immutable
    data class Moment(
        val data: DayMomentCardData,
        val onClick: (() -> Unit)?,
    ) : ZmanimGridItem()

    @Immutable
    data class Shema(
        val title: StringResource,
        val titleAbbrev: StringResource? = null,
        val graLabel: StringResource,
        val graTime: String,
        val graTimeValue: Date?,
        val mgaLabel: StringResource,
        val mgaTime: String,
        val mgaTimeValue: Date?,
        val onGraClick: (() -> Unit)?,
        val onMgaClick: (() -> Unit)?,
    ) : ZmanimGridItem()

    @Immutable
    data class Tefila(
        val title: StringResource,
        val titleAbbrev: StringResource? = null,
        val graLabel: StringResource,
        val graTime: String,
        val graTimeValue: Date?,
        val mgaLabel: StringResource,
        val mgaTime: String,
        val mgaTimeValue: Date?,
        val onGraClick: (() -> Unit)?,
        val onMgaClick: (() -> Unit)?,
    ) : ZmanimGridItem()

    @Immutable
    data class VisibleStars(
        val title: StringResource,
        val titleAbbrev: StringResource? = null,
        val geonimLabel: StringResource,
        val geonimLabelAbbrev: StringResource? = null,
        val geonimTime: String,
        val geonimTimeValue: Date?,
        val rabbeinuTamLabel: StringResource,
        val rabbeinuTamLabelAbbrev: StringResource? = null,
        val rabbeinuTamTime: String,
        val rabbeinuTamTimeValue: Date?,
        val onGeonimClick: (() -> Unit)?,
        val onRabbeinuTamClick: (() -> Unit)?,
    ) : ZmanimGridItem()

    @Immutable
    data class Shabbat(
        val title: String,
        val entryLabel: StringResource,
        val entryTime: String,
        val entryTimeValue: Date?,
        val exitLabel: StringResource,
        val exitTime: String,
        val exitTimeValue: Date?,
        val onEntryClick: (() -> Unit)?,
        val onExitClick: (() -> Unit)?,
    ) : ZmanimGridItem()

    @Immutable
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
    // Use Sephardic zmanim calculations for SEPHARADE community
    val zmanimOpinion = remember(userCommunity) {
        if (userCommunity == Community.SEPHARADE) {
            ZmanimOpinion.SEPHARDIC
        } else {
            ZmanimOpinion.DEFAULT
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

    // Compute zmanim times based on selected date, effective location, and community opinion
    val zmanimTimes = remember(selectedDate, effectiveLocation, zmanimOpinion) {
        computeZmanimTimes(selectedDate, effectiveLocation, zmanimOpinion)
    }
    val shabbatTimes = remember(selectedDate, effectiveLocation, zmanimOpinion, effectiveCityLabel) {
        computeShabbatTimes(selectedDate, effectiveLocation, zmanimOpinion, effectiveCityLabel)
    }
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
            titleAbbrev = Res.string.home_widget_card_first_light_abbrev,
            time = formatTime(zmanimTimes.alosHashachar),
            timeValue = zmanimTimes.alosHashachar,
            accentStart = Color(0xFF8AB4F8),
            accentEnd = Color(0xFFC3DAFE)
        ),
        DayMomentCardData(
            title = Res.string.home_widget_card_sunrise_title,
            titleAbbrev = Res.string.home_widget_card_sunrise_abbrev,
            time = formatTime(zmanimTimes.sunrise),
            timeValue = zmanimTimes.sunrise,
            accentStart = Color(0xFFFFCA7A),
            accentEnd = Color(0xFFFFE0A3)
        ),
        DayMomentCardData(
            title = Res.string.home_widget_card_noon_title,
            titleAbbrev = Res.string.home_widget_card_noon_abbrev,
            time = formatTime(zmanimTimes.chatzosHayom),
            timeValue = zmanimTimes.chatzosHayom,
            accentStart = Color(0xFFFFA94D),
            accentEnd = Color(0xFFFFC58A)
        ),
        DayMomentCardData(
            title = Res.string.home_widget_card_sunset_title,
            titleAbbrev = Res.string.home_widget_card_sunset_abbrev,
            time = formatTime(zmanimTimes.sunset),
            timeValue = zmanimTimes.sunset,
            accentStart = Color(0xFF9CB9FF),
            accentEnd = Color(0xFFB6D4FF)
        )
    )

    val chatzosLaylaCard = DayMomentCardData(
        title = Res.string.home_widget_card_midnight_title,
        titleAbbrev = Res.string.home_widget_card_midnight_abbrev,
        time = formatTime(zmanimTimes.chatzosLayla),
        timeValue = zmanimTimes.chatzosLayla,
        accentStart = Color(0xFF8CA6FF),
        accentEnd = Color(0xFFC2D0FF)
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val horizontalSpacing = ZMANIM_HORIZONTAL_SPACING
        val verticalSpacing = ZMANIM_VERTICAL_SPACING
        val maxContentWidth = 1000.dp
        val effectiveWidth = maxContentWidth.coerceAtMost(maxWidth)
        val availableWidth = effectiveWidth - horizontalSpacing
        val rightColumnWidth = availableWidth * 0.35f
        val leftColumnWidth = availableWidth * 0.65f
        val maxColumnsLimit = 5
        val showExtraCards = maxWidth >= MIN_WIDTH_FOR_EXTRA_CARDS

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
                            titleAbbrev = Res.string.home_widget_shema_title_abbrev,
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
                            titleAbbrev = Res.string.home_widget_tefila_title_abbrev,
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
                    titleAbbrev = Res.string.home_widget_visible_stars_title_abbrev,
                    geonimLabel = Res.string.home_widget_tzais_geonim_label,
                    geonimLabelAbbrev = Res.string.home_widget_tzais_geonim_label_abbrev,
                    geonimTime = formatTime(tzaisGeonim),
                    geonimTimeValue = tzaisGeonim,
                    rabbeinuTamLabel = Res.string.home_widget_tzais_rabbeinu_tam_label,
                    rabbeinuTamTime = formatTime(tzaisRabbeinuTam),
                    rabbeinuTamTimeValue = tzaisRabbeinuTam,
                    onGeonimClick = tzaisGeonim?.let { { onZmanimClick(it) } },
                    onRabbeinuTamClick = tzaisRabbeinuTam?.let { { onZmanimClick(it) } },
                )
            )
            if (showExtraCards) {
                val chatzosLaylaClick = chatzosLaylaCard.timeValue?.let { { onZmanimClick(it) } }
                add(ZmanimGridItem.Moment(chatzosLaylaCard, chatzosLaylaClick))
            }
            val shabbatEntryTime = shabbatTimes.entryTime
            val shabbatExitTime = shabbatTimes.exitTime
            add(
                ZmanimGridItem.Shabbat(
                    title = shabbatTimes.parashaName,
                    entryLabel = Res.string.home_widget_shabbat_entry_label,
                    entryTime = formatTime(shabbatEntryTime),
                    entryTimeValue = shabbatEntryTime,
                    exitLabel = Res.string.home_widget_shabbat_exit_label,
                    exitTime = formatTime(shabbatExitTime),
                    exitTimeValue = shabbatExitTime,
                    onEntryClick = shabbatEntryTime?.let { { onZmanimClick(it) } },
                    onExitClick = shabbatExitTime?.let { { onZmanimClick(it) } },
                )
            )
            if (showExtraCards) {
                add(
                    ZmanimGridItem.MoonSky(
                        referenceTime = moonReferenceTime,
                        location = effectiveLocation,
                    )
                )
            }
        }
        val zmanimItemCount = zmanimItems.size
        val baseColumns = maxColumnsLimit.coerceAtMost(zmanimItemCount).coerceAtLeast(1)
        val columns = if (baseColumns == 5) {
            val totalSpacing = horizontalSpacing * (baseColumns - 1)
            val cardWidth = (leftColumnWidth - totalSpacing) / baseColumns
            if (cardWidth < MIN_ZMANIM_CARD_WIDTH) 4 else baseColumns
        } else {
            baseColumns
        }
        val rowCount = ((zmanimItemCount + columns - 1) / columns).coerceAtLeast(1)
        val leftColumnHeight = (ZMANIM_CARD_HEIGHT * rowCount) +
            (verticalSpacing * (rowCount - 1).coerceAtLeast(0))
        val heightForSphere = leftColumnHeight
        val showEarthWidget = rightColumnWidth >= MIN_EARTH_WIDGET_WIDTH
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
                        .weight(if (showEarthWidget) 0.65f else 1f)
                        .height(leftColumnHeight),
                    verticalArrangement = Arrangement.spacedBy(verticalSpacing)
                ) {
                    ZmanimCardsGrid(
                        items = zmanimItems,
                        columns = columns,
                        horizontalSpacing = horizontalSpacing,
                        verticalSpacing = verticalSpacing,
                        selectedTimeMillis = selectedTimeMillis,
                        compactMode = !showExtraCards,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (showEarthWidget) {
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
private fun ZmanimCardsGrid(
    items: List<ZmanimGridItem>,
    columns: Int,
    horizontalSpacing: Dp,
    verticalSpacing: Dp,
    selectedTimeMillis: Long?,
    compactMode: Boolean = false,
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
                                compactMode = compactMode,
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
                                titleAbbrev = item.titleAbbrev,
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
                                compactMode = compactMode,
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
                                titleAbbrev = item.titleAbbrev,
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
                                compactMode = compactMode,
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
                                titleAbbrev = item.titleAbbrev,
                                leftLabel = item.geonimLabel,
                                leftLabelAbbrev = item.geonimLabelAbbrev,
                                leftTime = item.geonimTime,
                                leftTimeValue = item.geonimTimeValue,
                                leftSelected = isLeftSelected,
                                rightLabel = item.rabbeinuTamLabel,
                                rightLabelAbbrev = item.rabbeinuTamLabelAbbrev,
                                rightTime = item.rabbeinuTamTime,
                                rightTimeValue = item.rabbeinuTamTimeValue,
                                rightSelected = isRightSelected,
                                onLeftClick = item.onGeonimClick,
                                onRightClick = item.onRabbeinuTamClick,
                                compactMode = compactMode,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        is ZmanimGridItem.Shabbat -> {
                            val isLeftSelected = selectedTimeMillis != null &&
                                item.entryTimeValue?.time == selectedTimeMillis
                            val isRightSelected = selectedTimeMillis != null &&
                                item.exitTimeValue?.time == selectedTimeMillis
                            ShabbatDualTimeCard(
                                title = item.title,
                                entryLabel = item.entryLabel,
                                entryTime = item.entryTime,
                                entryTimeValue = item.entryTimeValue,
                                entrySelected = isLeftSelected,
                                exitLabel = item.exitLabel,
                                exitTime = item.exitTime,
                                exitTimeValue = item.exitTimeValue,
                                exitSelected = isRightSelected,
                                onEntryClick = item.onEntryClick,
                                onExitClick = item.onExitClick,
                                compactMode = compactMode,
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
    compactMode: Boolean = false,
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
                    .background(JewelTheme.globalColors.outlines.focused.copy(alpha = 0.12f))
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
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                GradientDot(
                    colorStart = data.accentStart,
                    colorEnd = data.accentEnd,
                    size = 11.dp,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                AdaptiveCardTitle(
                    text = stringResource(data.title),
                    abbreviation = data.titleAbbrev?.let { stringResource(it) },
                    color = labelColor,
                    compactMode = compactMode,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                TimeValueLarge(
                    time = data.time,
                    color = JewelTheme.globalColors.text.normal,
                    compactMode = compactMode,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun AdaptiveCardTitle(
    text: String,
    abbreviation: String? = null,
    color: Color,
    compactMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    AdaptiveSingleLineText(
        text = text,
        abbreviation = abbreviation,
        color = color,
        fontSize = if (compactMode) 11.sp else 13.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun AdaptiveSingleLineText(
    text: String,
    abbreviation: String? = null,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    val resolvedAbbrev = abbreviation?.takeIf { it.isNotBlank() }
    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = constraints.maxWidth
        val textMeasurer = rememberTextMeasurer()
        val shouldAbbreviate = remember(text, resolvedAbbrev, maxWidthPx) {
            if (resolvedAbbrev == null || maxWidthPx == Constraints.Infinity || maxWidthPx <= 0) {
                false
            } else {
                val layoutResult = textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = TextStyle(
                        color = color,
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        textAlign = textAlign
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = maxWidthPx)
                )
                layoutResult.didOverflowWidth || layoutResult.hasVisualOverflow
            }
        }
        val displayText = if (shouldAbbreviate) resolvedAbbrev ?: text else text

        Text(
            text = displayText,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
        )
    }
}

@Composable
private fun DualTimeCard(
    title: StringResource,
    titleAbbrev: StringResource? = null,
    leftLabel: StringResource,
    leftLabelAbbrev: StringResource? = null,
    leftTime: String,
    leftTimeValue: Date?,
    leftSelected: Boolean,
    rightLabel: StringResource,
    rightLabelAbbrev: StringResource? = null,
    rightTime: String,
    rightTimeValue: Date?,
    rightSelected: Boolean,
    onLeftClick: (() -> Unit)? = null,
    onRightClick: (() -> Unit)? = null,
    compactMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    DualTimeCardContent(
        title = stringResource(title),
        titleAbbrev = titleAbbrev?.let { stringResource(it) },
        leftLabel = stringResource(leftLabel),
        leftLabelAbbrev = leftLabelAbbrev?.let { stringResource(it) },
        leftTime = leftTime,
        leftTimeValue = leftTimeValue,
        leftSelected = leftSelected,
        rightLabel = stringResource(rightLabel),
        rightLabelAbbrev = rightLabelAbbrev?.let { stringResource(it) },
        rightTime = rightTime,
        rightTimeValue = rightTimeValue,
        rightSelected = rightSelected,
        modifier = modifier,
        onLeftClick = onLeftClick,
        onRightClick = onRightClick,
        compactMode = compactMode,
    )
}

@Composable
private fun DualTimeCardContent(
    title: String,
    titleAbbrev: String? = null,
    leftLabel: String,
    leftLabelAbbrev: String? = null,
    leftTime: String,
    leftTimeValue: Date?,
    leftSelected: Boolean,
    rightLabel: String,
    rightLabelAbbrev: String? = null,
    rightTime: String,
    rightTimeValue: Date?,
    rightSelected: Boolean,
    modifier: Modifier = Modifier,
    onLeftClick: (() -> Unit)? = null,
    onRightClick: (() -> Unit)? = null,
    backgroundOverride: Brush? = null,
    borderColorOverride: Color? = null,
    accentStartOverride: Color? = null,
    accentEndOverride: Color? = null,
    premiumOverlay: Brush? = null,
    compactMode: Boolean = false,
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
    val resolvedBackground = backgroundOverride ?: background
    val resolvedBorderColor = borderColorOverride ?: borderColor
    val resolvedAccentStart = accentStartOverride ?: accentStart
    val resolvedAccentEnd = accentEndOverride ?: accentEnd
    val dividerColor = resolvedBorderColor.copy(alpha = 0.7f)
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
            .background(resolvedBackground)
            .border(1.dp, resolvedBorderColor, shape)
    ) {
        if (premiumOverlay != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(premiumOverlay)
            )
        }
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
                    .background(JewelTheme.globalColors.outlines.focused.copy(alpha = 0.12f))
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
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                GradientDot(
                    colorStart = resolvedAccentStart,
                    colorEnd = resolvedAccentEnd,
                    size = 11.dp,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                AdaptiveCardTitle(
                    text = title,
                    abbreviation = titleAbbrev,
                    color = labelColor,
                    compactMode = compactMode,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val labelFontSize = if (compactMode) 10.sp else 12.sp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AdaptiveSingleLineText(
                            text = leftLabel,
                            abbreviation = leftLabelAbbrev,
                            color = labelColor,
                            fontSize = labelFontSize,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TimeValue(
                            time = leftTime,
                            color = JewelTheme.globalColors.text.normal,
                            compactMode = compactMode,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Divider(
                        orientation = Orientation.Vertical,
                        modifier = Modifier
                            .fillMaxHeight(0.5f)
                            .align(Alignment.CenterVertically)
                            .width(1.dp),
                        color = dividerColor
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AdaptiveSingleLineText(
                            text = rightLabel,
                            abbreviation = rightLabelAbbrev,
                            color = labelColor,
                            fontSize = labelFontSize,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TimeValue(
                            time = rightTime,
                            color = JewelTheme.globalColors.text.normal,
                            compactMode = compactMode,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
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
private fun TimeValue(
    time: String,
    color: Color,
    compactMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val primaryFontSize = if (compactMode) 18.sp else 22.sp
    val secondaryFontSize = if (compactMode) 17.sp else 21.sp
    val parts = remember(time) { splitTimeParts(time) }
    if (parts == null) {
        Text(
            text = time,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = primaryFontSize,
            modifier = modifier
        )
        return
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = parts.first,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = primaryFontSize,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = parts.second,
            color = color.copy(alpha = 0.8f),
            fontWeight = FontWeight.SemiBold,
            fontSize = secondaryFontSize,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TimeValueLarge(
    time: String,
    color: Color,
    compactMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val primaryFontSize = if (compactMode) 24.sp else 30.sp
    val secondaryFontSize = if (compactMode) 22.sp else 28.sp
    val parts = remember(time) { splitTimeParts(time) }
    if (parts == null) {
        Text(
            text = time,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = primaryFontSize,
            modifier = modifier
        )
        return
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = parts.first,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = primaryFontSize,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = parts.second,
            color = color.copy(alpha = 0.75f),
            fontWeight = FontWeight.SemiBold,
            fontSize = secondaryFontSize,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun splitTimeParts(time: String): Pair<String, String>? {
    val sanitized = time.replace("\u2066", "").replace("\u2069", "").trim()
    if (sanitized.isEmpty()) return null
    val parts = sanitized.split(":")
    if (parts.size != 2) return null
    val hours = parts[0].trim()
    val minutes = parts[1].trim()
    if (hours.isEmpty() || minutes.isEmpty()) return null
    return hours to minutes
}

@Composable
private fun ShabbatDualTimeCard(
    title: String,
    entryLabel: StringResource,
    entryTime: String,
    entryTimeValue: Date?,
    entrySelected: Boolean,
    exitLabel: StringResource,
    exitTime: String,
    exitTimeValue: Date?,
    exitSelected: Boolean,
    modifier: Modifier = Modifier,
    onEntryClick: (() -> Unit)? = null,
    onExitClick: (() -> Unit)? = null,
    compactMode: Boolean = false,
) {
    val isDark = JewelTheme.isDark
    val panelBackground = JewelTheme.globalColors.panelBackground
    val premiumStart = if (isDark) {
        panelBackground.blendTowards(Color(0xFF2B1F06), 0.7f)
    } else {
        panelBackground.blendTowards(Color(0xFFFFF4D0), 0.92f)
    }
    val premiumMid = if (isDark) {
        panelBackground.blendTowards(Color(0xFF4A3309), 0.6f)
    } else {
        panelBackground.blendTowards(Color(0xFFFFE3A8), 0.86f)
    }
    val premiumEnd = if (isDark) {
        panelBackground.blendTowards(Color(0xFF7A5414), 0.5f)
    } else {
        panelBackground.blendTowards(Color(0xFFFFD187), 0.8f)
    }
    val background = Brush.verticalGradient(listOf(premiumStart, premiumMid, premiumEnd))
    val borderColor = if (isDark) Color(0xFFB78A2E) else Color(0xFFE0B65C)
    val accentStart = if (isDark) Color(0xFFF4D37D) else Color(0xFFC58A0E)
    val accentEnd = if (isDark) Color(0xFFFFE7B0) else Color(0xFFFFD489)
    val sheen = if (isDark) {
        Brush.horizontalGradient(
            listOf(
                Color.White.copy(alpha = 0.14f),
                Color.Transparent,
                Color.White.copy(alpha = 0.08f)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                Color.White.copy(alpha = 0.3f),
                Color.Transparent,
                Color.White.copy(alpha = 0.12f)
            )
        )
    }

    DualTimeCardContent(
        title = title,
        leftLabel = stringResource(entryLabel),
        leftTime = entryTime,
        leftTimeValue = entryTimeValue,
        leftSelected = entrySelected,
        rightLabel = stringResource(exitLabel),
        rightTime = exitTime,
        rightTimeValue = exitTimeValue,
        rightSelected = exitSelected,
        modifier = modifier,
        onLeftClick = onEntryClick,
        onRightClick = onExitClick,
        backgroundOverride = background,
        borderColorOverride = borderColor,
        accentStartOverride = accentStart,
        accentEndOverride = accentEnd,
        premiumOverlay = sheen,
        compactMode = compactMode,
    )
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

private fun computeShabbatTimes(
    date: LocalDate,
    location: EarthWidgetLocation,
    opinion: ZmanimOpinion = ZmanimOpinion.DEFAULT,
    cityLabel: String? = null,
): ShabbatTimes {
    val shabbatDate = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
    val fridayDate = shabbatDate.minusDays(1)

    fun calendarForDate(localDate: LocalDate): Calendar {
        return Calendar.getInstance(location.timeZone).apply {
            set(Calendar.YEAR, localDate.year)
            set(Calendar.MONTH, localDate.monthValue - 1)
            set(Calendar.DAY_OF_MONTH, localDate.dayOfMonth)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    val geoLocation = GeoLocation(
        "shabbat",
        location.latitude,
        location.longitude,
        location.elevationMeters,
        location.timeZone,
    )

    val parashaName = run {
        val jewishCalendar = JewishCalendar().apply {
            setDate(calendarForDate(shabbatDate))
        }
        val formatter = HebrewDateFormatter().apply {
            isHebrewFormat = true
            isUseGershGershayim = false
        }
        val parasha = formatter.formatParsha(jewishCalendar)
        if (parasha.isBlank()) formatter.formatSpecialParsha(jewishCalendar) else parasha
    }
    val parashaTitle = if (parashaName.isBlank()) "שבת" else "שבת $parashaName"
    val isJerusalem = isJerusalemLocation(location, cityLabel)

    // Calculate entry and exit times based on opinion
    val (entryTime, exitTime) = when (opinion) {
        ZmanimOpinion.SEPHARDIC -> {
            val isInIsrael = location.timeZone.id == "Asia/Jerusalem"
            val useAmudehHoraah = !isInIsrael
            // Sephardic: Use ROZmanimCalendar with default Ohr HaChaim/Amudei Horaah rules
            // Candle lighting: 20 minutes before sunset (Ohr HaChaim default)
            // End of Shabbat: Ateret Torah in Israel, Amudei Horaah outside Israel
            val entryCalendar = ROZmanimCalendar(geoLocation).apply {
                calendar = calendarForDate(fridayDate)
                isUseElevation = false
                isUseAmudehHoraah = useAmudehHoraah
            }
            val exitCalendar = ROZmanimCalendar(geoLocation).apply {
                calendar = calendarForDate(shabbatDate)
                isUseElevation = false
                isUseAmudehHoraah = useAmudehHoraah
            }
            val entry = entryCalendar.getCandleLightingWithElevation(20.0)
            val exit = if (useAmudehHoraah) {
                exitCalendar.getTzeitShabbatAmudeiHoraah()
            } else {
                val offsetMinutes = if (isInIsrael) 30.0 else 40.0
                exitCalendar.getTzaisAteretTorah(offsetMinutes)
            }
            entry to exit
        }
        ZmanimOpinion.DEFAULT -> {
            val entryCalendar = ComplexZmanimCalendar(geoLocation).apply {
                calendar = calendarForDate(fridayDate)
            }
            val exitCalendar = ComplexZmanimCalendar(geoLocation).apply {
                calendar = calendarForDate(shabbatDate)
            }
            val entry = if (isJerusalem) {
                entryCalendar.setCandleLightingOffset(40.0)
                entryCalendar.candleLighting
            } else {
                entryCalendar.candleLighting
            }
            val exit = if (isJerusalem) {
                offsetDateByMinutes(exitCalendar.sunset, 40.0)
            } else {
                exitCalendar.tzais
            }
            entry to exit
        }
    }

    return ShabbatTimes(
        parashaName = parashaTitle,
        entryTime = entryTime,
        exitTime = exitTime,
    )
}

private fun isJerusalemLocation(location: EarthWidgetLocation, cityLabel: String?): Boolean {
    val trimmedLabel = cityLabel?.trim().orEmpty()
    if (trimmedLabel == "ירושלים" || trimmedLabel.equals("Jerusalem", ignoreCase = true)) {
        return true
    }

    val jerusalem = worldPlaces["ישראל"]?.get("ירושלים") ?: return false
    val latDiff = abs(location.latitude - jerusalem.lat)
    val lonDiff = abs(location.longitude - jerusalem.lng)
    return latDiff < 0.1 && lonDiff < 0.1
}

private fun offsetDateByMinutes(date: Date?, minutes: Double): Date? {
    if (date == null) return null
    return Date(date.time + (minutes * 60_000L).toLong())
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
