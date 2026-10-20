package io.github.kdroidfilter.seforimapp.features.home.widgets.temple

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.kosherkotlin.ComplexZmanimCalendar
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishDate
import io.github.kdroidfilter.kosherkotlin.util.GeoLocation
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.core.settings.SettingCodec
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetCard
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetMenuItem
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetSettings
import io.github.kdroidfilter.seforimapp.features.home.widgets.measures.Source
import io.github.kdroidfilter.seforimapp.features.home.widgets.measures.rememberOpenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_temple_days
import seforimapp.seforimapp.generated.resources.home_temple_months
import seforimapp.seforimapp.generated.resources.home_temple_subtitle
import seforimapp.seforimapp.generated.resources.home_temple_title
import seforimapp.seforimapp.generated.resources.home_temple_years
import seforimapp.seforimapp.generated.resources.home_widget_name_temple
import seforimapp.seforimapp.generated.resources.home_widgets_options
import kotlin.time.Clock

@Immutable
internal data class TempleCountdownData(
    val years: Int,
    val months: Int,
    val days: Int,
)

private const val DESTRUCTION_DAY = 9
private val DESTRUCTION_MONTH = HebrewMonth.AV

// Fallback if sunset is null (polar regions) or computation fails — keeps the loop alive.
private const val FALLBACK_REFRESH_MS = 60L * 60 * 1000

// Small buffer past sunset so JewishDate definitely resolves the new Hebrew day.
private const val POST_SUNSET_BUFFER_MS = 1_000L

private val JERUSALEM =
    GeoLocation(
        "Jerusalem",
        31.7683,
        35.2137,
        800.0,
        TimeZone.of("Asia/Jerusalem"),
    )

private fun computeTempleCountdown(count: TempleCount): TempleCountdownData {
    // After sunset in Jerusalem the Hebrew day rolls over — advance the Gregorian date by one day
    // so JewishDate resolves to the new Hebrew day instead of yesterday's.
    val calendar = ComplexZmanimCalendar(JERUSALEM)
    var date = calendar.localDateTime.date
    val sunset = calendar.sunset
    if (sunset != null && sunset <= Clock.System.now()) {
        date = date.plus(1, DateTimeUnit.DAY)
    }
    return templeCountdownAt(JewishDate(date), count)
}

internal fun templeCountdownAt(
    today: JewishDate,
    count: TempleCount,
): TempleCountdownData {
    // Most recent Av-9 anniversary that is strictly before today. Using `>=` (rather than `>`)
    // means the anniversary day itself is reported as "almost a full year" instead of "X years
    // and 0 days", keeping the display free of 0-day artifacts.
    var years = (today.jewishYear - count.destructionYear).toInt()
    var anniversary = JewishDate(today.jewishYear, DESTRUCTION_MONTH, DESTRUCTION_DAY)
    if (anniversary >= today) {
        years -= 1
        anniversary = JewishDate(today.jewishYear - 1, DESTRUCTION_MONTH, DESTRUCTION_DAY)
    }

    // Walk forward by full Hebrew months. Same `>=` rationale: stop just before reaching today.
    var months = 0
    var cursor = anniversary
    while (true) {
        val next = cursor.copy().forward(DateTimeUnit.MONTH, 1)
        if (next >= today) break
        cursor = next
        months += 1
    }

    val days = cursor.gregorianLocalDate.daysUntil(today.gregorianLocalDate)
    return TempleCountdownData(years, months, days)
}

// The displayed value only changes at Jerusalem sunset, so wake up exactly then.
private fun millisUntilNextJerusalemSunset(nowMillis: Long): Long {
    val cal = ComplexZmanimCalendar(JERUSALEM)
    var sunset = cal.sunset
    if (sunset == null || sunset.toEpochMilliseconds() <= nowMillis) {
        cal.localDateTime = LocalDateTime(cal.localDateTime.date.plus(1, DateTimeUnit.DAY), cal.localDateTime.time)
        sunset = cal.sunset
    }
    val target = sunset?.toEpochMilliseconds() ?: return FALLBACK_REFRESH_MS
    return (target - nowMillis + POST_SUNSET_BUFFER_MS).coerceAtLeast(POST_SUNSET_BUFFER_MS)
}

private val ACCENT_START = Color(0xFFFF6B35)
private val ACCENT_END = Color(0xFFFFAA70)

private const val TEMPLE_ANIMATION = "files/temple_jerusalem_in_fire.webp"
private const val MIN_FRAME_MS = 20L

/**
 * Plays the animated WebP frame by frame with Skia's [Codec]: only one decoded frame is alive at a time
 * (plus the one still on screen), instead of the whole animation in RAM. Decoded off the UI thread (up to 50 frames a
 * second of a large image), and held on its frame while its tab is hidden; stops when the card leaves composition.
 */
