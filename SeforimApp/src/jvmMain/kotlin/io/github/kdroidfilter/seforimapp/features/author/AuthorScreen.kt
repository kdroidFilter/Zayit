package io.github.kdroidfilter.seforimapp.features.author

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewDateFormatter
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.deeplink.parseZayitDeepLink
import io.github.kdroidfilter.seforimapp.core.presentation.components.EmptyState
import io.github.kdroidfilter.seforimapp.core.presentation.components.ProseMarkdown
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBar
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBarPosition
import io.github.kdroidfilter.seforimapp.core.presentation.utils.UrlOpener
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.ZoomButtons
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.PaneCard
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.WritingHand
import io.github.kdroidfilter.seforimapp.icons.bookOpenTabs
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.AuthorDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.author_bio_license
import seforimapp.seforimapp.generated.resources.author_books
import seforimapp.seforimapp.generated.resources.author_books_pane
import seforimapp.seforimapp.generated.resources.author_info_aliases
import seforimapp.seforimapp.generated.resources.author_info_era
import seforimapp.seforimapp.generated.resources.author_info_pane
import seforimapp.seforimapp.generated.resources.author_info_years
import seforimapp.seforimapp.generated.resources.author_not_found
import seforimapp.seforimapp.generated.resources.author_sources
import seforimapp.seforimapp.generated.resources.notoserifhebrew
import java.util.UUID

/** From this width the page opens with both side panes. */
private val WIDE_LAYOUT = 1100.dp
private val SIDE_PANE = 280.dp
private const val NAME_SCALE = 1.75f
private const val LINE_SPACING = 1.75f

/**
 * An author's page, laid out like a book's: the biography in the middle pane at the app's text size
 * (the zoom buttons apply), the author's books in a pane at the start, and a pane at the end with
 * the era, years, aliases and the biography's sources. Both side panes toggle from the side bars.
 */
@Composable
fun AuthorTabContent(
    tabId: String,
    authorId: Long,
) {
    val appGraph = LocalAppGraph.current
    val tabsViewModel = LocalOpenWindow.current.tabsViewModel
    val page by produceState<AuthorPage>(AuthorPage.Loading, authorId) {
        value =
            withContext(Dispatchers.IO) {
                appGraph.repository.getAuthorDetails(authorId)?.withHebrewQuotes()?.let { details ->
                    val categories =
                        details.books
                            .map { it.categoryId }
                            .distinct()
                            .associateWith { id ->
                                appGraph.repository
                                    .getCategory(id)
                                    ?.title
                                    ?.hebrewQuotes()
                                    .orEmpty()
                            }
                    AuthorPage.Loaded(details, categories, BioText.of(details.bio?.bodyMd))
                } ?: AuthorPage.Missing
            }
    }
    val displayName = (page as? AuthorPage.Loaded)?.let { AuthorNames.display(it.details.name).hebrewQuotes() }
    LaunchedEffect(tabId, displayName) {
        if (displayName != null) appGraph.tabTitleUpdateManager.updateTabTitle(tabId, displayName, TabType.AUTHOR)
    }

    fun openLink(url: String) {
        val destination = parseZayitDeepLink(url)
        if (destination != null) tabsViewModel.openTab(destination) else UrlOpener.open(url)
    }

    fun openBook(book: Book) {
        tabsViewModel.openTab(TabsDestination.BookContent(bookId = book.id, tabId = UUID.randomUUID().toString()))
    }

    when (val current = page) {
        AuthorPage.Loading -> Unit
        AuthorPage.Missing -> EmptyState(iconKey = AllIconsKeys.General.User, message = stringResource(Res.string.author_not_found))
        is AuthorPage.Loaded -> AuthorPageLayout(current, onLink = ::openLink, onBook = ::openBook)
    }
}

