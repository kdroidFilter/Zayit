package io.github.kdroidfilter.seforimapp.features.home.widgets.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.annotations.BookNote
import io.github.kdroidfilter.seforimapp.core.favorites.FavoriteEntry
import io.github.kdroidfilter.seforimapp.core.favorites.FavoriteFolder
import io.github.kdroidfilter.seforimapp.core.history.VisitEntry
import io.github.kdroidfilter.seforimapp.core.history.VisitKind
import io.github.kdroidfilter.seforimapp.core.history.destination
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.FitColumn
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.HoverBox
import io.github.kdroidfilter.seforimapp.features.home.widgets.PanelCard
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetTitle
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.BookmarkFilled
import io.github.kdroidfilter.seforimapp.icons.NotebookPen
import io.github.kdroidfilter.seforimapp.icons.bookOpenTabs
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.favorites_empty
import seforimapp.seforimapp.generated.resources.favorites_show_all
import seforimapp.seforimapp.generated.resources.history_empty
import seforimapp.seforimapp.generated.resources.history_yesterday
import seforimapp.seforimapp.generated.resources.home_widget_name_favorites
import seforimapp.seforimapp.generated.resources.home_widget_name_history
import seforimapp.seforimapp.generated.resources.home_widget_name_notes
import seforimapp.seforimapp.generated.resources.notes_empty
import seforimapp.seforimapp.generated.resources.notes_show_all
import seforimapp.seforimapp.generated.resources.tab_search_show_all_history
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// The app's own lists on the Home: the history, the favorites and the user's notes, each opening its entry in a new
// tab. They follow their stores' writes, and show as many whole rows as the card holds.

private const val LIST_LIMIT = 40

/** The books opened and searches run lately; clicking one opens it again. */
internal object HistoryWidget : HomeWidget {
    override val id = "history"
    override val title = Res.string.home_widget_name_history
    override val defaultSpan = CellSpan(5, 4)
    override val minSpan = CellSpan(4, 3)
    override val maxSpan = CellSpan(7, 8)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val store = LocalAppGraph.current.historyStore
        val revision by store.revision.collectAsState()
        val entries by produceState(emptyList<VisitEntry>(), revision) { value = store.query("", LIST_LIMIT) }
        val yesterday = stringResource(Res.string.history_yesterday)
        ListCard(
            title = stringResource(Res.string.home_widget_name_history),
            onShowAll = { state.openTab(TabsDestination.History(UUID.randomUUID().toString())) },
            showAllLabel = stringResource(Res.string.tab_search_show_all_history),
            empty = stringResource(Res.string.history_empty).takeIf { entries.isEmpty() },
            modifier = modifier,
        ) {
            entries.forEach { entry ->
                EntryRow(
                    title = entry.title,
                    trailing = entry.visitedAt.whenLabel(yesterday),
                    icon = { if (entry.kind == VisitKind.SEARCH) Icon(AllIconsKeys.Actions.Find, null) else VectorIcon(bookOpenTabs(it)) },
                    onClick = { entry.destination()?.let(state.openTab) },
                )
            }
        }
    }
}

/** The user's favorites, by folder when there are folders; clicking one opens it where it was starred. */
internal object FavoritesWidget : HomeWidget {
    override val id = "favorites"
    override val title = Res.string.home_widget_name_favorites
    override val defaultSpan = CellSpan(5, 4)
    override val minSpan = CellSpan(4, 3)
    override val maxSpan = CellSpan(7, 8)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val store = LocalAppGraph.current.favoritesStore
        val revision by store.revision.collectAsState()
        val entries by produceState(emptyList<FavoriteEntry>(), revision) { value = store.query() }
        val folders by produceState(emptyList<FavoriteFolder>(), revision) { value = store.folders() }
        // null: every favorite
        var folder by remember { mutableStateOf<Long?>(null) }
        val shown = entries.filter { folder == null || it.folderId == folder }
        val accent = rememberAccentColor(JewelTheme.isDark)
        ListCard(
            title = stringResource(Res.string.home_widget_name_favorites),
            onShowAll = { state.openTab(TabsDestination.Favorites(UUID.randomUUID().toString())) },
            showAllLabel = stringResource(Res.string.favorites_show_all),
            empty = stringResource(Res.string.favorites_empty).takeIf { entries.isEmpty() },
            header = {
                if (folders.isNotEmpty()) FolderChips(folders, folder, accent) { folder = it }
            },
            modifier = modifier,
        ) {
            shown.take(LIST_LIMIT).forEach { entry ->
                EntryRow(
                    title = entry.title,
                    icon = { VectorIcon(BookmarkFilled, tint = accent) },
                    onClick = {
                        state.openTab(
                            TabsDestination.BookContent(bookId = entry.bookId, tabId = UUID.randomUUID().toString(), lineId = entry.lineId),
                        )
                    },
                )
            }
        }
    }
}

