@file:OptIn(ExperimentalSerializationApi::class)

package io.github.kdroidfilter.seforimapp.framework.session

import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.File

/**
 * Persists and restores the navigation session (open tabs + per-tab persisted UI state) when enabled.
 *
 * Persistence is performed only at app close.
 */
object SessionManager {
    private val proto = ProtoBuf

    private val _isRestoringSession = MutableStateFlow(hasSavedSessionToRestore())
    val isRestoringSession: StateFlow<Boolean> = _isRestoringSession

    private fun sessionDir(): File {
        val root = File(FileKit.databasesDir.path, "session").apply { mkdirs() }
        return root
    }

    private fun sessionFile(): File = File(sessionDir(), "session_v2.pb")

    private fun hasSavedSessionToRestore(): Boolean = AppSettings.isPersistSessionEnabled() && sessionFile().exists()

    /** Saves the current session snapshot if the user enabled persistence in settings. */
    fun saveIfEnabled(appGraph: AppGraph) {
        if (!AppSettings.isPersistSessionEnabled()) return

        val tabsVm: TabsViewModel = appGraph.tabsViewModel
        val store: TabPersistedStateStore = appGraph.tabPersistedStateStore

        val tabs = tabsVm.tabs.value
        if (tabs.isEmpty()) return

        // `TabsDestination.BookContent.lineId` is an ephemeral navigation hint (e.g., open from search).
        // Session restore should rely on the per-tab persisted UI state (scroll/selection) instead.
        val destinations =
            tabs.map { it.destination }.map { dest ->
                when (dest) {
                    is TabsDestination.BookContent -> dest.copy(lineId = null)
                    else -> dest
                }
            }
        val selectedIndex = tabsVm.selectedTabIndex.value.coerceIn(0, destinations.lastIndex)

        val storeSnapshot = store.snapshot()
        val tabStates =
            destinations.associate { dest ->
                dest.tabId to (storeSnapshot[dest.tabId] ?: TabPersistedState())
            }

        debugln {
            buildString {
                append("[SessionManager] Saving session: tabs=${destinations.size}, selectedIndex=$selectedIndex\n")
                destinations.forEach { dest ->
                    when (dest) {
                        is TabsDestination.BookContent -> {
                            val bc = tabStates[dest.tabId]?.bookContent
                            append(
                                "  - BookContent tabId=${dest.tabId} destBookId=${dest.bookId} " +
                                    "persistedBookId=${bc?.selectedBookId} " +
                                    "selectedLineId=${bc?.selectedLineId} " +
                                    "anchorLineId=${bc?.contentAnchorLineId} " +
                                    "scroll=(${bc?.contentScrollIndex},${bc?.contentScrollOffset})\n",
                            )
                        }

                        is TabsDestination.Search -> {
                            val s = tabStates[dest.tabId]?.search
                            append(
                                "  - Search tabId=${dest.tabId} query=${dest.searchQuery} " +
                                    "persistedQuery=${s?.query} " +
                                    "scroll=(${s?.scrollIndex},${s?.scrollOffset}) " +
                                    "anchorId=${s?.anchorId}\n",
                            )
                        }

                        is TabsDestination.Home -> {
                            append("  - Home tabId=${dest.tabId}\n")
                        }
                    }
                }
            }
        }

        val saved =
            SavedSessionV2(
                tabs = destinations,
                selectedIndex = selectedIndex,
                tabStates = tabStates,
            )

        runCatching {
            val bytes = proto.encodeToByteArray(SavedSessionV2.serializer(), saved)
            sessionFile().writeBytes(bytes)
        }
    }

