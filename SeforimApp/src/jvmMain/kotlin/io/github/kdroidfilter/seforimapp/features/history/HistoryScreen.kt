package io.github.kdroidfilter.seforimapp.features.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.history.VisitEntry
import io.github.kdroidfilter.seforimapp.core.history.VisitKind
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.bookOpenTabs
import io.github.kdroidfilter.seforimapp.logger.debugln
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.history_clear_all
import seforimapp.seforimapp.generated.resources.history_empty
import seforimapp.seforimapp.generated.resources.history_title
import seforimapp.seforimapp.generated.resources.history_today
import seforimapp.seforimapp.generated.resources.history_yesterday
import seforimapp.seforimapp.generated.resources.tab_search_placeholder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private const val HISTORY_PAGE_LIMIT = 500

/**
 * Full visit-history page (the chrome://history equivalent): searchable, grouped by day,
 * unbounded, with per-entry deletion and clear-all. Rendered as a regular tab destination.
 */
@Composable
fun HistoryTabContent(tabId: String) {
    val appGraph = LocalAppGraph.current
    val historyStore = appGraph.historyStore
    val tabsViewModel = LocalOpenWindow.current.tabsViewModel
    val scope = rememberCoroutineScope()

    // The tab title is localized here (TabsViewModel can't reach resources)
    val historyTitle = stringResource(Res.string.history_title)
    LaunchedEffect(tabId, historyTitle) {
        appGraph.tabTitleUpdateManager.updateTabTitle(tabId, historyTitle, TabType.HISTORY)
    }

    var query by remember { mutableStateOf("") }
    val revision by historyStore.revision.collectAsState()
    var entries by remember { mutableStateOf<List<VisitEntry>>(emptyList()) }
    LaunchedEffect(query, revision) {
        entries = historyStore.query(query, HISTORY_PAGE_LIMIT)
    }

    fun openEntry(entry: VisitEntry) {
        val destination =
            when (entry.kind) {
                VisitKind.BOOK ->
                    entry.bookId?.let {
                        TabsDestination.BookContent(bookId = it, tabId = UUID.randomUUID().toString(), lineId = entry.lineId)
                    }
                VisitKind.SEARCH ->
                    entry.searchQuery?.let {
                        TabsDestination.Search(searchQuery = it, tabId = UUID.randomUUID().toString())
                    }
            } ?: return
        debugln { "[History] open ${entry.key}" }
        // Chrome-like: clicking a history entry navigates in the current tab
        tabsViewModel.replaceCurrentTabWithNewTabId(destination)
    }

    HistoryPageContent(
        historyTitle = historyTitle,
        query = query,
        onQueryChange = { query = it },
        entries = entries,
        onClearAll = { scope.launch { historyStore.clearAll() } },
        onOpen = ::openEntry,
        onDelete = { entry -> scope.launch { historyStore.delete(entry.key) } },
    )
}

@Composable
private fun HistoryPageContent(
    historyTitle: String,
    query: String,
    onQueryChange: (String) -> Unit,
    entries: List<VisitEntry>,
    onClearAll: () -> Unit,
    onOpen: (VisitEntry) -> Unit,
    onDelete: (VisitEntry) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = historyTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = JewelTheme.globalColors.text.normal,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onClearAll) {
                    Text(stringResource(Res.string.history_clear_all))
                }
            }

            HistorySearchField(query = query, onQueryChange = onQueryChange)

            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(Res.string.history_empty),
                        color = JewelTheme.globalColors.text.info,
                    )
                }
            } else {
                val zone = remember { ZoneId.systemDefault() }
                val grouped =
                    remember(entries) {
                        entries.groupBy { Instant.ofEpochMilli(it.visitedAt).atZone(zone).toLocalDate() }
                    }
                val today = LocalDate.now(zone)
                val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()) }
                val todayLabel = stringResource(Res.string.history_today)
                val yesterdayLabel = stringResource(Res.string.history_yesterday)

                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    grouped.forEach { (day, dayEntries) ->
                        item(key = "day-$day") {
                            Text(
                                text =
                                    when (day) {
                                        today -> todayLabel
                                        today.minusDays(1) -> yesterdayLabel
                                        else -> day.format(dateFormatter)
                                    },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = JewelTheme.globalColors.text.info,
                                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                            )
                        }
                        items(dayEntries, key = { it.key }) { entry ->
                            HistoryRow(
                                entry = entry,
                                zone = zone,
                                onOpen = { onOpen(entry) },
                                onDelete = { onDelete(entry) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(8.dp))
                .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            key = AllIconsKeys.Actions.Find,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = JewelTheme.globalColors.text.info,
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp, color = JewelTheme.globalColors.text.normal),
            cursorBrush = SolidColor(JewelTheme.globalColors.outlines.focused),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.tab_search_placeholder),
                            fontSize = 13.sp,
                            color = JewelTheme.globalColors.text.info,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun HistoryRow(
    entry: VisitEntry,
    zone: ZoneId,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val accent = JewelTheme.globalColors.outlines.focused
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .background(if (isHovered) accent.copy(alpha = 0.06f) else Color.Transparent, RoundedCornerShape(6.dp))
                .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
                .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text =
                Instant
                    .ofEpochMilli(entry.visitedAt)
                    .atZone(zone)
                    .toLocalTime()
                    .format(timeFormatter),
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.info,
        )
        if (entry.kind == VisitKind.BOOK) {
            androidx.compose.foundation.Image(
                painter = rememberVectorPainter(bookOpenTabs(JewelTheme.globalColors.text.normal)),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.normal),
            )
        } else {
            Icon(
                key = AllIconsKeys.Actions.Find,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = JewelTheme.globalColors.text.normal,
            )
        }
        Text(
            text = entry.title,
            fontSize = 13.sp,
            color = JewelTheme.globalColors.text.normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.size(4.dp))
        Box(
            modifier =
                Modifier
                    .size(20.dp)
                    .background(if (isHovered) accent.copy(alpha = 0.10f) else Color.Transparent, CircleShape)
                    .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                key = AllIconsKeys.Actions.Close,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isHovered) JewelTheme.globalColors.text.normal else Color.Transparent,
            )
        }
    }
}
