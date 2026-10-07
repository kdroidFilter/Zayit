package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipPlacement
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupPositionProvider
import io.github.erkko68.filament.compose.rememberFilamentEngineAsync
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewDateFormatter
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforimapp.luach.LuachDateButton
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.styling.TooltipColors
import org.jetbrains.jewel.ui.component.styling.TooltipMetrics
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.earthwidget.generated.resources.Res
import seforimapp.earthwidget.generated.resources.earthwidget_kiddush_levana_legend
import seforimapp.earthwidget.generated.resources.earthwidget_solar_title
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Default camera elevation over the ecliptic for the Home card's shape ([CARD_ASPECT] high per wide): low, so the
 * orbit flattens into an ellipse that fills it. A taller area (the full window) looks from higher up.
 */
private const val DEFAULT_SOLAR_ELEVATION_DEGREES = 22f
private const val CARD_ASPECT = 0.472f

/** Width of the Home card, the size the labels are designed at; they grow with a larger view. */
private const val CARD_WIDTH_DP = 429f

/** Text grows much slower than the view: ×1.4 at most, reached around a 1700 dp wide window. */
private fun textScaleFor(widthDp: Float): Float = sqrt(widthDp / CARD_WIDTH_DP).coerceIn(1f, 1.4f)

private const val MIN_SOLAR_ELEVATION_DEGREES = 10f
private const val SOLAR_OBLIQUITY_DEGREES = 23.44f

/** The Earth moves ~0.99° a day: event longitudes are taken at noon, so a day spans ±half of that. */
private const val HALF_DAY_DEGREES = 0.49f

private const val UNIX_EPOCH_JD = 2_440_587.5
private const val MILLIS_PER_DAY = 86_400_000.0

/** Half a mean lunation, the "between moldos" limit: 14 d 18 h 22 min 1⅔ s. */
private const val HALF_LUNATION_MILLIS = ((14L * 24 + 18) * 60 + 22) * 60_000L + 1_667L

/**
 * Kiddush Levana window (epoch millis) of the lunar month [date] falls in, counted from its molad as the Earth
 * widget's opinions do: 3 or 7 days after it, until half a lunation or 15 days.
 */
