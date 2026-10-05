package io.github.kdroidfilter.seforimapp.features.home.widgets.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.HoverBox
import io.github.kdroidfilter.seforimapp.features.home.widgets.PanelCard
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetMenuItem
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetTitle
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_dictionary_all
import seforimapp.seforimapp.generated.resources.home_dictionary_back
import seforimapp.seforimapp.generated.resources.home_dictionary_failed
import seforimapp.seforimapp.generated.resources.home_dictionary_hint
import seforimapp.seforimapp.generated.resources.home_dictionary_loading
import seforimapp.seforimapp.generated.resources.home_dictionary_not_found
import seforimapp.seforimapp.generated.resources.home_dictionary_open_book
import seforimapp.seforimapp.generated.resources.home_dictionary_options_title
import seforimapp.seforimapp.generated.resources.home_dictionary_random
import seforimapp.seforimapp.generated.resources.home_dictionary_retry
import seforimapp.seforimapp.generated.resources.home_widget_name_dictionary
import seforimapp.seforimapp.generated.resources.home_widgets_options
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// The library's dictionaries in one card: a word looked up in one of them or in all, its entry read in the card, and
// opened in its book from there.

/** A dictionary of the library, by its book's [bookTitle]; each one marks its words in its own way (see [parse]). */
internal enum class Dictionary(
    val bookTitle: String,
    val label: String,
) {
    ARUKH("ספר הערוך", "הערוך"),
    HAFLAAH("הפלאה שבערכין על ספר הערוך", "הפלאה שבערכין"),
    LAAZEI_RASHI("אוצר לעזי רש\"י", "לעזי רש״י"),
    SHORASHIM("ספר השרשים לרדק", "השרשים לרד״ק"),
}

/** A word of [dictionary], the start of its explanation, and its lines in the book, [first] to [last]. */
@Immutable
internal data class DictionaryEntry(
    val word: String,
    val gloss: String,
    val dictionary: Dictionary,
    val bookId: Long,
    val lineId: Long,
    val first: Int,
    val last: Int = first,
) {
    val key = normalizeWord(word.replace(CORRECTED, ""))
}

private const val RESULT_LIMIT = 50
private const val GLOSS_LENGTH = 160
private const val PAGE_LINES = 300

private val TAG = Regex("<[^>]+>")

// הערוך: <b><big>אגמא</big></b> [German]. explanation
private val ARUKH_ENTRY = Regex("""^<b><big>(.+?)</big></b>\s*(.*)""")

// Its words come with a German translation in brackets, of no use to its readers today
private val GERMAN = Regex("""^\[[^]]*]\.?\s*""")

// הפלאה שבערכין: <b>אבלא</b> explanation
private val BOLD_ENTRY = Regex("""^<b>(.+?)</b>\s*(.*)""")

// לעזי רש"י: 17 / (ברכות כד:) / <b>סנטר</b> מינטו"ן / menton / <b>סנטר</b> notes
private val LAAZ_ENTRY = Regex("""^\d+ / \(([^)]*)\) / <b>(.+?)</b>([^/]*)/([^/]*)/\s*<b>(.+?)</b>""")

// A few have their fields otherwise divided: the rest of the line explains them
private val LAAZ_LOOSE = Regex("""^\d+ / \(([^)]*)\) / <b>(.+?)</b>(.*)""")

// A word the editor set right: (סנטרו) [סנטר] is looked up as סנטר
private val CORRECTED = Regex("""\([^)]*\)|[\[\]]""")

private val ROOT = Regex("""^<h3>(.+?)</h3>""")

private fun String.plain() = replace(TAG, "").replace("&nbsp;", " ").trim()

// The end of a letter (נשלם אות הבית), and the רד״ק's list of Aramaic words by book (בספר דניאל)
private val NOT_A_WORD = Regex("""^(נשלם|סליק|בספר )""")

/** From the first letter's heading (אות האל״ף) on: the preface's bold lines and headings aren't words. */
private fun List<Line>.fromFirstLetter() = dropWhile { !(it.content.startsWith("<h2") && it.content.plain().startsWith("אות")) }

