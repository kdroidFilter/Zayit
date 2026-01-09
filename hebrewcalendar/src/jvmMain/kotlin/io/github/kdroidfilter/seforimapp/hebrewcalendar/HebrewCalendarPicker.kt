package io.github.kdroidfilter.seforimapp.hebrewcalendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.LocalMenuController
import org.jetbrains.jewel.ui.component.OutlinedSplitButton
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.theme.menuStyle
import org.jetbrains.jewel.ui.theme.segmentedControlButtonStyle
import seforimapp.hebrewcalendar.generated.resources.Res
import seforimapp.hebrewcalendar.generated.resources.hebrewcalendar_mode_gregorian
import seforimapp.hebrewcalendar.generated.resources.hebrewcalendar_mode_hebrew
import seforimapp.hebrewcalendar.generated.resources.hebrewcalendar_next_month
import seforimapp.hebrewcalendar.generated.resources.hebrewcalendar_prev_month
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val CALENDAR_MENU_WIDTH = 320.dp
private val CALENDAR_GRID_SPACING = 4.dp

/**
 * A Hebrew/Gregorian calendar picker component.
 *
 * This composable displays a calendar with the ability to switch between
 * Hebrew and Gregorian calendar modes. It supports month navigation and
 * date selection.
 *
 * @param initialDate The initially selected date
 * @param onDateSelected Callback invoked when a date is selected
 * @param initialMode The initial calendar mode (Hebrew or Gregorian)
 * @param modifier Modifier for styling
 */
