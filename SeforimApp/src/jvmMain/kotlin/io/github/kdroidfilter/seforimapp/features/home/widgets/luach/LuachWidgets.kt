package io.github.kdroidfilter.seforimapp.features.home.widgets.luach

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
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
// They keep the app's text sizes (only the next zman's clock grows), and their lists show as many whole rows as fit.

/** This week's parsha and the day's dafim; clicking one opens it in a new tab. */
internal object LimudWidget : HomeWidget {
    override val id = "limud"
    override val title = Res.string.home_widget_name_limud
    override val defaultSpan = CellSpan(4, 2)
    override val minSpan = CellSpan(4, 2)

    // Three short lines: wider would only part them from their tags
    override val maxSpan = CellSpan(5, 2)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) = LimudPanel(state, rememberOpenInLibrary(), modifier)
}

/** The limud lines, opening a place with [open]. */
@Composable
internal fun LimudPanel(
    state: HomeWidgetsState,
    open: (LibraryPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember(state.selectedDate, state.inIsrael) { limudOfDay(state.selectedDate, state.inIsrael) }
    val accent = rememberAccentColor(JewelTheme.isDark)
    PanelCard(modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(
                "לימוד יומי",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                items.forEach { LimudRow(it, accent, open) }
            }
        }
    }
}

@Composable
private fun LimudRow(
    item: LimudItem,
    accent: Color,
    open: (LibraryPlace) -> Unit,
) {
    val place = item.place
    HoverBox(onClick = place?.let { { open(it) } }, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
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
            if (place != null) {
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
        val events =
            remember(state.selectedDate, location, state.zmanimOpinion, state.cityLabel, state.inIsrael) {
                upcomingEvents(state.selectedDate, location, state.zmanimOpinion, state.cityLabel, state.inIsrael, limit = 14)
            }
        val clock = rememberClock(location.timeZone)
        PanelCard(modifier) {
            FitColumn(Modifier.fillMaxSize().padding(6.dp)) {
                events.forEach { event ->
                    HoverBox(onClick = { state.selectDate(event.date) }, modifier = Modifier.fillMaxWidth()) {
                        EventRow(event, clock)
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

    // Seven short lines at most: more room would only spread them apart
    override val maxSpan = CellSpan(6, 3)

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
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("תפילת היום", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(hebrewDate, fontSize = 11.sp, color = JewelTheme.globalColors.text.info, maxLines = 1)
                }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    // The lines share the card's height, but never further apart than reads as one list
                    val gap = ((maxHeight - TEFILA_LINE_HEIGHT * lines.size) / lines.size).coerceIn(5.dp, 10.dp)
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically)) {
                        lines.forEach { TefilaRow(it, accent) }
                    }
                }
            }
        }
    }
}

private val TEFILA_LINE_HEIGHT = 17.dp

@Composable
private fun TefilaRow(
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
    override val defaultSpan = CellSpan(6, 2)
    override val minSpan = CellSpan(4, 2)
    override val maxSpan = CellSpan(8, 2)

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
        val moment = remember(zone) { SimpleDateFormat("d.M · HH:mm").apply { timeZone = zone } }
        val accent = rememberAccentColor(JewelTheme.isDark)
        val moladPart = @Composable { partModifier: Modifier ->
            Column(partModifier, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                Kicker("מולד חודש ${molad.month}")
                Text("יום ${molad.weekday}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = accent, maxLines = 1)
                Text("⁦${molad.time}⁩ · ${molad.chalakim} חלקים", fontSize = 12.sp, maxLines = 1)
            }
        }
        val levanaPart = @Composable { partModifier: Modifier ->
            Column(partModifier, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                Kicker("קידוש לבנה")
                Text("מ־ ${moment.format(molad.kiddushLevanaStart).ltr()}", fontSize = 12.sp, maxLines = 1)
                Text("עד ${moment.format(molad.kiddushLevanaEnd).ltr()}", fontSize = 12.sp, maxLines = 1)
            }
        }
        PanelCard(modifier) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Side by side on a wide card, one above the other on a narrow one
                if (maxWidth >= 200.dp) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        moladPart(Modifier.weight(1f).fillMaxHeight())
                        levanaPart(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        moladPart(Modifier.fillMaxWidth().weight(1f))
                        levanaPart(Modifier.fillMaxWidth().weight(1f))
                    }
                }
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

/** Its children from the top, as many as fit whole; the others aren't shown. */
@Composable
private fun FitColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        var y = 0
        val placed =
            buildList {
                for (measurable in measurables) {
                    val placeable = measurable.measure(loose)
                    if (y + placeable.height > constraints.maxHeight) break
                    add(y to placeable)
                    y += placeable.height + gap
                }
            }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placed.forEach { (top, placeable) -> placeable.placeRelative(0, top) }
        }
    }
}

/** A rounded area lit on hover, with a hand cursor where it can be clicked; [tinted] shows its area at rest. */
@Composable
private fun HoverBox(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tinted: Boolean = false,
    content: @Composable () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10.dp)
    val tint = JewelTheme.globalColors.text.normal
    val alpha =
        when {
            hovered && onClick != null -> 0.10f
            tinted -> 0.05f
            else -> 0f
        }
    Box(
        modifier
            .clip(shape)
            .background(if (alpha > 0f) tint.copy(alpha = alpha) else Color.Transparent)
            .then(
                if (onClick != null) {
                    Modifier.hoverable(hover).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) { content() }
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
