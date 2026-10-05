package io.github.kdroidfilter.seforimapp.features.home.widgets.luach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar.Parsha
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.FitColumn
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.HoverBox
import io.github.kdroidfilter.seforimapp.features.home.widgets.PanelCard
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.features.home.widgets.shownDateHere
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.launch
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widget_name_shnayim_mikra
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// Shnayim mikra ve'echad targum: the week's parsha by aliyos, each one ticked once read (in the local DB) and opened
// in the Chumash.

internal val ALIYOS = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שביעי")

/** The parsha of [shabbat]'s week, under its [name], read in the Hebrew [year]: its ticks are that year's. */
@Immutable
internal data class MikraWeek(
    val parsha: Parsha,
    val name: String,
    val shabbat: LocalDate,
    val year: Long,
)

/**
 * The parsha to read by the coming Shabbat, in Israel or abroad: past Shabbatot of Yom Tov, which read none, to the
 * next one read (a double one as one); וזאת הברכה once האזינו is read (on Shabbat Shuva when Yom Kippur is on Shabbat)
 * until Simchat Torah, which reads it on no Shabbat.
 */
internal fun mikraWeek(
    date: LocalDate,
    inIsrael: Boolean,
): MikraWeek {
    var shabbat = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
    var day = JewishCalendar(shabbat.toKotlinLocalDate(), inIsrael)
    while (day.parshah == Parsha.NONE) {
        shabbat = shabbat.plusWeeks(1)
        day = JewishCalendar(shabbat.toKotlinLocalDate(), inIsrael)
    }
    if (day.parshah == Parsha.BERESHIS) {
        val today = JewishCalendar(date.toKotlinLocalDate(), inIsrael)
        val simchatTorah = if (inIsrael) 22 else 23
        if (today.jewishMonth == HebrewMonth.TISHREI && today.jewishDayOfMonth <= simchatTorah) {
            return MikraWeek(Parsha.VZOS_HABERACHA, "וזאת הברכה", shabbat, today.jewishYear)
        }
    }
    return MikraWeek(day.parshah, hebrewFormatter.formatParsha(day).orEmpty(), shabbat, day.jewishYear)
}