/** [lines] of [bookId], the book of this dictionary, as its entries; its headings and other lines left out. */
internal fun Dictionary.parse(
    bookId: Long,
    lines: List<Line>,
): List<DictionaryEntry> {
    fun entry(
        line: Line,
        word: String,
        gloss: String,
    ) = DictionaryEntry(word.plain().trimEnd('.', ':', ','), gloss.plain().take(GLOSS_LENGTH), this, bookId, line.id, line.lineIndex)

    val entries =
        when (this) {
            Dictionary.ARUKH ->
                lines.mapNotNull { line ->
                    ARUKH_ENTRY.find(line.content)?.destructured?.let { (word, rest) ->
                        entry(line, word, rest.plain().replace(GERMAN, ""))
                    }
                }
            Dictionary.HAFLAAH ->
                lines.fromFirstLetter().mapNotNull { line ->
                    BOLD_ENTRY.find(line.content)?.destructured?.let { (word, rest) -> entry(line, word, rest) }
                }
            Dictionary.LAAZEI_RASHI ->
                lines.mapNotNull { line ->
                    LAAZ_ENTRY.find(line.content)?.destructured?.let { (ref, word, laaz, french, meaning) ->
                        entry(line, word, "${meaning.plain()} · ${laaz.plain()} (${french.plain()}) · $ref")
                    }
                        ?: LAAZ_LOOSE.find(line.content)?.destructured?.let { (ref, word, rest) ->
                            entry(line, word, "${rest.plain()} · $ref")
                        }
                }
            // A root's heading, then its explanation on the lines up to the next heading
            Dictionary.SHORASHIM -> {
                val book = lines.fromFirstLetter()
                book.mapIndexedNotNull { i, line ->
                    val root = ROOT.find(line.content)?.groupValues?.get(1) ?: return@mapIndexedNotNull null
                    val body = book.subList(i + 1, book.size).takeWhile { !it.content.startsWith("<h") }
                    entry(line, root, body.firstOrNull()?.content.orEmpty()).copy(last = body.lastOrNull()?.lineIndex ?: line.lineIndex)
                }
            }
        }
    return entries.filterNot { it.word.isEmpty() || NOT_A_WORD.containsMatchIn(it.word) }
}

// Nikud, teamim, geresh and quotes: אַגְמָא and אגמא are one word
private val MARKS = Regex("""[֑-ׇ'"׳״\s]""")

// A final letter is the same letter, so a word half typed (אגמ) finds the whole one (אגם)
private val FINALS = mapOf('ך' to 'כ', 'ם' to 'מ', 'ן' to 'נ', 'ף' to 'פ', 'ץ' to 'צ')

internal fun normalizeWord(word: String) = word.replace(MARKS, "").map { FINALS[it] ?: it }.joinToString("")

/** The entries whose word starts with [query], the word itself first; the dictionaries' order otherwise. */
internal fun lookup(
    entries: List<DictionaryEntry>,
    query: String,
): List<DictionaryEntry> {
    val q = normalizeWord(query)
    if (q.isEmpty()) return emptyList()
    return entries.filter { it.key.startsWith(q) }.sortedBy { it.key != q }.take(RESULT_LIMIT)
}

// ponytail: each dictionary read once for the app's life (~16,000 short entries in all, a few MB); a new library DB
// shows after a restart. An empty list: the library has no such book.
private val cache = ConcurrentHashMap<Dictionary, List<DictionaryEntry>>()

// Two windows showing the card read each dictionary once: the second one waits, then finds it in the cache
private val reading = Mutex()

/** [dictionaries] as read so far, or null while one of them is still to be read. */
private fun cached(dictionaries: List<Dictionary>): Map<Dictionary, List<DictionaryEntry>>? =
    dictionaries.associateWith { cache[it] ?: return null }

