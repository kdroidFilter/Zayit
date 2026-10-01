package io.github.kdroidfilter.seforimapp.features.home.widgets.temple

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.kosherkotlin.ComplexZmanimCalendar
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishDate
import io.github.kdroidfilter.kosherkotlin.util.GeoLocation
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetCard
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.Text
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
import kotlin.time.Clock

@Immutable
private data class TempleCountdownData(
    val years: Int,
    val months: Int,
    val days: Int,
)

private const val DESTRUCTION_YEAR = 3830
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

private fun computeTempleCountdown(): TempleCountdownData {
    // After sunset in Jerusalem the Hebrew day rolls over — advance the Gregorian date by one day
    // so JewishDate resolves to the new Hebrew day instead of yesterday's.
    val calendar = ComplexZmanimCalendar(JERUSALEM)
    var date = calendar.localDateTime.date
    val sunset = calendar.sunset
    if (sunset != null && sunset <= Clock.System.now()) {
        date = date.plus(1, DateTimeUnit.DAY)
    }
    val today = JewishDate(date)

    // Most recent Av-9 anniversary that is strictly before today. Using `>=` (rather than `>`)
    // means the anniversary day itself is reported as "almost a full year" instead of "X years
    // and 0 days", keeping the display free of 0-day artifacts.
    var years = (today.jewishYear - DESTRUCTION_YEAR).toInt()
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
 * (plus the one still on screen), instead of the whole animation in RAM. Stops when the card leaves composition.
 */
@Composable
private fun AnimatedTempleBackground(modifier: Modifier = Modifier) {
    val frame by produceState<ImageBitmap?>(null) {
        val codec = Codec.makeFromData(Data.makeFromBytes(Res.readBytes(TEMPLE_ANIMATION)))
        val durations = codec.framesInfo.map { it.duration.toLong().coerceAtLeast(MIN_FRAME_MS) }
        val work = Bitmap().apply { allocPixels(codec.imageInfo) }
        var shown: Bitmap? = null
        var previous: Bitmap? = null
        try {
            var index = 0
            while (true) {
                // Frames are deltas on top of the previous one, which `work` still holds.
                codec.readPixels(work, index, if (index == 0) -1 else index - 1)
                val next = work.makeClone()
                value = next.asComposeImageBitmap()
                // The one before the current frame has been off screen for a whole frame duration: safe to free.
                previous?.close()
                previous = shown
                shown = next
                delay(durations[index])
                index = (index + 1) % codec.frameCount
            }
        } finally {
            work.close()
            codec.close()
        }
    }
    frame?.let { Image(it, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop) }
}

/** Years, months and days since the Temple's destruction, over the burning Temple. */
internal object TempleCountdownWidget : HomeWidget {
    override val id = "temple_countdown"
    override val title = Res.string.home_widget_name_temple
    override val defaultSpan = CellSpan(6, 3)
    override val minSpan = CellSpan(4, 3)
    override val maxSpan = CellSpan(12, 6)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) = TempleDestructionCountdownCard(modifier)
}

@Composable
private fun TempleDestructionCountdownCard(modifier: Modifier = Modifier) {
    val countdownData by produceState(initialValue = computeTempleCountdown()) {
        while (true) {
            delay(millisUntilNextJerusalemSunset(System.currentTimeMillis()))
            value = computeTempleCountdown()
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
                    modifier = Modifier.padding(start = 8.dp),
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
