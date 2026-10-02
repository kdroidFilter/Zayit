package io.github.kdroidfilter.seforimapp.features.home.widgets.measures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.HoverBox
import io.github.kdroidfilter.seforimapp.features.home.widgets.PanelCard
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widget_name_measures
import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.UUID

/**
 * A length in one of the Torah's measures converted into another, and how long it is today and how long it takes to
 * walk after the opinion picked; each figure links to the line of the library it comes from.
 */
internal object MeasuresWidget : HomeWidget {
    override val id = "measures"
    override val title = Res.string.home_widget_name_measures
    override val defaultSpan = CellSpan(7, 4)
    override val minSpan = CellSpan(7, 4)
    override val maxSpan = CellSpan(10, 4)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val field = rememberTextFieldState("1")
        var from by remember { mutableStateOf(LengthUnit.AMMA) }
        var to by remember { mutableStateOf(LengthUnit.TEFACH) }
        val typed = field.text.trim().toString()
        val amount = typed.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
        val etzbaos = amount?.let { it * from.etzbaos }
        val accent = rememberAccentColor(JewelTheme.isDark)
        val open = rememberOpenSource(state)
        PanelCard(modifier) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(field, Modifier.width(64.dp))
                    UnitPicker(from, onPick = { from = it }, Modifier.weight(1f))
                    HoverBox(onClick = {
                        val was = from
                        from = to
                        to = was
                    }) {
                        Text("⇄", fontSize = 16.sp, color = accent, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    UnitPicker(to, onPick = { to = it }, Modifier.weight(1f))
                }
                Column {
                    Text(
                        etzbaos?.let { "= ${quantity(it / to.etzbaos, to)}" } ?: "–",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Row {
                        conversionSources(from, to).forEach { SourceLink(it, open) }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionTitle("בימינו")
                    AMMA_OPINIONS.forEach { opinion ->
                        OpinionRow(
                            label = opinion.label,
                            value = etzbaos?.let { length(it / ETZBAOS_IN_AMMA * opinion.cm) },
                            source = opinion.source,
                            accent = accent,
                            open = open,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionTitle("זמן הליכה")
                    MIL_OPINIONS.forEach { opinion ->
                        OpinionRow(
                            label = opinion.label,
                            value = etzbaos?.let { duration(it / ETZBAOS_IN_AMMA / AMOS_IN_MIL * opinion.minutes * 60) },
                            source = opinion.source,
                            accent = accent,
                            open = open,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitPicker(
    unit: LengthUnit,
    onPick: (LengthUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListComboBox(
        items = LengthUnit.entries.map { it.title },
        selectedIndex = unit.ordinal,
        onSelectedItemChange = { onPick(LengthUnit.entries[it]) },
        modifier = modifier,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = JewelTheme.globalColors.text.info, maxLines = 1)
}

/** What the amount is after an opinion, with its source. */
@Composable
private fun OpinionRow(
    label: String,
    value: String?,
    source: Source,
    accent: Color,
    open: (Source) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, maxLines = 1)
        SourceLink(source, open, Modifier.weight(1f))
        Text(value ?: "–", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = accent, maxLines = 1)
    }
}

/** [source]'s reference, opening it in a new tab. */
@Composable
private fun SourceLink(
    source: Source,
    open: (Source) -> Unit,
    modifier: Modifier = Modifier,
) {
    HoverBox(onClick = { open(source) }, modifier = modifier) {
        Text(
            source.ref,
            fontSize = 11.sp,
            color = JewelTheme.globalColors.text.info,
            textDecoration = TextDecoration.Underline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

/** A line of the library, [ref] as shown: [lineIndex] in the book [bookTitle]. */
internal data class Source(
    val ref: String,
    val bookTitle: String,
    val lineIndex: Int,
)

/** What says how many [from] are in [to] or back: each unit's source from the smaller one's next to the larger. */
internal fun conversionSources(
    from: LengthUnit,
    to: LengthUnit,
): List<Source> {
    val range = minOf(from, to)..maxOf(from, to)
    return LengthUnit.entries
        .filter { it in range && it != range.start }
        .map { it.source }
        .distinct()
}

/** Opens [Source] in a new tab, on its line. */
@Composable
private fun rememberOpenSource(state: HomeWidgetsState): (Source) -> Unit {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    return remember(graph, state, scope) {
        { source ->
            scope.launch {
                // Read on click: the books DB is opened when first needed, never by showing the card
                val repository = graph.repository
                val book = repository.getBookByTitle(source.bookTitle) ?: return@launch
                val line = repository.getLineByIndex(book.id, source.lineIndex)
                state.openTab(TabsDestination.BookContent(bookId = book.id, tabId = UUID.randomUUID().toString(), lineId = line?.id))
            }
        }
    }
}

// Every figure below is in the library, on the line its source points to.
// ponytail: lines by index in their book, as the library numbers them; a book re-cut would need them found again

private val RAMBAM_SHABBOS_17_36 = Source("רמב״ם שבת יז, לו", "משנה תורה, הלכות שבת", 370)
private val RAMBAM_TEFILLAH_4_2 = Source("רמב״ם תפילה ד, ב", "משנה תורה, הלכות תפילה וברכת כהנים", 47)
private val MISHNAH_BERURAH_110_31 = Source("משנה ברורה קי, לא", "משנה ברורה", 2603)

private const val ETZBAOS_IN_AMMA = 24.0
private const val AMOS_IN_MIL = 2000.0

/** The lengths, in etzbaos, with what says so. */
internal enum class LengthUnit(
    val title: String,
    val plural: String,
    val etzbaos: Double,
    val source: Source,
) {
    // "האצבע… רחב הגודל של יד. והטפח ארבע אצבעות… אמה בת ששה טפחים"
    ETZBA("אצבע", "אצבעות", 1.0, RAMBAM_SHABBOS_17_36),
    TEFACH("טפח", "טפחים", 4.0, RAMBAM_SHABBOS_17_36),
    AMMA("אמה", "אמות", ETZBAOS_IN_AMMA, RAMBAM_SHABBOS_17_36),

    // "ארבעה מילין שהם שמונת אלפים אמה"
    MIL("מיל", "מילין", ETZBAOS_IN_AMMA * AMOS_IN_MIL, RAMBAM_TEFILLAH_4_2),

    // "מיל הוא אלפים אמה ופרסה הוא ד' מילין"
    PARSAH("פרסה", "פרסאות", ETZBAOS_IN_AMMA * AMOS_IN_MIL * 4, MISHNAH_BERURAH_110_31),
}

internal class AmmaOpinion(
    val label: String,
    val cm: Double,
    val source: Source,
)

internal val AMMA_OPINIONS =
    listOf(
        // "ומדת האמה 58 ס"מ" (קונטרס השיעורים)
        AmmaOpinion("חזון איש", 58.0, Source("חזו״א, קונטרס השיעורים לט, ט", "חזון איש, אורח חיים מועד", 1533)),
        // "אמה של תורה שהוא כ"ד אצבעות מדת כ"א אינטשעס ורביע"
        AmmaOpinion("אגרות משה", 21.25 * 2.54, Source("אגרות משה או״ח א, קלו", "אגרות משה אורח חיים א", 1256)),
    )

internal class MilOpinion(
    val label: String,
    val minutes: Double,
    val source: Source,
)

private val BIUR_HALACHA_459_2 = Source("ביאור הלכה תנט, ב", "ביאור הלכה", 4452)

internal val MIL_OPINIONS =
    listOf(
        // "ושיעור מיל הוי רביעית שעה וחלק מעשרים מן השעה"
        MilOpinion("מיל 18 דק׳", 18.0, Source("שו״ע או״ח תנט, ב", "שולחן ערוך, אורח חיים", 3402)),
        // "שחושבין שיעור מיל לחשבון כ"ב מינוטין וחצי"
        MilOpinion("מיל 22.5 דק׳", 22.5, BIUR_HALACHA_459_2),
        // "שליש שעה וחלק ט"ו מן השעה"
        MilOpinion("מיל 24 דק׳", 24.0, BIUR_HALACHA_459_2),
    )

/** [count] [unit]s, its name in the singular for one. */
internal fun quantity(
    count: Double,
    unit: LengthUnit,
): String = "${amount(count)} ${if (count == 1.0) unit.title else unit.plural}"

/** [cm] in centimetres, metres or kilometres. */
internal fun length(cm: Double): String =
    when {
        cm < 100 -> "${amount(cm)} ס״מ"
        cm < 100_000 -> "${amount(cm / 100)} מ׳"
        else -> "${amount(cm / 100_000)} ק״מ"
    }

/** [seconds] in seconds, minutes or hours. */
internal fun duration(seconds: Double): String =
    when {
        seconds < 60 -> "${amount(seconds)} שניות"
        seconds < 3600 -> "${amount(seconds / 60)} דקות"
        else -> "${amount(seconds / 3600)} שעות"
    }

// The same "8,000" whatever the system's language
private val whole = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.ROOT))

/** Whole from 100, else three significant digits: 58, 1.74, 0.54. */
private fun amount(x: Double): String =
    when {
        x >= 100 -> whole.format(x)
        x == 0.0 -> "0"
        else -> BigDecimal(x).round(MathContext(3)).stripTrailingZeros().toPlainString()
    }