/** [dictionaries]' entries, each read once; one that fails to read is left out of the map, to be tried again. */
private suspend fun readDictionaries(
    graph: AppGraph,
    dictionaries: List<Dictionary>,
): Map<Dictionary, List<DictionaryEntry>> =
    reading.withLock {
        dictionaries
            .mapNotNull { dictionary ->
                cache[dictionary]?.let { return@mapNotNull dictionary to it }
                runCatching {
                    withContext(Dispatchers.Default) {
                        // The repository opens the library DB: never while the card composes, so a DB missing fails here
                        val repository = graph.repository
                        repository
                            .getBookByTitle(dictionary.bookTitle)
                            ?.let { book ->
                                // The library DB has one connection, held through each query: read in short
                                // pages, so a book opened meanwhile waits for one page, not for the whole dictionary
                                val lines =
                                    (0 until book.totalLines step PAGE_LINES).flatMap { start ->
                                        repository.getLines(book.id, start, start + PAGE_LINES - 1)
                                    }
                                dictionary.parse(book.id, lines)
                            }.orEmpty()
                    }
                }.onFailure { if (it is CancellationException) throw it }
                    .getOrNull()
                    ?.also { cache[dictionary] = it }
                    ?.let { dictionary to it }
            }.toMap()
    }

/**
 * The dictionaries searched, ticked in the options page, and the one of them its chip [picked] (null: all of them),
 * saved together as `ARUKH,HAFLAAH|ARUKH`.
 */
internal data class DictionaryOptions(
    val shown: List<Dictionary> = Dictionary.entries,
    val picked: Dictionary? = null,
) {
    fun encode() = shown.joinToString(",") { it.name } + "|" + picked?.name.orEmpty()

    companion object {
        fun decode(options: String?): DictionaryOptions {
            if (options == null) return DictionaryOptions()
            // Saved before the options page: the chip alone
            val (shownPart, pickedPart) = if ('|' in options) options.split('|', limit = 2) else listOf("", options)

            fun named(name: String) = Dictionary.entries.find { it.name == name }
            // None ticked can't be: every one, as at first
            val shown =
                shownPart
                    .split(',')
                    .mapNotNull(::named)
                    .ifEmpty { Dictionary.entries }
                    .sortedBy { it.ordinal }
            return DictionaryOptions(shown, named(pickedPart)?.takeIf { it in shown })
        }
    }
}

/** A word typed, its entries in the dictionary picked (or in all) below; one clicked reads in the card. */
internal object DictionaryWidget : HomeWidget {
    override val id = "dictionary"
    override val title = Res.string.home_widget_name_dictionary
    override val defaultSpan = CellSpan(6, 5)
    override val toolWindowSize = DpSize(460.dp, 560.dp)
    override val toolWindowMinSize = DpSize(340.dp, 380.dp)
    override val toolSymbol = "character.book.closed"
    override val minSpan = CellSpan(4, 4)
    override val maxSpan = CellSpan(10, 10)

    // Its dictionaries ticked in a page of their own, as the limudim and the measures' opinions
    @Composable
    override fun menuItems(state: HomeWidgetsState) =
        listOf(
            WidgetMenuItem(stringResource(Res.string.home_widgets_options), icon = AllIconsKeys.General.Settings) {
                state.optionsOpen = this
            },
        )

    @Composable
    override fun Options(state: HomeWidgetsState) = DictionaryOptionsPage(state)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val graph = LocalAppGraph.current
        val options = DictionaryOptions.decode(state.optionsOf(this))
        // null: all those ticked; one the library no longer has, all of them too rather than none
        val picked = options.picked?.takeUnless { cache[it]?.isEmpty() == true }
        val needed = picked?.let(::listOf) ?: options.shown
        // The one dictionary searched, picked or the only one ticked: its name is in the field, not on each word
        val searched = picked ?: options.shown.singleOrNull()
        // Read on first use (the field focused, a dictionary or a random word asked for), not as soon as the Home shows
        var wanted by remember { mutableStateOf(false) }
        var attempt by remember { mutableIntStateOf(0) }
        val read by produceState(cached(needed), graph, needed, wanted, attempt) {
            // Another choice's map would show this one's dictionaries as failed while they're read
            value = cached(needed)
            if (wanted || value != null) value = readDictionaries(graph, needed)
        }
        val failed = needed.filter { read != null && it !in read.orEmpty() }
        val pool = remember(read, needed) { needed.flatMap { read?.get(it).orEmpty() } }
        val query = rememberTextFieldState()
        val results = remember(pool, query.text) { lookup(pool, query.text.toString()) }
        var opened by remember { mutableStateOf<DictionaryEntry?>(null) }
        val accent = rememberAccentColor(JewelTheme.isDark)
        val allLabel = stringResource(Res.string.home_dictionary_all)