// After hebcal-leyning's aliyot.json (BSD-2, see resources/limud/NOTICE.txt): each of a parsha's seven aliyos, from
// chapter:verse to chapter:verse; Parsha.BERESHIS..VZOS_HABERACHA in order.
private val SINGLE_ALIYOS =
    listOf(
        "1:1-2:3 2:4-2:19 2:20-3:21 3:22-4:18 4:19-4:22 4:23-5:24 5:25-6:8", // Bereshit
        "6:9-6:22 7:1-7:16 7:17-8:14 8:15-9:7 9:8-9:17 9:18-10:32 11:1-11:32", // Noach
        "12:1-12:13 12:14-13:4 13:5-13:18 14:1-14:20 14:21-15:6 15:7-17:6 17:7-17:27", // Lech-Lecha
        "18:1-18:14 18:15-18:33 19:1-19:20 19:21-21:4 21:5-21:21 21:22-21:34 22:1-22:24", // Vayera
        "23:1-23:16 23:17-24:9 24:10-24:26 24:27-24:52 24:53-24:67 25:1-25:11 25:12-25:18", // Chayei Sara
        "25:19-26:5 26:6-26:12 26:13-26:22 26:23-26:29 26:30-27:27 27:28-28:4 28:5-28:9", // Toldot
        "28:10-28:22 29:1-29:17 29:18-30:13 30:14-30:27 30:28-31:16 31:17-31:42 31:43-32:3", // Vayetzei
        "32:4-32:13 32:14-32:30 32:31-33:5 33:6-33:20 34:1-35:11 35:12-36:19 36:20-36:43", // Vayishlach
        "37:1-37:11 37:12-37:22 37:23-37:36 38:1-38:30 39:1-39:6 39:7-39:23 40:1-40:23", // Vayeshev
        "41:1-41:14 41:15-41:38 41:39-41:52 41:53-42:18 42:19-43:15 43:16-43:29 43:30-44:17", // Miketz
        "44:18-44:30 44:31-45:7 45:8-45:18 45:19-45:27 45:28-46:27 46:28-47:10 47:11-47:27", // Vayigash
        "47:28-48:9 48:10-48:16 48:17-48:22 49:1-49:18 49:19-49:26 49:27-50:20 50:21-50:26", // Vayechi
        "1:1-1:17 1:18-2:10 2:11-2:25 3:1-3:15 3:16-4:17 4:18-4:31 5:1-6:1", // Shemot
        "6:2-6:13 6:14-6:28 6:29-7:7 7:8-8:6 8:7-8:18 8:19-9:16 9:17-9:35", // Vaera
        "10:1-10:11 10:12-10:23 10:24-11:3 11:4-12:20 12:21-12:28 12:29-12:51 13:1-13:16", // Bo
        "13:17-14:8 14:9-14:14 14:15-14:25 14:26-15:26 15:27-16:10 16:11-16:36 17:1-17:16", // Beshalach
        "18:1-18:12 18:13-18:23 18:24-18:27 19:1-19:6 19:7-19:19 19:20-20:14 20:15-20:23", // Yitro
        "21:1-21:19 21:20-22:3 22:4-22:26 22:27-23:5 23:6-23:19 23:20-23:25 23:26-24:18", // Mishpatim
        "25:1-25:16 25:17-25:40 26:1-26:14 26:15-26:30 26:31-26:37 27:1-27:8 27:9-27:19", // Terumah
        "27:20-28:12 28:13-28:30 28:31-28:43 29:1-29:18 29:19-29:37 29:38-29:46 30:1-30:10", // Tetzaveh
        "30:11-31:17 31:18-33:11 33:12-33:16 33:17-33:23 34:1-34:9 34:10-34:26 34:27-34:35", // Ki Tisa
        "35:1-35:20 35:21-35:29 35:30-36:7 36:8-36:19 36:20-37:16 37:17-37:29 38:1-38:20", // Vayakhel
        "38:21-39:1 39:2-39:21 39:22-39:32 39:33-39:43 40:1-40:16 40:17-40:27 40:28-40:38", // Pekudei
        "1:1-1:13 1:14-2:6 2:7-2:16 3:1-3:17 4:1-4:26 4:27-5:10 5:11-5:26", // Vayikra
        "6:1-6:11 6:12-7:10 7:11-7:38 8:1-8:13 8:14-8:21 8:22-8:29 8:30-8:36", // Tzav
        "9:1-9:16 9:17-9:23 9:24-10:11 10:12-10:15 10:16-10:20 11:1-11:32 11:33-11:47", // Shmini
        "12:1-13:5 13:6-13:17 13:18-13:23 13:24-13:28 13:29-13:39 13:40-13:54 13:55-13:59", // Tazria
        "14:1-14:12 14:13-14:20 14:21-14:32 14:33-14:53 14:54-15:15 15:16-15:28 15:29-15:33", // Metzora
        "16:1-16:17 16:18-16:24 16:25-16:34 17:1-17:7 17:8-18:5 18:6-18:21 18:22-18:30", // Achrei Mot
        "19:1-19:14 19:15-19:22 19:23-19:32 19:33-19:37 20:1-20:7 20:8-20:22 20:23-20:27", // Kedoshim
        "21:1-21:15 21:16-22:16 22:17-22:33 23:1-23:22 23:23-23:32 23:33-23:44 24:1-24:23", // Emor
        "25:1-25:13 25:14-25:18 25:19-25:24 25:25-25:28 25:29-25:38 25:39-25:46 25:47-26:2", // Behar
        "26:3-26:5 26:6-26:9 26:10-26:46 27:1-27:15 27:16-27:21 27:22-27:28 27:29-27:34", // Bechukotai
        "1:1-1:19 1:20-1:54 2:1-2:34 3:1-3:13 3:14-3:39 3:40-3:51 4:1-4:20", // Bamidbar
        "4:21-4:37 4:38-4:49 5:1-5:10 5:11-6:27 7:1-7:41 7:42-7:71 7:72-7:89", // Nasso
        "8:1-8:14 8:15-8:26 9:1-9:14 9:15-10:10 10:11-10:34 10:35-11:29 11:30-12:16", // Beha'alotcha
        "13:1-13:20 13:21-14:7 14:8-14:25 14:26-15:7 15:8-15:16 15:17-15:26 15:27-15:41", // Sh'lach
        "16:1-16:13 16:14-16:19 16:20-17:8 17:9-17:15 17:16-17:24 17:25-18:20 18:21-18:32", // Korach
        "19:1-19:17 19:18-20:6 20:7-20:13 20:14-20:21 20:22-21:9 21:10-21:20 21:21-22:1", // Chukat
        "22:2-22:12 22:13-22:20 22:21-22:38 22:39-23:12 23:13-23:26 23:27-24:13 24:14-25:9", // Balak
        "25:10-26:4 26:5-26:51 26:52-27:5 27:6-27:23 28:1-28:15 28:16-29:11 29:12-30:1", // Pinchas
        "30:2-30:17 31:1-31:12 31:13-31:24 31:25-31:41 31:42-31:54 32:1-32:19 32:20-32:42", // Matot
        "33:1-33:10 33:11-33:49 33:50-34:15 34:16-34:29 35:1-35:8 35:9-35:34 36:1-36:13", // Masei
        "1:1-1:10 1:11-1:21 1:22-1:38 1:39-2:1 2:2-2:30 2:31-3:14 3:15-3:22", // Devarim
        "3:23-4:4 4:5-4:40 4:41-4:49 5:1-5:18 5:19-6:3 6:4-6:25 7:1-7:11", // Vaetchanan
        "7:12-8:10 8:11-9:3 9:4-9:29 10:1-10:11 10:12-11:9 11:10-11:21 11:22-11:25", // Eikev
        "11:26-12:10 12:11-12:28 12:29-13:19 14:1-14:21 14:22-14:29 15:1-15:18 15:19-16:17", // Re'eh
        "16:18-17:13 17:14-17:20 18:1-18:5 18:6-18:13 18:14-19:13 19:14-20:9 20:10-21:9", // Shoftim
        "21:10-21:21 21:22-22:7 22:8-23:7 23:8-23:24 23:25-24:4 24:5-24:13 24:14-25:19", // Ki Teitzei
        "26:1-26:11 26:12-26:15 26:16-26:19 27:1-27:10 27:11-28:6 28:7-28:69 29:1-29:8", // Ki Tavo
        "29:9-29:11 29:12-29:14 29:15-29:28 30:1-30:6 30:7-30:10 30:11-30:14 30:15-30:20", // Nitzavim
        "31:1-31:3 31:4-31:6 31:7-31:9 31:10-31:13 31:14-31:19 31:20-31:24 31:25-31:30", // Vayeilech
        "32:1-32:6 32:7-32:12 32:13-32:18 32:19-32:28 32:29-32:39 32:40-32:43 32:44-32:52", // Ha'azinu
        "33:1-33:7 33:8-33:12 33:13-33:17 33:18-33:21 33:22-33:26 33:27-33:29 34:1-34:12", // Vezot Haberakhah
    )

