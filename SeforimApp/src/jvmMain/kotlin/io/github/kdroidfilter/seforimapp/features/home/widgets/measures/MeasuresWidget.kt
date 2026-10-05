package io.github.kdroidfilter.seforimapp.features.home.widgets.measures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.HoverBox
import io.github.kdroidfilter.seforimapp.features.home.widgets.PanelCard
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetMenuItem
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widget_name_measures
import seforimapp.seforimapp.generated.resources.home_widgets_options
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
    override val toolWindowSize = DpSize(400.dp, 400.dp)
    override val toolWindowMinSize = DpSize(340.dp, 200.dp)
    override val toolSymbol = "ruler"
    override val minSpan = CellSpan(7, 2)

    // Its height is the one its opinions need (heightAt), no more: only its width is the user's
    override val maxSpan = CellSpan(10, 2)

    // The opinions shown, as saved: read by the grid for the card's height as well as by the card
    private var shown by mutableStateOf(shownOpinions(null))

    override fun applyOptions(options: String?) {
        shown = shownOpinions(options)
    }

    /** Tall enough for every opinion picked, never one hidden for want of room. */
    override fun heightAt(width: Dp): Dp {
        var height = PADDING * 2 + TITLE_HEIGHT + BLOCK_GAP + INPUT_HEIGHT + BLOCK_GAP + RESULT_HEIGHT + LINK_HEIGHT
        // A kind with no opinion shown is left out, its title too
        for (rows in listOf(AMMA_OPINIONS.count { it.id in shown }, MIL_OPINIONS.count { it.id in shown })) {
            if (rows > 0) height += BLOCK_GAP + SECTION_TITLE_HEIGHT + (ROW_GAP + OPINION_ROW_HEIGHT) * rows
        }
        return height
    }

    // Its opinions picked in a page of their own, as the limudim
    @Composable
    override fun menuItems(state: HomeWidgetsState) =
        listOf(
            WidgetMenuItem(stringResource(Res.string.home_widgets_options), icon = AllIconsKeys.General.Settings) {
                state.optionsOpen = this
            },
        )

    @Composable
    override fun Options(state: HomeWidgetsState) = MeasuresOptions(state)

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
        val ammaOpinions = AMMA_OPINIONS.filter { it.id in shown }
        val milOpinions = MIL_OPINIONS.filter { it.id in shown }
        val accent = rememberAccentColor(JewelTheme.isDark)
        val open = rememberOpenSource(state)
        PanelCard(modifier) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = PADDING),
                // Taller than its content (heightAt) by a row's rounding: spread over its blocks
                verticalArrangement = Arrangement.spacedBy(BLOCK_GAP, Alignment.CenterVertically),
            ) {
                Row(Modifier.fillMaxWidth().height(TITLE_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    // Its options at hand, as in its menu
                    IconButton(
                        onClick = { state.optionsOpen = MeasuresWidget },
                        modifier = Modifier.size(TITLE_HEIGHT).testTag("measures-settings"),
                    ) {
                        Icon(
                            key = AllIconsKeys.General.Settings,
                            contentDescription = stringResource(Res.string.home_widgets_options),
                            tint = JewelTheme.globalColors.text.info,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(INPUT_HEIGHT),
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
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.height(RESULT_HEIGHT).padding(horizontal = 4.dp),
                    )
                    Row(Modifier.height(LINK_HEIGHT)) {
                        conversionSources(from, to).forEach { SourceLink(it, open) }
                    }
                }
                if (ammaOpinions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                        SectionTitle(TODAY_TITLE)
                        ammaOpinions.forEach { opinion ->
                            OpinionRow(
                                label = opinion.label,
                                value = etzbaos?.let { length(it / ETZBAOS_IN_AMMA * opinion.cm) },
                                source = opinion.source,
                                accent = accent,
                                open = open,
                            )
                        }
                    }
                }
                if (milOpinions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                        SectionTitle(WALK_TITLE)
                        milOpinions.forEach { opinion ->
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
}

// Every block a fixed height: the card's is worked out from the opinions shown (heightAt)
private val PADDING = 8.dp
private val TITLE_HEIGHT = 22.dp
private val INPUT_HEIGHT = 30.dp
private val RESULT_HEIGHT = 28.dp
private val LINK_HEIGHT = 16.dp
private val SECTION_TITLE_HEIGHT = 16.dp
private val OPINION_ROW_HEIGHT = 20.dp
private val BLOCK_GAP = 8.dp
private val ROW_GAP = 2.dp

private const val TODAY_TITLE = "בימינו"
private const val WALK_TITLE = "זמן הליכה"

/** The ids of the opinions shown, after [options]: every one until the user picks. */
internal fun shownOpinions(options: String?): Set<String> =
    options?.split(',')?.filter { it.isNotEmpty() }?.toSet() ?: (AMMA_OPINIONS.map { it.id } + MIL_OPINIONS.map { it.id }).toSet()

/** A box for each opinion, by kind, with its figure and source; each tick shows or hides it at once. */
@Composable
private fun MeasuresOptions(state: HomeWidgetsState) {
    val shown = shownOpinions(state.optionsOf(MeasuresWidget))
    val toggle = { id: String, on: Boolean -> state.setOptions(MeasuresWidget, (if (on) shown + id else shown - id).joinToString(",")) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OptionsGroup(TODAY_TITLE)
        AMMA_OPINIONS.forEach { OptionRow(it.id, it.label, "אמה ${length(it.cm)} · ${it.source.ref}", it.id in shown, toggle) }
        OptionsGroup(WALK_TITLE)
        MIL_OPINIONS.forEach { OptionRow(it.id, it.label, it.source.ref, it.id in shown, toggle) }
    }
}

@Composable
private fun OptionsGroup(title: String) {
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

@Composable
private fun OptionRow(
    id: String,
    label: String,
    detail: String,
    checked: Boolean,
    onToggle: (String, Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CheckboxRow(
            text = label,
            checked = checked,
            onCheckedChange = { onToggle(id, it) },
            modifier = Modifier.weight(1f).testTag("measures-option-$id"),
        )
        Text(detail, fontSize = 12.sp, color = JewelTheme.globalColors.text.info, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = JewelTheme.globalColors.text.info,
        maxLines = 1,
        modifier = Modifier.height(SECTION_TITLE_HEIGHT),
    )
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
    Row(
        Modifier.fillMaxWidth().height(OPINION_ROW_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
    val id: String,
    val label: String,
    val cm: Double,
    val source: Source,
)

internal val AMMA_OPINIONS =
    listOf(
        // "ומדת האמה 58 ס"מ" (קונטרס השיעורים)
        AmmaOpinion("chazon_ish", "חזון איש", 58.0, Source("חזו״א, קונטרס השיעורים לט, ט", "חזון איש, אורח חיים מועד", 1533)),
        // "אמה של תורה שהוא כ"ד אצבעות מדת כ"א אינטשעס ורביע"
        AmmaOpinion("igros_moshe", "אגרות משה", 21.25 * 2.54, Source("אגרות משה או״ח א, קלו", "אגרות משה אורח חיים א", 1256)),
    )

internal class MilOpinion(
    val id: String,
    val label: String,
    val minutes: Double,
    val source: Source,
)

private val BIUR_HALACHA_459_2 = Source("ביאור הלכה תנט, ב", "ביאור הלכה", 4452)

internal val MIL_OPINIONS =
    listOf(
        // "ושיעור מיל הוי רביעית שעה וחלק מעשרים מן השעה"
        MilOpinion("mil_18", "מיל 18 דק׳", 18.0, Source("שו״ע או״ח תנט, ב", "שולחן ערוך, אורח חיים", 3402)),
        // "שחושבין שיעור מיל לחשבון כ"ב מינוטין וחצי"
        MilOpinion("mil_22_5", "מיל 22.5 דק׳", 22.5, BIUR_HALACHA_459_2),
        // "שליש שעה וחלק ט"ו מן השעה"
        MilOpinion("mil_24", "מיל 24 דק׳", 24.0, BIUR_HALACHA_459_2),
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
