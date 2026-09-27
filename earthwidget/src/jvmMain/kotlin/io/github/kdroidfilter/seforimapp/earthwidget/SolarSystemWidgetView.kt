package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import io.github.erkko68.filament.compose.rememberFilamentEngine
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.earthwidget.generated.resources.Res
import seforimapp.earthwidget.generated.resources.earthwidget_solar_title
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Default camera elevation over the ecliptic: low, so the orbit flattens into an ellipse that fills the wide card. */
private const val DEFAULT_SOLAR_ELEVATION_DEGREES = 22f
private const val MIN_SOLAR_ELEVATION_DEGREES = 10f
private const val SOLAR_OBLIQUITY_DEGREES = 23.44f

/** The Earth moves ~0.99° a day: event longitudes are taken at noon, so a day spans ±half of that. */
private const val HALF_DAY_DEGREES = 0.49f

/** Outward steps tried to fit a label before leaving it out. */
private const val LABEL_PLACEMENT_TRIES = 5

/** Local time used to place a date on the orbit. */
private const val EVENT_HOUR = 12

internal enum class SolarEventCategory(
    val colorRgb: Int,
    val major: Boolean,
) {
    YomTov(0xFFD166, major = true),
    Rabbinic(0x7FDBFF, major = true),
    Fast(0xFF8A80, major = true),
    RoshChodesh(0xB8B8B8, major = false),
}

/** A holiday / fast / Rosh Chodesh of a Hebrew year, spanning [start]..[end] (inclusive). */
@Immutable
internal data class SolarEvent(
    val name: String,
    val category: SolarEventCategory,
    val start: LocalDate,
    val end: LocalDate,
    /** Always named on the orbit (the landmarks of the year); the others only on hover. */
    val pinned: Boolean = false,
)

private val PinnedFamilies =
    setOf(
        JewishCalendar.ROSH_HASHANA,
        JewishCalendar.SUCCOS,
        JewishCalendar.CHANUKAH,
        JewishCalendar.PURIM,
        JewishCalendar.PESACH,
        JewishCalendar.SHAVUOS,
        JewishCalendar.TISHA_BEAV,
    )

/** Groups kosherjava's per-day indices into one event per holiday (Sukkot with its Chol HaMoed, etc.). */
private fun holidayFamily(yomTovIndex: Int): Pair<Int, SolarEventCategory>? =
    when (yomTovIndex) {
        JewishCalendar.ROSH_HASHANA -> JewishCalendar.ROSH_HASHANA to SolarEventCategory.YomTov
        JewishCalendar.YOM_KIPPUR -> JewishCalendar.YOM_KIPPUR to SolarEventCategory.YomTov
        JewishCalendar.SUCCOS, JewishCalendar.CHOL_HAMOED_SUCCOS, JewishCalendar.HOSHANA_RABBA ->
            JewishCalendar.SUCCOS to SolarEventCategory.YomTov
        JewishCalendar.SHEMINI_ATZERES, JewishCalendar.SIMCHAS_TORAH ->
            JewishCalendar.SHEMINI_ATZERES to SolarEventCategory.YomTov
        JewishCalendar.PESACH, JewishCalendar.CHOL_HAMOED_PESACH -> JewishCalendar.PESACH to SolarEventCategory.YomTov
        JewishCalendar.SHAVUOS -> JewishCalendar.SHAVUOS to SolarEventCategory.YomTov
        JewishCalendar.CHANUKAH -> JewishCalendar.CHANUKAH to SolarEventCategory.Rabbinic
        JewishCalendar.TU_BESHVAT -> JewishCalendar.TU_BESHVAT to SolarEventCategory.Rabbinic
        JewishCalendar.PURIM, JewishCalendar.SHUSHAN_PURIM -> JewishCalendar.PURIM to SolarEventCategory.Rabbinic
        JewishCalendar.LAG_BAOMER -> JewishCalendar.LAG_BAOMER to SolarEventCategory.Rabbinic
        JewishCalendar.TU_BEAV -> JewishCalendar.TU_BEAV to SolarEventCategory.Rabbinic
        JewishCalendar.FAST_OF_GEDALYAH, JewishCalendar.TENTH_OF_TEVES, JewishCalendar.FAST_OF_ESTHER,
        JewishCalendar.SEVENTEEN_OF_TAMMUZ, JewishCalendar.TISHA_BEAV,
        -> yomTovIndex to SolarEventCategory.Fast
        else -> null
    }

