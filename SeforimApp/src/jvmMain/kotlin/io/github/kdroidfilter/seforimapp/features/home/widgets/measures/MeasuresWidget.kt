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
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.HoverBox
import io.github.kdroidfilter.seforimapp.features.home.widgets.PanelCard
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetMenuItem
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetSettings
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
import kotlin.math.pow

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

    override val settings get() = MeasuresSettings

    // The opinions shown, as saved: read by the grid for the card's height as well as by the card
    // Not MeasuresSettings.shown.default: MeasuresSettings needs this object's id, so reading it here is a cycle
    private var shown by mutableStateOf(ALL_OPINION_IDS)

    override fun applySettings(appSettings: AppSettings) {
        shown = shownOpinions(appSettings[MeasuresSettings.shown], appSettings[MeasuresSettings.offered])
    }

    /** Tall enough for every opinion picked in either kind, so switching kind never resizes the card. */
    override fun heightAt(width: Dp): Dp = Kind.entries.maxOf { heightOf(it) }

    internal fun heightOf(kind: Kind): Dp {
        var height = PADDING * 2 + TITLE_HEIGHT + BLOCK_GAP + INPUT_HEIGHT + BLOCK_GAP + RESULT_HEIGHT + LINK_HEIGHT
        // A section with no opinion shown is left out, its title too; volume has no walking time
        val walk = if (kind == Kind.LENGTH) MIL_OPINIONS.count { it.id in shown } else 0
        for (rows in listOf(opinionsFor(kind, shown).size, walk)) {
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
        var kind by remember { mutableStateOf(Kind.LENGTH) }
        // Each kind starts on its own pair of units
        var from by remember(kind) { mutableStateOf(kind.defaultFrom) }
        var to by remember(kind) { mutableStateOf(kind.defaultTo) }
        val typed = field.text.trim().toString()
        val amount = typed.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
        val opinions = opinionsFor(kind, shown)
        // Walking time is per mil: only for a Torah length, a metric one would first need an amma opinion
        val walkFrom = from as? LengthUnit
        val milOpinions = if (walkFrom != null) MIL_OPINIONS.filter { it.id in shown } else emptyList()
        // Between a Torah measure and today's, the answer is the opinion's: one per opinion, the range on top
        val perOpinion = needsOpinion(from, to)
        val main =
            amount?.let { a ->
                if (perOpinion) {
                    opinions.mapNotNull { convert(a, from, to, it) }.takeIf { it.isNotEmpty() }?.let { range(it.min(), it.max(), to) }
                } else {
                    convert(a, from, to, null)?.let { quantity(it, to) }
                }
            }
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
                    Kind.entries.forEach { k ->
                        HoverBox(onClick = { kind = k }, tinted = k == kind, modifier = Modifier.testTag("measures-kind-${k.name}")) {
                            Text(
                                k.title,
                                fontSize = 12.sp,
                                fontWeight = if (k == kind) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (k == kind) accent else JewelTheme.globalColors.text.info,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
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
                    UnitPicker(kind, from, onPick = { from = it }, Modifier.weight(1f))
                    HoverBox(onClick = {
                        val was = from
                        from = to
                        to = was
                    }) {
                        Text("⇄", fontSize = 16.sp, color = accent, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    UnitPicker(kind, to, onPick = { to = it }, Modifier.weight(1f))
                }
                Column {
                    Text(
                        main?.let { "= $it" } ?: "–",
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
                if (opinions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                        // A revi'is today is worked out from the etzba: cite where it is measured in etzbaos
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle(if (from is MetricUnit || to is MetricUnit) BY_OPINION_TITLE else TODAY_TITLE)
                            if (kind == Kind.VOLUME) SourceLink(PESACHIM_109A, open, Modifier.height(SECTION_TITLE_HEIGHT))
                        }
                        opinions.forEach { opinion ->
                            OpinionRow(
                                label = opinion.label,
                                value = amount?.let { opinionValue(it, from, to, opinion) },
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
                                value = amount?.let { a -> walkFrom?.let { duration(walkMinutes(a, it, opinion) * 60) } },
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
private const val BY_OPINION_TITLE = "לפי השיטות"

// The opinions offered before גר״ח נאה was added: the ones a choice saved by then was made among
private val FIRST_OPINION_IDS = setOf("chazon_ish", "igros_moshe", "mil_18", "mil_22_5", "mil_24")

internal object MeasuresSettings : WidgetSettings(MeasuresWidget.id) {
    /** The ids of the opinions ticked: every one until the user picks. */
    val shown by stringSet(ALL_OPINION_IDS)

    /**
     * The ids offered when [shown] was last saved. An opinion added since is shown until the user unticks it, so a
     * choice saved before it was added doesn't hide it.
     */
    val offered by stringSet(FIRST_OPINION_IDS)
}

/** The opinions to show: those ticked, and any added since the ticks were saved ([offered] lacks them). */
internal fun shownOpinions(
    shown: Set<String>,
    offered: Set<String>,
): Set<String> = shown + (ALL_OPINION_IDS - offered)

/** A box for each opinion, by kind, with its figure and source; each tick shows or hides it at once. */
@Composable
private fun MeasuresOptions(state: HomeWidgetsState) {
    val shown = shownOpinions(state[MeasuresSettings.shown], state[MeasuresSettings.offered])
    val toggle = { id: String, on: Boolean ->
        state[MeasuresSettings.shown] = if (on) shown + id else shown - id
        // Every opinion now offered was seen: from here on only the ticks say what is shown
        state[MeasuresSettings.offered] = ALL_OPINION_IDS
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OptionsGroup(TODAY_TITLE)
        AMMA_OPINIONS.forEach {
            // The revi'is it gives, where it gives one
            val reviis = if (it.derivesVolume) " · רביעית ${volume(it.reviisMl)}" else ""
            OptionRow(it.id, it.label, "אמה ${length(it.cm)}$reviis · ${it.source.ref}", it.id in shown, toggle)
        }
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
    kind: Kind,
    unit: Measure,
    onPick: (Measure) -> Unit,
    modifier: Modifier = Modifier,
) {
    val units = kind.units
    ListComboBox(
        items = units.map { it.title },
        selectedIndex = units.indexOf(unit).coerceAtLeast(0),
        onSelectedItemChange = { onPick(units[it]) },
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
internal fun SourceLink(
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

/**
 * What says how many [from] are in [to] or back: each unit's source from the smaller one's next to the larger. A metric
 * unit has none of its own: it goes through the Torah unit its kind's opinions are given in (the amma, the revi'is).
 */
internal fun conversionSources(
    from: Measure,
    to: Measure,
): List<Source> {
    val units = from.kind.halachicUnits
    val a = units.indexOf(from as? HalachicUnit ?: from.kind.anchor)
    val b = units.indexOf(to as? HalachicUnit ?: to.kind.anchor)
    return units
        .filterIndexed { i, _ -> i > minOf(a, b) && i <= maxOf(a, b) }
        .map { it.source }
        .distinct()
}

/** Opens [Source] in a new tab, on its line. */
@Composable
internal fun rememberOpenSource(state: HomeWidgetsState): (Source) -> Unit {
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

// "רביעית של תורה אצבעים על אצבעים ברום אצבעים וחצי אצבע וחומש אצבע": 2 × 2 × 2.7 cubic etzbaos
private const val REVIIS_CUBIC_ETZBAOS = 2.0 * 2.0 * 2.7
internal val PESACHIM_109A = Source("פסחים קט, א", "פסחים", 3388)

/** What is measured: each kind its Torah units, then today's. */
internal enum class Kind(
    val title: String,
) {
    LENGTH("אורך"),
    VOLUME("נפח"),
    ;

    // Getters, not constructor values: the units' enums refer back to their kind
    val halachicUnits: List<HalachicUnit>
        get() =
            when (this) {
                LENGTH -> LengthUnit.entries
                VOLUME -> VolumeUnit.entries
            }

    /** The unit picker's list: the Torah units, smallest first, then the metric ones. */
    val units: List<Measure> get() = halachicUnits + MetricUnit.entries.filter { it.kind == this }

    /** The Torah unit an opinion gives today's size of, which every metric unit converts through. */
    val anchor: HalachicUnit
        get() =
            when (this) {
                LENGTH -> LengthUnit.AMMA
                VOLUME -> VolumeUnit.REVIIS
            }

    val defaultFrom: Measure get() = if (this == LENGTH) LengthUnit.AMMA else VolumeUnit.LOG
    val defaultTo: Measure get() = if (this == LENGTH) LengthUnit.TEFACH else VolumeUnit.REVIIS
}

/** A unit of the picker: a Torah measure or a metric one. */
internal sealed interface Measure {
    val title: String
    val plural: String
    val kind: Kind
}

/** A Torah measure, [size] in its kind's smallest unit (etzbaos, revi'iyos), with what says so. */
internal sealed interface HalachicUnit : Measure {
    val size: Double
    val source: Source
}

/** The lengths, in etzbaos, with what says so. */
internal enum class LengthUnit(
    override val title: String,
    override val plural: String,
    val etzbaos: Double,
    override val source: Source,
) : HalachicUnit {
    // "האצבע… רחב הגודל של יד. והטפח ארבע אצבעות… אמה בת ששה טפחים"
    ETZBA("אצבע", "אצבעות", 1.0, RAMBAM_SHABBOS_17_36),
    TEFACH("טפח", "טפחים", 4.0, RAMBAM_SHABBOS_17_36),
    AMMA("אמה", "אמות", ETZBAOS_IN_AMMA, RAMBAM_SHABBOS_17_36),

    // "ארבעה מילין שהם שמונת אלפים אמה"
    MIL("מיל", "מילין", ETZBAOS_IN_AMMA * AMOS_IN_MIL, RAMBAM_TEFILLAH_4_2),

    // "מיל הוא אלפים אמה ופרסה הוא ד' מילין"
    PARSAH("פרסה", "פרסאות", ETZBAOS_IN_AMMA * AMOS_IN_MIL * 4, MISHNAH_BERURAH_110_31),
    ;

    override val size get() = etzbaos
    override val kind get() = Kind.LENGTH
}

private val RAMBAM_ERUVIN_1_13 = Source("רמב״ם עירובין א, יג", "משנה תורה, הלכות עירובין", 15)
private val RAMBAM_ARACHIN_4_4 = Source("רמב״ם ערכין ד, ד", "משנה תורה, הלכות ערכים וחרמין", 65)

/** The volumes, in revi'iyos, with what says so. */
internal enum class VolumeUnit(
    override val title: String,
    override val plural: String,
    val reviios: Double,
    override val source: Source,
) : HalachicUnit {
    // "סאה האמורה בכל מקום ששת קבין. והקב ארבעה לוגין. והלוג ארבע רביעיות"
    REVIIS("רביעית", "רביעיות", 1.0, RAMBAM_ERUVIN_1_13),
    LOG("לוג", "לוגין", 4.0, RAMBAM_ERUVIN_1_13),
    KAV("קב", "קבין", 16.0, RAMBAM_ERUVIN_1_13),
    SEAH("סאה", "סאין", 96.0, RAMBAM_ERUVIN_1_13),

    // "החמר הוא הכור… נמצא החמר שלשים סאה שהן עשר איפות כל שלש סאין איפה"
    EIFAH("איפה", "איפות", 288.0, RAMBAM_ARACHIN_4_4),
    KOR("כור", "כורים", 2880.0, RAMBAM_ARACHIN_4_4),
    ;

    override val size get() = reviios
    override val kind get() = Kind.VOLUME
}

/** Today's units, [size] in centimetres or millilitres: no source, their size in Torah units is the opinion's. */
internal enum class MetricUnit(
    override val title: String,
    override val kind: Kind,
    val size: Double,
) : Measure {
    MM("מ״מ", Kind.LENGTH, 0.1),
    CM("ס״מ", Kind.LENGTH, 1.0),
    METER("מטר", Kind.LENGTH, 100.0),
    KM("ק״מ", Kind.LENGTH, 100_000.0),
    ML("מ״ל", Kind.VOLUME, 1.0),
    LITER("ליטר", Kind.VOLUME, 1000.0),
    ;

    // "3 מטר", "2 ליטר": the same word for any number
    override val plural get() = title
}

internal class AmmaOpinion(
    val id: String,
    val label: String,
    val cm: Double,
    val source: Source,
    /**
     * Whether its revi'is is its etzba cubed (10.8 cubic etzbaos). Not so for the Igros Moshe: R' Moshe's own revi'is
     * is a separate ruling that doesn't follow from his amma, so cubing his amma would give a
     * misleading ~123 cc. It is left out of volumes rather than shown with a figure he didn't hold.
     */
    val derivesVolume: Boolean = true,
) {
    /** Its revi'is in millilitres (cc). */
    val reviisMl: Double get() = REVIIS_CUBIC_ETZBAOS * (cm / ETZBAOS_IN_AMMA).pow(3)

    /** Today's size (cm, ml) of [kind]'s smallest Torah unit, the etzba or the revi'is. */
    fun modernPerUnit(kind: Kind): Double =
        when (kind) {
            Kind.LENGTH -> cm / ETZBAOS_IN_AMMA
            Kind.VOLUME -> reviisMl
        }
}

// Smallest amma first
internal val AMMA_OPINIONS =
    listOf(
        // "וכל אצבע, שהוא ברוחב האגודל, שני סנטימטר... וכאשר ביאר הגר"ח נאה בספרו שיעורי תורה"
        AmmaOpinion(
            "naeh",
            "גר״ח נאה",
            48.0,
            Source("קיצור שו״ע ילקוט יוסף, תרל, ד", "קיצור שע ילקוט יוסף החדש - אורח חיים", 16940),
        ),
        // "אמה של תורה שהוא כ"ד אצבעות מדת כ"א אינטשעס ורביע"
        AmmaOpinion(
            "igros_moshe",
            "אגרות משה",
            21.25 * 2.54,
            Source("אגרות משה או״ח א, קלו", "אגרות משה אורח חיים א", 1256),
            derivesVolume = false,
        ),
        // "ומדת האמה 58 ס"מ" (קונטרס השיעורים)
        AmmaOpinion("chazon_ish", "חזון איש", 58.0, Source("חזו״א, קונטרס השיעורים לט, ט", "חזון איש, אורח חיים מועד", 1533)),
    )

/** The amma opinions shown for [kind]: for volume, only those whose revi'is follows from their amma. */
internal fun opinionsFor(
    kind: Kind,
    shown: Set<String>,
): List<AmmaOpinion> = AMMA_OPINIONS.filter { it.id in shown && (kind == Kind.LENGTH || it.derivesVolume) }

/** Whether going [from] → [to] crosses between Torah and metric units, so the answer depends on the opinion. */
internal fun needsOpinion(
    from: Measure,
    to: Measure,
): Boolean = (from is MetricUnit) != (to is MetricUnit)

/** [amount] [from] in [to] after [opinion]; null when the units need an opinion ([needsOpinion]) and none is given. */
internal fun convert(
    amount: Double,
    from: Measure,
    to: Measure,
    opinion: AmmaOpinion?,
): Double? {
    if (from is HalachicUnit && to is HalachicUnit) return amount * from.size / to.size
    val f = modernSize(from, opinion) ?: return null
    val t = modernSize(to, opinion) ?: return null
    return amount * f / t
}

/** [unit] in centimetres or millilitres; a Torah unit only after an opinion. */
private fun modernSize(
    unit: Measure,
    opinion: AmmaOpinion?,
): Double? =
    when (unit) {
        is MetricUnit -> unit.size
        is HalachicUnit -> opinion?.let { unit.size * it.modernPerUnit(unit.kind) }
    }

/**
 * An opinion's row for [amount] [from] → [to]: the answer in [to] when it depends on the opinion; else what the amount
 * is in the other system, today's units for a Torah measure, Torah ones for a metric one.
 */
internal fun opinionValue(
    amount: Double,
    from: Measure,
    to: Measure,
    opinion: AmmaOpinion,
): String? =
    when {
        needsOpinion(from, to) -> convert(amount, from, to, opinion)?.let { quantity(it, to) }
        from is HalachicUnit -> modern(amount * from.size * opinion.modernPerUnit(from.kind), from.kind)
        from is MetricUnit -> halachic(amount * from.size / opinion.modernPerUnit(from.kind), from.kind)
        else -> null
    }

/** Minutes to walk [amount] [from] at [opinion]'s pace. */
internal fun walkMinutes(
    amount: Double,
    from: LengthUnit,
    opinion: MilOpinion,
): Double = amount * from.etzbaos / LengthUnit.MIL.etzbaos * opinion.minutes

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

// After both lists: top-level values are initialised in the file's order
internal val ALL_OPINION_IDS = (AMMA_OPINIONS.map { it.id } + MIL_OPINIONS.map { it.id }).toSet()

/** [count] [unit]s, its name in the singular for one. */
internal fun quantity(
    count: Double,
    unit: Measure,
): String = "${amount(count)} ${if (count == 1.0) unit.title else unit.plural}"

/** [min] to [max] [unit]s, one figure when they round the same. */
internal fun range(
    min: Double,
    max: Double,
    unit: Measure,
): String = if (amount(min) == amount(max)) quantity(min, unit) else "${amount(min)}–${amount(max)} ${unit.plural}"

/** [x] cm or ml in the metric unit that suits it. */
internal fun modern(
    x: Double,
    kind: Kind,
): String =
    when (kind) {
        Kind.LENGTH -> length(x)
        Kind.VOLUME -> volume(x)
    }

/** [base] etzbaos or revi'iyos in the largest Torah unit it makes at least one of. */
internal fun halachic(
    base: Double,
    kind: Kind,
): String {
    val units = kind.halachicUnits
    val unit = units.lastOrNull { base >= it.size } ?: units.first()
    return quantity(base / unit.size, unit)
}

/** [cm] in millimetres, centimetres, metres or kilometres. */
internal fun length(cm: Double): String =
    when {
        cm > 0 && cm < 1 -> "${amount(cm * 10)} מ״מ"
        cm < 100 -> "${amount(cm)} ס״מ"
        cm < 100_000 -> "${amount(cm / 100)} מטר"
        else -> "${amount(cm / 100_000)} ק״מ"
    }

/** [ml] in millilitres or litres. */
internal fun volume(ml: Double): String =
    when {
        ml < 1000 -> "${amount(ml)} מ״ל"
        else -> "${amount(ml / 1000)} ליטר"
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
