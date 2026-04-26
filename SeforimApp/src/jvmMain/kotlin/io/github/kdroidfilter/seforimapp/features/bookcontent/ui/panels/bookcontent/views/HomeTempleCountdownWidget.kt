package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_temple_days
import seforimapp.seforimapp.generated.resources.home_temple_months
import seforimapp.seforimapp.generated.resources.home_temple_subtitle
import seforimapp.seforimapp.generated.resources.home_temple_title
import seforimapp.seforimapp.generated.resources.home_temple_years
import seforimapp.seforimapp.generated.resources.temple_jerusalem_in_fire_medium
import java.util.Calendar
import java.util.TimeZone

@Immutable
private data class TempleCountdownData(
    val years: Int,
    val months: Int,
    val days: Int,
)

private const val DESTRUCTION_YEAR = 3830
private const val DESTRUCTION_DAY = 9
private val DESTRUCTION_MONTH = JewishCalendar.AV
private const val REFRESH_INTERVAL_MS = 60_000L

private val JERUSALEM =
    GeoLocation(
        "Jerusalem",
        31.7683,
        35.2137,
        800.0,
        TimeZone.getTimeZone("Asia/Jerusalem"),
    )

private fun computeTempleCountdown(): TempleCountdownData {
    // After sunset in Jerusalem the Hebrew day rolls over — advance the Gregorian date by one day
    // so JewishCalendar resolves to the new Hebrew day instead of yesterday's.
    val now = Calendar.getInstance()
    val sunset = ComplexZmanimCalendar(JERUSALEM).sunset
    if (sunset != null && sunset.time <= now.timeInMillis) {
        now.add(Calendar.DAY_OF_MONTH, 1)
    }
    val raw = JewishCalendar(now.time)
    val today = JewishCalendar(raw.jewishYear, raw.jewishMonth, raw.jewishDayOfMonth)

    // Most recent Av-9 anniversary that is strictly before today. Using `>=` (rather than `>`)
    // means the anniversary day itself is reported as "almost a full year" instead of "X years
    // and 0 days", keeping the display free of 0-day artifacts.
    var years = today.jewishYear - DESTRUCTION_YEAR
    var anniversary = JewishCalendar(today.jewishYear, DESTRUCTION_MONTH, DESTRUCTION_DAY)
    if (anniversary.absDate >= today.absDate) {
        years -= 1
        anniversary = JewishCalendar(today.jewishYear - 1, DESTRUCTION_MONTH, DESTRUCTION_DAY)
    }

    // Walk forward by full Hebrew months. Same `>=` rationale: stop just before reaching today.
    var months = 0
    var cursor = anniversary
    while (true) {
        val next = cursor.clone() as JewishCalendar
        next.forward(Calendar.MONTH, 1)
        if (next.absDate >= today.absDate) break
        cursor = next
        months += 1
    }

    val days = today.absDate - cursor.absDate
    return TempleCountdownData(years, months, days)
}

private val ACCENT_START = Color(0xFFFF6B35)
private val ACCENT_END = Color(0xFFFFAA70)

@Composable
fun TempleDestructionCountdownCard(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    // Always use dark border — the image background is always dark
    val borderColor = JewelTheme.globalColors.borders.disabled

    val countdownData by produceState(initialValue = computeTempleCountdown()) {
        while (true) {
            delay(REFRESH_INTERVAL_MS)
            value = computeTempleCountdown()
        }
    }

    val countdownItems =
        listOf(
            countdownData.years to stringResource(Res.string.home_temple_years),
            countdownData.months to stringResource(Res.string.home_temple_months),
            countdownData.days to stringResource(Res.string.home_temple_days),
        )

    Box(
        modifier =
            modifier
                .clip(shape)
                .border(1.5.dp, borderColor, shape),
    ) {
        // Background image
        Image(
            painter = painterResource(Res.drawable.temple_jerusalem_in_fire_medium),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
        )

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
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    countdownItems.forEach { (value, label) ->
                        CountdownUnit(
                            value = value,
                            label = label,
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value.toString(),
                fontSize = 22.sp,
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
