package io.github.kdroidfilter.seforimapp.features.home.widgets.luach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.PanelCard
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widget_name_events
import seforimapp.seforimapp.generated.resources.home_widget_name_limud
import seforimapp.seforimapp.generated.resources.home_widget_name_molad
import seforimapp.seforimapp.generated.resources.home_widget_name_next_zman
import seforimapp.seforimapp.generated.resources.home_widget_name_tefila
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import java.util.UUID

// The luach widgets, after the KosherKotlin demo's luach: text on the calendar's panel, following the Home's day.

/** This week's parsha and the day's dafim; clicking one opens it in a new tab. */
internal object LimudWidget : HomeWidget {
    override val id = "limud"
    override val title = Res.string.home_widget_name_limud
    override val defaultSpan = CellSpan(9, 2)
    override val minSpan = CellSpan(6, 2)
    override val maxSpan = CellSpan(20, 4)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val items = remember(state.selectedDate, state.inIsrael) { limudOfDay(state.selectedDate, state.inIsrael) }
        val open = rememberOpenInLibrary()
        PanelCard(modifier) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp)) {
                // Side by side where each tile can hold its two lines, one line each under one another otherwise
                if (maxWidth >= 360.dp) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.forEach { LimudTile(it, stacked = true, open, Modifier.weight(1f).fillMaxHeight()) }
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items.forEach { LimudTile(it, stacked = false, open, Modifier.weight(1f).fillMaxWidth()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LimudTile(
    item: LimudItem,
    stacked: Boolean,
    open: (LibraryPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = rememberAccentColor(JewelTheme.isDark)
    val place = item.place
    val kicker = @Composable { Text(item.kicker, fontSize = 11.sp, color = JewelTheme.globalColors.text.info, maxLines = 1) }
    val value = @Composable { valueModifier: Modifier ->
        Text(
            text = item.value,
            fontSize = if (stacked) 16.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (place != null) accent else JewelTheme.globalColors.text.normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = valueModifier,
        )
    }
    HoverBox(onClick = place?.let { { open(it) } }, modifier = modifier) {
        if (stacked) {
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                kicker()
                value(Modifier)
            }
        } else {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                kicker()
                value(Modifier.weight(1f))
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
    override val maxSpan = CellSpan(10, 4)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val now = rememberMinute()
        val location = state.location
        val zmanim =
            remember(now, location, state.zmanimOpinion, state.inIsrael) {
                nextZmanim(now, location, state.zmanimOpinion, state.inIsrael)
            }
        val clock = rememberClock(location.timeZone)
        val accent = rememberAccentColor(JewelTheme.isDark)
        PanelCard(modifier) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val next = zmanim.firstOrNull() ?: return@Column
                Kicker("הזמן הבא · ${countdown(now, next.time)}")
                HoverBox(onClick = { state.targetTime = next.time }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            next.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(clock(next.time), fontSize = 22.sp, color = accent)
                    }
                }
                Spacer(Modifier.weight(1f))
                zmanim.drop(1).forEach { zman ->
                    HoverBox(onClick = { state.targetTime = zman.time }) {
                        TimeRow(zman.name, clock(zman.time), fontSize = 12.sp)
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
    override val maxSpan = CellSpan(10, 8)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val location = state.location
        val events =
            remember(state.selectedDate, location, state.zmanimOpinion, state.cityLabel, state.inIsrael) {
                upcomingEvents(state.selectedDate, location, state.zmanimOpinion, state.cityLabel, state.inIsrael)
            }
        val clock = rememberClock(location.timeZone)
        PanelCard(modifier) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp)) {
                // As many as fit, whole
                val shown = (maxHeight / EVENT_ROW_HEIGHT).toInt().coerceAtLeast(1)
                Column(Modifier.fillMaxSize()) {
                    events.take(shown).forEach { event ->
                        HoverBox(
                            onClick = { state.selectDate(event.date) },
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        ) {
                            EventRow(event, clock)
                        }
                    }
                }
            }
        }
    }
}

private val EVENT_ROW_HEIGHT = 46.dp

@Composable
private fun EventRow(
    event: LuachEvent,
    clock: (Date) -> String,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
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
    override val defaultSpan = CellSpan(6, 3)
    override val minSpan = CellSpan(5, 3)
    override val maxSpan = CellSpan(10, 5)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        // ponytail: the civil day's tefila; tonight's maariv already belongs to tomorrow's
        val lines = remember(state.selectedDate, state.inIsrael) { tefilaOfDay(state.selectedDate, state.inIsrael) }
        val hebrewDate =
            remember(state.selectedDate, state.inIsrael) {
                hebrewFormatter.format(JewishCalendar(state.selectedDate.toKotlinLocalDate(), state.inIsrael))
            }
        val accent = rememberAccentColor(JewelTheme.isDark)
        PanelCard(modifier) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Kicker("תפילת היום · $hebrewDate")
                lines.forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(line.label, fontSize = 12.sp, color = JewelTheme.globalColors.text.info, modifier = Modifier.width(78.dp))
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
            }
        }
    }
}