@Composable
private fun AuthorPageLayout(
    page: AuthorPage.Loaded,
    onLink: (String) -> Unit,
    onBook: (Book) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Wide windows open with both side panes, narrow ones with the text alone
        val wide = maxWidth >= WIDE_LAYOUT
        var booksVisible by rememberSaveable { mutableStateOf(wide) }
        var infoVisible by rememberSaveable { mutableStateOf(wide) }
        Row(Modifier.fillMaxSize()) {
            AuthorStartBar(booksVisible) { booksVisible = !booksVisible }
            if (booksVisible) {
                Box(Modifier.width(SIDE_PANE).fillMaxHeight()) {
                    PaneCard {
                        Column(Modifier.fillMaxSize()) {
                            PaneHeader(label = stringResource(Res.string.author_books_pane), onHide = { booksVisible = false })
                            BooksPane(page, onBook)
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                PaneCard { TextPane(page, showFacts = !infoVisible, onLink = onLink) }
            }
            if (infoVisible) {
                Box(Modifier.width(SIDE_PANE).fillMaxHeight()) {
                    PaneCard {
                        Column(Modifier.fillMaxSize()) {
                            PaneHeader(label = stringResource(Res.string.author_info_pane), onHide = { infoVisible = false })
                            InfoPane(page, onLink)
                        }
                    }
                }
            }
            AuthorEndBar(infoVisible) { infoVisible = !infoVisible }
        }
    }
}

@Composable
private fun AuthorStartBar(
    booksVisible: Boolean,
    onBooks: () -> Unit,
) {
    val label = stringResource(Res.string.author_books_pane)
    VerticalLateralBar(
        position = VerticalLateralBarPosition.Start,
        topContent = {
            SelectableIconButtonWithToolip(
                toolTipText = label,
                onClick = onBooks,
                isSelected = booksVisible,
                icon = bookOpenTabs(JewelTheme.globalColors.text.normal),
                iconDescription = label,
                label = label,
            )
        },
        bottomContent = {},
    )
}

@Composable
private fun AuthorEndBar(
    infoVisible: Boolean,
    onInfo: () -> Unit,
) {
    val label = stringResource(Res.string.author_info_pane)
    VerticalLateralBar(
        position = VerticalLateralBarPosition.End,
        topContent = {
            ZoomButtons()
            SelectableIconButtonWithToolip(
                toolTipText = label,
                onClick = onInfo,
                isSelected = infoVisible,
                icon = WritingHand,
                iconDescription = label,
                label = label,
            )
        },
        bottomContent = {},
    )
}

/** The name, then the biography at the app's text size; its era and years too when the info pane is closed. */
@Composable
private fun TextPane(
    page: AuthorPage.Loaded,
    showFacts: Boolean,
    onLink: (String) -> Unit,
) {
    val textSize by LocalAppGraph.current.appSettings.textSizeFlow
        .collectAsState()
    val scroll = rememberScrollState()
    VerticallyScrollableContainer(scrollState = scroll as ScrollableState, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 32.dp, vertical = 28.dp)) {
            Text(
                AuthorNames.display(page.details.name).hebrewQuotes(),
                fontSize = (textSize * NAME_SCALE).sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily(Font(Res.font.notoserifhebrew)),
                color = JewelTheme.globalColors.text.normal,
            )
            val facts = listOfNotNull(page.details.era?.let { ERAS[it] }, years(page.details))
            if (showFacts && facts.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(facts.joinToString(" · "), fontSize = 13.sp, color = JewelTheme.globalColors.text.info)
            }
            Spacer(Modifier.height(20.dp))
            page.details.bio?.summary?.let {
                ProseMarkdown(
                    plainPersonLinks(it),
                    onLink,
                    fontSize = (textSize + 2).sp,
                    lineHeight = ((textSize + 2) * LINE_SPACING).sp,
                )
            }
            if (page.bio.sections.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                ProseMarkdown(page.bio.sections, onLink, fontSize = textSize.sp, lineHeight = (textSize * LINE_SPACING).sp)
            }
        }
    }
}

/** The author's books by category, opened in a tab; categories fold when there are many. */
@Composable
private fun BooksPane(
    page: AuthorPage.Loaded,
    onBook: (Book) -> Unit,
) {
    val scroll = rememberScrollState()
    VerticallyScrollableContainer(scrollState = scroll as ScrollableState, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 10.dp, vertical = 6.dp)) {
            val groups = page.details.books.groupBy { it.categoryId }
            val openByDefault = groups.size <= 2
            groups.forEach { (categoryId, books) ->
                CategoryBooks(page.categories[categoryId].orEmpty(), books, openByDefault, onBook)
            }
        }
    }
}