@Composable
private fun AnimatedTempleBackground(modifier: Modifier = Modifier) {
    val playing by rememberUpdatedState(LocalTabSelected.current)
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        val player = TemplePlayer()
        try {
            withContext(Dispatchers.Default) { player.open() }
            while (true) {
                // Decoded off the UI thread; shown, and the old frames freed, back on it, between two frames drawn
                // (a bitmap freed under the drawing would crash). Cancelled, the decoding finishes before the close.
                val decoded = withContext(Dispatchers.Default) { player.decode() }
                frame = player.show(decoded)
                delay(player.duration)
                snapshotFlow { playing }.first { it }
            }
        } finally {
            player.close()
        }
    }
    frame?.let { Image(it, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop) }
}

/**
 * The animation's decoder: one frame decoded at a time, the one before it freed once off screen. [decode] may run on
 * another thread than the rest, never at the same time.
 */
private class TemplePlayer {
    private var codec: Codec? = null
    private var durations: List<Long> = emptyList()
    private var work: Bitmap? = null
    private var pending: Bitmap? = null
    private var shown: Bitmap? = null
    private var previous: Bitmap? = null
    private var index = 0

    /** How long the frame last decoded stays on screen. */
    var duration = MIN_FRAME_MS
        private set

    suspend fun open() {
        val opened = Codec.makeFromData(Data.makeFromBytes(Res.readBytes(TEMPLE_ANIMATION)))
        durations = opened.framesInfo.map { it.duration.toLong().coerceAtLeast(MIN_FRAME_MS) }
        work = Bitmap().apply { allocPixels(opened.imageInfo) }
        codec = opened
    }

    fun decode(): Bitmap {
        val codec = checkNotNull(codec)
        val work = checkNotNull(work)
        // Frames are deltas on top of the previous one, which `work` still holds.
        codec.readPixels(work, index, if (index == 0) -1 else index - 1)
        duration = durations[index]
        index = (index + 1) % codec.frameCount
        // Held until shown: a decode cancelled midway (the card leaving) still gets it freed
        return work.makeClone().also { pending = it }
    }

    /** UI thread: [next] goes on screen; the one before the current frame has been off it a whole frame: freed. */
    fun show(next: Bitmap): ImageBitmap {
        pending = null
        previous?.close()
        previous = shown
        shown = next
        return next.asComposeImageBitmap()
    }

    /** Off screen for good (the card left composition): every bitmap goes. */
    fun close() {
        listOfNotNull(pending, shown, previous, work).forEach { it.close() }
        codec?.close()
    }
}

/** Years, months and days since the Temple's destruction, over the burning Temple. */
internal object TempleCountdownWidget : HomeWidget {
    override val id = "temple_countdown"
    override val title = Res.string.home_widget_name_temple
    override val defaultSpan = CellSpan(6, 3)
    override val minSpan = CellSpan(4, 3)
    override val maxSpan = CellSpan(12, 6)

    override val settings get() = TempleSettings

    // As measured with its text, so the window opens at its size and never leaves a gap
    override val optionsSize = DpSize(520.dp, 541.dp)

    @Composable
    override fun menuItems(state: HomeWidgetsState) =
        listOf(
            WidgetMenuItem(stringResource(Res.string.home_widgets_options), icon = AllIconsKeys.General.Settings) {
                state.optionsOpen = this
            },
        )

    @Composable
    override fun Options(state: HomeWidgetsState) = TempleOptions(state)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) = TempleDestructionCountdownCard(state, modifier)
}

/**
 * How the years since the destruction are counted (מראה מקומות on סי' תקנט). Rashi and the Rambam agree the Temple fell
 * on ט' באב ג'תתכ"ט of our count (Rav Mazuz, בינה לעתים): Rashi counts that very year as the first, the Rambam starts
 * after it. Tosafot have it a year later, in ג'תת"ל.
 */
internal enum class TempleCount(
    val label: String,
    val destructionYear: Int,
) {
    // "נטפי חדא שתא ... לוספה לשנת חורבן על מנין שני דבתר חורבן": the year minus 68, Chazon Ovadia
    RASHI("שיטת רש״י", 3828),

    // "שתחלתה מתשרי שאחר החרבן": the year minus 69, the Gra (חו״מ סז, ח) and Rav Mazuz
    RAMBAM("שיטת הרמב״ם", 3829),

    // "אין זה כי אם תכ״א": 420 full years, so ג'תת"ל, the year minus 70; the Ritva and Rashi on ערכין too
    TOSAFOT("שיטת התוספות", 3830),
}

internal object TempleSettings : WidgetSettings(TempleCountdownWidget.id) {
    val count by setting(TempleCount.RASHI, SettingCodec({ it.name }, { name -> TempleCount.entries.find { it.name == name } }))
}

private const val COUNT_TITLE = "שיטת החשבון"

// In our own words, after the מראי מקומות credited in its last paragraph (its text is theirs, all rights reserved).
// Its paragraphs: what the counts turn on, then one per [TempleCount] in their order, then the credit.
private const val TEMPLE_COUNT_TEXT = "files/temple_count.txt"