/** The coming month's molad, and when Kiddush Levana can be said. */
internal object MoladWidget : HomeWidget {
    override val id = "molad"
    override val title = Res.string.home_widget_name_molad
    override val defaultSpan = CellSpan(6, 2)
    override val minSpan = CellSpan(5, 2)
    override val maxSpan = CellSpan(10, 3)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val now = rememberMinute()
        val molad =
            remember(now, state.selectedDate, state.inIsrael, state.kiddushLevanaEarliest, state.kiddushLevanaLatest) {
                moladInfo(now, state.selectedDate, state.inIsrael, state.kiddushLevanaEarliest, state.kiddushLevanaLatest)
            }
        val zone = state.location.timeZone
        val moment = remember(zone) { SimpleDateFormat("d.M HH:mm").apply { timeZone = zone } }
        val accent = rememberAccentColor(JewelTheme.isDark)
        PanelCard(modifier) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Kicker("מולד חודש ${molad.month}")
                Text(
                    "יום ${molad.weekday}, ⁦${molad.time}⁩ ו־${molad.chalakim} חלקים",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Kicker("קידוש לבנה")
                TimeRow("מ־", moment.format(molad.kiddushLevanaStart).ltr(), fontSize = 12.sp)
                TimeRow("עד", moment.format(molad.kiddushLevanaEnd).ltr(), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Kicker(text: String) {
    Text(text, fontSize = 11.sp, color = JewelTheme.globalColors.text.info, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun TimeRow(
    label: String,
    time: String,
    fontSize: TextUnit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

/** A rounded area lit on hover, with a hand cursor where it can be clicked. */
@Composable
private fun HoverBox(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10.dp)
    val tint = JewelTheme.globalColors.text.normal
    Column(
        modifier
            .clip(shape)
            .background(if (hovered && onClick != null) tint.copy(alpha = 0.08f) else Color.Transparent)
            .then(
                if (onClick != null) {
                    Modifier.hoverable(hover).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        content = content,
    )
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

/** Opens a [LibraryPlace] in a new tab of this window; does nothing if the library doesn't have the book. */
@Composable
private fun rememberOpenInLibrary(): (LibraryPlace) -> Unit {
    val repository = LocalAppGraph.current.repository
    val tabs = LocalOpenWindow.current.tabsViewModel
    val scope = rememberCoroutineScope()
    return remember(repository, tabs, scope) {
        { place ->
            scope.launch {
                val book = repository.getBookByTitle(place.bookTitle) ?: return@launch
                val lineId =
                    when {
                        place.heading != null ->
                            repository.getTocEntriesForBook(book.id).firstOrNull { it.text == place.heading }?.lineId

                        place.parashaIndex != null ->
                            repository
                                .getAltTocStructuresForBook(book.id)
                                .firstOrNull { it.key == "Parasha" }
                                ?.let { repository.getAltRootToc(it.id).sortedBy { entry -> entry.id } }
                                ?.getOrNull(place.parashaIndex)
                                ?.lineId

                        else -> null
                    }
                tabs.openTab(TabsDestination.BookContent(bookId = book.id, tabId = UUID.randomUUID().toString(), lineId = lineId))
            }
        }
    }
}