/** "All" and the user's folders, the one shown lit. */
@Composable
private fun FolderChips(
    folders: List<FavoriteFolder>,
    selected: Long?,
    accent: Color,
    onSelect: (Long?) -> Unit,
) {
    // ponytail: one line, the last folders cut off when they don't fit; a scrolling row if users keep many
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (listOf<FavoriteFolder?>(null) + folders).forEach { folder ->
            val lit = folder?.id == selected
            Text(
                text = folder?.name ?: "הכל",
                fontSize = 11.sp,
                fontWeight = if (lit) FontWeight.SemiBold else FontWeight.Normal,
                color = if (lit) accent else JewelTheme.globalColors.text.info,
                maxLines = 1,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (lit) accent.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { onSelect(folder?.id) }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** The user's latest notes, with the passage and the book; clicking one opens the passage. */
internal object NotesWidget : HomeWidget {
    override val id = "notes"
    override val title = Res.string.home_widget_name_notes
    override val defaultSpan = CellSpan(6, 4)
    override val minSpan = CellSpan(5, 3)

    // Wider, a one-line note would leave half its row empty
    override val maxSpan = CellSpan(7, 8)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val graph = LocalAppGraph.current
        // Its per-book cache changes on every write: the cue to read the latest again
        val written by graph.noteStore.notesByBook.collectAsState()
        val notes by produceState(emptyList<Pair<BookNote, String>>(), written) {
            val recent = graph.noteStore.recent(LIST_LIMIT)
            // The book's name is a nicety: a note still shows, and opens, without the books DB
            val titles =
                recent.map { it.bookId }.distinct().associateWith { id ->
                    runCatching { graph.repository.getBookCore(id)?.title }.getOrNull().orEmpty()
                }
            value = recent.map { it to titles[it.bookId].orEmpty() }
        }
        val accent = rememberAccentColor(JewelTheme.isDark)
        ListCard(
            title = stringResource(Res.string.home_widget_name_notes),
            onShowAll = { state.openTab(TabsDestination.Notes(UUID.randomUUID().toString())) },
            showAllLabel = stringResource(Res.string.notes_show_all),
            empty = stringResource(Res.string.notes_empty).takeIf { notes.isEmpty() },
            modifier = modifier,
        ) {
            notes.forEach { (bookNote, bookTitle) ->
                HoverBox(
                    onClick = {
                        state.openTab(
                            TabsDestination.BookContent(
                                bookId = bookNote.bookId,
                                tabId = UUID.randomUUID().toString(),
                                lineId = bookNote.note.lineId,
                                openNotes = true,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VectorIcon(NotebookPen, Modifier.padding(top = 1.dp), accent)
                        Column(Modifier.weight(1f)) {
                            Text(bookNote.note.note, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(bookNote.note.quote, bookTitle).filter { it.isNotBlank() }.joinToString(" · "),
                                fontSize = 11.sp,
                                color = JewelTheme.globalColors.text.info,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A titled card of rows, or its [empty] message when there is nothing to list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListCard(
    title: String,
    empty: String?,
    modifier: Modifier = Modifier,
    onShowAll: (() -> Unit)? = null,
    showAllLabel: String = "",
    header: @Composable () -> Unit = {},
    rows: @Composable () -> Unit,
) {
    PanelCard(modifier) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WidgetTitle(title) {
                if (onShowAll != null) {
                    Tooltip(
                        tooltip = { Text(showAllLabel) },
                        // Centred above the button, not under the cursor
                        tooltipPlacement =
                            TooltipPlacement.ComponentRect(
                                Alignment.TopCenter,
                                Alignment.TopCenter,
                                DpOffset(0.dp, (-8).dp),
                            ),
                    ) {
                        IconButton(onClick = onShowAll, modifier = Modifier.size(22.dp)) {
                            Icon(AllIconsKeys.Actions.OpenNewTab, showAllLabel, tint = JewelTheme.globalColors.text.info)
                        }
                    }
                }
            }
            header()
            if (empty != null) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(empty, fontSize = 13.sp, color = JewelTheme.globalColors.text.info)
                }
            } else {
                FitColumn(Modifier.fillMaxWidth().weight(1f)) { rows() }
            }
        }
    }
}

/** A row of a list: its icon, its title, and what goes at the end. */
@Composable
private fun EntryRow(
    title: String,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    trailing: String? = null,
) {
    HoverBox(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon(JewelTheme.globalColors.text.normal)
            Text(title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (trailing != null) Text(trailing, fontSize = 11.sp, color = JewelTheme.globalColors.text.info, maxLines = 1)
        }
    }
}

@Composable
private fun VectorIcon(
    vector: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    Image(vector, contentDescription = null, modifier = modifier.size(16.dp), colorFilter = tint?.let { ColorFilter.tint(it) })
}

/** The time today, "yesterday", or the date before: as the history page labels its days. */
private fun Long.whenLabel(yesterday: String): String {
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(this).atZone(zone)
    val today = LocalDate.now(zone)
    return when (at.toLocalDate()) {
        today -> "⁦%02d:%02d⁩".format(at.hour, at.minute)
        today.minusDays(1) -> yesterday
        else -> "⁦${at.dayOfMonth}.${at.monthValue}⁩"
    }
}
