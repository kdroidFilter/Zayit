package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Checkbox
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import org.jetbrains.compose.resources.stringResource
import seforimapp.earthwidget.generated.resources.Res
import seforimapp.earthwidget.generated.resources.earthwidget_date_offset_label
import seforimapp.earthwidget.generated.resources.earthwidget_datetime_label
import seforimapp.earthwidget.generated.resources.earthwidget_marker_latitude_label
import seforimapp.earthwidget.generated.resources.earthwidget_marker_longitude_label
import seforimapp.earthwidget.generated.resources.earthwidget_show_background_label
import seforimapp.earthwidget.generated.resources.earthwidget_show_orbit_label
import seforimapp.earthwidget.generated.resources.earthwidget_time_hour_label
import seforimapp.earthwidget.generated.resources.earthwidget_time_minute_label
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
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

/** Israel longitude bounds (west to east). */
private const val ISRAEL_LON_MIN = 34.0
private const val ISRAEL_LON_MAX = 36.6

/** Minimum GMT offset in hours. */
private const val MIN_GMT_OFFSET = -12

/** Maximum GMT offset in hours. */
private const val MAX_GMT_OFFSET = 14

/** Maximum day offset for date slider. */
private const val MAX_DAY_OFFSET = 30

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
private data class ZmanimModel(
    val lightDegrees: Float,
    val sunElevationDegrees: Float,
    val moonOrbitDegrees: Float,
    val moonPhaseAngleDegrees: Float,
    val julianDay: Double,
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
    sphereSize: Dp = 500.dp,
    renderSizePx: Int = 600,
) {
    // Location state
    var markerLatitudeDegrees by remember { mutableFloatStateOf(DEFAULT_MARKER_LAT.toFloat()) }
    var markerLongitudeDegrees by remember { mutableFloatStateOf(DEFAULT_MARKER_LON.toFloat()) }

    // Display options
    var showBackground by remember { mutableStateOf(true) }
    var showOrbitPath by remember { mutableStateOf(true) }

    // Calculate timezone based on location
    val timeZone = remember(markerLatitudeDegrees, markerLongitudeDegrees) {
        timeZoneForLocation(
            latitude = markerLatitudeDegrees.toDouble(),
            longitude = markerLongitudeDegrees.toDouble(),
        )
    }

    // Base time reference
    val baseNow = remember(timeZone) { Date() }
    val nowCalendar = remember(timeZone, baseNow) {
        Calendar.getInstance(timeZone).apply { time = baseNow }
    }

    // Time adjustment state
    var dayOffset by remember { mutableFloatStateOf(0f) }
    var hourOfDay by remember(timeZone, nowCalendar) {
        mutableFloatStateOf(nowCalendar.get(Calendar.HOUR_OF_DAY).toFloat())
    }
    var minuteOfHour by remember(timeZone, nowCalendar) {
        mutableFloatStateOf(nowCalendar.get(Calendar.MINUTE).toFloat())
    }

    // Calculate reference time from adjustments
    val referenceTime = remember(baseNow, dayOffset, hourOfDay, minuteOfHour, timeZone) {
        Calendar.getInstance(timeZone).apply {
            time = baseNow
            add(Calendar.DAY_OF_YEAR, dayOffset.roundToInt())
            set(Calendar.HOUR_OF_DAY, hourOfDay.roundToInt().coerceIn(0, 23))
            set(Calendar.MINUTE, minuteOfHour.roundToInt().coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    // Compute astronomical model
    val model = remember(
        referenceTime,
        markerLatitudeDegrees,
        markerLongitudeDegrees,
        timeZone,
    ) {
        computeZmanimModel(
            referenceTime = referenceTime,
            latitude = markerLatitudeDegrees.toDouble(),
            longitude = markerLongitudeDegrees.toDouble(),
            elevation = DEFAULT_MARKER_ELEVATION,
            timeZone = timeZone,
            earthRotationDegrees = 0f,
            earthTiltDegrees = DEFAULT_EARTH_TILT_DEGREES,
        )
    }

    // Format time for display
    val formatter = remember(timeZone) {
        SimpleDateFormat("yyyy-MM-dd HH:mm").apply { this.timeZone = timeZone }
    }
    val formattedTime = remember(referenceTime, formatter) { formatter.format(referenceTime) }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Main scene
        item {
            EarthWidgetScene(
                sphereSize = sphereSize,
                renderSizePx = renderSizePx,
                earthRotationDegrees = 0f,
                lightDegrees = model.lightDegrees,
                sunElevationDegrees = model.sunElevationDegrees,
                earthTiltDegrees = DEFAULT_EARTH_TILT_DEGREES,
                moonOrbitDegrees = model.moonOrbitDegrees,
                markerLatitudeDegrees = markerLatitudeDegrees,
                markerLongitudeDegrees = markerLongitudeDegrees,
                showBackgroundStars = showBackground,
                showOrbitPath = showOrbitPath,
                moonPhaseAngleDegrees = model.moonPhaseAngleDegrees,
                julianDay = model.julianDay,
            )
        }

        // Date/time display
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(Res.string.earthwidget_datetime_label))
                Text(text = formattedTime)
            }
        }

        // Date offset slider
        item {
            LabeledSlider(
                label = stringResource(Res.string.earthwidget_date_offset_label),
                value = dayOffset,
                onValueChange = { dayOffset = it },
                valueRange = -MAX_DAY_OFFSET.toFloat()..MAX_DAY_OFFSET.toFloat(),
            )
        }

        // Hour slider
        item {
            LabeledSlider(
                label = stringResource(Res.string.earthwidget_time_hour_label),
                value = hourOfDay,
                onValueChange = { hourOfDay = it },
                valueRange = 0f..23f,
            )
        }

        // Minute slider
        item {
            LabeledSlider(
                label = stringResource(Res.string.earthwidget_time_minute_label),
                value = minuteOfHour,
                onValueChange = { minuteOfHour = it },
                valueRange = 0f..59f,
            )
        }

        // Latitude slider
        item {
            LabeledSlider(
                label = stringResource(Res.string.earthwidget_marker_latitude_label),
                value = markerLatitudeDegrees,
                onValueChange = { markerLatitudeDegrees = it },
                valueRange = -90f..90f,
            )
        }

        // Longitude slider
        item {
            LabeledSlider(
                label = stringResource(Res.string.earthwidget_marker_longitude_label),
                value = markerLongitudeDegrees,
                onValueChange = { markerLongitudeDegrees = it },
                valueRange = -180f..180f,
            )
        }

        // Background toggle
        item {
            LabeledCheckbox(
                checked = showBackground,
                onCheckedChange = { showBackground = it },
                label = stringResource(Res.string.earthwidget_show_background_label),
            )
        }

        // Orbit path toggle
        item {
            LabeledCheckbox(
                checked = showOrbitPath,
                onCheckedChange = { showOrbitPath = it },
                label = stringResource(Res.string.earthwidget_show_orbit_label),
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

// ============================================================================
// REUSABLE UI COMPONENTS
// ============================================================================

/**
 * A slider with label and current value display.
 */
@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label)
            Text(text = value.roundToInt().toString())
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
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
    val moonOrbitDegrees = normalizeOrbitDegrees(phaseAngle + 90f)

    return ZmanimModel(
        lightDegrees = sunDirection.lightDegrees,
        sunElevationDegrees = sunDirection.sunElevationDegrees,
        moonOrbitDegrees = moonOrbitDegrees,
        moonPhaseAngleDegrees = phaseAngle,
        julianDay = julianDay,
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
private fun timeZoneForLocation(latitude: Double, longitude: Double): TimeZone {
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
