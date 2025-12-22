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
import com.kosherjava.zmanim.AstronomicalCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.kosherjava.zmanim.util.GeoLocation
import com.kosherjava.zmanim.util.NOAACalculator
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
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

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

/**
 * 3D vector with double precision for coordinate transformations.
 */
private data class Vec3(val x: Double, val y: Double, val z: Double) {
    /** Returns a unit vector in the same direction. */
    fun normalized(): Vec3 {
        val len = sqrt(x * x + y * y + z * z)
        if (len <= 1e-9) return this
        val inv = 1.0 / len
        return Vec3(x * inv, y * inv, z * inv)
    }
}

/**
 * Sun direction in world coordinates.
 *
 * @property lightDegrees Azimuth angle for rendering.
 * @property elevationDegrees Elevation angle for rendering.
 */
private data class SunDirection(
    val lightDegrees: Float,
    val elevationDegrees: Float,
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
    val location = GeoLocation("Marker", latitude, longitude, elevation, timeZone)
    val astronomicalCalendar = AstronomicalCalendar(location)
    val baseCalendar = Calendar.getInstance(timeZone).apply { time = referenceTime }
    astronomicalCalendar.calendar = baseCalendar

    // Get key solar events
    val sunrise = astronomicalCalendar.sunrise
    val sunset = astronomicalCalendar.sunset
    val solarNoon = astronomicalCalendar.sunTransit

    // NOAA returns azimuth with North = 0
    val solarAzimuthNorth = NOAACalculator.getSolarAzimuth(baseCalendar, latitude, longitude)

    // Calculate sun elevation with interpolation
    val localSunElevationDegrees = computeSunElevationDegrees(
        referenceTime = referenceTime,
        sunrise = sunrise,
        sunset = sunset,
        solarNoon = solarNoon,
        latitude = latitude,
        longitude = longitude,
        timeZone = timeZone,
    )

    // Transform to world coordinates
    val sunDirection = computeSunDirectionWorld(
        latitude = latitude,
        longitude = longitude,
        azimuthDegrees = solarAzimuthNorth,
        elevationDegrees = localSunElevationDegrees,
        earthRotationDegrees = earthRotationDegrees,
        earthTiltDegrees = earthTiltDegrees,
    )

    // Calculate moon position
    val julianDay = computeJulianDay(referenceTime)
    val phaseAngle = computeHalakhicPhaseAngle(referenceTime, timeZone)
    val moonOrbitDegrees = normalizeOrbitDegrees(phaseAngle + 90f)

    return ZmanimModel(
        lightDegrees = sunDirection.lightDegrees,
        sunElevationDegrees = sunDirection.elevationDegrees,
        moonOrbitDegrees = moonOrbitDegrees,
        moonPhaseAngleDegrees = phaseAngle,
        julianDay = julianDay,
    )
}

/**
 * Computes sun elevation with sinusoidal interpolation between sunrise and sunset.
 *
 * This provides smoother elevation curves than point calculations,
 * better representing the sun's arc through the sky.
 *
 * @param referenceTime Time for calculation.
 * @param sunrise Sunrise time (or null if polar day/night).
 * @param sunset Sunset time (or null if polar day/night).
 * @param solarNoon Solar noon time.
 * @param latitude Observer latitude.
 * @param longitude Observer longitude.
 * @param timeZone Local timezone.
 * @return Sun elevation in degrees.
 */
private fun computeSunElevationDegrees(
    referenceTime: Date,
    sunrise: Date?,
    sunset: Date?,
    solarNoon: Date?,
    latitude: Double,
    longitude: Double,
    timeZone: TimeZone,
): Float {
    val referenceCalendar = Calendar.getInstance(timeZone).apply { time = referenceTime }

    // Fallback to NOAA point calculation
    val fallback = NOAACalculator.getSolarElevation(referenceCalendar, latitude, longitude).toFloat()

    // If no sunrise/sunset (polar regions), use point calculation
    if (sunrise == null || sunset == null) {
        return fallback.coerceIn(-90f, 90f)
    }

    val sunriseMillis = sunrise.time
    val sunsetMillis = sunset.time
    if (sunriseMillis >= sunsetMillis) {
        return fallback.coerceIn(-90f, 90f)
    }

    // Calculate day progress (0 = sunrise, 1 = sunset)
    val dayProgress = (referenceTime.time - sunriseMillis).toDouble() /
            (sunsetMillis - sunriseMillis).toDouble()

    // Use sinusoidal interpolation during daylight hours
    if (dayProgress in 0.0..1.0) {
        val noonElevation = if (solarNoon != null) {
            val noonCalendar = Calendar.getInstance(timeZone).apply { time = solarNoon }
            NOAACalculator.getSolarElevation(noonCalendar, latitude, longitude)
        } else {
            fallback.toDouble()
        }
        // Sin interpolation: 0 at sunrise/sunset, max at noon
        val elevation = sin(PI * dayProgress) * noonElevation
        return elevation.toFloat().coerceIn(-90f, 90f)
    }

    return fallback.coerceIn(-90f, 90f)
}