/** Every holiday, fast and Rosh Chodesh of Hebrew [hebrewYear] (1 Tishrei → 29 Elul), in date order. */
internal fun computeHebrewYearEvents(
    hebrewYear: Int,
    inIsrael: Boolean,
): List<SolarEvent> {
    val formatter = HebrewDateFormatter().apply { isHebrewFormat = true }
    val calendar = JewishCalendar(hebrewYear, JewishDate.TISHREI, 1).apply { this.inIsrael = inIsrael }
    val events = ArrayList<SolarEvent>()

    // Holidays and Rosh Chodesh are tracked apart: Rosh Chodesh Tevet falls inside Chanukah.
    class Run(
        var key: Int?,
        var event: SolarEvent? = null,
    )
    val holiday = Run(null)
    val roshChodesh = Run(null)

    fun Run.step(
        family: Pair<Int, SolarEventCategory>?,
        date: LocalDate,
        name: () -> String,
    ) {
        val current = event
        if (current != null && family?.first == key && current.end == date.minusDays(1)) {
            event = current.copy(end = date)
        } else {
            current?.let { events += it }
            key = family?.first
            event = family?.let { SolarEvent(name(), it.second, date, date, pinned = it.first in PinnedFamilies) }
        }
    }

    repeat(calendar.daysInJewishYear) {
        val date = calendar.localDate
        val family = holidayFamily(calendar.yomTovIndex)
        holiday.step(family, date) {
            // "א׳ חנוכה" → "חנוכה": one label for the eight days
            formatter.formatYomTov(calendar).let { if (family?.first == JewishCalendar.CHANUKAH) it.substringAfter(' ') else it }
        }
        roshChodesh.step(if (calendar.isRoshChodesh) 0 to SolarEventCategory.RoshChodesh else null, date) { "" }
        // Rosh Chodesh is named after the month it opens: its last day
        if (calendar.isRoshChodesh) roshChodesh.event = roshChodesh.event?.copy(name = formatter.formatMonth(calendar))
        calendar.forward(Calendar.DATE, 1)
    }
    listOf(holiday, roshChodesh).forEach { run -> run.event?.let { events += it } }
    return events.sortedBy { it.start }
}

private fun julianDayAt(
    date: LocalDate,
    timeZone: TimeZone,
): Double =
    computeJulianDayUtc(
        Date.from(
            date
                .atTime(EVENT_HOUR, 0)
                .atZone(timeZone.toZoneId())
                .toInstant(),
        ),
    )

/** Heliocentric longitude of the Earth on [date]. */
private fun earthLongitudeOn(
    date: LocalDate,
    timeZone: TimeZone,
): Float = normalizeAngle360(computeSunEclipticLongitude(julianDayAt(date, timeZone)) + 180f)

/**
 * The Sun, the Earth on its orbit and the Moon around it, with every holiday of a Hebrew year placed where the
 * Earth stands on that day. Sized and laid out like the Temple countdown card it sits next to: a title row, the
 * scene, one caption line. The Earth stands where it is on [date] (the Home page's date, shared with the other
 * widgets) and can't be moved from here; hovering a holiday names it. Another year's holidays can be shown.
 * Same camera gestures as [EarthWidgetZmanimView]: drag, trackpad pinch / scroll, Ctrl+wheel.
 */
