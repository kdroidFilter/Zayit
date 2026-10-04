package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.erkko68.filament.compose.rememberFilamentEngineAsync
import io.github.kdroidfilter.kosherkotlin.ComplexZmanimCalendar
import io.github.kdroidfilter.kosherkotlin.Zman
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewDateFormatter
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishDate
import io.github.kdroidfilter.kosherkotlin.util.GeoLocation
import io.github.kdroidfilter.kosherkotlin.util.ItimLabinaCalculator
import io.github.kdroidfilter.kosherkotlin.util.NOAACalculator
import io.github.kdroidfilter.seforimapp.hebrewcalendar.CalendarMode
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinTimeZone
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.component.LocalMenuController
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.menuStyle
import org.jetbrains.jewel.ui.theme.segmentedControlButtonStyle
import seforimapp.earthwidget.generated.resources.*
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.math.roundToInt

// ============================================================================
// CONSTANTS
// ============================================================================

/** Default marker latitude (Jerusalem). */
private const val DEFAULT_MARKER_LAT = 31.7683

/** Default marker longitude (Jerusalem). */
private const val DEFAULT_MARKER_LON = 35.2137

/** Default marker elevation in meters (Jerusalem average). */
private const val DEFAULT_MARKER_ELEVATION = 800.0

/** Default Earth axial tilt in degrees. */
private const val DEFAULT_EARTH_TILT_DEGREES = 23.44f

/**
 * Lunar synodic month in milliseconds.
 * 29 days + 12 hours + 793 chalakim (where 1 chelek = 10/3 seconds).
 */
private const val LUNAR_CYCLE_MILLIS = 29.0 * 86_400_000.0 + 12.0 * 3_600_000.0 + 793.0 * 10_000.0 / 3.0

/** Degrees per hour for GMT offset calculation. */
private const val DEGREES_PER_HOUR = 15.0

/** Israel latitude bounds (south to north). */
private const val ISRAEL_LAT_MIN = 29.0
private const val ISRAEL_LAT_MAX = 34.8

private val LOCATION_MENU_WIDTH = 320.dp
private val LOCATION_MENU_HEIGHT = 260.dp

/** Israel longitude bounds (west to east). */
private const val ISRAEL_LON_MIN = 34.0
private const val ISRAEL_LON_MAX = 36.6

/** Minimum GMT offset in hours. */
private const val MIN_GMT_OFFSET = -12

/** Maximum GMT offset in hours. */
private const val MAX_GMT_OFFSET = 14

private val KIDDUSH_LEVANA_LEGEND_COLOR = Color(0xFFFFD700)

// ============================================================================
// DATA CLASSES
// ============================================================================

/**
 * Computed rendering parameters from Zmanim calculations.
 *
 * @property lightDegrees Sun azimuth in world coordinates.
 * @property sunElevationDegrees Sun elevation angle.
 * @property moonOrbitDegrees Moon position on orbit.
 * @property moonPhaseAngleDegrees Moon phase angle (0-360).
 * @property julianDay Julian Day number for ephemeris.
 */
@Immutable
private data class ZmanimModel(
    val lightDegrees: Float,
    val sunElevationDegrees: Float,
    val moonOrbitDegrees: Float,
    val moonPhaseAngleDegrees: Float,
    val julianDay: Double,
)

/**
 * Stable wrapper for orbit labels list to enable Compose skipping.
 * Uses reference equality for stability checks.
 */
@Stable
private class StableOrbitLabels(
    val list: List<OrbitLabelData>,
)

@Stable
data class EarthWidgetLocation(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double,
    val timeZone: TimeZone,
)

data class ZmanimTimes(
    val alosHashachar: Date?,
    val sunrise: Date?,
    val sofZmanShmaGra: Date?,
    val sofZmanShmaMga: Date?,
    val sofZmanTfilaGra: Date?,
    val sofZmanTfilaMga: Date?,
    val chatzosHayom: Date?,
    val minchaGedola: Date?,
    val minchaKetana: Date?,
    val plagHamincha: Date?,
    val sunset: Date?,
    val tzais: Date?,
    val tzaisRabbeinuTam: Date?,
    val chatzosLayla: Date?,
)

/** The luach the zmanim are read from: its sun, and which opinions it prints. */
enum class ZmanimOpinion {
    /** עתים לבינה: degree-based zmanim off the luach's own refracted horizon. */
    ITIM_LABINA,

    /** אור החיים, the luach Rabbi Ovadia Yosef used: zmaniyos minutes off an ordinary sun. */
    OHR_HACHAIM,
}

// ============================================================================
// MAIN COMPOSABLE
// ============================================================================

/**
 * Earth widget with Zmanim (Jewish time) integration.
 *
 * Displays Earth and Moon with sun/moon positions calculated from
 * the Zmanim library based on location and time. Includes controls
 * for adjusting date, time, and marker location.
 *
 * @param modifier Modifier for the widget container.
 * @param sphereSize Display size of the sphere.
 * @param renderSizePx Internal render resolution.
 */