internal fun kiddushLevanaWindow(
    date: LocalDate,
    earliest: KiddushLevanaEarliestOpinion,
    latest: KiddushLevanaLatestOpinion,
): Pair<Long, Long> {
    val calendar = JewishCalendar(date.toKotlinLocalDate())
    // The molad that opened this lunar month: this Hebrew month's, or the previous one's if it isn't past yet
    val noon =
        date
            .atTime(12, 0)
            .atZone(java.time.ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    var molad = calendar.moladAsInstant.toEpochMilliseconds()
    if (molad > noon) {
        goToPreviousHebrewMonth(calendar)
        molad = calendar.moladAsInstant.toEpochMilliseconds()
    }
    val start = molad + if (earliest == KiddushLevanaEarliestOpinion.DAYS_7) 7 * DAY_MILLIS else 3 * DAY_MILLIS
    val end = molad + if (latest == KiddushLevanaLatestOpinion.DAYS_15) 15 * DAY_MILLIS else HALF_LUNATION_MILLIS
    return start to end
}

private const val DAY_MILLIS = 86_400_000L

/** Play speed of the full window: a lunar month in ~5 s, a year in ~1 min. */
const val PLAY_DAYS_PER_SECOND = 6f

/** The Earth's shown spin while playing — slowed down (the real one is a turn per day, 6 a second here). */
const val PLAY_SPIN_SECONDS_PER_TURN = 2f

/** Outward steps tried to fit a label before leaving it out. */
private const val LABEL_PLACEMENT_TRIES = 6

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

/** Groups KosherKotlin's per-day indices into one event per holiday (Sukkot with its Chol HaMoed, etc.). */
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
    val calendar = JewishCalendar(hebrewYear, HebrewMonth.TISHREI, 1).apply { this.inIsrael = inIsrael }
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
        val date = calendar.gregorianLocalDate.toJavaLocalDate()
        val family = holidayFamily(calendar.yomTovIndex)
        holiday.step(family, date) {
            // "א׳ חנוכה" → "חנוכה": one label for the eight days
            formatter.formatYomTov(calendar).let { if (family?.first == JewishCalendar.CHANUKAH) it.substringAfter(' ') else it }
        }
        roshChodesh.step(if (calendar.isRoshChodesh) 0 to SolarEventCategory.RoshChodesh else null, date) { "" }
        // Rosh Chodesh is named after the month it opens: its last day
        if (calendar.isRoshChodesh) roshChodesh.event = roshChodesh.event?.copy(name = formatter.formatMonth(calendar))
        calendar.forward(DateTimeUnit.DAY, 1)
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
    /** Applied to the header row (e.g. a window drag area when the header sits in the title bar band). */
    headerModifier: Modifier = Modifier,
    date: LocalDate? = null,
    /** An instant of [date] to show (e.g. a zman picked on the Home cards) instead of its noon. */
    timeMillis: Long? = null,
    inIsrael: Boolean = true,
    timeZone: TimeZone = TimeZone.getDefault(),
    /** Shows a button opening the widget in its own window; null inside that window. */
    onFullscreen: (() -> Unit)? = null,
    /** Shows a button opening the widget's options; null for none. */
    onOptions: (() -> Unit)? = null,
    /** The Earth's shown spin while playing, in seconds a turn; null for its real one (a turn per simulated day). */
    spinSecondsPerTurn: Float? = PLAY_SPIN_SECONDS_PER_TURN,
    /** Inside its own window: finer, more realistic rendering (see [SolarRenderState.detailed]). */
    fullWindow: Boolean = false,
    /** Shows a date picker (as on the Earth widget) that reports the chosen day; null on the Home card. */
    onDateSelect: ((LocalDate) -> Unit)? = null,
    /** The date picker's accent (today, the selection); the theme's focus colour by default. */
    accentColor: Color? = null,
    kiddushLevanaEarliestOpinion: KiddushLevanaEarliestOpinion = KiddushLevanaEarliestOpinion.DAYS_3,
    kiddushLevanaLatestOpinion: KiddushLevanaLatestOpinion = KiddushLevanaLatestOpinion.BETWEEN_MOLDOS,
    /**
     * The instant played, owned by the caller with every other view's (one clock for all): non-null while playing, it
     * is shown as is, frame by frame.
     */
    playMillis: Long? = null,
    /** Shows a play / pause button (in the full window); the caller runs the clock. */
    onPlayToggle: (() -> Unit)? = null,
    /** Keeps the header and caption clear of the window's own controls; the 3D scene still fills the view. */
    chromePadding: PaddingValues = PaddingValues(0.dp),
) {
    val today = remember(timeZone) { LocalDate.now(timeZone.toZoneId()) }
    val baseDate = date ?: today
    val playing = playMillis != null
    // A visible spin of the Earth while playing (its real one, a turn per simulated day, can only flicker at speed):
    // wall-clock driven, a look only — the orbits and the Moon keep to the one played instant
    var playSpinDegrees by remember { mutableFloatStateOf(0f) }
    val spinRate by rememberUpdatedState(spinSecondsPerTurn)
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        playSpinDegrees = 0f
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) spinRate?.let { playSpinDegrees = (playSpinDegrees + (now - last) / 1e9f * 360f / it).mod(360f) }
                last = now
            }
        }
    }
    val displayedDate =
        playMillis?.let { Instant.ofEpochMilli(it).atZone(timeZone.toZoneId()).toLocalDate() } ?: baseDate
    // Shows the holidays of the date's Hebrew year, until the user picks another one
    val dateHebrewYear = remember(displayedDate) { JewishCalendar(displayedDate.toKotlinLocalDate()).jewishYear.toInt() }
    var hebrewYear by remember(dateHebrewYear) { mutableIntStateOf(dateHebrewYear) }
    val events = remember(hebrewYear, inIsrael) { computeHebrewYearEvents(hebrewYear, inIsrael) }
    // The instant played, else the given one, else noon of the day
    val julianDay =
        (playMillis ?: timeMillis)?.let { it / MILLIS_PER_DAY + UNIX_EPOCH_JD } ?: julianDayAt(displayedDate, timeZone)
    // While playing, the noon of the played day: the stylised spin below turns from there
    val dayFraction = if (playing) (julianDay - julianDayAt(displayedDate, timeZone)).toFloat() else 0f

    val earthLongitudeTarget = normalizeAngle360(computeSunEclipticLongitude(julianDay) + 180f)
    // While playing, the Earth is shown at noon each day (solar-day frame) plus a slowed-down visible spin: at real
    // speed it would turn several times a second. Sidereal time runs 360.9856°/day: dropping the 360° keeps the slow
    // 0.9856°/day drift only.
    val siderealTarget =
        (greenwichMeanSiderealTimeRad(julianDay) * 180.0 / PI).toFloat() +
            if (spinSecondsPerTurn == null || !playing) 0f else -360f * dayFraction + playSpinDegrees
    val moon = computeMoonEclipticPosition(julianDay)
    // Kiddush Levana of the lunar month at the displayed instant, as the Moon's longitudes at its start and end
    val kiddushLevana =
        remember(displayedDate, kiddushLevanaEarliestOpinion, kiddushLevanaLatestOpinion) {
            kiddushLevanaWindow(displayedDate, kiddushLevanaEarliestOpinion, kiddushLevanaLatestOpinion)
        }
    val instantMillis = ((julianDay - UNIX_EPOCH_JD) * MILLIS_PER_DAY).toLong()
    val kiddushLevanaNow = kiddushLevana.let { instantMillis in it.first..it.second }
    val kiddushLevanaLongitudes =
        remember(kiddushLevana) {
            kiddushLevana.let { (start, end) ->
                computeMoonEclipticPosition(start / MILLIS_PER_DAY + UNIX_EPOCH_JD).longitude to
                    computeMoonEclipticPosition(end / MILLIS_PER_DAY + UNIX_EPOCH_JD).longitude
            }
        }
    // Eased on a date change; followed directly while playing (a spring would trail the running clock, and jolt
    // back and forth on pause)
    val earthLongitude = rememberSmoothAnimatedAngle(earthLongitudeTarget, ::normalizeAngle360, instant = playing)
    val sidereal = rememberSmoothAnimatedAngle(normalizeAngle360(siderealTarget), ::normalizeAngle360, instant = playing)
    val moonLongitude = rememberSmoothAnimatedAngle(moon.longitude, ::normalizeAngle360, instant = playing)

    val camera = rememberOrbitCameraState()
    // Aimed so the Earth of [anchorDate] is right in front of the viewer; kept when the date changes (the Earth then
    // travels along its orbit), re-aimed by the recenter button.
    var anchorDate by remember { mutableStateOf(displayedDate) }
    val defaultAzimuth =
        rememberSmoothAnimatedAngle(
            targetValue = remember(anchorDate) { azimuthFacingLongitude(earthLongitudeOn(anchorDate, timeZone)) },
            normalize = ::normalizeAngle360,
        )

    val eventLongitudes =
        remember(events, timeZone) { events.map { earthLongitudeOn(it.start, timeZone) to earthLongitudeOn(it.end, timeZone) } }
    var hovered by remember { mutableStateOf<SolarEvent?>(null) }
    // The holiday the Earth stands on: named in the caption, and its label placed first
    val currentEvent = events.firstOrNull { it.category != SolarEventCategory.RoshChodesh && displayedDate in it.start..it.end }
    // A holiday is its whole stretch of orbit, first day's start to last day's end; Rosh Chodesh is a tick.
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

    // In use while the pointer is over it (or it plays): only then does the Sun's glare breathe
    val pointerSource =
        remember {
            androidx.compose.foundation.interaction
                .MutableInteractionSource()
        }
    val pointerOver by pointerSource.collectIsHoveredAsState()
    BoxWithConstraints(
        modifier =
            modifier
                .clipToBounds()
                // The card in its own layer, under the same clip wherever it is: cut by the edge of a scrolling page,
                // its drawing would otherwise need new GPU programs, compiled there and then (a ~30 ms frame)
                .then(if (fullWindow) Modifier else Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen })
                .hoverable(pointerSource),
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1)
        val heightPx = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1)
        val defaultElevation =
            (DEFAULT_SOLAR_ELEVATION_DEGREES * (heightPx.toFloat() / widthPx) / CARD_ASPECT)
                .coerceIn(DEFAULT_SOLAR_ELEVATION_DEGREES, 45f)
        camera.pitchRange = (MIN_SOLAR_ELEVATION_DEGREES - defaultElevation)..(90f - defaultElevation)
        // Gestures drive the camera directly; anything else (recenter) eases back
        val viewSpec = if (camera.isGesturing) snap() else SmoothAngleSpringSpec
        val animatedYaw by animateFloatAsState(camera.yaw, viewSpec, label = "solarYaw")
        val animatedPitch by animateFloatAsState(camera.pitch, viewSpec, label = "solarPitch")
        val animatedZoom by animateFloatAsState(camera.zoom, viewSpec, label = "solarZoom")
        val state =
            SolarRenderState(
                widthPx = widthPx,
                heightPx = heightPx,
                earthLongitudeDegrees = earthLongitude,
                siderealDegrees = sidereal,
                obliquityDegrees = SOLAR_OBLIQUITY_DEGREES,
                moonLongitudeDegrees = moonLongitude,
                moonLatitudeDegrees = moon.latitude,
                // The Sun's own rotation on the date, not a spin on screen (Carrington: 25.38 d, epoch JD 2398140.227)
                sunRotationDegrees = (((julianDay - 2398140.227) / 25.38).mod(1.0) * 360.0).toFloat(),
                viewAzimuthDegrees = defaultAzimuth + animatedYaw,
                viewElevationDegrees = defaultElevation + animatedPitch,
                viewZoom = animatedZoom,
                detailed = fullWindow,
                markers = markers,
                kiddushLevanaStartDegrees = kiddushLevanaLongitudes.first,
                kiddushLevanaEndDegrees = kiddushLevanaLongitudes.second,
            )
        // Null while the engine is being created, off the UI thread: a loader meanwhile
        // The full window draws a larger scene: Filament's defaults there
        val engine = rememberFilamentEngineAsync(config = if (fullWindow) null else widgetEngineConfig())

        Box(modifier = Modifier.fillMaxSize().orbitCameraGestures(camera) { 180f / widthPx }) {
            if (engine == null) {
                WidgetLoader(Modifier.matchParentSize())
            } else {
                SolarSystemSceneView(
                    state = state,
                    engine = engine,
                    modifier = Modifier.matchParentSize(),
                    animated = pointerOver || playing,
                )
            }
            SolarEventMarkers(
                state = state,
                events = events,
                shown = hovered,
                current = currentEvent,
                onHover = { event, isHovered -> hovered = if (isHovered) event else hovered.takeIf { it != event } },
                modifier = Modifier.matchParentSize(),
            )
        }

        // Chrome laid out like the Temple card: title row on top, one caption line at the bottom. Designed at the
        // card's size; a larger view (the full window) scales it all through the density.
        val chromeScale = textScaleFor(maxWidth.value)
        // Outside the scaled density: the title bar's height is in real dp
        Box(modifier = Modifier.fillMaxSize().padding(chromePadding)) {
            CompositionLocalProvider(LocalDensity provides Density(density.density * chromeScale, density.fontScale)) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().then(headerModifier), verticalAlignment = Alignment.CenterVertically) {
                        // The full window's title bar already names it
                        if (!fullWindow) {
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
                                modifier = Modifier.padding(start = 8.dp, end = 12.dp),
                            )
                        }
                        // Full window: the date picker next to the title (the right in the app's RTL), as on the Earth
                        // widget; its calendar picks the year too, so no separate year selector
                        if (onDateSelect != null) {
                            IntUiTheme(isDark = true) {
                                LuachDateButton(
                                    label =
                                        remember(displayedDate) {
                                            HebrewDateFormatter().apply { isHebrewFormat = true }.format(
                                                JewishCalendar(displayedDate.toKotlinLocalDate()),
                                            )
                                        },
                                    selected = displayedDate,
                                    inIsrael = inIsrael,
                                    accent = accentColor ?: JewelTheme.globalColors.outlines.focused,
                                    onSelect = onDateSelect,
                                    today = today,
                                    onToday = { onDateSelect(today) },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (onDateSelect == null) {
                            YearSelector(
                                hebrewYear = hebrewYear,
                                onYearChange = { hebrewYear = it },
                            )
                        }
                        onOptions?.let { ChromeIcon(AllIconsKeys.General.Settings, onClick = it) }
                        onFullscreen?.let { ChromeIcon(AllIconsKeys.General.ExpandComponent, onClick = it) }
                        if (camera.isMoved || anchorDate != displayedDate) {
                            ChromeIcon(AllIconsKeys.General.Locate) {
                                camera.reset()
                                anchorDate = displayedDate
                            }
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // Caption on the start side (the right in the app's RTL), play on the end side
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // The full window shows the date in its picker already
                        DisplayedDateCaption(
                            date = displayedDate,
                            event = currentEvent,
                            showDate = onDateSelect == null,
                            kiddushLevanaNow = fullWindow && kiddushLevanaNow,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        onPlayToggle?.let { toggle ->
                            ChromeIcon(if (playing) AllIconsKeys.Actions.Pause else AllIconsKeys.Actions.Execute, onClick = toggle)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChromeIcon(
    key: IconKey,
    active: Boolean = true,
    tooltip: String? = null,
    onClick: () -> Unit,
) {
    val icon =
        @Composable {
            Box(
                modifier =
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    key,
                    contentDescription = tooltip,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White.copy(alpha = if (active) 0.8f else 0.35f),
                )
            }
        }
    if (tooltip == null) {
        icon()
    } else {
        Tooltip(
            tooltip = { Text(tooltip, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f)) },
            style = ChromeTooltipStyle,
        ) { icon() }
    }
}

// Dark and translucent like the widget's own chrome, instead of the app theme's tooltip over the starfield
@OptIn(ExperimentalFoundationApi::class)
private val ChromeTooltipStyle =
    TooltipStyle(
        colors =
            TooltipColors(
                background = Color(0xE6101520),
                content = Color.White,
                border = Color.White.copy(alpha = 0.15f),
                shadow = Color.Black.copy(alpha = 0.4f),
            ),
        metrics =
            TooltipMetrics.defaults(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                cornerSize = CornerSize(8.dp),
                placement = AboveAnchor,
            ),
    )

// Centred just above the icon (the chrome sits at the bottom), not at the cursor. A raw provider: Jewel's
// ComponentRect needs a LayoutBoundsHolder its TooltipArea doesn't wire (see the tabs' tooltip).
@OptIn(ExperimentalFoundationApi::class)
private object AboveAnchor : TooltipPlacement {
    @Composable
    override fun positionProvider(cursorPosition: Offset): PopupPositionProvider =
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset =
                IntOffset(
                    x =
                        (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
                            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                    y = (anchorBounds.top - popupContentSize.height - 6).coerceAtLeast(0),
                )
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
    showDate: Boolean = true,
    kiddushLevanaNow: Boolean = false,
) {
    val formatter = remember { HebrewDateFormatter().apply { isHebrewFormat = true } }
    val hebrewDate = remember(date) { formatter.format(JewishCalendar(date.toKotlinLocalDate())) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        event?.let {
            Text(it.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF000000.toInt() or it.category.colorRgb))
        }
        if (showDate) Text(hebrewDate, fontSize = 11.sp, color = Color.White.copy(alpha = 0.60f), maxLines = 1)
        if (kiddushLevanaNow) {
            Text(
                stringResource(Res.string.earthwidget_kiddush_levana_legend),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF000000.toInt() or KIDDUSH_LEVANA_SOLAR_RGB),
            )
        }
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
    current: SolarEvent?,
    onHover: (SolarEvent, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hit targets and labels sit on the middle of each holiday's stretch
    // Only the camera and the markers place them: not the Earth moving along (each frame of a play)
    val positions =
        remember(state.widthPx, state.heightPx, state.viewAzimuthDegrees, state.viewElevationDegrees, state.viewZoom, state.markers) {
            state.markers.map { solarOrbitScreenPosition(state, it.middleDegrees()) }
        }
    // Labels next to the Earth step out of its way
    val earth = solarOrbitScreenPosition(state, state.earthLongitudeDegrees)
    val earthClearance = SolarGeometry(state.widthPx, state.heightPx).earthRadius * state.viewZoom * 1.4f
    // Placed in priority order (the Earth's holiday, hovered, Yamim Tovim), so the important ones win a crowded spot
    val labeled =
        events.indices
            // The full window has room for every name; the card names the landmarks, the rest on hover
            .filter { state.detailed || events[it].pinned || events[it] == shown || events[it] == current }
            .sortedBy {
                when {
                    events[it] == current -> 0
                    events[it] == shown -> 1
                    events[it].category == SolarEventCategory.YomTov -> 2
                    else -> 3
                }
            }
    val currentOnHover by rememberUpdatedState(onHover)
    // Labels are designed for the Home card; a larger view (the full window) scales them
    val density = LocalDensity.current
    val uiScale = textScaleFor(state.widthPx / density.density)
    val clearance = with(density) { 4.dp.toPx() }
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
                            fontSize = (if (emphasized) 12f else 10f).sp * uiScale,
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
                // Steps grow with the Earth too: in a large window it's much taller than a label, and a label
                // beside it must be able to step all the way past it
                val stepPx = maxOf(placeable.height * 0.8f, earthClearance * 0.6f)
                val rect =
                    (0 until LABEL_PLACEMENT_TRIES)
                        .flatMap { step -> directions.map { (ux, uy) -> rectAt(ux, uy, clearance + step * stepPx) } }
                        .firstOrNull { candidate -> taken.none { it.overlaps(candidate) } }
                        ?: return@forEachIndexed // no room: better unnamed than on top of another
                taken += rect
                // Absolute pixel placement; do not mirror in RTL.
                placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
            }
        }
    }
}