@Composable
fun SolarSystemWidgetView(
    modifier: Modifier = Modifier,
    date: LocalDate? = null,
    inIsrael: Boolean = true,
    timeZone: TimeZone = TimeZone.getDefault(),
) {
    val today = remember(timeZone) { LocalDate.now(timeZone.toZoneId()) }
    val displayedDate = date ?: today
    // Shows the holidays of the date's Hebrew year, until the user picks another one
    val dateHebrewYear = remember(displayedDate) { JewishCalendar(displayedDate).jewishYear }
    var hebrewYear by remember(dateHebrewYear) { mutableIntStateOf(dateHebrewYear) }
    val events = remember(hebrewYear, inIsrael) { computeHebrewYearEvents(hebrewYear, inIsrael) }
    val julianDay = julianDayAt(displayedDate, timeZone)

    val earthLongitude =
        rememberSmoothAnimatedAngle(
            targetValue = normalizeAngle360(computeSunEclipticLongitude(julianDay) + 180f),
            normalize = ::normalizeAngle360,
        )
    val sidereal =
        rememberSmoothAnimatedAngle(
            targetValue = (greenwichMeanSiderealTimeRad(julianDay) * 180.0 / PI).toFloat(),
            normalize = ::normalizeAngle360,
        )
    val moon = computeMoonEclipticPosition(julianDay)
    val moonLongitude = rememberSmoothAnimatedAngle(targetValue = moon.longitude, normalize = ::normalizeAngle360)

    val camera = rememberOrbitCameraState()
    camera.pitchRange =
        (MIN_SOLAR_ELEVATION_DEGREES - DEFAULT_SOLAR_ELEVATION_DEGREES)..(90f - DEFAULT_SOLAR_ELEVATION_DEGREES)
    // Aimed once so today's Earth sits at the left end of the orbit, its day side half turned to the viewer (from
    // the front it shows its night side); then fixed, so changing the year or the holiday never swings it.
    val defaultAzimuth = remember { azimuthFacingLongitude(earthLongitudeOn(today, timeZone)) - 90f }

    val eventLongitudes =
        remember(events, timeZone) { events.map { earthLongitudeOn(it.start, timeZone) to earthLongitudeOn(it.end, timeZone) } }
    // A holiday is its whole stretch of orbit, first day's start to last day's end; Rosh Chodesh stays a dot.
    val markers =
        remember(events, eventLongitudes) {
            events.mapIndexed { i, e ->
                val isArc = e.category != SolarEventCategory.RoshChodesh
                val pad = if (isArc) HALF_DAY_DEGREES else 0f
                SolarOrbitMarker(
                    startDegrees = eventLongitudes[i].first - pad,
                    endDegrees = if (isArc) eventLongitudes[i].second + pad else eventLongitudes[i].first,
                    colorRgb = e.category.colorRgb,
                    isArc = isArc,
                )
            }
        }
    var hovered by remember { mutableStateOf<SolarEvent?>(null) }
    // The holiday the Earth stands on, named in the caption
    val currentEvent = events.firstOrNull { it.category != SolarEventCategory.RoshChodesh && displayedDate in it.start..it.end }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1)
        val heightPx = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1)
        val state =
            SolarRenderState(
                widthPx = widthPx,
                heightPx = heightPx,
                earthLongitudeDegrees = earthLongitude,
                siderealDegrees = sidereal,
                obliquityDegrees = SOLAR_OBLIQUITY_DEGREES,
                moonLongitudeDegrees = moonLongitude,
                moonLatitudeDegrees = moon.latitude,
                viewAzimuthDegrees = defaultAzimuth + camera.yaw,
                viewElevationDegrees = DEFAULT_SOLAR_ELEVATION_DEGREES + camera.pitch,
                viewZoom = camera.zoom,
                markers = markers,
            )
        val engine = rememberFilamentEngine()
        val textures = rememberWidgetTextures(engine)

        Box(modifier = Modifier.fillMaxSize().orbitCameraGestures(camera) { 180f / widthPx }) {
            SolarSystemSceneView(state = state, engine = engine, textures = textures, modifier = Modifier.matchParentSize())
            SolarEventMarkers(
                state = state,
                events = events,
                shown = hovered,
                onHover = { event, isHovered -> hovered = if (isHovered) event else hovered.takeIf { it != event } },
                modifier = Modifier.matchParentSize(),
            )
        }

        // Chrome laid out like the Temple card: title row on top, one caption line at the bottom
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(11.dp)
                            .background(Brush.radialGradient(listOf(Color(0xFFFFB347), Color(0xFFFFE08A))), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                )
                Text(
                    text = stringResource(Res.string.earthwidget_solar_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                YearSelector(
                    hebrewYear = hebrewYear,
                    onYearChange = { hebrewYear = it },
                )
                if (camera.isMoved) {
                    ChromeIcon(AllIconsKeys.General.Locate, onClick = camera::reset)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            DisplayedDateCaption(
                date = displayedDate,
                event = currentEvent,
            )
        }
    }
}

