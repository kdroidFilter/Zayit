package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.kosherjava.zmanim.util.GeoLocation
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.component.LocalMenuController
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.segmentedControlButtonStyle
import io.github.kdroidfilter.seforimapp.hebrewcalendar.CalendarMode
import io.github.kdroidfilter.seforimapp.hebrewcalendar.HebrewCalendarPicker
import io.github.kdroidfilter.seforimapp.hebrewcalendar.HebrewYearMonth
import io.github.kdroidfilter.seforimapp.hebrewcalendar.hebrewYearMonthFromLocalDate
import seforimapp.earthwidget.generated.resources.*
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
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

/** Starting orbit angle for day labels (day 1). */
private const val ORBIT_DAY_LABEL_START_DEGREES = 90f

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
private class StableOrbitLabels(val list: List<OrbitLabelData>)

private data class KnownLocation(
    val name: StringResource,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double,
    val timeZoneId: String,
)

data class EarthWidgetLocation(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double,
    val timeZone: TimeZone,
)

private data class ZmanimPresetTimes(
    val sunrise: Date?,
    val sunset: Date?,
    val chatzosHayom: Date?,
    val chatzosLayla: Date?,
)

data class ZmanimTimes(
    val alosHashachar: Date?,
    val sunrise: Date?,
    val sofZmanShmaGra: Date?,
    val sofZmanShmaMga: Date?,
    val sofZmanTfilaGra: Date?,
    val sofZmanTfilaMga: Date?,
    val chatzosHayom: Date?,
    val sunset: Date?,
    val tzais: Date?,
    val tzaisRabbeinuTam: Date?,
)

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
    targetTime: Date? = null,
    targetDate: LocalDate? = null,
    onDateSelected: ((LocalDate) -> Unit)? = null,
    onLocationSelected: ((country: String, city: String, location: EarthWidgetLocation) -> Unit)? = null,
    allowLocationSelection: Boolean = true,
    containerBackground: Color? = null,
    contentPadding: Dp = 12.dp,
    showControls: Boolean = true,
    showOrbitLabels: Boolean = true,
    showMoonInOrbit: Boolean = true,
    initialShowMoonFromMarker: Boolean = true,
    useScroll: Boolean = true,
    earthSizeFraction: Float = EARTH_SIZE_FRACTION,
) {
    // Location state
    val knownLocations = remember {
        listOf(
            KnownLocation(
                name = Res.string.earthwidget_city_jerusalem,
                latitude = DEFAULT_MARKER_LAT,
                longitude = DEFAULT_MARKER_LON,
                elevationMeters = DEFAULT_MARKER_ELEVATION,
                timeZoneId = "Asia/Jerusalem",
            ),
            KnownLocation(
                name = Res.string.earthwidget_city_paris,
                latitude = 48.8566,
                longitude = 2.3522,
                elevationMeters = 35.0,
                timeZoneId = "Europe/Paris",
            ),
            KnownLocation(
                name = Res.string.earthwidget_city_new_york,
                latitude = 40.7128,
                longitude = -74.0060,
                elevationMeters = 10.0,
                timeZoneId = "America/New_York",
            ),
            KnownLocation(
                name = Res.string.earthwidget_city_london,
                latitude = 51.5074,
                longitude = -0.1278,
                elevationMeters = 11.0,
                timeZoneId = "Europe/London",
            ),
            KnownLocation(
                name = Res.string.earthwidget_city_los_angeles,
                latitude = 34.0522,
                longitude = -118.2437,
                elevationMeters = 71.0,
                timeZoneId = "America/Los_Angeles",
            ),
            KnownLocation(
                name = Res.string.earthwidget_city_moscow,
                latitude = 55.7558,
                longitude = 37.6173,
                elevationMeters = 156.0,
                timeZoneId = "Europe/Moscow",
            ),
            // Southern hemisphere and equator locations for testing moon crescent orientation
            KnownLocation(
                name = Res.string.earthwidget_city_sydney,
                latitude = -33.8688,
                longitude = 151.2093,
                elevationMeters = 58.0,
                timeZoneId = "Australia/Sydney",
            ),
            KnownLocation(
                name = Res.string.earthwidget_city_singapore,
                latitude = 1.3521,
                longitude = 103.8198,
                elevationMeters = 15.0,
                timeZoneId = "Asia/Singapore",
            ),
            KnownLocation(
                name = Res.string.earthwidget_city_buenos_aires,
                latitude = -34.6037,
                longitude = -58.3816,
                elevationMeters = 25.0,
                timeZoneId = "America/Argentina/Buenos_Aires",
            ),
        )
    }

    var selectedLocation by remember { mutableStateOf(knownLocations.first()) }
    var markerLatitudeDegrees by remember { mutableFloatStateOf(selectedLocation.latitude.toFloat()) }
    var markerLongitudeDegrees by remember { mutableFloatStateOf(selectedLocation.longitude.toFloat()) }
    var markerElevationMeters by remember { mutableStateOf(selectedLocation.elevationMeters) }
    var timeZone by remember { mutableStateOf(TimeZone.getTimeZone(selectedLocation.timeZoneId)) }

    // Display options
    var showBackground by remember { mutableStateOf(true) }
    var showOrbitPath by remember { mutableStateOf(true) }
    var showMoonFromMarker by remember { mutableStateOf(initialShowMoonFromMarker) }

    // Earth rotation offset from user drag (added to marker longitude)
    var earthRotationOffset by remember { mutableFloatStateOf(0f) }
    var isDraggingEarth by remember { mutableStateOf(false) }

    // Date/time selection - initialized once with the default timezone, then preserved across location changes
    val initialCalendar = remember {
        Calendar.getInstance(TimeZone.getTimeZone(knownLocations.first().timeZoneId)).apply { time = Date() }
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

    LaunchedEffect(locationOverride) {
        locationOverride?.let { override ->
            markerLatitudeDegrees = override.latitude.toFloat()
            markerLongitudeDegrees = override.longitude.toFloat()
            markerElevationMeters = override.elevationMeters
            timeZone = override.timeZone
            earthRotationOffset = 0f

            if (targetTime == null) {
                val now = Calendar.getInstance(override.timeZone)
                selectedDate = LocalDate.of(
                    now.get(Calendar.YEAR),
                    now.get(Calendar.MONTH) + 1,
                    now.get(Calendar.DAY_OF_MONTH),
                )
                selectedHour = now.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
                selectedMinute = now.get(Calendar.MINUTE).coerceIn(0, 59)
            }
        }
    }

    LaunchedEffect(targetTime, timeZone) {
        targetTime?.let { date ->
            val cal = Calendar.getInstance(timeZone).apply { time = date }
            selectedDate = LocalDate.of(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
            )
            selectedHour = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
            selectedMinute = cal.get(Calendar.MINUTE).coerceIn(0, 59)
        }
    }

    // Sync with external targetDate if provided
    LaunchedEffect(targetDate) {
        targetDate?.let { date ->
            if (date != selectedDate) {
                selectedDate = date
            }
        }
    }

    val referenceTime = remember(selectedDate, selectedHour, selectedMinute, timeZone) {
        Calendar.getInstance(timeZone).apply {
            set(Calendar.YEAR, selectedDate.year)
            set(Calendar.MONTH, selectedDate.monthValue - 1)
            set(Calendar.DAY_OF_MONTH, selectedDate.dayOfMonth)
            set(Calendar.HOUR_OF_DAY, selectedHour.coerceIn(0, 23))
            set(Calendar.MINUTE, selectedMinute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    // Compute astronomical model
    val model = remember(
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

    val stableOrbitLabels = remember(referenceTime, timeZone, showOrbitLabels) {
        StableOrbitLabels(
            if (showOrbitLabels) {
                computeHebrewMonthOrbitLabels(
                    referenceTime = referenceTime,
                    timeZone = timeZone,
                )
            } else {
                emptyList()
            }
        )
    }

    // Format time for display
    val formatter = remember(timeZone) {
        SimpleDateFormat("yyyy-MM-dd HH:mm").apply { this.timeZone = timeZone }
    }
    val formattedTime = remember(referenceTime, formatter) { formatter.format(referenceTime) }
    val selectedLocationLabel = stringResource(selectedLocation.name)
    val normalizedLocationOptions = remember(locationOptions) {
        locationOptions.filterValues { it.isNotEmpty() }
    }
    var selectedLocationSelection by remember(normalizedLocationOptions) {
        mutableStateOf(resolveInitialLocationSelection(normalizedLocationOptions, locationLabel))
    }
    var menuCountrySelection by remember(normalizedLocationOptions) {
        mutableStateOf(
            selectedLocationSelection?.country ?: normalizedLocationOptions.keys.firstOrNull()
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
    val resolvedLocationLabel = when {
        normalizedLocationOptions.isNotEmpty() ->
            selectedLocationSelection?.city ?: locationLabel?.takeIf { it.isNotBlank() }
        !locationLabel.isNullOrBlank() -> locationLabel
        locationOverride == null -> selectedLocationLabel
        else -> null
    }
    val hebrewDateLabel = remember(referenceTime, timeZone) {
        val calendar = Calendar.getInstance(timeZone).apply { time = referenceTime }
        val jewishDate = JewishDate().apply { setDate(calendar) }
        val dateFormatter = HebrewDateFormatter().apply {
            isHebrewFormat = true
            isUseGershGershayim = false
        }
        val dayOfMonth = dateFormatter.formatHebrewNumber(jewishDate.jewishDayOfMonth)
        val month = dateFormatter.formatMonth(jewishDate)
        val year = dateFormatter.formatHebrewNumber(jewishDate.getJewishYear())
        "$dayOfMonth $month $year"
    }

    val presetTimeFormatter = remember(timeZone) {
        SimpleDateFormat("HH:mm").apply { this.timeZone = timeZone }
    }

    val zmanimPresets = remember(selectedDate, markerLatitudeDegrees, markerLongitudeDegrees, markerElevationMeters, timeZone) {
        computeZmanimPresetTimes(
            date = selectedDate,
            latitude = markerLatitudeDegrees.toDouble(),
            longitude = markerLongitudeDegrees.toDouble(),
            elevationMeters = markerElevationMeters,
            timeZone = timeZone,
        )
    }

    fun applyPreset(date: Date) {
        val cal = Calendar.getInstance(timeZone).apply { time = date }
        selectedDate = LocalDate.of(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
        selectedHour = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
        selectedMinute = cal.get(Calendar.MINUTE).coerceIn(0, 59)
    }

    val backgroundColor = containerBackground ?: JewelTheme.globalColors.panelBackground

    // Stable callbacks to avoid recomposition - these lambdas reference mutableStateOf-backed vars
    // so they remain stable across recompositions while still accessing the latest state
    val onEarthRotationDeltaCallback = remember { { delta: Float -> earthRotationOffset += delta } }
    val onDragStateChangeCallback = remember { { dragging: Boolean -> isDraggingEarth = dragging } }
    val onRecenterCallback = remember { { earthRotationOffset = 0f } }

    // Use rememberUpdatedState to keep the lambda stable while accessing latest values
    val currentTimeZone by rememberUpdatedState(timeZone)
    val currentReferenceTime by rememberUpdatedState(referenceTime)
    val currentOnDateSelected by rememberUpdatedState(onDateSelected)
    val onCalendarDateSelected: (LocalDate) -> Unit = { date ->
        selectedDate = date
        currentOnDateSelected?.invoke(date)
    }
    val onOrbitLabelClickHandler: (OrbitLabelData) -> Unit = remember {
        { label: OrbitLabelData ->
            val calendar = Calendar.getInstance(currentTimeZone).apply { time = currentReferenceTime }
            val jewishCalendar = JewishCalendar().apply { setDate(calendar) }
            val newDate = JewishDate().apply {
                setJewishDate(jewishCalendar.jewishYear, jewishCalendar.jewishMonth, label.dayOfMonth)
            }.localDate
            selectedDate = newDate
            currentOnDateSelected?.invoke(newDate)
            Unit
        }
    }
    val onLocationSelectedInternal: (String, String) -> Unit = selection@{ country, city ->
        val location = normalizedLocationOptions[country]?.get(city) ?: return@selection
        selectedLocationSelection = LocationSelection(country, city)
        menuCountrySelection = country
        markerLatitudeDegrees = location.latitude.toFloat()
        markerLongitudeDegrees = location.longitude.toFloat()
        markerElevationMeters = location.elevationMeters
        timeZone = location.timeZone
        earthRotationOffset = 0f
        onLocationSelected?.invoke(country, city, location)
    }

    if (useScroll) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        DateSelectionSplitButton(
                            label = hebrewDateLabel,
                            selectedDate = selectedDate,
                            onDateSelected = onCalendarDateSelected,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 8.dp, top = 8.dp),
                        )
                        if (resolvedLocationLabel != null) {
                            LocationSelectionSplitButton(
                                label = resolvedLocationLabel,
                                locations = normalizedLocationOptions,
                                selectedCountry = menuCountrySelection,
                                selectedSelection = selectedLocationSelection,
                                onCountrySelected = { menuCountrySelection = it },
                                onCitySelected = { country, city -> onLocationSelectedInternal(country, city) },
                                modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(end = 8.dp, top = 8.dp),
                            )
                        }
                    }
                    EarthSceneContent(
                        modifier = Modifier.fillMaxWidth(),
                        sphereSize = sphereSize,
                        renderSizePx = renderSizePx,
                        markerLongitudeDegrees = markerLongitudeDegrees,
                        earthRotationOffset = earthRotationOffset,
                        onEarthRotationDelta = onEarthRotationDeltaCallback,
                        onDragStateChange = onDragStateChangeCallback,
                        model = model,
                        markerLatitudeDegrees = markerLatitudeDegrees,
                        showBackground = showBackground,
                        showOrbitPath = showOrbitPath,
                        stableOrbitLabels = stableOrbitLabels,
                        onOrbitLabelClick = onOrbitLabelClickHandler,
                        showMoonFromMarker = showMoonFromMarker,
                        showMoonInOrbit = showMoonInOrbit,
                        earthSizeFraction = earthSizeFraction,
                        isDraggingEarth = isDraggingEarth,
                    )
                    RecenterButton(
                        earthRotationOffset = earthRotationOffset,
                        onRecenter = onRecenterCallback,
                    )
                }
            }

            if (showControls) {
                item {
                    val panelShape = RoundedCornerShape(10.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp)
                            .background(JewelTheme.globalColors.toolwindowBackground, panelShape)
                            .border(1.dp, JewelTheme.globalColors.borders.normal, panelShape)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        GroupHeader(text = stringResource(Res.string.earthwidget_datetime_label))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(text = formattedTime, modifier = Modifier.weight(1f))
                            DateSelectionSplitButton(
                                label = hebrewDateLabel,
                                selectedDate = selectedDate,
                                onDateSelected = onCalendarDateSelected,
                            )
                        }

                        Divider(orientation = Orientation.Horizontal)

                        GroupHeader(text = stringResource(Res.string.earthwidget_time_preset_section))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { zmanimPresets.sunrise?.let(::applyPreset) },
                                enabled = zmanimPresets.sunrise != null,
                                modifier = Modifier.weight(1f),
                            ) {
                                TimePresetButtonLabel(
                                    label = stringResource(Res.string.earthwidget_time_preset_sunrise),
                                    time = zmanimPresets.sunrise,
                                    timeFormatter = presetTimeFormatter,
                                )
                            }
                            OutlinedButton(
                                onClick = { zmanimPresets.sunset?.let(::applyPreset) },
                                enabled = zmanimPresets.sunset != null,
                                modifier = Modifier.weight(1f),
                            ) {
                                TimePresetButtonLabel(
                                    label = stringResource(Res.string.earthwidget_time_preset_sunset),
                                    time = zmanimPresets.sunset,
                                    timeFormatter = presetTimeFormatter,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { zmanimPresets.chatzosHayom?.let(::applyPreset) },
                                enabled = zmanimPresets.chatzosHayom != null,
                                modifier = Modifier.weight(1f),
                            ) {
                                TimePresetButtonLabel(
                                    label = stringResource(Res.string.earthwidget_time_preset_chatzos_hayom),
                                    time = zmanimPresets.chatzosHayom,
                                    timeFormatter = presetTimeFormatter,
                                )
                            }
                            OutlinedButton(
                                onClick = { zmanimPresets.chatzosLayla?.let(::applyPreset) },
                                enabled = zmanimPresets.chatzosLayla != null,
                                modifier = Modifier.weight(1f),
                            ) {
                                TimePresetButtonLabel(
                                    label = stringResource(Res.string.earthwidget_time_preset_chatzos_layla),
                                    time = zmanimPresets.chatzosLayla,
                                    timeFormatter = presetTimeFormatter,
                                )
                            }
                        }

                        if (allowLocationSelection) {
                            Divider(orientation = Orientation.Horizontal)
                            GroupHeader(text = stringResource(Res.string.earthwidget_location_section))
                            for (row in knownLocations.chunked(3)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    for (location in row) {
                                        val isSelected = location == selectedLocation
                                        val onSelect = {
                                            selectedLocation = location
                                            markerLatitudeDegrees = location.latitude.toFloat()
                                            markerLongitudeDegrees = location.longitude.toFloat()
                                            markerElevationMeters = location.elevationMeters
                                            timeZone = TimeZone.getTimeZone(location.timeZoneId)
                                            earthRotationOffset = 0f // Reset rotation when changing location
                                        }

                                        if (isSelected) {
                                            DefaultButton(
                                                onClick = onSelect,
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Text(text = stringResource(location.name))
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = onSelect,
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Text(text = stringResource(location.name))
                                            }
                                        }
                                    }
                                    repeat(3 - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        Divider(orientation = Orientation.Horizontal)

                        GroupHeader(text = stringResource(Res.string.earthwidget_display_section))
                        LabeledCheckbox(
                            checked = showBackground,
                            onCheckedChange = { showBackground = it },
                            label = stringResource(Res.string.earthwidget_show_background_label),
                        )
                        LabeledCheckbox(
                            checked = showOrbitPath,
                            onCheckedChange = { showOrbitPath = it },
                            label = stringResource(Res.string.earthwidget_show_orbit_label),
                        )
                        if (showMoonInOrbit) {
                            LabeledCheckbox(
                                checked = showMoonFromMarker,
                                onCheckedChange = { showMoonFromMarker = it },
                                label = stringResource(Res.string.earthwidget_moon_from_marker_label),
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            EarthSceneContent(
                modifier = Modifier.fillMaxSize(),
                sphereSize = sphereSize,
                renderSizePx = renderSizePx,
                markerLongitudeDegrees = markerLongitudeDegrees,
                earthRotationOffset = earthRotationOffset,
                onEarthRotationDelta = onEarthRotationDeltaCallback,
                onDragStateChange = onDragStateChangeCallback,
                model = model,
                markerLatitudeDegrees = markerLatitudeDegrees,
                showBackground = showBackground,
                showOrbitPath = showOrbitPath,
                stableOrbitLabels = stableOrbitLabels,
                onOrbitLabelClick = onOrbitLabelClickHandler,
                showMoonFromMarker = showMoonFromMarker,
                showMoonInOrbit = showMoonInOrbit,
                earthSizeFraction = earthSizeFraction,
                isDraggingEarth = isDraggingEarth,
            )
            RecenterButton(
                earthRotationOffset = earthRotationOffset,
                onRecenter = onRecenterCallback,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 8.dp),
            )
            DateSelectionSplitButton(
                label = hebrewDateLabel,
                selectedDate = selectedDate,
                onDateSelected = onCalendarDateSelected,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 8.dp),
            )
            if (resolvedLocationLabel != null) {
                LocationSelectionSplitButton(
                    label = resolvedLocationLabel,
                    locations = normalizedLocationOptions,
                    selectedCountry = menuCountrySelection,
                    selectedSelection = selectedLocationSelection,
                    onCountrySelected = { menuCountrySelection = it },
                    onCitySelected = { country, city -> onLocationSelectedInternal(country, city) },
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
    modifier: Modifier = Modifier,
    sphereSize: Dp = 140.dp,
    renderSizePx: Int = 0,
    location: EarthWidgetLocation,
    referenceTime: Date,
    showBackground: Boolean = true,
    earthSizeFraction: Float = EARTH_SIZE_FRACTION,
) {
    val markerLatitudeDegrees = location.latitude.toFloat()
    val markerLongitudeDegrees = location.longitude.toFloat()
    val model = remember(
        referenceTime,
        markerLatitudeDegrees,
        markerLongitudeDegrees,
        location.elevationMeters,
        location.timeZone,
    ) {
        computeZmanimModel(
            referenceTime = referenceTime,
            latitude = markerLatitudeDegrees.toDouble(),
            longitude = markerLongitudeDegrees.toDouble(),
            elevation = location.elevationMeters,
            timeZone = location.timeZone,
            earthRotationDegrees = markerLongitudeDegrees,
            earthTiltDegrees = DEFAULT_EARTH_TILT_DEGREES,
        )
    }

    val density = LocalDensity.current
    val resolvedRenderSizePx = remember(sphereSize, renderSizePx, density) {
        if (renderSizePx > 0) {
            renderSizePx
        } else {
            (with(density) { sphereSize.toPx() } * 1.35f).roundToInt().coerceAtLeast(160)
        }
    }
    val renderer = remember { EarthWidgetRenderer() }
    val moonState = remember(
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

    MoonFromMarkerWidgetView(
        renderer = renderer,
        moonTexture = null,
        state = moonState,
        modifier = modifier,
        sphereSize = sphereSize,
        animateTransitions = true,
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
    modifier: Modifier,
    sphereSize: Dp,
    renderSizePx: Int,
    markerLongitudeDegrees: Float,
    earthRotationOffset: Float,
    onEarthRotationDelta: (Float) -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    model: ZmanimModel,
    markerLatitudeDegrees: Float,
    showBackground: Boolean,
    showOrbitPath: Boolean,
    stableOrbitLabels: StableOrbitLabels,
    onOrbitLabelClick: (OrbitLabelData) -> Unit,
    showMoonFromMarker: Boolean,
    showMoonInOrbit: Boolean,
    earthSizeFraction: Float,
    isDraggingEarth: Boolean,
) {
    val density = LocalDensity.current
    val degreesPerPx = remember(sphereSize) {
        // Calculate how many degrees of rotation per pixel of drag
        // A full drag across the sphere width = 180 degrees
        with(density) { 180f / sphereSize.toPx() }
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { onDragStateChange(true) },
                onDragEnd = { onDragStateChange(false) },
                onDragCancel = { onDragStateChange(false) },
            ) { change, dragAmount ->
                change.consume()
                // Horizontal drag rotates the Earth (negative because dragging right
                // should rotate the Earth to show what's on the left)
                onEarthRotationDelta(-dragAmount.x * degreesPerPx)
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        EarthWidgetScene(
            sphereSize = sphereSize,
            renderSizePx = renderSizePx,
            earthRotationDegrees = markerLongitudeDegrees + earthRotationOffset,
            // Compensate light direction for Earth rotation offset.
            // Model computed lightDegrees for earthRotation = markerLongitude.
            // Subtracting offset keeps the sun fixed relative to Earth's surface,
            // so the marker always shows correct day/night for the selected time.
            lightDegrees = model.lightDegrees - earthRotationOffset,
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
            animateEarthRotation = !isDraggingEarth, // Instant rotation during drag
        )
    }
}

/**
 * Recenter button shown when Earth is rotated away from marker.
 */
@Composable
private fun RecenterButton(
    earthRotationOffset: Float,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (earthRotationOffset != 0f) {
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

/**
 * A checkbox with accompanying label.
 */
@Composable
private fun LabeledCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label)
    }
}

@Composable
private fun TimePresetButtonLabel(
    label: String,
    time: Date?,
    timeFormatter: SimpleDateFormat,
) {
    val timeText = remember(time, timeFormatter) {
        time?.let { "\u2066${timeFormatter.format(it)}\u2069" }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label)
        if (timeText != null) {
            Text(
                text = timeText,
                style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun DateSelectionSplitButton(
    label: String,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    io.github.kdroidfilter.seforimapp.hebrewcalendar.DateSelectionSplitButton(
        label = label,
        selectedDate = selectedDate,
        onDateSelected = onDateSelected,
        initialMode = CalendarMode.HEBREW,
        modifier = modifier,
    )
}

@Composable
private fun LocationSelectionSplitButton(
    label: String,
    locations: Map<String, Map<String, EarthWidgetLocation>>,
    selectedCountry: String?,
    selectedSelection: LocationSelection?,
    onCountrySelected: (String) -> Unit,
    onCitySelected: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        OutlinedSplitButton(
            onClick = {},
            secondaryOnClick = {},
            popupModifier = Modifier.width(LOCATION_MENU_WIDTH),
            maxPopupWidth = LOCATION_MENU_WIDTH,
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
                            selectedCity = selectedSelection
                                ?.takeIf { it.country == selectedCountry }
                                ?.city,
                            onCountrySelected = onCountrySelected,
                            onCitySelected = onCitySelected,
                        )
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
    onCountrySelected: (String) -> Unit,
    onCitySelected: (String, String) -> Unit,
) {
    val menuController = LocalMenuController.current
    val inputModeManager = LocalInputModeManager.current
    val closeMenu = {
        menuController.closeAll(inputModeManager.inputMode, true)
    }
    val countries = remember(locations) { locations.keys.toList() }
    val activeCountry = selectedCountry ?: countries.firstOrNull()
    val cities = remember(activeCountry, locations) {
        activeCountry?.let { locations[it]?.keys?.toList().orEmpty() }.orEmpty()
    }

    Row(
        modifier = Modifier
            .width(LOCATION_MENU_WIDTH)
            .heightIn(max = LOCATION_MENU_HEIGHT)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        LocationListColumn(
            items = countries,
            selectedItem = activeCountry,
            onItemSelected = onCountrySelected,
            modifier = Modifier.weight(1f),
        )
        Divider(
            orientation = Orientation.Vertical,
            modifier = Modifier.fillMaxHeight(),
        )
        LocationListColumn(
            items = cities,
            selectedItem = selectedCity,
            onItemSelected = { city ->
                activeCountry?.let { country ->
                    onCitySelected(country, city)
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
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hoveredItem by remember(items) { mutableStateOf<String?>(null) }
    val itemShape = RoundedCornerShape(6.dp)
    val selectedBg = JewelTheme.segmentedControlButtonStyle.colors.backgroundSelected
    val hoverBg = JewelTheme.segmentedControlButtonStyle.colors.backgroundHovered
    val selectedTextColor = JewelTheme.globalColors.text.selected
    val normalTextColor = JewelTheme.globalColors.text.normal

    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (item in items) {
            val isSelected = item == selectedItem
            val isHovered = item == hoveredItem
            val background: Brush = when {
                isSelected -> selectedBg
                isHovered -> hoverBg
                else -> SolidColor(Color.Transparent)
            }
            val textColor = if (isSelected) selectedTextColor else normalTextColor

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(itemShape)
                    .background(background, itemShape)
                    .onPointerEvent(PointerEventType.Enter) { hoveredItem = item }
                    .onPointerEvent(PointerEventType.Exit) {
                        if (hoveredItem == item) {
                            hoveredItem = null
                        }
                    }
                    .clickable { onItemSelected(item) }
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
    val sunDirection = computeSunLightDirectionForEarth(
        referenceTime = referenceTime,
        latitude = latitude,
        longitude = longitude,
        earthRotationDegrees = earthRotationDegrees,
        earthTiltDegrees = earthTiltDegrees,
    )

    // Calculate moon position
    val julianDay = computeJulianDayUtc(referenceTime)
    val phaseAngle = computeHalakhicPhaseAngle(referenceTime, timeZone)
    val moonOrbitDegrees = run {
        val jewishCalendar = JewishCalendar()
        val calendar = Calendar.getInstance(timeZone).apply { time = referenceTime }
        jewishCalendar.setDate(calendar)

        val daysInMonth = jewishCalendar.daysInJewishMonth
        val dayOfMonth = jewishCalendar.jewishDayOfMonth
        if (daysInMonth > 0 && dayOfMonth in 1..daysInMonth) {
            val stepDegrees = 360f / daysInMonth.toFloat()
            normalizeOrbitDegrees(ORBIT_DAY_LABEL_START_DEGREES + (dayOfMonth - 1) * stepDegrees)
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
    val jewishCalendar = JewishCalendar()
    val calendar = Calendar.getInstance(timeZone).apply { time = referenceTime }
    jewishCalendar.setDate(calendar)

    val daysInMonth = jewishCalendar.daysInJewishMonth
    if (daysInMonth <= 0) return emptyList()

    val formatter = HebrewDateFormatter().apply {
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

private fun computeZmanimPresetTimes(
    date: LocalDate,
    latitude: Double,
    longitude: Double,
    elevationMeters: Double,
    timeZone: TimeZone,
): ZmanimPresetTimes {
    val geoLocation = GeoLocation("earthwidget", latitude, longitude, elevationMeters, timeZone)
    val calendar = ComplexZmanimCalendar(geoLocation).apply {
        this.calendar = Calendar.getInstance(timeZone).apply {
            set(Calendar.YEAR, date.year)
            set(Calendar.MONTH, date.monthValue - 1)
            set(Calendar.DAY_OF_MONTH, date.dayOfMonth)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    return ZmanimPresetTimes(
        sunrise = calendar.sunrise,
        sunset = calendar.sunset,
        chatzosHayom = calendar.chatzos,
        chatzosLayla = calendar.solarMidnight,
    )
}

fun computeZmanimTimes(
    date: LocalDate,
    location: EarthWidgetLocation,
): ZmanimTimes {
    val geoLocation = GeoLocation(
        "earthwidget",
        location.latitude,
        location.longitude,
        location.elevationMeters,
        location.timeZone,
    )
    val calendar = ComplexZmanimCalendar(geoLocation).apply {
        this.calendar = Calendar.getInstance(location.timeZone).apply {
            set(Calendar.YEAR, date.year)
            set(Calendar.MONTH, date.monthValue - 1)
            set(Calendar.DAY_OF_MONTH, date.dayOfMonth)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    return ZmanimTimes(
        alosHashachar = calendar.alosHashachar,
        sunrise = calendar.sunrise,
        sofZmanShmaGra = calendar.getSofZmanShmaGRA(),
        sofZmanShmaMga = calendar.getSofZmanShmaMGA(),
        sofZmanTfilaGra = calendar.getSofZmanTfilaGRA(),
        sofZmanTfilaMga = calendar.getSofZmanTfilaMGA(),
        chatzosHayom = calendar.chatzos,
        sunset = calendar.sunset,
        tzais = calendar.tzais,
        tzaisRabbeinuTam = calendar.getTzais72(),
    )
}

// ============================================================================
// MOON PHASE CALCULATION
// ============================================================================

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
private fun computeHalakhicPhaseAngle(referenceTime: Date, timeZone: TimeZone): Float {
    val jewishCalendar = JewishCalendar()
    val calendar = Calendar.getInstance(timeZone).apply { time = referenceTime }
    jewishCalendar.setDate(calendar)

    var molad = jewishCalendar.moladAsDate

    // If current month's molad is in the future, use previous month's molad
    if (molad.time > referenceTime.time) {
        goToPreviousHebrewMonth(jewishCalendar)
        molad = jewishCalendar.moladAsDate
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
private fun goToPreviousHebrewMonth(jewishCalendar: JewishCalendar) {
    val currentMonth = jewishCalendar.jewishMonth
    val currentYear = jewishCalendar.jewishYear

    when (currentMonth) {
        JewishDate.TISHREI -> {
            // Tishrei -> previous year's Elul
            jewishCalendar.jewishYear = currentYear - 1
            jewishCalendar.jewishMonth = JewishDate.ELUL
        }
        JewishDate.NISSAN -> {
            // Nissan -> Adar (or Adar II in leap year)
            val prevMonth = if (jewishCalendar.isJewishLeapYear) {
                JewishDate.ADAR_II
            } else {
                JewishDate.ADAR
            }
            jewishCalendar.jewishMonth = prevMonth
        }
        else -> {
            jewishCalendar.jewishMonth = currentMonth - 1
        }
    }
    jewishCalendar.jewishDayOfMonth = 1
}

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

/**
 * Normalizes an orbit angle to [0, 360) range.
 */
private fun normalizeOrbitDegrees(angleDegrees: Float): Float {
    return ((angleDegrees % 360f) + 360f) % 360f
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
fun timeZoneForLocation(latitude: Double, longitude: Double): TimeZone {
    // Use Israel timezone for coordinates within Israel
    if (latitude in ISRAEL_LAT_MIN..ISRAEL_LAT_MAX &&
        longitude in ISRAEL_LON_MIN..ISRAEL_LON_MAX) {
        return TimeZone.getTimeZone("Asia/Jerusalem")
    }

    // Calculate GMT offset from longitude
    val offsetHours = (longitude / DEGREES_PER_HOUR).roundToInt()
        .coerceIn(MIN_GMT_OFFSET, MAX_GMT_OFFSET)
    val zoneId = if (offsetHours >= 0) "GMT+$offsetHours" else "GMT$offsetHours"

    return TimeZone.getTimeZone(zoneId)
}