/** Era, years, aliases and number of books; the biography's sources and license under them. */
@Composable
private fun InfoPane(
    page: AuthorPage.Loaded,
    onLink: (String) -> Unit,
) {
    val details = page.details
    val scroll = rememberScrollState()
    VerticallyScrollableContainer(scrollState = scroll as ScrollableState, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            details.era?.let { ERAS[it] }?.let { InfoLine(stringResource(Res.string.author_info_era), it) }
            years(details)?.let { InfoLine(stringResource(Res.string.author_info_years), it) }
            if (details.aliases.isNotEmpty()) {
                InfoLine(stringResource(Res.string.author_info_aliases), details.aliases.joinToString(" · ") { it.withoutNikud() })
            }
            InfoLine(stringResource(Res.string.author_books), details.books.size.toString())
            if (page.bio.sources.isNotBlank()) {
                Sources(page.bio.sources, onLink)
            }
            if (details.bio != null) {
                Text(stringResource(Res.string.author_bio_license), fontSize = 11.sp, color = JewelTheme.globalColors.text.disabled)
            }
        }
    }
}

@Composable
private fun Sources(
    markdown: String,
    onLink: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Disclosure(stringResource(Res.string.author_sources), expanded) { expanded = !expanded }
        if (expanded) {
            ProseMarkdown(markdown, onLink, fontSize = 12.sp, lineHeight = 20.sp, color = JewelTheme.globalColors.text.info)
        }
    }
}

@Composable
private fun InfoLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, color = JewelTheme.globalColors.text.disabled)
        Text(value, fontSize = 13.sp, color = JewelTheme.globalColors.text.normal)
    }
}

@Composable
private fun CategoryBooks(
    category: String,
    books: List<Book>,
    openByDefault: Boolean,
    onOpen: (Book) -> Unit,
) {
    var expanded by remember { mutableStateOf(openByDefault) }
    Column {
        Disclosure("$category (${books.size})", expanded) { expanded = !expanded }
        if (expanded) {
            Column(Modifier.padding(start = 12.dp, top = 2.dp)) {
                books.forEach { book -> BookRow(book, onOpen) }
            }
        }
    }
}

@Composable
private fun Disclosure(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onToggle)
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(vertical = 4.dp, horizontal = 2.dp),
    ) {
        Icon(
            if (expanded) AllIconsKeys.General.ChevronDown else AllIconsKeys.General.ChevronLeft,
            null,
            Modifier.size(14.dp),
            tint = JewelTheme.globalColors.text.info,
        )
        Spacer(Modifier.width(6.dp))
        Text(title, fontSize = 13.sp, color = JewelTheme.globalColors.text.info)
    }
}

@Composable
private fun BookRow(
    book: Book,
    onOpen: (Book) -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val background by animateColorAsState(
        if (hovered) {
            JewelTheme.globalColors.outlines.focused
                .copy(alpha = 0.08f)
        } else {
            Color.Transparent
        },
        tween(150),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(background)
                .hoverable(hover)
                .clickable { onOpen(book) }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        val tint = JewelTheme.globalColors.text.info
        Image(rememberVectorPainter(bookOpenTabs(tint)), null, Modifier.size(13.dp), colorFilter = ColorFilter.tint(tint))
        Spacer(Modifier.width(8.dp))
        Text(book.title, fontSize = 13.sp, color = JewelTheme.globalColors.text.normal)
    }
}

private sealed interface AuthorPage {
    data object Loading : AuthorPage

    data object Missing : AuthorPage

    data class Loaded(
        val details: AuthorDetails,
        val categories: Map<Long, String>,
        val bio: BioText,
    ) : AuthorPage
}

/**
 * A biography split for the page: its sections (without the summary, shown as the lead, nor its
 * own book list, which dates from its writing) and its sources, folded at the bottom.
 */
