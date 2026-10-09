package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookdetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.features.author.AUTHOR_ERAS
import io.github.kdroidfilter.seforimapp.features.author.authorYears
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimapp.features.search.ResultLink
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.features.search.domain.SearchEntity
import io.github.kdroidfilter.seforimapp.features.search.readingSecondary
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_book_details
import seforimapp.seforimapp.generated.resources.search_panel_author
import seforimapp.seforimapp.generated.resources.search_panel_parts
import java.util.UUID

/**
 * The book-details pane, in every reader tab (a book's, a search's selected result's): the book's
 * categories, authors with their years and main parts, which open in a new tab.
 */
@Composable
fun BookDetailsPane(
    book: SearchEntity.BookEntity?,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = LocalOpenWindow.current.tabsViewModel
    Column(modifier) {
        PaneHeader(label = stringResource(Res.string.search_book_details), onHide = onHide)
        if (book != null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            ) {
                BookDetails(
                    entity = book,
                    onOpenBookAt = { bookId, lineId ->
                        tabs.openTab(TabsDestination.BookContent(bookId = bookId, tabId = UUID.randomUUID().toString(), lineId = lineId))
                    },
                    onOpenAuthor = { authorId ->
                        tabs.openTab(TabsDestination.Author(tabId = UUID.randomUUID().toString(), authorId = authorId))
                    },
                )
            }
        }
    }
}

/** A book's details: title, categories, description, authors (to their page) and parts (to open). */
@Composable
fun ColumnScope.BookDetails(
    entity: SearchEntity.BookEntity,
    onOpenBookAt: (bookId: Long, lineId: Long) -> Unit,
    onOpenAuthor: (Long) -> Unit,
) {
    val grey = readingSecondary()
    val book = entity.book
    val accent = JewelTheme.globalColors.outlines.focused
    Text(book.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = JewelTheme.globalColors.text.normal)
    if (entity.categories.isNotEmpty()) {
        Text(entity.categories.joinToString(" › "), fontSize = 12.sp, color = grey)
    }
    book.heShortDesc?.takeIf { it.isNotBlank() }?.let {
        Text(
            it,
            fontSize = 13.sp,
            lineHeight = 21.sp,
            color = grey,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
    entity.authors.forEach { author ->
        PanelSection(stringResource(Res.string.search_panel_author)) {
            ResultLink(AuthorNames.display(author.name), accent, 14.sp) { onOpenAuthor(author.id) }
            val facts = listOfNotNull(author.era?.let { AUTHOR_ERAS[it] }, authorYears(author))
            if (facts.isNotEmpty()) Text(facts.joinToString(" · "), fontSize = 12.sp, color = grey)
        }
    }
    if (entity.parts.isNotEmpty()) {
        PanelSection(stringResource(Res.string.search_panel_parts)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                entity.parts.forEach { part ->
                    ResultLink(part.title, accent, 13.sp) { onOpenBookAt(book.id, part.lineId) }
                }
            }
        }
    }
}

/** A titled part of the panel, under a thin divider. */
@Composable
internal fun PanelSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
        Divider(Orientation.Horizontal, Modifier.fillMaxWidth().padding(bottom = 6.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = readingSecondary())
        content()
    }
}