/**
 * Transforms sun direction from local horizontal to world coordinates.
 *
 * Converts observer's local azimuth/elevation to the rendering coordinate system,
 * accounting for Earth rotation and tilt.
 *
 * @param latitude Observer latitude.
 * @param longitude Observer longitude.
 * @param azimuthDegrees Sun azimuth (North = 0, clockwise).
 * @param elevationDegrees Sun elevation above horizon.
 * @param earthRotationDegrees Earth rotation angle.
 * @param earthTiltDegrees Earth axial tilt.
 * @return Sun direction for rendering.
 */
private fun computeSunDirectionWorld(
    latitude: Double,
    longitude: Double,
    azimuthDegrees: Double,
    elevationDegrees: Float,
    earthRotationDegrees: Float,
    earthTiltDegrees: Float,
): SunDirection {
    // Convert to radians
    val latRad = Math.toRadians(latitude)
    val lonRad = Math.toRadians(longitude)
    val azRad = Math.toRadians(azimuthDegrees)
    val elRad = Math.toRadians(elevationDegrees.toDouble())

    // Precompute trig values
    val sinLat = sin(latRad)
    val cosLat = cos(latRad)
    val sinLon = sin(lonRad)
    val cosLon = cos(lonRad)

    // Build local coordinate frame at observer position
    // East vector (tangent to latitude circle)
    val eastX = cosLon
    val eastY = 0.0
    val eastZ = -sinLon

    // North vector (tangent to meridian)
    val northX = -sinLat * sinLon
    val northY = cosLat
    val northZ = -sinLat * cosLon

    // Up vector (radial, away from Earth center)
    val upX = cosLat * sinLon
    val upY = sinLat
    val upZ = cosLat * cosLon

    // Convert horizontal coordinates to direction
    val sinAz = sin(azRad)
    val cosAz = cos(azRad)
    val cosEl = cos(elRad)
    val sinEl = sin(elRad)

    // Sun direction in local frame, then transform to Earth-centered
    val dirX = (eastX * sinAz + northX * cosAz) * cosEl + upX * sinEl
    val dirY = (eastY * sinAz + northY * cosAz) * cosEl + upY * sinEl
    val dirZ = (eastZ * sinAz + northZ * cosAz) * cosEl + upZ * sinEl

    // Transform to world coordinates
    val earthDir = Vec3(dirX, dirY, dirZ).normalized()
    val worldDir = earthToWorld(earthDir, earthRotationDegrees, earthTiltDegrees).normalized()

    // Convert back to azimuth/elevation for rendering
    val lightDegrees = Math.toDegrees(atan2(worldDir.x, worldDir.z)).toFloat()
    val elevation = Math.toDegrees(asin(worldDir.y.coerceIn(-1.0, 1.0))).toFloat()

    return SunDirection(lightDegrees = lightDegrees, elevationDegrees = elevation)
}

/**
 * Transforms a vector from Earth-fixed to world coordinates.
 *
 * Applies inverse Earth rotation and tilt.
 *
 * @param earthDir Direction in Earth-fixed coordinates.
 * @param earthRotationDegrees Earth rotation angle.
 * @param earthTiltDegrees Earth axial tilt.
 * @return Direction in world coordinates.
 */
private fun earthToWorld(
    earthDir: Vec3,
    earthRotationDegrees: Float,
    earthTiltDegrees: Float,
): Vec3 {
    // Inverse Earth rotation (yaw)
    val yawRad = Math.toRadians(-earthRotationDegrees.toDouble())
    val cosYaw = cos(yawRad)
    val sinYaw = sin(yawRad)
    val x1 = earthDir.x * cosYaw + earthDir.z * sinYaw
    val z1 = -earthDir.x * sinYaw + earthDir.z * cosYaw
    val y1 = earthDir.y

    // Inverse Earth tilt (pitch)
    val tiltRad = Math.toRadians(-earthTiltDegrees.toDouble())
    val cosTilt = cos(tiltRad)
    val sinTilt = sin(tiltRad)
    val x2 = x1 * cosTilt - y1 * sinTilt
    val y2 = x1 * sinTilt + y1 * cosTilt

    return Vec3(x2, y2, z1)
}

// ============================================================================
// JULIAN DAY CALCULATION
// ============================================================================

/**
 * Converts a Date to Julian Day number.
 *
 * Uses the standard Gregorian calendar to Julian Day conversion algorithm.
 *
 * @param date Date to convert.
 * @return Julian Day number (decimal).
 */
private fun computeJulianDay(date: Date): Double {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date }
    var year = cal.get(Calendar.YEAR)
    var month = cal.get(Calendar.MONTH) + 1
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val second = cal.get(Calendar.SECOND)

    // Adjust January/February (treat as months 13/14 of previous year)
    if (month <= 2) {
        year -= 1
        month += 12
    }

    // Gregorian calendar correction
    val A = year / 100
    val B = 2 - A + A / 4

    // Day fraction
    val dayFraction = (hour + minute / 60.0 + second / 3600.0) / 24.0

    return floor(365.25 * (year + 4716)) + floor(30.6001 * (month + 1)) + day + dayFraction + B - 1524.5
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