private data class BioText(
    val sections: String,
    val sources: String,
) {
    companion object {
        private const val SUMMARY = "תקציר"
        private const val BOOKS = "במאגר"
        private const val SOURCES = "מקורות"

        fun of(markdown: String?): BioText {
            if (markdown == null) return BioText("", "")
            val sections = mutableListOf<String>()
            var sources = ""
            markdown.split(Regex("(?m)^(?=## )")).forEach { section ->
                when (
                    section
                        .lineSequence()
                        .first()
                        .removePrefix("## ")
                        .trim()
                ) {
                    SUMMARY, BOOKS -> Unit
                    SOURCES -> sources = section.substringAfter('\n').trim()
                    else -> sections += section.trim()
                }
            }
            return BioText(plainPersonLinks(sections.joinToString("\n\n")), sources)
        }
    }
}

// The biographies link every person they name to a search: shown as plain text, only books stay links
private fun plainPersonLinks(markdown: String): String = markdown.replace(Regex("""\[([^\]]+)]\(zayit://search/[^)]*\)"""), "$1")

/** The page's texts with Hebrew geresh and gershayim (רמב״ם, ר׳, ״בית יוסף״) instead of ASCII quotes and guillemets; the name stays raw for its honorific. */
private fun AuthorDetails.withHebrewQuotes(): AuthorDetails =
    copy(
        aliases = aliases.map { it.hebrewQuotes() },
        bio = bio?.let { it.copy(summary = it.summary?.markdownHebrewQuotes(), bodyMd = it.bodyMd.markdownHebrewQuotes()) },
        books = books.map { it.copy(title = it.title.hebrewQuotes()) },
    )

private val GERSHAYIM = Regex("""(?<=\p{InHebrew})["“”](?=\p{InHebrew})""")
private val GERESH = Regex("""(?<=\p{InHebrew})['‘’]""")
private val LINK_TARGET = Regex("""]\([^)]*\)""")

private val GUILLEMETS = Regex("[«»]")

private fun String.hebrewQuotes(): String =
    replace(GUILLEMETS, "״")
        .replace(GERSHAYIM, "״")
        .replace(GERESH, "׳")

/** Like [hebrewQuotes], leaving link targets untouched. */
private fun String.markdownHebrewQuotes(): String {
    val out = StringBuilder()
    var last = 0
    LINK_TARGET.findAll(this).forEach { link ->
        out.append(substring(last, link.range.first).hebrewQuotes()).append(link.value)
        last = link.range.last + 1
    }
    return out.append(substring(last).hebrewQuotes()).toString()
}

private fun String.withoutNikud(): String = replace(Regex("[֑-ׇ]"), "").replace(Regex("\\s+"), " ").trim()

// Sefaria era codes, as named in Hebrew
private val ERAS =
    mapOf(
        "T" to "תנאים",
        "A" to "אמוראים",
        "GN" to "גאונים",
        "RI" to "ראשונים",
        "AH" to "אחרונים",
        "CO" to "בני זמננו",
    )

private val hebrewYears =
    HebrewDateFormatter().apply {
        isHebrewFormat = true
        isUseGershGershayim = true
    }

/**
 * `ה׳תקכ״א–ה׳תקצ״ז (1761–1837)`, `~1089` for an approximate year; null when nothing is known.
 * Sefaria gives civil years only: the Hebrew one is the year that covers most of it (+3760), so
 * it may be one off.
 */
private fun years(details: AuthorDetails): String? {
    fun span(year: (Int) -> String): String? {
        fun one(
            value: Int?,
            approx: Boolean,
        ) = value?.let { (if (approx) "~" else "") + year(it) }
        val birth = one(details.birthYear, details.birthYearApprox)
        val death = one(details.deathYear, details.deathYearApprox)
        return when {
            birth != null && death != null -> "$birth–$death"
            death != null -> "–$death"
            birth != null -> "$birth–"
            else -> null
        }
    }
    val civil = span { it.toString() } ?: return null
    val hebrew = span { hebrewYears.formatHebrewNumber(it + HEBREW_YEAR_OFFSET) }
    return "$hebrew ($civil)"
}

private const val HEBREW_YEAR_OFFSET = 3760