    /** Restores a saved session snapshot if the user enabled persistence in settings. */
    suspend fun restoreIfEnabled(appGraph: AppGraph) {
        if (!AppSettings.isPersistSessionEnabled()) return

        val file = sessionFile()
        if (!file.exists()) return

        _isRestoringSession.value = true
        try {
            val bytes = withContext(Dispatchers.IO) { file.readBytes() }
            val saved =
                runCatching {
                    proto.decodeFromByteArray(SavedSessionV2.serializer(), bytes)
                }.getOrElse {
                    // Corrupt session; delete to avoid repeated restore attempts.
                    runCatching { file.delete() }
                    return
                }
            if (saved.tabs.isEmpty()) return

            // Ignore ephemeral `lineId` navigation hints on cold boot restore.
            // Scroll/selection restoration is handled via `tabStates`.
            val destinations =
                saved.tabs.map { dest ->
                    when (dest) {
                        is TabsDestination.BookContent -> dest.copy(lineId = null)
                        else -> dest
                    }
                }

            debugln {
                buildString {
                    append("[SessionManager] Restoring session: tabs=${destinations.size}, selectedIndex=${saved.selectedIndex}\n")
                    destinations.forEach { dest ->
                        when (dest) {
                            is TabsDestination.BookContent -> {
                                val bc = saved.tabStates[dest.tabId]?.bookContent
                                append(
                                    "  - BookContent tabId=${dest.tabId} destBookId=${dest.bookId} " +
                                        "persistedBookId=${bc?.selectedBookId} " +
                                        "selectedLineId=${bc?.selectedLineId} " +
                                        "anchorLineId=${bc?.contentAnchorLineId} " +
                                        "scroll=(${bc?.contentScrollIndex},${bc?.contentScrollOffset})\n",
                                )
                            }

                            is TabsDestination.Search -> {
                                val s = saved.tabStates[dest.tabId]?.search
                                append(
                                    "  - Search tabId=${dest.tabId} query=${dest.searchQuery} " +
                                        "persistedQuery=${s?.query} " +
                                        "scroll=(${s?.scrollIndex},${s?.scrollOffset}) " +
                                        "anchorId=${s?.anchorId}\n",
                                )
                            }

                            is TabsDestination.Home -> {
                                append("  - Home tabId=${dest.tabId}\n")
                            }
                        }
                    }
                }
            }

            // Restore persisted tab state first, so viewmodels can consume it as they start.
            appGraph.tabPersistedStateStore.restore(saved.tabStates)

            // Restore tabs and selection.
            val tabsVm: TabsViewModel = appGraph.tabsViewModel
            tabsVm.restoreTabs(destinations, saved.selectedIndex)

            // Best-effort immediate title hydration to avoid showing raw IDs.
            hydrateTabTitles(
                destinations = destinations,
                tabStates = saved.tabStates,
                titleUpdateManager = appGraph.tabTitleUpdateManager,
                appGraph = appGraph,
            )
        } finally {
            _isRestoringSession.value = false
        }
    }

    private suspend fun hydrateTabTitles(
        destinations: List<TabsDestination>,
        tabStates: Map<String, TabPersistedState>,
        titleUpdateManager: TabTitleUpdateManager,
        appGraph: AppGraph,
    ) {
        for (dest in destinations) {
            val tabId = dest.tabId
            when (dest) {
                is TabsDestination.Search -> {
                    val q = tabStates[tabId]?.search?.query?.takeIf { it.isNotBlank() } ?: dest.searchQuery
                    if (q.isNotBlank()) {
                        titleUpdateManager.updateTabTitle(tabId, q, TabType.SEARCH)
                    }
                }

                is TabsDestination.BookContent -> {
                    val bookId = tabStates[tabId]?.bookContent?.selectedBookId?.takeIf { it > 0 } ?: dest.bookId
                    if (bookId > 0) {
                        val book = withContext(Dispatchers.IO) { appGraph.repository.getBookCore(bookId) }
                        if (book != null) {
                            titleUpdateManager.updateTabTitle(tabId, book.title, TabType.BOOK)
                        }
                    }
                }

                is TabsDestination.Home -> {
                    // No-op: Home titles are localized in the UI.
                }
            }
        }
    }
}