@Composable
fun EarthWidgetZmanimView(
    modifier: Modifier = Modifier,
    sphereSize: Dp = 300.dp,
    renderSizePx: Int = 350,
    locationOverride: EarthWidgetLocation? = null,
    locationLabel: String? = null,
    locationOptions: Map<String, Map<String, EarthWidgetLocation>> = emptyMap(),
    targetTimeMillis: Long? = null,
    targetDateEpochDay: Long? = null,
    /** [targetTimeMillis] is a running clock (a moment per frame): the scene follows it as is, without easing. */
    followClock: Boolean = false,
    onDateSelect: ((LocalDate) -> Unit)? = null,
    onLocationSelect: ((country: String, city: String, location: EarthWidgetLocation) -> Unit)? = null,
    containerBackground: Color? = null,
    contentPadding: Dp = 0.dp,
    showOrbitLabels: Boolean = true,
    showMoonInOrbit: Boolean = true,
    initialShowMoonFromMarker: Boolean = false,
    earthSizeFraction: Float = EARTH_SIZE_FRACTION,
    kiddushLevanaEarliestOpinion: KiddushLevanaEarliestOpinion = KiddushLevanaEarliestOpinion.DAYS_3,
    kiddushLevanaLatestOpinion: KiddushLevanaLatestOpinion = KiddushLevanaLatestOpinion.BETWEEN_MOLDOS,
    initialShowKiddushLevana: Boolean = true,
    kiddushLevanaColorRgb: Int = KIDDUSH_LEVANA_COLOR_RGB,
) {
    // Location state (defaults to Jerusalem, overridden by locationOverride)
    var markerLatitudeDegrees by remember { mutableFloatStateOf(DEFAULT_MARKER_LAT.toFloat()) }
    var markerLongitudeDegrees by remember { mutableFloatStateOf(DEFAULT_MARKER_LON.toFloat()) }
    var markerElevationMeters by remember { mutableStateOf(DEFAULT_MARKER_ELEVATION) }
    var timeZone by remember { mutableStateOf(TimeZone.getTimeZone("Asia/Jerusalem")) }

    // Display options
    var showBackground by remember { mutableStateOf(true) }
    var showOrbitPath by remember { mutableStateOf(true) }
    var showMoonFromMarker by remember { mutableStateOf(initialShowMoonFromMarker) }
    var showKiddushLevana by remember { mutableStateOf(initialShowKiddushLevana) }
    val showKiddushLevanaLegend = showKiddushLevana && showOrbitPath

    // Camera moved by the user: orbit around the ecliptic pole, elevation over the ecliptic, zoom
    val camera = rememberOrbitCameraState()

    // Date/time selection - initialized once with the default timezone, then preserved across location changes
    val initialCalendar =
        remember {
            Calendar.getInstance(TimeZone.getTimeZone("Asia/Jerusalem")).apply { time = Date() }
        }
    var selectedDate by remember {
        mutableStateOf(
            LocalDate.of(
                initialCalendar.get(Calendar.YEAR),
                initialCalendar.get(Calendar.MONTH) + 1,
                initialCalendar.get(Calendar.DAY_OF_MONTH),
            ),
        )
    }
    var selectedHour by remember {
        mutableStateOf(initialCalendar.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23))
    }
    var selectedMinute by remember {
        mutableStateOf(initialCalendar.get(Calendar.MINUTE).coerceIn(0, 59))
    }

    // Track if date/time is different from current time
    val isDateTimeModified by remember(selectedDate, selectedHour, selectedMinute, timeZone) {
        derivedStateOf {
            val now = Calendar.getInstance(timeZone)
            val currentDate =
                LocalDate.of(
                    now.get(Calendar.YEAR),
                    now.get(Calendar.MONTH) + 1,
                    now.get(Calendar.DAY_OF_MONTH),
                )
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val currentMinute = now.get(Calendar.MINUTE)
            selectedDate != currentDate || selectedHour != currentHour || selectedMinute != currentMinute
        }
    }

    LaunchedEffect(locationOverride) {
        locationOverride?.let { override ->
            markerLatitudeDegrees = override.latitude.toFloat()
            markerLongitudeDegrees = override.longitude.toFloat()
            markerElevationMeters = override.elevationMeters
            timeZone = override.timeZone
            camera.reset()

            if (targetTimeMillis == null) {
                val now = Calendar.getInstance(override.timeZone)
                selectedDate =
                    LocalDate.of(
                        now.get(Calendar.YEAR),
                        now.get(Calendar.MONTH) + 1,
                        now.get(Calendar.DAY_OF_MONTH),
                    )
                selectedHour = now.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
                selectedMinute = now.get(Calendar.MINUTE).coerceIn(0, 59)
            }
        }
    }

    LaunchedEffect(targetTimeMillis, timeZone) {
        targetTimeMillis?.let { millis ->
            val cal = Calendar.getInstance(timeZone).apply { time = Date(millis) }
            selectedDate =
                LocalDate.of(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH),
                )
            selectedHour = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
            selectedMinute = cal.get(Calendar.MINUTE).coerceIn(0, 59)
        }
    }

    // Sync with external targetDate if provided
    LaunchedEffect(targetDateEpochDay) {
        targetDateEpochDay?.let { epochDay ->
            val date = LocalDate.ofEpochDay(epochDay)
            if (date != selectedDate) {
                selectedDate = date
            }
        }
    }

    val pickedTime =
        remember(selectedDate, selectedHour, selectedMinute, timeZone) {
            Calendar
                .getInstance(timeZone)
                .apply {
                    set(Calendar.YEAR, selectedDate.year)
                    set(Calendar.MONTH, selectedDate.monthValue - 1)
                    set(Calendar.DAY_OF_MONTH, selectedDate.dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, selectedHour.coerceIn(0, 23))
                    set(Calendar.MINUTE, selectedMinute.coerceIn(0, 59))
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
        }
    // Following a running clock, its instant is the one truth, read as is at each frame (no copy a frame behind, no
    // rounding to the minute); else the date and time picked here
    val clockMillis = targetTimeMillis?.takeIf { followClock }
    val referenceTime = clockMillis?.let { Date(it) } ?: pickedTime
    val shownDay = clockMillis?.let { Instant.ofEpochMilli(it).atZone(timeZone.toZoneId()).toLocalDate() } ?: selectedDate

    // Compute astronomical model
    val model =
        remember(
            referenceTime,
            markerLatitudeDegrees,
            markerLongitudeDegrees,
            markerElevationMeters,
            timeZone,
        ) {
            computeZmanimModel(
                referenceTime = referenceTime,
                latitude = markerLatitudeDegrees.toDouble(),
                longitude = markerLongitudeDegrees.toDouble(),
                elevation = markerElevationMeters,
                timeZone = timeZone,
                earthRotationDegrees = markerLongitudeDegrees,
                earthTiltDegrees = DEFAULT_EARTH_TILT_DEGREES,
            )
        }

    val stableOrbitLabels =
        // By day, not by minute: a moment per frame (the solar system's play) would rebuild them at every frame
        remember(shownDay, timeZone, showOrbitLabels) {
            StableOrbitLabels(
                if (showOrbitLabels) {
                    computeHebrewMonthOrbitLabels(
                        referenceTime = referenceTime,
                        timeZone = timeZone,
                    )
                } else {
                    emptyList()
                },
            )
        }

    // Compute Kiddush Levana data
    val kiddushLevanaData =
        remember(
            shownDay,
            // On a molad's day the window it opens starts at the molad: the hour sides with one month or the other
            hebrewDayAt(referenceTime.time, timeZone).isAfterMolad(referenceTime.time),
            timeZone,
            showKiddushLevana,
            kiddushLevanaEarliestOpinion,
            kiddushLevanaLatestOpinion,
        ) {
            if (showKiddushLevana) {
                computeKiddushLevanaData(
                    referenceTime = referenceTime,
                    timeZone = timeZone,
                    earliestOpinion = kiddushLevanaEarliestOpinion,
                    latestOpinion = kiddushLevanaLatestOpinion,
                )
            } else {
                null
            }
        }

    val normalizedLocationOptions =
        remember(locationOptions) {
            locationOptions.filterValues { it.isNotEmpty() }
        }
    var selectedLocationSelection by remember(normalizedLocationOptions) {
        mutableStateOf(resolveInitialLocationSelection(normalizedLocationOptions, locationLabel))
    }
    var menuCountrySelection by remember(normalizedLocationOptions) {
        mutableStateOf(
            selectedLocationSelection?.country ?: normalizedLocationOptions.keys.firstOrNull(),
        )
    }
    LaunchedEffect(locationLabel, normalizedLocationOptions) {
        if (selectedLocationSelection == null && normalizedLocationOptions.isNotEmpty()) {
            selectedLocationSelection = resolveInitialLocationSelection(normalizedLocationOptions, locationLabel)
        }
        if (menuCountrySelection == null) {
            menuCountrySelection = selectedLocationSelection?.country ?: normalizedLocationOptions.keys.firstOrNull()
        }
    }
    val resolvedLocationLabel =
        when {
            normalizedLocationOptions.isNotEmpty() -> {
                selectedLocationSelection?.city ?: locationLabel?.takeIf { it.isNotBlank() }
            }

            !locationLabel.isNullOrBlank() -> {
                locationLabel
            }

            else -> {
                null
            }
        }
    val hebrewDateLabel =
        remember(shownDay, timeZone) {
            val jewishDate = jewishCalendarAt(referenceTime, timeZone)
            val dateFormatter =
                HebrewDateFormatter().apply {
                    isHebrewFormat = true
                    isUseGershGershayim = false
                }
            val dayOfMonth = dateFormatter.formatHebrewNumber(jewishDate.jewishDayOfMonth)
            val month = dateFormatter.formatMonth(jewishDate)
            val year = dateFormatter.formatHebrewNumber(jewishDate.jewishYear)
            "$dayOfMonth $month $year"
        }

    val backgroundColor = containerBackground ?: JewelTheme.globalColors.panelBackground
    val globalMenuStyle = JewelTheme.menuStyle

    val currentReferenceTime by rememberUpdatedState(referenceTime)
    // Stable callbacks to avoid recomposition - these lambdas reference mutableStateOf-backed vars
    // so they remain stable across recompositions while still accessing the latest state
    val onRecenterCallback =
        remember {
            { camera.reset() }
        }

    // Use rememberUpdatedState to keep the lambda stable while accessing latest values
    val currentTimeZone by rememberUpdatedState(timeZone)
    val currentOnDateSelected by rememberUpdatedState(onDateSelect)

    val onResetDateTimeCallback: () -> Unit = {
        val now = Calendar.getInstance(timeZone)
        selectedDate =
            LocalDate.of(
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH) + 1,
                now.get(Calendar.DAY_OF_MONTH),
            )
        selectedHour = now.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
        selectedMinute = now.get(Calendar.MINUTE).coerceIn(0, 59)
        currentOnDateSelected?.invoke(selectedDate)
    }
    val onCalendarDateSelected: (LocalDate) -> Unit = { date ->
        selectedDate = date
        currentOnDateSelected?.invoke(date)
    }
    val onOrbitLabelClickHandler: (OrbitLabelData) -> Unit =
        remember {
            { label: OrbitLabelData ->
                val jewishCalendar = jewishCalendarAt(currentReferenceTime, currentTimeZone)
                val newDate =
                    JewishDate(jewishCalendar.jewishYear, jewishCalendar.jewishMonth, label.dayOfMonth)
                        .gregorianLocalDate
                        .toJavaLocalDate()
                selectedDate = newDate
                currentOnDateSelected?.invoke(newDate)
            }
        }
    val onLocationSelectInternal: (String, String) -> Unit = selection@{ country, city ->
        val location = normalizedLocationOptions[country]?.get(city) ?: return@selection
        selectedLocationSelection = LocationSelection(country, city)
        menuCountrySelection = country
        markerLatitudeDegrees = location.latitude.toFloat()
        markerLongitudeDegrees = location.longitude.toFloat()
        markerElevationMeters = location.elevationMeters
        timeZone = location.timeZone
        camera.reset()
        onLocationSelect?.invoke(country, city, location)
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        EarthSceneContent(
            modifier = Modifier.fillMaxSize(),
            followClock = followClock,
            sphereSize = sphereSize,
            renderSizePx = renderSizePx,
            markerLongitudeDegrees = markerLongitudeDegrees,
            camera = camera,
            model = model,
            markerLatitudeDegrees = markerLatitudeDegrees,
            showBackground = showBackground,
            showOrbitPath = showOrbitPath,
            stableOrbitLabels = stableOrbitLabels,
            onOrbitLabelClick = onOrbitLabelClickHandler,
            showMoonFromMarker = showMoonFromMarker,
            showMoonInOrbit = showMoonInOrbit,
            earthSizeFraction = earthSizeFraction,
            kiddushLevanaData = kiddushLevanaData,
            kiddushLevanaColorRgb = kiddushLevanaColorRgb,
        )
        if (showKiddushLevanaLegend) {
            KiddushLevanaLegend(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 8.dp),
                legendColorRgb = kiddushLevanaColorRgb,
            )
        }
        val isViewMoved = camera.isMoved
        if (isViewMoved || isDateTimeModified) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                ResetDateTimeButton(
                    isDateTimeModified = isDateTimeModified,
                    onResetDateTime = onResetDateTimeCallback,
                )
                RecenterButton(
                    isViewMoved = isViewMoved,
                    onRecenter = onRecenterCallback,
                )
            }
        }
        IntUiTheme(isDark = true) {
            DateSelectionSplitButton(
                label = hebrewDateLabel,
                selectedDate = selectedDate,
                onDateSelect = onCalendarDateSelected,
                menuStyle = globalMenuStyle,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = 8.dp),
            )
        }
        if (resolvedLocationLabel != null) {
            IntUiTheme(isDark = true) {
                LocationSelectionSplitButton(
                    label = resolvedLocationLabel,
                    locations = normalizedLocationOptions,
                    selectedCountry = menuCountrySelection,
                    selectedSelection = selectedLocationSelection,
                    onCountrySelect = { menuCountrySelection = it },
                    onCitySelect = { country, city -> onLocationSelectInternal(country, city) },
                    menuStyle = globalMenuStyle,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 8.dp, top = 8.dp),
                )
            }
        }
    }
}

