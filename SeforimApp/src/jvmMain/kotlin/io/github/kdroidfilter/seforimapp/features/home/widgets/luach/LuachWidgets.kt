package io.github.kdroidfilter.seforimapp.features.home.widgets.luach

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.FitColumn
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.HoverBox
import io.github.kdroidfilter.seforimapp.features.home.widgets.PanelCard
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetCardLoading
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetMenuItem
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberOffMain
import io.github.kdroidfilter.seforimapp.features.home.widgets.shownDateHere
import io.github.kdroidfilter.seforimapp.features.siddur.installedSiddur
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widget_name_events
import seforimapp.seforimapp.generated.resources.home_widget_name_limud
import seforimapp.seforimapp.generated.resources.home_widget_name_molad
import seforimapp.seforimapp.generated.resources.home_widget_name_next_zman
import seforimapp.seforimapp.generated.resources.home_widget_name_tefila
import seforimapp.seforimapp.generated.resources.home_widgets_options
import seforimapp.seforimapp.generated.resources.siddur_open_siddur
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import java.util.UUID

// The luach widgets, after the KosherKotlin demo's luach: text on the calendar's panel, following the Home's day.
// They keep the app's text sizes (only the next zman's clock grows), and their lists show as many whole rows as fit.

// Every line the same height, and the title: the card's height is worked out from their number (heightAt)
private val LIMUD_ROW_HEIGHT = 26.dp
private val LIMUD_ROW_GAP = 2.dp
private val LIMUD_TITLE_HEIGHT = 22.dp
private val LIMUD_PADDING = 8.dp
private val LIMUD_COLUMN_GAP = 12.dp

/** From this width the lines go two by two, the card half as tall. */
private val LIMUD_TWO_COLUMNS_WIDTH = 330.dp

internal fun limudColumnsAt(width: Dp) = if (width >= LIMUD_TWO_COLUMNS_WIDTH) 2 else 1

/** The day's limudim the user picked in its options (see [Limud]); clicking one opens it in a new tab. */
internal object LimudWidget : HomeWidget {
    override val id = "limud"
    override val title = Res.string.home_widget_name_limud
    override val defaultSpan = CellSpan(4, 4)
    override val minSpan = CellSpan(4, 2)

    // Its height is the one its lines need (heightAt), no more: only its width is the user's, to two columns
    override val maxSpan = CellSpan(10, 2)

    // The limudim shown, as saved: read by the grid for the card's height as well as by the card
    private var shown by mutableStateOf(Limud.defaults.toSet())

    override fun applyOptions(options: String?) {
        shown = Limud.decode(options)
    }

    /** Tall enough for every limud picked, never one hidden for want of room. */
    override fun heightAt(width: Dp): Dp {
        val columns = limudColumnsAt(width)
        val rows = (shown.size + columns - 1) / columns
        return LIMUD_PADDING * 2 + LIMUD_TITLE_HEIGHT + LIMUD_ROW_HEIGHT * rows + LIMUD_ROW_GAP * (rows - 1).coerceAtLeast(0)
    }

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) = LimudPanel(state, shown, rememberOpenInLibrary(state), modifier)

    // Its limudim are many: picked in a page of their own rather than in the menu
    @Composable
    override fun menuItems(state: HomeWidgetsState) =
        listOf(
            WidgetMenuItem(stringResource(Res.string.home_widgets_options), icon = AllIconsKeys.General.Settings) {
                state.optionsOpen = this
            },
        )

    @Composable
    override fun Options(state: HomeWidgetsState) = LimudOptions(state)
}