        PanelCard(modifier) {
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                opened?.let { entry ->
                    EntryPage(entry, accent, onBack = { opened = null }) {
                        state.openTab(
                            TabsDestination.BookContent(bookId = entry.bookId, tabId = UUID.randomUUID().toString(), lineId = entry.lineId),
                        )
                    }
                    return@Column
                }
                WidgetTitle(stringResource(Res.string.home_widget_name_dictionary)) {
                    // Its options at hand, as in its menu
                    IconButton(
                        onClick = { state.optionsOpen = DictionaryWidget },
                        modifier = Modifier.size(22.dp).testTag("dictionary-settings"),
                    ) {
                        Icon(
                            key = AllIconsKeys.General.Settings,
                            contentDescription = stringResource(Res.string.home_widgets_options),
                            tint = JewelTheme.globalColors.text.info,
                        )
                    }
                }
                // Chips only to choose between two dictionaries at least
                val available = options.shown.filterNot { cache[it]?.isEmpty() == true }
                if (available.size > 1) {
                    DictionaryChips(available, picked, allLabel, accent) {
                        wanted = true
                        state.setOptions(this@DictionaryWidget, options.copy(picked = it).encode())
                    }
                }
                TextField(
                    state = query,
                    placeholder = { Text(stringResource(Res.string.home_dictionary_hint, searched?.label ?: allLabel)) },
                    leadingIcon = { Icon(AllIconsKeys.Actions.Find, null, Modifier.padding(end = 6.dp)) },
                    trailingIcon = {
                        if (query.text.isNotEmpty()) {
                            Icon(
                                AllIconsKeys.Actions.Close,
                                null,
                                Modifier.clickable { query.clearText() }.pointerHoverIcon(PointerIcon.Hand),
                            )
                        }
                    },
                    textStyle = TextStyle(fontSize = 14.sp),
                    // Enter reads the first entry found
                    modifier =
                        Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) wanted = true }.onPreviewKeyEvent { event ->
                            val first = results.firstOrNull()
                            if (event.type == KeyEventType.KeyUp && event.key == Key.Enter && first != null) {
                                opened = first
                                true
                            } else {
                                false
                            }
                        },
                )
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        read == null && !wanted -> AskRandomWord { wanted = true }
                        read == null -> Hint(stringResource(Res.string.home_dictionary_loading))
                        failed.isNotEmpty() && pool.isEmpty() ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { FailedNotice(accent) { attempt++ } }
                        query.text.isBlank() -> RandomWord(pool, searched == null, accent) { opened = it }
                        results.isEmpty() -> Hint(stringResource(Res.string.home_dictionary_not_found))
                        else ->
                            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                items(results, key = { it.lineId }) { entry ->
                                    EntryRow(entry, showDictionary = searched == null, accent = accent) { opened = entry }
                                }
                            }
                    }
                }
                // The ones read still answer; the others are named lost, to be tried again
                if (failed.isNotEmpty() && pool.isNotEmpty()) FailedNotice(accent) { attempt++ }
            }
        }
    }
}