@Composable
fun EarthWidgetMoonSkyView(
    location: EarthWidgetLocation,
    referenceTimeMillis: Long,
    modifier: Modifier = Modifier,
    sphereSize: Dp = 140.dp,
    renderSizePx: Int = 0,
    showBackground: Boolean = true,
    earthSizeFraction: Float = EARTH_SIZE_FRACTION,
) {
    val markerLatitudeDegrees = location.latitude.toFloat()
    val markerLongitudeDegrees = location.longitude.toFloat()
    val model =
        remember(
            referenceTimeMillis,
            markerLatitudeDegrees,
            markerLongitudeDegrees,
            location.elevationMeters,
            location.timeZone,
        ) {
            computeZmanimModel(
                referenceTime = Date(referenceTimeMillis),
                latitude = markerLatitudeDegrees.toDouble(),
                longitude = markerLongitudeDegrees.toDouble(),
                elevation = location.elevationMeters,
                timeZone = location.timeZone,
                earthRotationDegrees = markerLongitudeDegrees,
                earthTiltDegrees = DEFAULT_EARTH_TILT_DEGREES,
            )
        }

    val density = LocalDensity.current
    val resolvedRenderSizePx =
        remember(sphereSize, renderSizePx, density) {
            if (renderSizePx > 0) {
                renderSizePx
            } else {
                (with(density) { sphereSize.toPx() } * 1.35f).roundToInt().coerceAtLeast(160)
            }
        }
    val moonState =
        remember(
            resolvedRenderSizePx,
            markerLatitudeDegrees,
            markerLongitudeDegrees,
            showBackground,
            earthSizeFraction,
            model,
        ) {
            MoonFromMarkerRenderState(
                renderSizePx = resolvedRenderSizePx,
                earthRotationDegrees = markerLongitudeDegrees,
                lightDegrees = model.lightDegrees,
                sunElevationDegrees = model.sunElevationDegrees,
                earthTiltDegrees = DEFAULT_EARTH_TILT_DEGREES,
                moonOrbitDegrees = model.moonOrbitDegrees,
                markerLatitudeDegrees = markerLatitudeDegrees,
                markerLongitudeDegrees = markerLongitudeDegrees,
                showBackgroundStars = showBackground,
                moonLightDegrees = model.lightDegrees,
                moonSunElevationDegrees = model.sunElevationDegrees,
                moonPhaseAngleDegrees = model.moonPhaseAngleDegrees,
                julianDay = model.julianDay,
                earthSizeFraction = earthSizeFraction,
            )
        }

    // Null while the engine is being created, off the UI thread
    val engine = rememberFilamentEngineAsync() ?: return WidgetLoader(modifier.size(sphereSize))
    MoonFromMarkerSceneView(
        state = moonState,
        engine = engine,
        moonTexture = rememberWidgetTextures(engine).moon,
        modifier = modifier.size(sphereSize),
    )
}