// Read together, two parshiyos are divided otherwise than each one alone
private val DOUBLE_ALIYOS =
    mapOf(
        Parsha.VAYAKHEL_PEKUDEI to "35:1-35:29 35:30-37:16 37:17-37:29 38:1-39:1 39:2-39:21 39:22-39:43 40:1-40:38",
        Parsha.TAZRIA_METZORA to "12:1-13:23 13:24-13:39 13:40-13:54 13:55-14:20 14:21-14:32 14:33-15:15 15:16-15:33",
        Parsha.ACHREI_MOS_KEDOSHIM to "16:1-16:24 16:25-17:7 17:8-18:21 18:22-19:14 19:15-19:32 19:33-20:7 20:8-20:27",
        Parsha.BEHAR_BECHUKOSAI to "25:1-25:18 25:19-25:28 25:29-25:38 25:39-26:9 26:10-26:46 27:1-27:15 27:16-27:34",
        Parsha.CHUKAS_BALAK to "19:1-20:6 20:7-20:21 20:22-21:20 21:21-22:12 22:13-22:38 22:39-23:26 23:27-25:9",
        Parsha.MATOS_MASEI to "30:2-31:12 31:13-31:54 32:1-32:19 32:20-33:49 33:50-34:15 34:16-35:8 35:9-36:13",
        Parsha.NITZAVIM_VAYEILECH to "29:9-29:28 30:1-30:6 30:7-30:14 30:15-31:6 31:7-31:13 31:14-31:19 31:20-31:30",
    )

/** A verse, by chapter and number. */
internal data class Verse(
    val chapter: Int,
    val verse: Int,
)