@Composable
fun HebrewCalendarPicker(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    initialMode: CalendarMode = CalendarMode.HEBREW,
    modifier: Modifier = Modifier,
) {
    var calendarMode by remember { mutableStateOf(initialMode) }
    var displayedMonth by remember(initialDate) { mutableStateOf(YearMonth.from(initialDate)) }
    var displayedHebrewMonth by remember(initialDate) {
        mutableStateOf(hebrewYearMonthFromLocalDate(initialDate))
    }
    var selectedDate by remember(initialDate) { mutableStateOf(initialDate) }

    val hebrewDateFormatter = remember {
        HebrewDateFormatter().apply {
            setHebrewFormat(true)
            setUseGershGershayim(false)
        }
    }
    val hebrewLocale = remember { Locale.forLanguageTag("he") }
    val monthTitleFormatter = remember {
        DateTimeFormatter.ofPattern("LLLL yyyy", hebrewLocale)
    }
    val weekDays = remember {
        listOf(
            DayOfWeek.SUNDAY,
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
        )
    }

    val density = LocalDensity.current
    val cellSize = remember(density) {
        with(density) {
            val widthPx = CALENDAR_MENU_WIDTH.toPx()
            val spacingPx = CALENDAR_GRID_SPACING.toPx()
            val minCellPx = 28.dp.toPx()
            val cellPx = ((widthPx - (spacingPx * 6f)) / 7f).coerceAtLeast(minCellPx)
            cellPx.toDp()
        }
    }

    Column(
        modifier = modifier.width(CALENDAR_MENU_WIDTH),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SegmentedControl(
            buttons = listOf(
                SegmentedControlButtonData(
                    selected = calendarMode == CalendarMode.HEBREW,
                    content = { Text(text = stringResource(Res.string.hebrewcalendar_mode_hebrew)) },
                    onSelect = {
                        calendarMode = CalendarMode.HEBREW
                        displayedHebrewMonth = hebrewYearMonthFromLocalDate(selectedDate)
                    },
                ),
                SegmentedControlButtonData(
                    selected = calendarMode == CalendarMode.GREGORIAN,
                    content = { Text(text = stringResource(Res.string.hebrewcalendar_mode_gregorian)) },
                    onSelect = {
                        calendarMode = CalendarMode.GREGORIAN
                        displayedMonth = YearMonth.from(selectedDate)
                    },
                ),
            ),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = {
                    when (calendarMode) {
                        CalendarMode.GREGORIAN -> displayedMonth = displayedMonth.minusMonths(1)
                        CalendarMode.HEBREW -> displayedHebrewMonth = previousHebrewYearMonth(displayedHebrewMonth)
                    }
                },
            ) {
                Icon(
                    key = AllIconsKeys.General.ChevronRight,
                    contentDescription = stringResource(Res.string.hebrewcalendar_prev_month),
                )
            }
            val monthShape = RoundedCornerShape(8.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(JewelTheme.globalColors.toolwindowBackground, monthShape)
                    .border(1.dp, JewelTheme.globalColors.borders.disabled, monthShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when (calendarMode) {
                        CalendarMode.GREGORIAN -> displayedMonth.format(monthTitleFormatter)
                        CalendarMode.HEBREW -> formatHebrewMonthTitle(displayedHebrewMonth, hebrewDateFormatter)
                    },
                    textAlign = TextAlign.Center,
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
            }
            IconButton(
                onClick = {
                    when (calendarMode) {
                        CalendarMode.GREGORIAN -> displayedMonth = displayedMonth.plusMonths(1)
                        CalendarMode.HEBREW -> displayedHebrewMonth = nextHebrewYearMonth(displayedHebrewMonth)
                    }
                },
            ) {
                Icon(
                    key = AllIconsKeys.General.ChevronLeft,
                    contentDescription = stringResource(Res.string.hebrewcalendar_next_month),
                )
            }
        }

        val weekHeaderShape = RoundedCornerShape(8.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JewelTheme.globalColors.toolwindowBackground, weekHeaderShape)
                    .border(1.dp, JewelTheme.globalColors.borders.disabled, weekHeaderShape)
                    .padding(vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CALENDAR_GRID_SPACING),
                ) {
                    for (dayOfWeek in weekDays) {
                        Text(
                            text = dayOfWeek.getDisplayName(TextStyle.NARROW, hebrewLocale),
                            modifier = Modifier.width(cellSize),
                            textAlign = TextAlign.Center,
                            style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }

            val onSelectDate: (LocalDate) -> Unit = { date ->
                selectedDate = date
                onDateSelected(date)
            }

            when (calendarMode) {
                CalendarMode.GREGORIAN -> MonthGrid(
                    displayedMonth = displayedMonth,
                    selectedDate = selectedDate,
                    onDateSelected = onSelectDate,
                    cellSize = cellSize,
                    spacing = CALENDAR_GRID_SPACING,
                )
                CalendarMode.HEBREW -> HebrewMonthGrid(
                    displayedMonth = displayedHebrewMonth,
                    selectedDate = selectedDate,
                    onDateSelected = onSelectDate,
                    formatter = hebrewDateFormatter,
                    cellSize = cellSize,
                    spacing = CALENDAR_GRID_SPACING,
                )
            }
        }
    }
}

/**
 * A split button that displays a date label and opens a calendar picker popup.
 *
 * @param label The label to display on the button
 * @param selectedDate The currently selected date
 * @param onDateSelected Callback invoked when a date is selected
 * @param initialMode The initial calendar mode when popup opens
 * @param menuStyle The menu styling used for the popup content
 * @param modifier Modifier for styling
 */