/** Each count picked by its own explanation, its sources linked to their lines in a new tab. */
@Composable
private fun TempleOptions(state: HomeWidgetsState) {
    val picked = state[TempleSettings.count]
    val open = rememberOpenSource(state)
    val paragraphs by produceState(emptyList<String>()) {
        value =
            Res
                .readBytes(TEMPLE_COUNT_TEXT)
                .decodeToString()
                .trim()
                .split("\n\n")
    }
    if (paragraphs.size < TempleCount.entries.size + 2) return
    val link = TextLinkStyles(SpanStyle(color = JewelTheme.globalColors.text.info, textDecoration = TextDecoration.Underline))
    VerticallyScrollableContainer(Modifier.fillMaxSize()) {
        // Longer than the window: scrolled, its bar beside the text
        Column(Modifier.fillMaxWidth().padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OptionsTitle(COUNT_TITLE)
            InlineInformationBanner(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(paragraphs.first(), fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Justify)
                    Text(paragraphs.last(), fontSize = 11.sp, color = JewelTheme.globalColors.text.info)
                }
            }
            TempleCount.entries.forEach { count ->
                val now = templeCountdownAt(JewishDate(), count)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    RadioButtonRow(
                        text = "${count.label} · ${now.years}",
                        selected = count == picked,
                        onClick = { state[TempleSettings.count] = count },
                        modifier = Modifier.testTag("temple-option-${count.name}"),
                    )
                    // Its explanation under its label, past the radio button
                    Text(
                        withSourceLinks(paragraphs[count.ordinal + 1], link, open),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Justify,
                        color = JewelTheme.globalColors.text.info,
                        modifier = Modifier.padding(start = 28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionsTitle(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}

// [shown](book title#line index): a link to that line of the library
private val SOURCE_LINK = Regex("""\[([^\]]+)]\(([^#)]+)#(\d+)\)""")

/** [text] with each [shown](book#line) as a link opening that line, the rest as is. */
internal fun withSourceLinks(
    text: String,
    style: TextLinkStyles? = null,
    open: (Source) -> Unit = {},
) = buildAnnotatedString {
    var at = 0
    SOURCE_LINK.findAll(text).forEach { match ->
        append(text.substring(at, match.range.first))
        val (shown, book, line) = match.destructured
        withLink(LinkAnnotation.Clickable(shown, style) { open(Source(shown, book, line.toInt())) }) { append(shown) }
        at = match.range.last + 1
    }
    append(text.substring(at))
}

@Composable
private fun TempleDestructionCountdownCard(
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
) {
    val count = state[TempleSettings.count]
    val countdownData by produceState(initialValue = computeTempleCountdown(count), count) {
        value = computeTempleCountdown(count)
        while (true) {
            delay(millisUntilNextJerusalemSunset(System.currentTimeMillis()))
            value = computeTempleCountdown(count)
        }
    }

    val countdownItems =
        listOf(
            countdownData.years to stringResource(Res.string.home_temple_years),
            countdownData.months to stringResource(Res.string.home_temple_months),
            countdownData.days to stringResource(Res.string.home_temple_days),
        )

    WidgetCard(modifier) {
        // Background image
        AnimatedTempleBackground(Modifier.matchParentSize())

        // Dark overlay
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Black.copy(alpha = 0.45f),
                                ),
                        ),
                    ),
        )

        // Content — same layout as DayMomentCard
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            // Title row with GradientDot — matches AdaptiveCardTitle pattern
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(11.dp)
                            .align(Alignment.CenterStart)
                            .background(
                                brush = Brush.radialGradient(listOf(ACCENT_START, ACCENT_END)),
                                shape = CircleShape,
                            ).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                )
                Text(
                    text = stringResource(Res.string.home_temple_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                // Its options at hand, as in its menu; lit in white over the fire, not Jewel's hover
                SettingsButton(
                    onClick = { state.optionsOpen = TempleCountdownWidget },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            // Values — aligned to bottom
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentAlignment = Alignment.BottomCenter,
            ) {
                // A narrow card can't hold three roomy units: tighter ones keep "1956" on one line
                val compact = maxWidth < 240.dp
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    countdownItems.forEach { (value, label) ->
                        CountdownUnit(
                            value = value,
                            label = label,
                            compact = compact,
                        )
                    }
                }
            }

            // Subtitle at bottom
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.home_temple_subtitle),
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.60f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (hovered) 0.18f else 0f))
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .testTag("temple-settings"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            key = AllIconsKeys.General.Settings,
            contentDescription = stringResource(Res.string.home_widgets_options),
            tint = Color.White.copy(alpha = if (hovered) 1f else 0.7f),
        )
    }
}

@Composable
private fun CountdownUnit(
    value: Int,
    label: String,
    compact: Boolean,
) {
    val glassShape = RoundedCornerShape(8.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .clip(glassShape)
                    .background(Color.Black.copy(alpha = 0.35f), glassShape)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), glassShape)
                    .padding(horizontal = if (compact) 8.dp else 16.dp, vertical = if (compact) 6.dp else 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value.toString(),
                fontSize = if (compact) 18.sp else 22.sp,
                maxLines = 1,
                fontWeight = FontWeight.Normal,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White.copy(alpha = 0.78f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
