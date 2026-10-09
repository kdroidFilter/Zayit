package io.github.kdroidfilter.seforimapp.features.author

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.deeplink.parseZayitDeepLink
import io.github.kdroidfilter.seforimapp.core.presentation.components.AccentMarkdownView
import io.github.kdroidfilter.seforimapp.core.presentation.components.CardSurface
import io.github.kdroidfilter.seforimapp.core.presentation.components.EmptyState
import io.github.kdroidfilter.seforimapp.core.presentation.components.PageHeader
import io.github.kdroidfilter.seforimapp.core.presentation.components.SectionHeader
import io.github.kdroidfilter.seforimapp.core.presentation.utils.UrlOpener
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.bookOpenTabs
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.AuthorDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.author_aliases
import seforimapp.seforimapp.generated.resources.author_bio_license
import seforimapp.seforimapp.generated.resources.author_books
import seforimapp.seforimapp.generated.resources.author_not_found
import java.util.UUID

/** An author's page: name with its honorific, era and years, aliases, biography and books. */
@Composable
fun AuthorTabContent(
    tabId: String,
    authorId: Long,
) {
    val appGraph = LocalAppGraph.current
    val tabsViewModel = LocalOpenWindow.current.tabsViewModel
    val page by produceState<AuthorPage>(AuthorPage.Loading, authorId) {
        value =
            withContext(Dispatchers.IO) { appGraph.repository.getAuthorDetails(authorId) }
                ?.let { details ->
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
                    AuthorPage.Loaded(details, categories)
                } ?: AuthorPage.Missing
    }
    val loaded = page as? AuthorPage.Loaded
    val displayName = loaded?.let { AuthorNames.display(it.details.name) }
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

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
            when (val current = page) {
                AuthorPage.Loading -> Unit
                AuthorPage.Missing -> EmptyState(iconKey = AllIconsKeys.General.User, message = stringResource(Res.string.author_not_found))
                is AuthorPage.Loaded -> {
                    val details = current.details
                    val aliasesLine = stringResource(Res.string.author_aliases, details.aliases.joinToString(" · "))
                    val licenseLine = stringResource(Res.string.author_bio_license)
                    val booksTitle = stringResource(Res.string.author_books)
                    val eraAndYears = eraAndYears(details)
                    AccentMarkdownView(
                        markdown =
                            details.bio
                                ?.bodyMd
                                ?.withoutBooksSection()
                                .orEmpty(),
                        onUrlClick = ::openLink,
                        headerItems = {
                            item {
                                Column(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    PageHeader(title = AuthorNames.display(details.name))
                                    if (eraAndYears.isNotEmpty()) {
                                        Text(eraAndYears, color = JewelTheme.globalColors.text.info, fontSize = 14.sp)
                                    }
                                    if (details.aliases.isNotEmpty()) {
                                        Text(aliasesLine, color = JewelTheme.globalColors.text.disabled, fontSize = 13.sp)
                                    }
                                }
                            }
                        },
                        extraItems = {
                            if (details.bio != null) {
                                item {
                                    Text(licenseLine, color = JewelTheme.globalColors.text.disabled, fontSize = 11.sp)
                                }
                            }
                            item {
                                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        "$booksTitle (${details.books.size})",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = JewelTheme.globalColors.text.normal,
                                    )
                                    details.books.groupBy { it.categoryId }.forEach { (categoryId, books) ->
                                        CategoryBooks(current.categories[categoryId].orEmpty(), books, ::openBook)
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

private sealed interface AuthorPage {
    data object Loading : AuthorPage

    data object Missing : AuthorPage

    data class Loaded(
        val details: AuthorDetails,
        val categories: Map<Long, String>,
    ) : AuthorPage
}

@Composable
private fun CategoryBooks(
    category: String,
    books: List<Book>,
    onOpen: (Book) -> Unit,
) {
    CardSurface {
        Column {
            SectionHeader(title = category, count = books.size, leadingIconKey = AllIconsKeys.Nodes.Folder)
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 6.dp)) {
                books.forEach { book -> BookRow(book, onOpen) }
            }
        }
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
                .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
        val tint = JewelTheme.globalColors.text.info
        Image(rememberVectorPainter(bookOpenTabs(tint)), null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(tint))
        Spacer(Modifier.width(8.dp))
        Text(book.title, color = JewelTheme.globalColors.text.normal, fontSize = 14.sp)
    }
}

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

// `אחרונים · 1761–1837`, `ראשונים · ~1089–1164`; empty when nothing is known
private fun eraAndYears(details: AuthorDetails): String {
    fun year(
        value: Int?,
        approx: Boolean,
    ) = value?.let { (if (approx) "~" else "") + it }
    val birth = year(details.birthYear, details.birthYearApprox)
    val death = year(details.deathYear, details.deathYearApprox)
    val years =
        when {
            birth != null && death != null -> "$birth–$death"
            death != null -> "–$death"
            birth != null -> "$birth–"
            else -> null
        }
    return listOfNotNull(details.era?.let { ERAS[it] }, years).joinToString(" · ")
}

// The biography's own list of books (`## במאגר`) dates from its writing: the page lists the database's
private fun String.withoutBooksSection(): String {
    val lines = lines()
    val start = lines.indexOfFirst { it.trim() == "## במאגר" }
    if (start < 0) return this
    val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
    return (lines.subList(0, start) + lines.subList(end, lines.size)).joinToString("\n")
}