// ============================================================================
// REUSABLE UI COMPONENTS
// ============================================================================

/**
 * Earth scene with drag-to-rotate support.
 * Extracted as a separate composable to enable Compose's skipping optimization.
 */
@Composable
private fun EarthSceneContent(
    sphereSize: Dp,
    renderSizePx: Int,
    markerLongitudeDegrees: Float,
    camera: OrbitCameraState,
    model: ZmanimModel,
    markerLatitudeDegrees: Float,
    showBackground: Boolean,
    showOrbitPath: Boolean,
    stableOrbitLabels: StableOrbitLabels,
    onOrbitLabelClick: (OrbitLabelData) -> Unit,
    showMoonFromMarker: Boolean,
    showMoonInOrbit: Boolean,
    earthSizeFraction: Float,
    modifier: Modifier = Modifier,
    kiddushLevanaData: KiddushLevanaData? = null,
    kiddushLevanaColorRgb: Int = KIDDUSH_LEVANA_COLOR_RGB,
    followClock: Boolean = false,
) {
    val density = LocalDensity.current
    // A full drag across the sphere width = 180 degrees
    val degreesPerPx = remember(sphereSize, density) { with(density) { 180f / sphereSize.toPx() } }

    Box(
        modifier = modifier.orbitCameraGestures(camera) { degreesPerPx },
        contentAlignment = Alignment.Center,
    ) {
        EarthWidgetScene(
            sphereSize = sphereSize,
            renderSizePx = renderSizePx,
            earthRotationDegrees = markerLongitudeDegrees,
            lightDegrees = model.lightDegrees,
            sunElevationDegrees = model.sunElevationDegrees,
            earthTiltDegrees = DEFAULT_EARTH_TILT_DEGREES,
            moonOrbitDegrees = model.moonOrbitDegrees,
            markerLatitudeDegrees = markerLatitudeDegrees,
            markerLongitudeDegrees = markerLongitudeDegrees,
            showBackgroundStars = showBackground,
            showOrbitPath = showOrbitPath,
            orbitLabels = stableOrbitLabels.list,
            onOrbitLabelClick = onOrbitLabelClick,
            showMoonFromMarker = showMoonFromMarker,
            showMoonInOrbit = showMoonInOrbit,
            earthSizeFraction = earthSizeFraction,
            moonPhaseAngleDegrees = model.moonPhaseAngleDegrees,
            julianDay = model.julianDay,
            moonFromMarkerLightDegrees = model.lightDegrees,
            moonFromMarkerSunElevationDegrees = model.sunElevationDegrees,
            animateEarthRotation = !camera.isGesturing, // Instant rotation during gestures
            followClock = followClock,
            kiddushLevanaStartDegrees = kiddushLevanaData?.startDegrees,
            kiddushLevanaEndDegrees = kiddushLevanaData?.endDegrees,
            kiddushLevanaColorRgb = kiddushLevanaColorRgb,
            viewYawDegrees = camera.yaw,
            viewPitchDegrees = camera.pitch,
            viewZoom = camera.zoom,
        )
    }
}