/** A box for each limud, by group, with the day's reading beside it; each tick shows or hides it at once. */
@Composable
private fun LimudOptions(state: HomeWidgetsState) {
    val shown = Limud.decode(state.optionsOf(LimudWidget))
    val readings =
        remember(state.shownDate, state.inIsrael) {
            Limud.entries.associateWith { limudOfDay(state.shownDate, state.inIsrael, setOf(it)).firstOrNull()?.value }
        }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (group in LimudGroup.entries) {
            Text(
                group.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
            )
            for (limud in Limud.entries.filter { it.group == group }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CheckboxRow(
                        text = limud.title,
                        checked = limud in shown,
                        onCheckedChange = { on -> state.setOptions(LimudWidget, Limud.encode(if (on) shown + limud else shown - limud)) },
                        modifier = Modifier.weight(1f).testTag("limud-option-${limud.id}"),
                    )
                    readings[limud]?.let {
                        Text(
                            it,
                            fontSize = 12.sp,
                            color = JewelTheme.globalColors.text.info,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** The limud lines of [shown], opening a place with [open]: as many as fit the card. */
@Composable
private fun LimudPanel(
    state: HomeWidgetsState,
    shown: Set<Limud>,
    open: (LibraryPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val day = state.shownDateHere()
    val inIsrael = state.inIsrael
    val items = rememberOffMain(day, inIsrael, shown) { limudOfDay(day, inIsrael, shown) } ?: return WidgetCardLoading(modifier)
    val accent = rememberAccentColor(JewelTheme.isDark)
    PanelCard(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = limudColumnsAt(maxWidth)
            Column(Modifier.fillMaxSize().padding(LIMUD_PADDING)) {
                Row(Modifier.fillMaxWidth().height(LIMUD_TITLE_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "לימוד יומי",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                    // Its options at hand, as in its menu
                    IconButton(
                        onClick = { state.optionsOpen = LimudWidget },
                        modifier = Modifier.size(LIMUD_TITLE_HEIGHT).testTag("limud-settings"),
                    ) {
                        Icon(
                            key = AllIconsKeys.General.Settings,
                            contentDescription = stringResource(Res.string.home_widgets_options),
                            tint = JewelTheme.globalColors.text.info,
                        )
                    }
                }
                // Two columns: the lines two by two, so both columns' lines stay level
                FitColumn(Modifier.fillMaxWidth().weight(1f), spacing = LIMUD_ROW_GAP, spread = true) {
                    items.chunked(columns).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(LIMUD_COLUMN_GAP)) {
                            repeat(columns) { i ->
                                Box(Modifier.weight(1f)) { pair.getOrNull(i)?.let { LimudRow(it, accent, open, chevron = columns == 1) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LimudRow(
    item: LimudItem,
    accent: Color,
    open: (LibraryPlace) -> Unit,
    // Left out on two columns, for the text's room: the hover still tells it opens
    chevron: Boolean = true,
) {
    val place = item.place
    HoverBox(onClick = place?.let { { open(it) } }, modifier = Modifier.fillMaxWidth().height(LIMUD_ROW_HEIGHT)) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The kind of limud, as a tag
            Box(
                Modifier
                    .width(54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent.copy(alpha = 0.16f))
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.kicker, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent, maxLines = 1)
            }
            Text(
                item.value,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (place != null && chevron) {
                // Towards the end of the reading direction: "open"
                val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                Icon(
                    key = if (rtl) AllIconsKeys.General.ChevronLeft else AllIconsKeys.General.ChevronRight,
                    contentDescription = null,
                    tint = JewelTheme.globalColors.text.info,
                )
            }
        }
    }
}

/** The zmanim coming next, counting down; clicking one points the Earth, the sky and the solar system at it. */
internal object NextZmanWidget : HomeWidget {
    override val id = "next_zman"
    override val title = Res.string.home_widget_name_next_zman
    override val defaultSpan = CellSpan(5, 2)
    override val minSpan = CellSpan(4, 2)

    // Wider, its name and its clock would stand apart across the card
    override val maxSpan = CellSpan(6, 6)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val now = rememberMinute()
        val location = state.location
        val zmanim =
            remember(now, location, state.zmanimOpinion, state.inIsrael) {
                nextZmanim(now, location, state.zmanimOpinion, state.inIsrael, count = 20)
            }
        val clock = rememberClock(location.timeZone)
        val accent = rememberAccentColor(JewelTheme.isDark)
        PanelCard(modifier) {
            val next = zmanim.firstOrNull() ?: return@PanelCard
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp)) {
                // The coming zman takes up to half the card, the ones after it the rest
                val heroHeight = (maxHeight * 0.5f).coerceIn(44.dp, 96.dp)
                // A narrow card leaves the name its room
                val clockSize = if (maxWidth < 220.dp) 28.sp else 44.sp
                Column(Modifier.fillMaxSize()) {
                    HoverBox(onClick = { state.targetTime = next.time }, modifier = Modifier.fillMaxWidth().height(heroHeight)) {
                        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Kicker("הזמן הבא · ${countdown(now, next.time)}")
                            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    next.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                FitText(clock(next.time), accent, max = clockSize, min = 18.sp)
                            }
                        }
                    }
                    FitColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 2.dp)) {
                        zmanim.drop(1).forEach { zman ->
                            HoverBox(onClick = { state.targetTime = zman.time }, modifier = Modifier.fillMaxWidth()) {
                                TimeRow(
                                    zman.name,
                                    clock(zman.time),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun countdown(
    now: Date,
    target: Date,
): String {
    val minutes = ((target.time - now.time + 59_999) / 60_000).toInt()
    return if (minutes < 60) "בעוד $minutes דק׳" else "בעוד ${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')} שע׳"
}

/** The coming Shabbatot, festivals and fasts with their times; clicking one moves every widget to it. */
internal object UpcomingEventsWidget : HomeWidget {
    override val id = "upcoming_events"
    override val title = Res.string.home_widget_name_events
    override val defaultSpan = CellSpan(6, 4)
    override val minSpan = CellSpan(5, 3)

    // Wider, the names and their times would stand apart across the card
    override val maxSpan = CellSpan(7, 8)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val location = state.location
        val day = state.shownDateHere()
        val opinion = state.zmanimOpinion
        val cityLabel = state.cityLabel
        val inIsrael = state.inIsrael
        val events =
            rememberOffMain(day, location, opinion, cityLabel, inIsrael) {
                upcomingEvents(day, location, opinion, cityLabel, inIsrael, limit = 14)
            } ?: return WidgetCardLoading(modifier)
        val clock = rememberClock(location.timeZone)
        PanelCard(modifier) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                Text(
                    "אירועים קרובים",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp).height(22.dp),
                )
                FitColumn(Modifier.fillMaxWidth().weight(1f)) {
                    events.forEach { event ->
                        HoverBox(onClick = { state.selectDate(event.date) }, modifier = Modifier.fillMaxWidth()) {
                            EventRow(event, clock)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(
    event: LuachEvent,
    clock: (Date) -> String,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(event.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${event.date.hebrewWeekday()} · ${event.hebrewDate} · ⁦${event.date.dayOfMonth}.${event.date.monthValue}⁩",
                fontSize = 11.sp,
                color = JewelTheme.globalColors.text.info,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            event.times.forEach { (label, time) ->
                time?.let { Text("$label ${clock(it)}", fontSize = 11.sp, color = JewelTheme.globalColors.text.info, maxLines = 1) }
            }
        }
    }
}

/** What changes in the day's tefila: the season's words, tachanun, hallel and the day's additions. */
internal object TefilaWidget : HomeWidget {
    override val id = "tefila"
    override val title = Res.string.home_widget_name_tefila
    override val defaultSpan = CellSpan(5, 3)
    override val minSpan = CellSpan(5, 3)
    override val toolWindowSize = DpSize(380.dp, 320.dp)
    override val toolSymbol = "text.book.closed"

    // Seven short lines at most: more room would only spread them apart
    override val maxSpan = CellSpan(6, 3)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        // ponytail: the civil day's tefila; tonight's maariv already belongs to tomorrow's
        val day = state.shownDateHere()
        val lines = remember(day, state.inIsrael) { tefilaOfDay(day, state.inIsrael) }
        val hebrewDate =
            remember(day, state.inIsrael) {
                hebrewFormatter.format(JewishCalendar(day.toKotlinLocalDate(), state.inIsrael))
            }
        val accent = rememberAccentColor(JewelTheme.isDark)
        PanelCard(modifier) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("תפילת היום", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(hebrewDate, fontSize = 11.sp, color = JewelTheme.globalColors.text.info, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    if (installedSiddur != null) {
                        Link(
                            stringResource(Res.string.siddur_open_siddur),
                            onClick = { state.openTab(TabsDestination.Siddur(tabId = UUID.randomUUID().toString())) },
                        )
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    // The lines share the card's height, but never further apart than reads as one list
                    val gap = ((maxHeight - LINE_HEIGHT * lines.size) / lines.size).coerceIn(5.dp, 10.dp)
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically)) {
                        lines.forEach { LabeledRow(it, accent) }
                    }
                }
            }
        }
    }
}

private val LINE_HEIGHT = 17.dp

@Composable
private fun LabeledRow(
    line: TefilaLine,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(line.label, fontSize = 12.sp, color = JewelTheme.globalColors.text.info, maxLines = 1, modifier = Modifier.width(68.dp))
        Text(
            line.value,
            fontSize = 13.sp,
            fontWeight = if (line.special) FontWeight.SemiBold else FontWeight.Normal,
            color = if (line.special) accent else JewelTheme.globalColors.text.normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The coming month's molad, and when Kiddush Levana can be said. */
internal object MoladWidget : HomeWidget {
    override val id = "molad"
    override val title = Res.string.home_widget_name_molad

    // A title and three short lines: the size they fill, no more
    override val defaultSpan = CellSpan(5, 2)
    override val minSpan = CellSpan(5, 2)
    override val toolWindowSize = DpSize(280.dp, 200.dp)
    override val toolSymbol = "moon.stars"
    override val maxSpan = CellSpan(5, 2)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val now = rememberMinute()
        val day = state.shownDateHere()
        val inIsrael = state.inIsrael
        val earliest = state.kiddushLevanaEarliest
        val latest = state.kiddushLevanaLatest
        val molad = rememberOffMain(now, day, inIsrael, earliest, latest) { moladInfo(now, day, inIsrael, earliest, latest) }
        if (molad == null) WidgetCardLoading(modifier) else MoladPanel(molad, now, state, modifier)
    }
}

@Composable
private fun MoladPanel(
    molad: MoladInfo,
    now: Date,
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
) {
    val zone = state.location.timeZone
    val moment = remember(zone) { SimpleDateFormat("d.M · HH:mm").apply { timeZone = zone } }
    val accent = rememberAccentColor(JewelTheme.isDark)
    // Kiddush Levana can be said now: its dates in the accent
    val open = now.after(molad.kiddushLevanaStart)
    PanelCard(modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("מולד חודש ${molad.month}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
            ) {
                LabeledRow(
                    TefilaLine("מולד", "${molad.weekday} · ${molad.time.ltr()} · ${molad.chalakim} ח׳", special = true),
                    accent,
                )
                LabeledRow(TefilaLine("קידוש לבנה", "מ־ ${moment.format(molad.kiddushLevanaStart).ltr()}", special = open), accent)
                LabeledRow(TefilaLine("", "עד ${moment.format(molad.kiddushLevanaEnd).ltr()}", special = open), accent)
            }
        }
    }
}

@Composable
private fun Kicker(text: String) {
    Text(text, fontSize = 11.sp, color = JewelTheme.globalColors.text.info, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** One line of text, as large as fits between [min] and [max]. */
@Composable
private fun FitText(
    text: String,
    color: Color,
    max: TextUnit,
    min: TextUnit,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = JewelTheme.defaultTextStyle.copy(color = color, fontWeight = weight),
        maxLines = 1,
        softWrap = false,
        // Ellipsis would hide the overflow from the autosizer: it would keep the largest size and cut the text
        overflow = TextOverflow.Clip,
        autoSize = TextAutoSize.StepBased(minFontSize = min, maxFontSize = max, stepSize = 1.sp),
    )
}

@Composable
private fun TimeRow(
    label: String,
    time: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            fontSize = fontSize,
            color = JewelTheme.globalColors.text.info,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(time, fontSize = fontSize, maxLines = 1)
    }
}

private fun String.ltr() = "⁦$this⁩"

/** HH:mm in [zone], kept left-to-right in the Hebrew text. */
@Composable
private fun rememberClock(zone: TimeZone): (Date) -> String {
    val format = remember(zone) { SimpleDateFormat("HH:mm").apply { timeZone = zone } }
    return remember(format) { { format.format(it).ltr() } }
}

/** Now, updated on every new minute. */
@Composable
private fun rememberMinute(): Date {
    val now by produceState(Date()) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = Date()
        }
    }
    return now
}

/**
 * Opens a [LibraryPlace] in a new tab of the Home's window, in [shnayimMikra] if asked; does nothing if the library
 * doesn't have the book.
 */
@Composable
internal fun rememberOpenInLibrary(
    state: HomeWidgetsState,
    shnayimMikra: Boolean = false,
): (LibraryPlace) -> Unit = rememberOpenInLibrary(openTab = { state.openTab(it) }, shnayimMikra = shnayimMikra)

/** Opens a [LibraryPlace] with [openTab]; does nothing if the library doesn't have the book. */
@Composable
internal fun rememberOpenInLibrary(
    openTab: (TabsDestination) -> Unit,
    shnayimMikra: Boolean = false,
): (LibraryPlace) -> Unit {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val currentOpenTab by rememberUpdatedState(openTab)
    return remember(graph, scope, shnayimMikra) {
        { place ->
            scope.launch {
                // Read on click: the books DB is opened when first needed, never by showing the card
                val repository = graph.repository
                val book = repository.getBookByTitle(place.bookTitle) ?: return@launch
                val (lineId, endLineId) = repository.linesOf(book, place)
                currentOpenTab(
                    TabsDestination.BookContent(
                        bookId = book.id,
                        tabId = UUID.randomUUID().toString(),
                        lineId = lineId,
                        endLineId = endLineId,
                        shnayimMikra = shnayimMikra,
                    ),
                )
            }
        }
    }
}

/** The lines [place] starts and ends on in [book]: its end only when it says where it is, for the book to mark it. */
private suspend fun SeforimRepository.linesOf(
    book: Book,
    place: LibraryPlace,
): Pair<Long?, Long?> {
    place.parashaIndex?.let { index ->
        val parshiyos =
            getAltTocStructuresForBook(book.id)
                .firstOrNull { it.key == "Parasha" }
                ?.let { getAltRootToc(it.id).sortedBy { entry -> entry.id } }
                ?: return null to null
        // To the line before the next parsha's, or the book's end
        val next = parshiyos.getOrNull(index + place.parashaCount)?.lineId?.let { getLine(it)?.lineIndex }
        return parshiyos.getOrNull(index)?.lineId to lineIdAt(book, (next ?: book.totalLines) - 1)
    }
    val entries = if (place.toc.isNotEmpty() || place.endTocs.isNotEmpty()) getTocEntriesForBook(book.id) else emptyList()
    val tocEntry = entries.atPath(place.toc)
    val tocIndex = tocEntry?.lineId?.let { getLine(it)?.lineIndex }
    val start = place.ref?.let { lineOfRef(book, tocIndex ?: 0, it) }
    val startIndex = start?.lineIndex ?: tocIndex ?: 0
    val end =
        place.endRefs.firstNotNullOfOrNull { lastLineOfRef(book, startIndex, it) }
            ?: place.endTocs.firstNotNullOfOrNull { heading ->
                // A heading beside the first one, from it on
                entries
                    .firstOrNull { it.text == heading && it.parentId == tocEntry?.parentId && it.id >= (tocEntry?.id ?: 0) }
                    ?.let { entryEnd(book, entries, it) }
            }
    return (start?.id ?: tocEntry?.lineId) to end
}

/** The TOC entry at [path]: its first heading anywhere in the book, each next one right under it. */
private fun List<TocEntry>.atPath(path: List<String>): TocEntry? {
    var parent: TocEntry? = null
    for (heading in path) {
        parent = firstOrNull { it.text == heading && (parent == null || it.parentId == parent.id) } ?: return null
    }
    return parent
}

/** The last line of [entry]'s section: the one before the next heading not under it, or the book's last. */
private suspend fun SeforimRepository.entryEnd(
    book: Book,
    entries: List<TocEntry>,
    entry: TocEntry,
): Long? {
    val next = entries.firstOrNull { it.id > entry.id && it.level <= entry.level }?.lineId?.let { getLine(it)?.lineIndex }
    return lineIdAt(book, (next ?: book.totalLines) - 1)
}

private suspend fun SeforimRepository.lineIdAt(
    book: Book,
    index: Int,
) = getLineByIndex(book.id, index.coerceIn(0, book.totalLines - 1))?.id

private const val REF_SCAN_LINES = 500

private val SPACES = Regex("""\s+""")

/** Whether a line's reference is [ref] or one of its parts: "…א, ב" is "…א, ב, א" but not "…א, בב". */
private fun Line.isOf(ref: String): Boolean {
    val lineRef = heRef?.replace(SPACES, " ") ?: return false
    return lineRef == ref || lineRef.startsWith("$ref,")
}

/** The lines of [book] from [from] on, by chunks, until [visit] says to stop. */
private suspend fun SeforimRepository.scanLines(
    book: Book,
    from: Int,
    visit: (Line) -> Boolean,
) {
    var start = from
    while (start < book.totalLines) {
        val end = start + REF_SCAN_LINES - 1
        for (line in getLines(book.id, start, end)) if (!visit(line)) return
        start = end + 1
    }
}

/** The first line from [from] of [ref]; the library's references may space their parts twice. */
private suspend fun SeforimRepository.lineOfRef(
    book: Book,
    from: Int,
    ref: String,
): Line? {
    var found: Line? = null
    scanLines(book, from) { line -> (!line.isOf(ref)).also { if (!it) found = line } }
    return found
}

/** The last line of [ref] from [from]: its run ends at the first line of another reference (headings have none). */
private suspend fun SeforimRepository.lastLineOfRef(
    book: Book,
    from: Int,
    ref: String,
): Long? {
    var last: Line? = null
    scanLines(book, from) { line ->
        when {
            line.isOf(ref) -> {
                last = line
                true
            }
            else -> last == null || line.heRef.isNullOrBlank()
        }
    }
    return last?.id
}
