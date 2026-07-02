package io.github.kdroidfilter.seforimapp.features.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.history.VisitEntry
import io.github.kdroidfilter.seforimapp.core.history.VisitKind
import io.github.kdroidfilter.seforimapp.core.presentation.components.CardSurface
import io.github.kdroidfilter.seforimapp.core.presentation.components.ConfirmPopup
import io.github.kdroidfilter.seforimapp.core.presentation.components.EmptyState
import io.github.kdroidfilter.seforimapp.core.presentation.components.ListPageContainer
import io.github.kdroidfilter.seforimapp.core.presentation.components.ListRow
import io.github.kdroidfilter.seforimapp.core.presentation.components.PageHeader
import io.github.kdroidfilter.seforimapp.core.presentation.components.PageSearchField
import io.github.kdroidfilter.seforimapp.core.presentation.components.SectionHeader
import io.github.kdroidfilter.seforimapp.core.presentation.components.StartBelowAnchorPositionProvider
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.bookOpenTabs
import io.github.kdroidfilter.seforimapp.logger.debugln
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.history_clear_all
import seforimapp.seforimapp.generated.resources.history_clear_confirm_message
import seforimapp.seforimapp.generated.resources.history_clear_confirm_title
import seforimapp.seforimapp.generated.resources.history_clear_confirm_yes
import seforimapp.seforimapp.generated.resources.history_empty
import seforimapp.seforimapp.generated.resources.history_title
import seforimapp.seforimapp.generated.resources.history_today
import seforimapp.seforimapp.generated.resources.history_yesterday
import seforimapp.seforimapp.generated.resources.settings_cancel
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
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val todayLabel = stringResource(Res.string.history_today)
    val yesterdayLabel = stringResource(Res.string.history_yesterday)

    val grouped = remember(entries) { entries.groupBy { Instant.ofEpochMilli(it.visitedAt).atZone(zone).toLocalDate() } }
    var expandedDays by remember { mutableStateOf<Set<LocalDate>>(grouped.keys) }
    LaunchedEffect(grouped.keys) {
        expandedDays = grouped.keys
    }

    fun dayLabel(day: LocalDate): String =
        when (day) {
            today -> todayLabel
            today.minusDays(1) -> yesterdayLabel
            else -> day.format(dateFormatter)
        }

    ListPageContainer {
        PageHeader(
            title = historyTitle,
            actions = {
                var showConfirm by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showConfirm = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            key = AllIconsKeys.General.Delete,
                            contentDescription = stringResource(Res.string.history_clear_all),
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    if (showConfirm) {
                        Popup(
                            onDismissRequest = { showConfirm = false },
                            popupPositionProvider = StartBelowAnchorPositionProvider,
                            properties = PopupProperties(focusable = true),
                        ) {
                            ConfirmPopup(
                                title = stringResource(Res.string.history_clear_confirm_title),
                                message = stringResource(Res.string.history_clear_confirm_message),
                                confirmText = stringResource(Res.string.history_clear_confirm_yes),
                                cancelText = stringResource(Res.string.settings_cancel),
                                onConfirm = {
                                    onClearAll()
                                    showConfirm = false
                                },
                                onCancel = { showConfirm = false },
                            )
                        }
                    }
                }
            },
        )

        PageSearchField(
            query = query,
            placeholder = Res.string.tab_search_placeholder,
            onQueryChange = onQueryChange,
        )

        if (entries.isEmpty()) {
            EmptyState(
                iconKey = AllIconsKeys.Vcs.History,
                message = stringResource(Res.string.history_empty),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                grouped.forEach { (day, dayEntries) ->
                    val expanded = day in expandedDays

                    item(key = "day-$day") {
                        val label = remember(day) { dayLabel(day) }
                        CardSurface(
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                SectionHeader(
                                    title = label,
                                    count = dayEntries.size,
                                    expanded = expanded,
                                    onToggleExpand = {
                                        expandedDays =
                                            if (expanded) {
                                                expandedDays - day
                                            } else {
                                                expandedDays + day
                                            }
                                    },
                                )
                                if (expanded) {
                                    dayEntries.forEachIndexed { index, entry ->
                                        val time =
                                            remember(entry.visitedAt) {
                                                Instant
                                                    .ofEpochMilli(entry.visitedAt)
                                                    .atZone(zone)
                                                    .toLocalTime()
                                                    .format(timeFormatter)
                                            }
                                        ListRow(
                                            title = entry.title,
                                            onOpen = { onOpen(entry) },
                                            onDelete = { onDelete(entry) },
                                            leadingContent = {
                                                Text(
                                                    text = time,
                                                    fontSize = 12.sp,
                                                    color = JewelTheme.globalColors.text.info,
                                                )
                                            },
                                            leadingIconVector =
                                                if (entry.kind == VisitKind.BOOK) {
                                                    bookOpenTabs(JewelTheme.globalColors.text.normal)
                                                } else {
                                                    null
                                                },
                                            leadingIconKey =
                                                if (entry.kind == VisitKind.SEARCH) {
                                                    AllIconsKeys.Actions.Find
                                                } else {
                                                    null
                                                },
                                            leadingTint = JewelTheme.globalColors.text.normal,
                                            showDivider = index < dayEntries.lastIndex,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