/** [parsha]'s seven aliyos, each from its first verse to its last. */
internal fun aliyosOf(parsha: Parsha): List<Pair<Verse, Verse>> {
    val table = DOUBLE_ALIYOS[parsha] ?: SINGLE_ALIYOS[parsha.ordinal - 1]

    fun verse(cv: String) = Verse(cv.substringBefore(':').toInt(), cv.substringAfter(':').toInt())
    return table.split(' ').map { verse(it.substringBefore('-')) to verse(it.substringAfter('-')) }
}

/** "ב, ד–יט" within a chapter, "ב, כ – ג, כא" across two. */
internal fun aliyaRange(
    from: Verse,
    to: Verse,
) = if (from.chapter == to.chapter) {
    "${hebrewNumeral(from.chapter)}, ${hebrewNumeral(from.verse)}–${hebrewNumeral(to.verse)}"
} else {
    "${hebrewNumeral(from.chapter)}, ${hebrewNumeral(from.verse)} – ${hebrewNumeral(to.chapter)}, ${hebrewNumeral(to.verse)}"
}

/** Where an aliya is in the library: its Chumash, from its first verse, marked through its last. */
internal fun aliyaPlace(
    parsha: Parsha,
    aliya: Int,
): LibraryPlace {
    val book = checkNotNull(parshaPlace(parsha)).bookTitle
    val (from, to) = aliyosOf(parsha)[aliya]

    fun ref(v: Verse) = "$book ${hebrewNumeral(v.chapter)}, ${hebrewNumeral(v.verse)}"
    return LibraryPlace(book, ref = ref(from), endRefs = listOf(ref(to)))
}

/** The week's parsha by aliyos: a tick marks one read, a click opens it in the Chumash, each verse twice then its targum. */
internal object ShnayimMikraWidget : HomeWidget {
    override val id = "shnayim_mikra"
    override val title = Res.string.home_widget_name_shnayim_mikra
    override val defaultSpan = CellSpan(4, 4)
    override val minSpan = CellSpan(4, 4)
    override val maxSpan = CellSpan(6, 4)
    override val toolWindowSize = DpSize(320.dp, 360.dp)
    override val toolWindowMinSize = DpSize(280.dp, 320.dp)
    override val toolSymbol = "checklist"

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val day = state.shownDateHere()
        val week = remember(day, state.inIsrael) { mikraWeek(day, state.inIsrael) }
        val store = LocalAppGraph.current.shnayimMikraStore
        var weekShown by remember { mutableStateOf(week) }
        val revision by store.revision.collectAsState()
        val done by produceState(emptySet<Int>(), week, revision) {
            // Another week's ticks aren't shown while this one's load
            if (week != weekShown) value = emptySet()
            weekShown = week
            value = store.read(week.year, week.parsha.name)
        }
        val scope = rememberCoroutineScope()
        val aliyos = remember(week) { aliyosOf(week.parsha) }
        val open = rememberOpenInLibrary(state, shnayimMikra = true)
        val accent = rememberAccentColor(JewelTheme.isDark)
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        PanelCard(modifier) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                Text(
                    "שנים מקרא · ${week.name}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp).height(22.dp),
                )
                FitColumn(Modifier.fillMaxWidth().weight(1f), spacing = 2.dp, spread = true) {
                    aliyos.forEachIndexed { i, (from, to) ->
                        Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = i in done,
                                onCheckedChange = { read -> scope.launch { store.setRead(week.year, week.parsha.name, i, read) } },
                                modifier = Modifier.testTag("shnayim-mikra-done-$i"),
                            )
                            HoverBox(
                                onClick = { open(aliyaPlace(week.parsha, i)) },
                                modifier = Modifier.weight(1f).height(26.dp).testTag("shnayim-mikra-aliya-$i"),
                            ) {
                                Row(
                                    Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        ALIYOS[i],
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (i in done) accent else JewelTheme.globalColors.text.normal,
                                        maxLines = 1,
                                        modifier = Modifier.width(48.dp),
                                    )
                                    Text(
                                        aliyaRange(from, to),
                                        fontSize = 12.sp,
                                        color = JewelTheme.globalColors.text.info,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        key = if (rtl) AllIconsKeys.General.ChevronLeft else AllIconsKeys.General.ChevronRight,
                                        contentDescription = null,
                                        tint = JewelTheme.globalColors.text.info,
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