/**
 * Recenter button shown when Earth is rotated away from marker.
 */
@Composable
internal fun RecenterButton(
    isViewMoved: Boolean,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isViewMoved) {
        IntUiTheme(isDark = true) {
            OutlinedButton(
                onClick = onRecenter,
                modifier = modifier,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        key = AllIconsKeys.General.Locate,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(text = stringResource(Res.string.earthwidget_recenter_button))
                }
            }
        }
    }
}

/**
 * Reset date/time button shown when date/time differs from current time.
 */
@Composable
private fun ResetDateTimeButton(
    isDateTimeModified: Boolean,
    onResetDateTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isDateTimeModified) {
        IntUiTheme(isDark = true) {
            OutlinedButton(
                onClick = onResetDateTime,
                modifier = modifier,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        key = AllIconsKeys.Actions.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(text = stringResource(Res.string.earthwidget_reset_datetime_button))
                }
            }
        }
    }
}

@Composable
private fun KiddushLevanaLegend(
    modifier: Modifier = Modifier,
    legendColorRgb: Int = KIDDUSH_LEVANA_COLOR_RGB,
) {
    IntUiTheme(isDark = true) {
        val shape = RoundedCornerShape(50)
        val background = JewelTheme.globalColors.panelBackground.copy(alpha = 0.86f)
        val borderColor = JewelTheme.globalColors.borders.disabled
        val textColor = JewelTheme.globalColors.text.normal
        val legendColor = Color(0xFF000000 + legendColorRgb)

        Row(
            modifier =
                modifier
                    .clip(shape)
                    .background(background, shape)
                    .border(1.dp, borderColor, shape)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(10.dp)
                        .background(legendColor, CircleShape)
                        .border(1.dp, borderColor, CircleShape),
            )
            Text(
                text = stringResource(Res.string.earthwidget_kiddush_levana_legend),
                color = textColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DateSelectionSplitButton(
    label: String,
    selectedDate: LocalDate,
    onDateSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    menuStyle: MenuStyle = JewelTheme.menuStyle,
) {
    io.github.kdroidfilter.seforimapp.hebrewcalendar.DateSelectionSplitButton(
        label = label,
        selectedDate = selectedDate,
        onDateSelect = onDateSelect,
        initialMode = CalendarMode.HEBREW,
        menuStyle = menuStyle,
        modifier = modifier,
    )
}

@Composable
private fun LocationSelectionSplitButton(
    label: String,
    locations: Map<String, Map<String, EarthWidgetLocation>>,
    selectedCountry: String?,
    selectedSelection: LocationSelection?,
    onCountrySelect: (String) -> Unit,
    onCitySelect: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    menuStyle: MenuStyle = JewelTheme.menuStyle,
) {
    Box(modifier = modifier) {
        OutlinedSplitButton(
            onClick = {},
            secondaryOnClick = {},
            popupModifier = Modifier.width(LOCATION_MENU_WIDTH),
            maxPopupWidth = LOCATION_MENU_WIDTH,
            menuStyle = menuStyle,
            content = {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
            },
            menuContent = {
                passiveItem {
                    IntUiTheme(isDark = menuStyle.isDark) {
                        if (locations.isEmpty()) {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            LocationMenuContent(
                                locations = locations,
                                selectedCountry = selectedCountry,
                                selectedCity =
                                    selectedSelection
                                        ?.takeIf { it.country == selectedCountry }
                                        ?.city,
                                onCountrySelect = onCountrySelect,
                                onCitySelect = onCitySelect,
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun LocationMenuContent(
    locations: Map<String, Map<String, EarthWidgetLocation>>,
    selectedCountry: String?,
    selectedCity: String?,
    onCountrySelect: (String) -> Unit,
    onCitySelect: (String, String) -> Unit,
) {
    val menuController = LocalMenuController.current
    val inputModeManager = LocalInputModeManager.current
    val closeMenu = {
        menuController.closeAll(inputModeManager.inputMode, true)
    }
    val countries = remember(locations) { locations.keys.toList() }
    val activeCountry = selectedCountry ?: countries.firstOrNull()
    val cities =
        remember(activeCountry, locations) {
            activeCountry?.let { locations[it]?.keys?.toList().orEmpty() }.orEmpty()
        }

    Row(
        modifier =
            Modifier
                .width(LOCATION_MENU_WIDTH)
                .heightIn(max = LOCATION_MENU_HEIGHT)
                .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        LocationListColumn(
            items = countries,
            selectedItem = activeCountry,
            onItemSelect = onCountrySelect,
            modifier = Modifier.weight(1f),
        )
        Divider(
            orientation = Orientation.Vertical,
            modifier = Modifier.fillMaxHeight(),
        )
        LocationListColumn(
            items = cities,
            selectedItem = selectedCity,
            onItemSelect = { city ->
                activeCountry?.let { country ->
                    onCitySelect(country, city)
                    closeMenu()
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LocationListColumn(
    items: List<String>,
    selectedItem: String?,
    onItemSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hoveredItem by remember(items) { mutableStateOf<String?>(null) }
    val itemShape = RoundedCornerShape(6.dp)
    val selectedBg = JewelTheme.segmentedControlButtonStyle.colors.backgroundSelected
    val hoverBg = JewelTheme.segmentedControlButtonStyle.colors.backgroundHovered
    val selectedTextColor = JewelTheme.globalColors.text.selected
    val normalTextColor = JewelTheme.globalColors.text.normal

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (item in items) {
            val isSelected = item == selectedItem
            val isHovered = item == hoveredItem
            val background: Brush =
                when {
                    isSelected -> selectedBg
                    isHovered -> hoverBg
                    else -> SolidColor(Color.Transparent)
                }
            val textColor = if (isSelected) selectedTextColor else normalTextColor

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(itemShape)
                        .background(background, itemShape)
                        .onPointerEvent(PointerEventType.Enter) { hoveredItem = item }
                        .onPointerEvent(PointerEventType.Exit) {
                            if (hoveredItem == item) {
                                hoveredItem = null
                            }
                        }.clickable { onItemSelect(item) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = item,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class LocationSelection(
    val country: String,
    val city: String,
)

// ============================================================================
// ZMANIM CALCULATIONS
// ============================================================================

/**
 * Computes the rendering model from Zmanim astronomical calculations.
 *
 * @param referenceTime Time for calculations.
 * @param latitude Observer latitude.
 * @param longitude Observer longitude.
 * @param elevation Observer elevation in meters.
 * @param timeZone Local timezone.
 * @param earthRotationDegrees Earth rotation angle.
 * @param earthTiltDegrees Earth axial tilt.
 * @return Computed rendering parameters.
 */
private fun computeZmanimModel(
    referenceTime: Date,
    latitude: Double,
    longitude: Double,
    elevation: Double,
    timeZone: TimeZone,
    earthRotationDegrees: Float,
    earthTiltDegrees: Float,
): ZmanimModel {
    val sunDirection =
        computeSunLightDirectionForEarth(
            referenceTime = referenceTime,
            latitude = latitude,
            longitude = longitude,
            earthRotationDegrees = earthRotationDegrees,
            earthTiltDegrees = earthTiltDegrees,
        )

    // Calculate moon position
    val julianDay = computeJulianDayUtc(referenceTime)
    // The Hebrew day, by day: a running clock asks for it at every frame
    val hebrewDay = hebrewDayAt(referenceTime.time, timeZone)
    val phaseAngle = hebrewDay.phaseAngleAt(referenceTime.time)
    val moonOrbitDegrees =
        run {
            val daysInMonth = hebrewDay.daysInMonth
            val dayOfMonth = hebrewDay.dayOfMonth
            if (daysInMonth > 0 && dayOfMonth in 1..daysInMonth) {
                val stepDegrees = 360f / daysInMonth.toFloat()
                // Gliding through the day from its label to the next one: a turn per month, no step at midnight
                val dayFraction = (referenceTime.time - hebrewDay.startMillis).toFloat() / (hebrewDay.endMillis - hebrewDay.startMillis)
                normalizeOrbitDegrees(ORBIT_DAY_LABEL_START_DEGREES + (dayOfMonth - 1 + dayFraction) * stepDegrees)
            } else {
                normalizeOrbitDegrees(phaseAngle + ORBIT_DAY_LABEL_START_DEGREES)
            }
        }

    return ZmanimModel(
        lightDegrees = sunDirection.lightDegrees,
        sunElevationDegrees = sunDirection.sunElevationDegrees,
        moonOrbitDegrees = moonOrbitDegrees,
        moonPhaseAngleDegrees = phaseAngle,
        julianDay = julianDay,
    )
}

// ============================================================================
// ORBIT LABELS (HEBREW CALENDAR)
// ============================================================================

private fun computeHebrewMonthOrbitLabels(
    referenceTime: Date,
    timeZone: TimeZone,
): List<OrbitLabelData> {
    val jewishCalendar = jewishCalendarAt(referenceTime, timeZone)

    val daysInMonth = jewishCalendar.daysInJewishMonth
    if (daysInMonth <= 0) return emptyList()

    val formatter =
        HebrewDateFormatter().apply {
            isHebrewFormat = true
            isUseGershGershayim = false
        }

    val stepDegrees = 360f / daysInMonth.toFloat()

    return (1..daysInMonth).map { day ->
        OrbitLabelData(
            orbitDegrees = ORBIT_DAY_LABEL_START_DEGREES + (day - 1) * stepDegrees,
            text = formatter.formatHebrewNumber(day),
            dayOfMonth = day,
        )
    }
}

fun computeZmanimTimes(
    date: LocalDate,
    location: EarthWidgetLocation,
    opinion: ZmanimOpinion = ZmanimOpinion.ITIM_LABINA,
    inIsrael: Boolean = false,
): ZmanimTimes {
    val calendar = zmanimCalendar(date, location, opinion, inIsrael)
    // GRA hours, chatzos and mincha ketana are shared; both luachs print sunrise במישור
    return when (opinion) {
        ZmanimOpinion.ITIM_LABINA -> {
            // The luach prints alos 90 במעלות in Israel and counts the MGA day from it; abroad it uses 72
            val alos = if (inIsrael) calendar.alos90ItimLabina else calendar.alos72ItimLabina
            val mgaEnd = if (inIsrael) calendar.tzais90ItimLabina else calendar.tzais72ItimLabina
            val alosAt = alos.momentOfOccurrence
            val mgaEndAt = mgaEnd.momentOfOccurrence
            ZmanimTimes(
                alosHashachar = alosAt?.toDate(),
                sunrise = calendar.seaLevelSunrise?.toDate(),
                sofZmanShmaGra = calendar.sofZmanShmaGRA.toDate(),
                sofZmanShmaMga = calendar.getSofZmanShma(alosAt, mgaEndAt)?.toDate(),
                sofZmanTfilaGra = calendar.sofZmanTfilaGRA.toDate(),
                sofZmanTfilaMga = calendar.getSofZmanTfila(alosAt, mgaEndAt)?.toDate(),
                chatzosHayom = calendar.chatzos.toDate(),
                minchaGedola = calendar.minchaGedolaGreaterThan30.toDate(),
                minchaKetana = calendar.minchaKetana.toDate(),
                plagHamincha = calendar.plagHamincha.toDate(),
                sunset = calendar.seaLevelSunset?.toDate(),
                tzais = calendar.tzaisGeonim18MinutesItimLabina.toDate(),
                tzaisRabbeinuTam = calendar.tzais72ItimLabina.toDate(),
                chatzosLayla = calendar.solarMidnight.toDate(),
            )
        }

        ZmanimOpinion.OHR_HACHAIM ->
            ZmanimTimes(
                alosHashachar = calendar.alos72Zmanis.toDate(),
                sunrise = calendar.seaLevelSunrise?.toDate(),
                sofZmanShmaGra = calendar.sofZmanShmaGRA.toDate(),
                sofZmanShmaMga = calendar.sofZmanShmaMGA72MinutesZmanis.toDate(),
                sofZmanTfilaGra = calendar.sofZmanTfilaGRA.toDate(),
                sofZmanTfilaMga = calendar.sofZmanTfilaMGA72MinutesZmanis.toDate(),
                chatzosHayom = calendar.chatzos.toDate(),
                minchaGedola = calendar.minchaGedolaOhrHaChaim.toDate(),
                minchaKetana = calendar.minchaKetana.toDate(),
                plagHamincha = calendar.plagHaminchaYalkutYosef.toDate(),
                sunset = calendar.ohrHaChaimSunset?.toDate(),
                tzais = calendar.tzais13Point5MinutesZmanis.toDate(),
                tzaisRabbeinuTam = calendar.tzais72Zmanis.toDate(),
                chatzosLayla = calendar.solarMidnight.toDate(),
            )
    }
}

/**
 * A [ComplexZmanimCalendar] for [date] at [location], running on the sun of [opinion]'s luach.
 * The אור החיים counts its day from the city's height in Israel, from sea level abroad.
 */
fun zmanimCalendar(
    date: LocalDate,
    location: EarthWidgetLocation,
    opinion: ZmanimOpinion,
    inIsrael: Boolean,
): ComplexZmanimCalendar {
    val timeZone = location.timeZone.toKotlin()
    return ComplexZmanimCalendar(
        location = GeoLocation("earthwidget", location.latitude, location.longitude, location.elevationMeters, timeZone),
        date = date.toKotlinLocalDate(),
        useElevation = opinion == ZmanimOpinion.OHR_HACHAIM && inIsrael,
    ).apply {
        astronomicalCalculator =
            when (opinion) {
                ZmanimOpinion.ITIM_LABINA -> ItimLabinaCalculator()
                ZmanimOpinion.OHR_HACHAIM -> NOAACalculator()
            }
    }
}

/** The sunset the אור החיים zmaniyos are counted to: from height when elevation is on (mirrors the protected one). */
val ComplexZmanimCalendar.ohrHaChaimSunset get() = if (isUseElevation) sunset else seaLevelSunset

fun Zman.DateBased.toDate(): Date? = momentOfOccurrence?.toDate()

fun kotlin.time.Instant.toDate(): Date = Date(toEpochMilliseconds())

fun TimeZone.toKotlin(): kotlinx.datetime.TimeZone = toZoneId().toKotlinTimeZone()

internal fun jewishCalendarAt(
    time: Date,
    timeZone: TimeZone,
): JewishCalendar = JewishCalendar(kotlin.time.Instant.fromEpochMilliseconds(time.time), timeZone.toKotlin())

// ============================================================================
// MOON PHASE CALCULATION
// ============================================================================

/**
 * The Hebrew calendar of one local day ([startMillis] until [endMillis]): its day of the month and the month's
 * length, and the moladot an instant of it is aged from.
 */
internal class HebrewDay(
    val zoneId: String,
    val startMillis: Long,
    val endMillis: Long,
    val dayOfMonth: Int,
    val daysInMonth: Int,
    private val molad: Long,
    private val previousMolad: Long,
) {
    /** Whether [millis] is past this month's molad (else the month is still aged from the previous one). */
    fun isAfterMolad(millis: Long): Boolean = molad <= millis

    /** As [computeHalakhicPhaseAngle]: the age since the last molad, as an angle. */
    fun phaseAngleAt(millis: Long): Float {
        val from = if (molad > millis) previousMolad else molad
        return (((millis - from).toDouble() / LUNAR_CYCLE_MILLIS) * 360.0).toFloat() % 360f
    }
}

// ponytail: one day cached (the shown one); a second widget on another zone recomputes on each switch
@Volatile
private var lastHebrewDay: HebrewDay? = null

internal fun hebrewDayAt(
    millis: Long,
    timeZone: TimeZone,
): HebrewDay {
    lastHebrewDay?.let { if (it.zoneId == timeZone.id && millis >= it.startMillis && millis < it.endMillis) return it }
    val zone = timeZone.toZoneId()
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end =
        date
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    val calendar = jewishCalendarAt(Date(millis), timeZone)
    val dayOfMonth = calendar.jewishDayOfMonth
    val daysInMonth = calendar.daysInJewishMonth
    val molad = calendar.moladAsInstant.toDate().time
    goToPreviousHebrewMonth(calendar)
    val previousMolad = calendar.moladAsInstant.toDate().time
    return HebrewDay(timeZone.id, start, end, dayOfMonth, daysInMonth, molad, previousMolad).also { lastHebrewDay = it }
}

/**
 * Computes the Halakhic moon phase angle based on the Hebrew calendar molad.
 *
 * The molad (lunar conjunction) is the traditional Hebrew calculation
 * for the start of each lunar month. This provides phase angles consistent
 * with Jewish calendar traditions.
 *
 * @param referenceTime Time for calculation.
 * @param timeZone Local timezone.
 * @return Moon phase angle in degrees (0 = new moon, 180 = full moon).
 */
internal fun computeHalakhicPhaseAngle(
    referenceTime: Date,
    timeZone: TimeZone,
): Float {
    val jewishCalendar = jewishCalendarAt(referenceTime, timeZone)

    var molad = jewishCalendar.moladAsInstant.toDate()

    // If current month's molad is in the future, use previous month's molad
    if (molad.time > referenceTime.time) {
        goToPreviousHebrewMonth(jewishCalendar)
        molad = jewishCalendar.moladAsInstant.toDate()
    }

    // Calculate age since molad and convert to phase angle
    val ageMillis = referenceTime.time - molad.time
    return ((ageMillis.toDouble() / LUNAR_CYCLE_MILLIS) * 360.0).toFloat() % 360f
}

/**
 * Moves the Jewish calendar to the previous Hebrew month.
 *
 * Handles special cases for Tishrei (previous year's Elul) and
 * Nissan (Adar or Adar II depending on leap year).
 *
 * @param jewishCalendar Calendar to modify.
 */
internal fun goToPreviousHebrewMonth(jewishCalendar: JewishCalendar) {
    jewishCalendar.setJewishDate(jewishCalendar.jewishYear, jewishCalendar.jewishMonth, 1)
    jewishCalendar.back() // lands on the last day of the previous month
    jewishCalendar.setJewishDate(jewishCalendar.jewishYear, jewishCalendar.jewishMonth, 1)
}

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

/**
 * Normalizes an orbit angle to [0, 360) range.
 */
private fun normalizeOrbitDegrees(angleDegrees: Float): Float = ((angleDegrees % 360f) + 360f) % 360f

internal fun orbitDegreesForDaysSinceMolad(
    daysSinceMolad: Double,
    daysInMonth: Int,
): Float {
    if (daysInMonth <= 0) return normalizeOrbitDegrees(ORBIT_DAY_LABEL_START_DEGREES)
    val stepDegrees = 360f / daysInMonth.toFloat()
    // Align "day N after molad" with the same day label (labels are 1-based).
    val alignedDaysSinceMolad = daysSinceMolad - 1.0
    return normalizeOrbitDegrees(
        ORBIT_DAY_LABEL_START_DEGREES + (alignedDaysSinceMolad * stepDegrees).toFloat(),
    )
}

private fun resolveInitialLocationSelection(
    locations: Map<String, Map<String, EarthWidgetLocation>>,
    preferredCity: String?,
): LocationSelection? {
    if (locations.isEmpty()) return null
    if (!preferredCity.isNullOrBlank()) {
        for ((country, cities) in locations) {
            if (cities.containsKey(preferredCity)) {
                return LocationSelection(country, preferredCity)
            }
        }
    }
    val fallbackCountry = locations.keys.firstOrNull() ?: return null
    val fallbackCity = locations[fallbackCountry]?.keys?.firstOrNull() ?: return null
    return LocationSelection(fallbackCountry, fallbackCity)
}

/**
 * Determines timezone for a given location.
 *
 * Uses Asia/Jerusalem for coordinates within Israel, otherwise
 * calculates a GMT offset based on longitude.
 *
 * @param latitude Location latitude.
 * @param longitude Location longitude.
 * @return Appropriate timezone.
 */
fun timeZoneForLocation(
    latitude: Double,
    longitude: Double,
): TimeZone {
    // Use Israel timezone for coordinates within Israel
    if (latitude in ISRAEL_LAT_MIN..ISRAEL_LAT_MAX &&
        longitude in ISRAEL_LON_MIN..ISRAEL_LON_MAX
    ) {
        return TimeZone.getTimeZone("Asia/Jerusalem")
    }

    // Calculate GMT offset from longitude
    val offsetHours =
        (longitude / DEGREES_PER_HOUR)
            .roundToInt()
            .coerceIn(MIN_GMT_OFFSET, MAX_GMT_OFFSET)
    val zoneId = if (offsetHours >= 0) "GMT+$offsetHours" else "GMT$offsetHours"

    return TimeZone.getTimeZone(zoneId)
}

// ============================================================================
// KIDDUSH LEVANA CALCULATION
// ============================================================================

/**
 * Computes the Kiddush Levana period data for display on the moon orbit.
 *
 * Uses the KosherJava library to determine the earliest and latest times
 * for reciting Kiddush Levana based on the selected halakhic opinions.
 *
 * @param referenceTime Current reference time for calculations.
 * @param timeZone Local timezone.
 * @param earliestOpinion Opinion for earliest time (3 or 7 days after molad).
 * @param latestOpinion Opinion for latest time (between moldos or 15 days).
 * @return KiddushLevanaData with orbit degrees and time information.
 */
private fun computeKiddushLevanaData(
    referenceTime: Date,
    timeZone: TimeZone,
    earliestOpinion: KiddushLevanaEarliestOpinion,
    latestOpinion: KiddushLevanaLatestOpinion,
): KiddushLevanaData {
    val jewishCalendar = jewishCalendarAt(referenceTime, timeZone)

    // Get earliest time based on opinion
    val earliestTime =
        when (earliestOpinion) {
            KiddushLevanaEarliestOpinion.DAYS_3 -> jewishCalendar.tchilasZmanKidushLevana3Days
            KiddushLevanaEarliestOpinion.DAYS_7 -> jewishCalendar.tchilasZmanKidushLevana7Days
        }.toDate()

    // Get latest time based on opinion
    val latestTime =
        when (latestOpinion) {
            KiddushLevanaLatestOpinion.BETWEEN_MOLDOS -> jewishCalendar.sofZmanKidushLevanaBetweenMoldos
            KiddushLevanaLatestOpinion.DAYS_15 -> jewishCalendar.sofZmanKidushLevana15Days
        }.toDate()

    // Get the molad for the current month
    var molad = jewishCalendar.moladAsInstant.toDate()
    if (molad.time > referenceTime.time) {
        goToPreviousHebrewMonth(jewishCalendar)
        molad = jewishCalendar.moladAsInstant.toDate()
    }

    val daysInMonth = jewishCalendar.daysInJewishMonth

    // Convert times to orbit degrees
    // Days since molad -> orbit degrees
    fun dateToOrbitDegrees(date: Date): Float {
        val millisSinceMolad = date.time - molad.time
        val daysSinceMolad = millisSinceMolad / (24.0 * 60.0 * 60.0 * 1000.0)
        return orbitDegreesForDaysSinceMolad(daysSinceMolad, daysInMonth)
    }

    val startDegrees = dateToOrbitDegrees(earliestTime)
    val endDegrees = dateToOrbitDegrees(latestTime)

    // Check if current time is within the Kiddush Levana period
    val isCurrentlyAllowed = referenceTime.time >= earliestTime.time && referenceTime.time <= latestTime.time

    return KiddushLevanaData(
        startDegrees = startDegrees,
        endDegrees = endDegrees,
        isCurrentlyAllowed = isCurrentlyAllowed,
        startTimeMillis = earliestTime.time,
        endTimeMillis = latestTime.time,
    )
}