/** A box for each dictionary, with its book; each tick adds it to the search or takes it out at once, the last one kept. */
@Composable
private fun DictionaryOptionsPage(state: HomeWidgetsState) {
    val options = DictionaryOptions.decode(state.optionsOf(DictionaryWidget))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(Res.string.home_dictionary_options_title),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
        )
        Dictionary.entries.forEach { dictionary ->
            val ticked = dictionary in options.shown
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CheckboxRow(
                    text = dictionary.label,
                    checked = ticked,
                    onCheckedChange = { on ->
                        val shown = if (on) options.shown + dictionary else options.shown - dictionary
                        state.setOptions(DictionaryWidget, options.copy(shown = shown).encode())
                    },
                    // The search needs one dictionary at least
                    enabled = !ticked || options.shown.size > 1,
                    modifier = Modifier.weight(1f).testTag("dictionary-option-${dictionary.name}"),
                )
                Text(
                    dictionary.bookTitle,
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.info,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "All" and each dictionary the library has, the one picked lit; a row that scrolls when the card is narrow. */
@Composable
private fun DictionaryChips(
    available: List<Dictionary>,
    picked: Dictionary?,
    allLabel: String,
    accent: Color,
    onPick: (Dictionary?) -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (listOf<Dictionary?>(null) + available).forEach { dictionary ->
            val lit = dictionary == picked
            Text(
                text = dictionary?.label ?: allLabel,
                fontSize = 12.sp,
                fontWeight = if (lit) FontWeight.SemiBold else FontWeight.Normal,
                color = if (lit) accent else JewelTheme.globalColors.text.info,
                maxLines = 1,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (lit) {
                                accent.copy(alpha = 0.16f)
                            } else {
                                JewelTheme.globalColors.text.normal
                                    .copy(alpha = 0.05f)
                            },
                        ).clickable { onPick(dictionary) }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

/** Before the dictionaries are read: a random word on request, which reads them. */
@Composable
private fun AskRandomWord(onAsk: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        HoverBox(onClick = onAsk) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(AllIconsKeys.Actions.Refresh, null, tint = JewelTheme.globalColors.text.info)
                Text(stringResource(Res.string.home_dictionary_random), fontSize = 13.sp, color = JewelTheme.globalColors.text.info)
            }
        }
    }
}

/** A word of [pool] drawn at random while nothing is typed, drawn again with its button. */
@Composable
private fun RandomWord(
    pool: List<DictionaryEntry>,
    showDictionary: Boolean,
    accent: Color,
    onOpen: (DictionaryEntry) -> Unit,
) {
    var draw by remember { mutableIntStateOf(0) }
    val entry = remember(pool, draw) { pool.randomOrNull() } ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.home_dictionary_random),
                fontSize = 11.sp,
                color = JewelTheme.globalColors.text.info,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { draw++ }, modifier = Modifier.size(22.dp)) {
                Icon(
                    AllIconsKeys.Actions.Refresh,
                    stringResource(Res.string.home_dictionary_random),
                    tint = JewelTheme.globalColors.text.info,
                )
            }
        }
        HoverBox(onClick = { onOpen(entry) }, tinted = true, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        entry.word,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (showDictionary) Tag(entry.dictionary.label)
                }
                Text(entry.gloss, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Some dictionaries couldn't be read (the library DB missing, say), with a way to try again. */
@Composable
private fun FailedNotice(
    accent: Color,
    onRetry: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.home_dictionary_failed), fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
        Text(
            stringResource(Res.string.home_dictionary_retry),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier.clickable(onClick = onRetry).pointerHoverIcon(PointerIcon.Hand),
        )
    }
}

/** A word found: the word, its dictionary when all are searched, and the start of its explanation. */
@Composable
private fun EntryRow(
    entry: DictionaryEntry,
    showDictionary: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    HoverBox(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    entry.word,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (showDictionary) Tag(entry.dictionary.label)
            }
            Text(entry.gloss, fontSize = 12.sp, color = JewelTheme.globalColors.text.info, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** An entry read in full, as its book has it, with the way back to the search and on to the book. */
@Composable
private fun EntryPage(
    entry: DictionaryEntry,
    accent: Color,
    onBack: () -> Unit,
    onOpenBook: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val lines by produceState(emptyList<Line>(), entry) {
        value = runCatching { graph.repository.getLines(entry.bookId, entry.first, entry.last) }.getOrDefault(emptyList())
    }
    // Back points to where reading came from: the start of the reading direction
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(24.dp)) {
                Icon(
                    if (rtl) AllIconsKeys.General.ChevronRight else AllIconsKeys.General.ChevronLeft,
                    stringResource(Res.string.home_dictionary_back),
                )
            }
            Text(entry.word, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1, modifier = Modifier.weight(1f))
            Tag(entry.dictionary.label)
        }
        val textColor = JewelTheme.globalColors.text.normal
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.forEach { line ->
                Text(
                    buildAnnotatedFromHtml(line.content, baseTextSize = 14f, boldColor = accent),
                    fontSize = 14.sp,
                    color = textColor,
                    lineHeight = 22.sp,
                )
            }
        }
        OutlinedButton(onClick = onOpenBook, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(Res.string.home_dictionary_open_book, entry.dictionary.label))
        }
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        color = JewelTheme.globalColors.text.info,
        maxLines = 1,
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    JewelTheme.globalColors.text.info
                        .copy(alpha = 0.12f),
                ).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = JewelTheme.globalColors.text.info)
    }
}
