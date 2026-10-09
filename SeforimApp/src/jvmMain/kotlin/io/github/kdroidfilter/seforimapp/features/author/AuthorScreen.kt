package io.github.kdroidfilter.seforimapp.features.author

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.deeplink.parseZayitDeepLink
import io.github.kdroidfilter.seforimapp.core.presentation.components.EmptyState
import io.github.kdroidfilter.seforimapp.core.presentation.components.ProseMarkdown
import io.github.kdroidfilter.seforimapp.core.presentation.utils.UrlOpener
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
import seforimapp.seforimapp.generated.resources.author_info_aliases
import seforimapp.seforimapp.generated.resources.author_info_era
import seforimapp.seforimapp.generated.resources.author_info_years
import seforimapp.seforimapp.generated.resources.author_not_found
import seforimapp.seforimapp.generated.resources.author_sources
import seforimapp.seforimapp.generated.resources.notoserifhebrew
import java.util.UUID

/** Below this width the info card goes under the header instead of beside the text. */
private val WIDE_LAYOUT = 1040.dp
private val TEXT_WIDTH = 680.dp
private val CARD_WIDTH = 300.dp

/**
 * An author's page, laid out like an encyclopedia entry: the name with its honorific and the era
 * and years as pills, the biography's summary as a lead then its sections, the sources folded
 * at the bottom, and an info card (aliases, books by category) that stays in view on wide windows.
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
                appGraph.repository.getAuthorDetails(authorId)?.let { details ->
                    val categories =
                        details.books
                            .map { it.categoryId }
                            .distinct()
                            .associateWith { id ->
                                appGraph.repository
                                    .getCategory(id)
                                    ?.title
                                    .orEmpty()
                            }
                    AuthorPage.Loaded(details, categories, BioText.of(details.bio?.bodyMd))
                } ?: AuthorPage.Missing
            }
    }
    val displayName = (page as? AuthorPage.Loaded)?.let { AuthorNames.display(it.details.name) }
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
    val scroll = rememberScrollState()
    // The page scrolls as a whole, its scrollbar along the window's edge
    VerticallyScrollableContainer(scrollState = scroll as ScrollableState, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxWidth().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
            if (maxWidth >= WIDE_LAYOUT) {
                // The card follows the scroll down to the text's end
                var rowTop by remember { mutableIntStateOf(0) }
                var rowHeight by remember { mutableIntStateOf(0) }
                var cardHeight by remember { mutableIntStateOf(0) }
                Row(
                    Modifier
                        .padding(horizontal = 32.dp, vertical = 28.dp)
                        .onPlaced { rowTop = it.positionInParent().y.toInt() }
                        .onSizeChanged { rowHeight = it.height },
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                ) {
                    Column(Modifier.widthIn(max = TEXT_WIDTH)) {
                        Header(page.details, large = true)
                        Spacer(Modifier.height(20.dp))
                        Body(page, onLink)
                    }
                    InfoCard(
                        page,
                        onBook,
                        Modifier
                            .width(CARD_WIDTH)
                            .onSizeChanged { cardHeight = it.height }
                            .offset {
                                val room = (rowHeight - cardHeight).coerceAtLeast(0)
                                IntOffset(0, (scroll.value - rowTop).coerceIn(0, room))
                            },
                    )
                }
            } else {
                Column(Modifier.widthIn(max = TEXT_WIDTH).padding(horizontal = 16.dp, vertical = 20.dp)) {
                    Header(page.details, large = false)
                    Spacer(Modifier.height(16.dp))
                    InfoCard(page, onBook, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(20.dp))
                    Body(page, onLink)
                }
            }
        }
    }
}

@Composable
private fun Header(
    details: AuthorDetails,
    large: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            AuthorNames.display(details.name),
            fontSize = if (large) 30.sp else 24.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily(Font(Res.font.notoserifhebrew)),
            color = JewelTheme.globalColors.text.normal,
        )
        val era = details.era?.let { ERAS[it] }
        val years = years(details)
        if (era != null || years != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                era?.let { Pill(it) }
                years?.let { Pill(it) }
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    val accent = JewelTheme.globalColors.outlines.focused
    Text(
        text,
        fontSize = 12.sp,
        color = JewelTheme.globalColors.text.info,
        modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.08f))
                .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun Body(
    page: AuthorPage.Loaded,
    onLink: (String) -> Unit,
) {
    val bio = page.bio
    Column {
        page.details.bio
            ?.summary
            ?.let { ProseMarkdown(plainPersonLinks(it), onLink, fontSize = 17.sp, lineHeight = 30.sp) }
        if (bio.sections.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            ProseMarkdown(bio.sections, onLink)
        }
        if (bio.sources.isNotBlank()) {
            Spacer(Modifier.height(24.dp))
            Sources(bio.sources, onLink)
        }
        if (page.details.bio != null) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.author_bio_license), fontSize = 11.sp, color = JewelTheme.globalColors.text.disabled)
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
            ProseMarkdown(markdown, onLink, fontSize = 13.sp, lineHeight = 22.sp, color = JewelTheme.globalColors.text.info)
        }
    }
}

@Composable
private fun InfoCard(
    page: AuthorPage.Loaded,
    onBook: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val details = page.details
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(10.dp))
            .background(JewelTheme.globalColors.panelBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                rememberVectorPainter(WritingHand),
                null,
                Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(JewelTheme.globalColors.outlines.focused),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                AuthorNames.display(details.name),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = JewelTheme.globalColors.text.normal,
            )
        }
        Divider()
        details.era?.let { ERAS[it] }?.let { InfoLine(stringResource(Res.string.author_info_era), it) }
        years(details)?.let { InfoLine(stringResource(Res.string.author_info_years), it) }
        if (details.aliases.isNotEmpty()) {
            InfoLine(stringResource(Res.string.author_info_aliases), details.aliases.joinToString(" · ") { it.withoutNikud() })
        }
        Divider()
        Text(
            "${stringResource(Res.string.author_books)} · ${details.books.size}",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = JewelTheme.globalColors.text.normal,
        )
        val groups = details.books.groupBy { it.categoryId }
        // A few categories open at once; many fold, to keep the card short
        val openByDefault = groups.size <= 2
        groups.forEach { (categoryId, books) ->
            CategoryBooks(page.categories[categoryId].orEmpty(), books, openByDefault, onBook)
        }
    }
}

@Composable
private fun InfoLine(
    label: String,
    value: String,
) {
    Row {
        Text(label, fontSize = 13.sp, color = JewelTheme.globalColors.text.disabled, modifier = Modifier.width(72.dp))
        Text(value, fontSize = 13.sp, color = JewelTheme.globalColors.text.normal)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(JewelTheme.globalColors.borders.normal))
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

// `1761–1837`, `~1089–1164`; null when nothing is known
private fun years(details: AuthorDetails): String? {
    fun year(
        value: Int?,
        approx: Boolean,
    ) = value?.let { (if (approx) "~" else "") + it }
    val birth = year(details.birthYear, details.birthYearApprox)
    val death = year(details.deathYear, details.deathYearApprox)
    return when {
        birth != null && death != null -> "$birth–$death"
        death != null -> "–$death"
        birth != null -> "$birth–"
        else -> null
    }
}