@Composable
fun DateSelectionSplitButton(
    label: String,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    initialMode: CalendarMode = CalendarMode.HEBREW,
    menuStyle: MenuStyle = JewelTheme.menuStyle,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        OutlinedSplitButton(
            onClick = {},
            secondaryOnClick = {},
            popupModifier = Modifier.width(CALENDAR_MENU_WIDTH),
            maxPopupWidth = CALENDAR_MENU_WIDTH,
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
                    // Access menu controller inside menu content where it's provided
                    val menuController = LocalMenuController.current
                    val inputModeManager = LocalInputModeManager.current
                    IntUiTheme(isDark = menuStyle.isDark) {
                        HebrewCalendarPicker(
                            initialDate = selectedDate,
                            onDateSelected = { date ->
                                onDateSelected(date)
                                menuController.closeAll(inputModeManager.inputMode, true)
                            },
                            initialMode = initialMode,
                        )
                    }
                }
            },
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun MonthGrid(
    displayedMonth: YearMonth,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    cellSize: Dp,
    spacing: Dp,
) {
    val weeks = remember(displayedMonth) { buildMonthGrid(displayedMonth) }
    var hoveredDate by remember(displayedMonth) { mutableStateOf<LocalDate?>(null) }
    val cellShape = RoundedCornerShape(6.dp)
    val selectedBg = JewelTheme.segmentedControlButtonStyle.colors.backgroundSelected
    val hoverBg = JewelTheme.segmentedControlButtonStyle.colors.backgroundHovered
    val selectedTextColor = JewelTheme.globalColors.text.selected
    val normalTextColor = JewelTheme.globalColors.text.normal

    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        for (week in weeks) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                for (day in week) {
                    if (day == null) {
                        Spacer(modifier = Modifier.size(cellSize))
                        continue
                    }

                    val isSelected = day == selectedDate
                    val isHovered = hoveredDate == day
                    val bgBrush: Brush = when {
                        isSelected -> selectedBg
                        isHovered -> hoverBg
                        else -> SolidColor(Color.Transparent)
                    }
                    val textColor = if (isSelected) selectedTextColor else normalTextColor
                    val border = when {
                        isSelected -> JewelTheme.globalColors.borders.focused
                        isHovered -> JewelTheme.globalColors.borders.normal
                        else -> Color.Transparent
                    }

                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .border(1.dp, border, cellShape)
                            .background(bgBrush, cellShape)
                            .onPointerEvent(PointerEventType.Enter) { hoveredDate = day }
                            .onPointerEvent(PointerEventType.Exit) { if (hoveredDate == day) hoveredDate = null }
                            .clickable { onDateSelected(day) }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = day.dayOfMonth.toString(), color = textColor)
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun HebrewMonthGrid(
    displayedMonth: HebrewYearMonth,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    formatter: HebrewDateFormatter,
    cellSize: Dp,
    spacing: Dp,
) {
    val weeks = remember(displayedMonth) { buildHebrewMonthGrid(displayedMonth, formatter) }
    var hoveredDate by remember(displayedMonth) { mutableStateOf<LocalDate?>(null) }
    val cellShape = RoundedCornerShape(6.dp)
    val selectedBg = JewelTheme.segmentedControlButtonStyle.colors.backgroundSelected
    val hoverBg = JewelTheme.segmentedControlButtonStyle.colors.backgroundHovered
    val selectedTextColor = JewelTheme.globalColors.text.selected
    val normalTextColor = JewelTheme.globalColors.text.normal

    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        for (week in weeks) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                for (day in week) {
                    if (day == null) {
                        Spacer(modifier = Modifier.size(cellSize))
                        continue
                    }

                    val isSelected = day.localDate == selectedDate
                    val isHovered = hoveredDate == day.localDate
                    val bgBrush: Brush = when {
                        isSelected -> selectedBg
                        isHovered -> hoverBg
                        else -> SolidColor(Color.Transparent)
                    }
                    val textColor = if (isSelected) selectedTextColor else normalTextColor
                    val border = when {
                        isSelected -> JewelTheme.globalColors.borders.focused
                        isHovered -> JewelTheme.globalColors.borders.normal
                        else -> Color.Transparent
                    }

                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .border(1.dp, border, cellShape)
                            .background(bgBrush, cellShape)
                            .onPointerEvent(PointerEventType.Enter) { hoveredDate = day.localDate }
                            .onPointerEvent(PointerEventType.Exit) { if (hoveredDate == day.localDate) hoveredDate = null }
                            .clickable { onDateSelected(day.localDate) }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = day.label, color = textColor)
                    }
                }
            }
        }
    }
}
