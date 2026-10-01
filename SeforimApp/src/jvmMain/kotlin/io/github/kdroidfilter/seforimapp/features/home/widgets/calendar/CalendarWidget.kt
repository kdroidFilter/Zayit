package io.github.kdroidfilter.seforimapp.features.home.widgets.calendar

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewDateFormatter
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.PanelCard
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_calendar_next_month
import seforimapp.seforimapp.generated.resources.home_calendar_previous_month
import seforimapp.seforimapp.generated.resources.home_widget_name_calendar
import java.time.LocalDate

/** The Hebrew month, as in the KosherKotlin demo's luach; clicking a day moves every widget to it. */
internal object CalendarWidget : HomeWidget {
    override val id = "calendar"
    override val title = Res.string.home_widget_name_calendar
    override val defaultSpan = CellSpan(7, 5)
    override val minSpan = CellSpan(7, 5)
    override val maxSpan = CellSpan(14, 8)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val selected = state.selectedDate
        val today = remember(selected, state.location) { LocalDate.now(state.location.timeZone.toZoneId()) }
        // Any day of the month on show; follows the date picked elsewhere (a zman card, the Earth orbit)
        var shown by remember(selected) { mutableStateOf(selected) }
        val month = remember(shown, state.inIsrael) { calendarMonth(shown, state.inIsrael) }
        val accent = rememberAccentColor(JewelTheme.isDark)

        PanelCard(modifier) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MonthHeader(
                    month = month,
                    onPrevious = { shown = month.first.minusDays(1) },
                    onNext = { shown = month.last.plusDays(1) },
                )
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    // Holiday names only where a cell can hold a word
                    val showTags = maxWidth / 7 >= 64.dp
                    // Too narrow for both dates side by side: the Gregorian one goes under the Hebrew one
                    val stacked = maxWidth / 7 < 56.dp
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth()) {
                            WEEKDAY_INITIALS.forEach { name ->
                                Text(
                                    text = name,
                                    fontSize = 11.sp,
                                    color = JewelTheme.globalColors.text.info,
                                    modifier = Modifier.weight(1f).padding(bottom = 4.dp),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        month.weeks.forEach { week ->
                            Row(Modifier.fillMaxWidth().weight(1f)) {
                                week.forEach { day ->
                                    DayCell(
                                        day = day,
                                        isToday = day?.date == today,
                                        isSelected = day?.date == selected,
                                        showTag = showTags,
                                        stacked = stacked,
                                        accent = accent,
                                        onClick = { day?.let { state.selectDate(it.date) } },
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val WEEKDAY_INITIALS = listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳")

@Immutable
internal data class CalendarDay(
    val date: LocalDate,
    val hebrewDay: String,
    val gregorianDay: Int,
    /** The festival or Rosh Chodesh of the day, blank on an ordinary one. */
    val tag: String,
    val isShabbat: Boolean,
)

@Immutable
internal data class CalendarMonth(
    val title: String,
    val first: LocalDate,
    val last: LocalDate,
    /** Sunday-first weeks; null pads the days of the neighbouring months. */
    val weeks: List<List<CalendarDay?>>,
)

private val hebrewFormatter =
    HebrewDateFormatter().apply {
        isHebrewFormat = true
        isUseGershGershayim = true
    }

/** The Hebrew month that [date] falls in. */
internal fun calendarMonth(
    date: LocalDate,
    inIsrael: Boolean,
): CalendarMonth {
    val anchor =
        JewishCalendar(date.toKotlinLocalDate(), inIsrael).apply {
            setJewishDate(jewishYear, jewishMonth, 1)
        }
    val first = anchor.gregorianLocalDate
    val length = anchor.daysInJewishMonth
    val cells =
        buildList {
            repeat(first.dayOfWeek.isoDayNumber % 7) { add(null) } // Sunday is column 0
            for (offset in 0 until length) {
                val gregorian = first.plus(offset, DateTimeUnit.DAY)
                val day = JewishCalendar(gregorian, inIsrael)
                add(
                    CalendarDay(
                        date = gregorian.toJavaLocalDate(),
                        hebrewDay = hebrewFormatter.formatHebrewNumber(day.jewishDayOfMonth),
                        gregorianDay = gregorian.day,
                        tag = day.tag(),
                        isShabbat = gregorian.dayOfWeek == DayOfWeek.SATURDAY,
                    ),
                )
            }
            while (size % 7 != 0) add(null)
        }
    return CalendarMonth(
        title = "${hebrewFormatter.formatMonth(anchor)} ${hebrewFormatter.formatHebrewNumber(anchor.jewishYear)}",
        first = first.toJavaLocalDate(),
        last = first.plus(length - 1, DateTimeUnit.DAY).toJavaLocalDate(),
        weeks = cells.chunked(7),
    )
}

private fun JewishCalendar.tag(): String {
    hebrewFormatter.formatYomTov(this).takeIf { it.isNotBlank() }?.let { return it }
    if (isRoshChodesh) return hebrewFormatter.formatRoshChodesh(this).ifBlank { "ראש חודש" }
    return ""
}

@Composable
private fun MonthHeader(
    month: CalendarMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    // The earlier month sits at the start of the reading direction; Jewel icons don't mirror themselves
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val towardsStart = if (rtl) AllIconsKeys.General.ChevronRight else AllIconsKeys.General.ChevronLeft
    val towardsEnd = if (rtl) AllIconsKeys.General.ChevronLeft else AllIconsKeys.General.ChevronRight
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepButton(towardsStart, stringResource(Res.string.home_calendar_previous_month), onPrevious)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(month.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                text =
                    "⁦${month.first.dayOfMonth}.${month.first.monthValue} – " +
                        "${month.last.dayOfMonth}.${month.last.monthValue}.${month.last.year}⁩",
                fontSize = 11.sp,
                color = JewelTheme.globalColors.text.info,
            )
        }
        StepButton(towardsEnd, stringResource(Res.string.home_calendar_next_month), onNext)
    }
}

@Composable
private fun StepButton(
    icon: IconKey,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) {
        Icon(key = icon, contentDescription = description)
    }
}

@Composable
private fun DayCell(
    day: CalendarDay?,
    isToday: Boolean,
    isSelected: Boolean,
    showTag: Boolean,
    stacked: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val textColor = JewelTheme.globalColors.text.normal
    val background =
        when {
            day == null -> Color.Transparent
            isToday -> accent.copy(alpha = 0.22f)
            hovered -> textColor.copy(alpha = 0.08f)
            day.isShabbat -> textColor.copy(alpha = 0.05f)
            else -> Color.Transparent
        }
    Box(
        modifier
            .padding(1.dp)
            .clip(shape)
            .background(background)
            .then(if (isSelected) Modifier.border(1.5.dp, accent, shape) else Modifier)
            .then(
                if (day != null) {
                    Modifier.hoverable(hover).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        if (day == null) return@Box
        Column(
            Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalAlignment = if (stacked) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            val hebrew = @Composable { rowModifier: Modifier ->
                Text(
                    text = day.hebrewDay,
                    fontSize = 14.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (isToday) accent else textColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = rowModifier,
                )
            }
            val gregorian = @Composable {
                Text(
                    text = day.gregorianDay.toString(),
                    fontSize = 9.sp,
                    color = JewelTheme.globalColors.text.info,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            if (stacked) {
                hebrew(Modifier)
                gregorian()
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    hebrew(Modifier.weight(1f))
                    gregorian()
                }
            }
            if (showTag && day.tag.isNotBlank()) {
                Text(
                    text = day.tag,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