@Composable
private fun ChromeIcon(
    key: IconKey,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(key, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun YearSelector(
    hebrewYear: Int,
    onYearChange: (Int) -> Unit,
) {
    val formatter = remember { HebrewDateFormatter().apply { isHebrewFormat = true } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        // RTL page: "previous" points right
        ChromeIcon(AllIconsKeys.General.ChevronRight) { onYearChange(hebrewYear - 1) }
        Text(
            formatter.formatHebrewNumber(hebrewYear),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.85f),
        )
        ChromeIcon(AllIconsKeys.General.ChevronLeft) { onYearChange(hebrewYear + 1) }
    }
}

@Composable
private fun DisplayedDateCaption(
    date: LocalDate,
    event: SolarEvent?,
) {
    val formatter = remember { HebrewDateFormatter().apply { isHebrewFormat = true } }
    val hebrewDate = remember(date) { formatter.format(JewishCalendar(date)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        event?.let {
            Text(it.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF000000.toInt() or it.category.colorRgb))
        }
        Text(hebrewDate, fontSize = 11.sp, color = Color.White.copy(alpha = 0.60f), maxLines = 1)
    }
}

/**
 * An invisible hover target on each holiday (hovering names it), the names of the pinned landmarks, and the name of
 * the hovered one, each pushed just outside the orbit. Behind the Sun, nothing shows.
 */
@Composable
private fun SolarEventMarkers(
    state: SolarRenderState,
    events: List<SolarEvent>,
    shown: SolarEvent?,
    onHover: (SolarEvent, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hit targets and labels sit on the middle of each holiday's stretch
    val positions = remember(state) { state.markers.map { solarOrbitScreenPosition(state, it.middleDegrees()) } }
    // Labels next to the Earth step out of its way
    val earth = solarOrbitScreenPosition(state, state.earthLongitudeDegrees)
    val earthClearance = SolarGeometry(state.widthPx, state.heightPx).earthRadius * state.viewZoom * 1.4f
    // Placed in priority order (hovered, then Yamim Tovim), so the important ones win a crowded spot
    val labeled =
        events.indices
            .filter { events[it].pinned || events[it] == shown }
            .sortedBy {
                if (events[it] == shown) {
                    0
                } else if (events[it].category == SolarEventCategory.YomTov) {
                    1
                } else {
                    2
                }
            }
    val currentOnHover by rememberUpdatedState(onHover)
    Layout(
        modifier = modifier,
        content = {
            events.forEachIndexed { i, event ->
                key(event.start, event.name) {
                    val interactionSource = remember { MutableInteractionSource() }
                    val isHovered by interactionSource.collectIsHoveredAsState()
                    LaunchedEffect(isHovered) { currentOnHover(event, isHovered) }
                    Box(
                        Modifier
                            .size(16.dp)
                            .hoverable(interactionSource, enabled = !positions[i].hiddenByEarth),
                    )
                }
            }
            labeled.forEach { i ->
                val event = events[i]
                val emphasized = event == shown
                BasicText(
                    text = event.name,
                    style =
                        TextStyle(
                            color = Color(0xFF000000.toInt() or event.category.colorRgb),
                            fontSize = if (emphasized) 12.sp else 10.sp,
                            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
                            shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 3f),
                        ),
                    // Far side fades like the orbit line
                    modifier = Modifier.graphicsLayer { alpha = if (emphasized) 1f else 0.45f + 0.55f * positions[i].depth },
                )
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val centerX = constraints.maxWidth / 2f
        val centerY = constraints.maxHeight / 2f
        // Labels step radially outward until they clear the Earth and the labels already placed
        val earthRect =
            Rect(Offset(earth.x, earth.y), earthClearance).takeUnless { earth.hiddenByEarth } ?: Rect.Zero
        val taken = mutableListOf(earthRect)
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { slot, placeable ->
                val isLabel = slot >= events.size
                val index = if (isLabel) labeled[slot - events.size] else slot
                val p = positions[index]
                if (p.hiddenByEarth) return@forEachIndexed
                if (!isLabel) {
                    placeable.place((p.x - placeable.width / 2f).roundToInt(), (p.y - placeable.height / 2f).roundToInt())
                    return@forEachIndexed
                }
                val dx = p.x - centerX
                val dy = p.y - centerY
                val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
                val halfW = placeable.width / 2f
                val halfH = placeable.height / 2f

                // Along a direction (ux, uy) from the dot, gap px clear of it
                fun rectAt(
                    ux: Float,
                    uy: Float,
                    gap: Float,
                ): Rect {
                    val x = (p.x + ux * (halfW + gap)).coerceIn(halfW, (constraints.maxWidth - halfW).coerceAtLeast(halfW))
                    val y = p.y + uy * (halfH + gap)
                    return Rect(x - halfW, y - halfH, x + halfW, y + halfH)
                }
                val directions = listOf(dx / len to dy / len, 0f to -1f, 0f to 1f)
                val rect =
                    (0 until LABEL_PLACEMENT_TRIES)
                        .flatMap { step -> directions.map { (ux, uy) -> rectAt(ux, uy, 4f + step * placeable.height * 0.8f) } }
                        .firstOrNull { candidate -> taken.none { it.overlaps(candidate) } }
                        ?: return@forEachIndexed // no room: better unnamed than on top of another
                taken += rect
                // Absolute pixel placement; do not mirror in RTL.
                placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
            }
        }
    }
}
