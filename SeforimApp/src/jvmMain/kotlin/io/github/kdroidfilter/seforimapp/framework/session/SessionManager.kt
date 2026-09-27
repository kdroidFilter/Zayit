@file:OptIn(ExperimentalSerializationApi::class)

package io.github.kdroidfilter.seforimapp.framework.session

import io.github.kdroidfilter.seforim.desktop.VirtualDesktop
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.File

/**
 * Persists and restores the navigation session (open tabs + per-tab persisted UI state) when enabled.
 *
 * Now supports multiple virtual desktops via [DesktopsState].
 * Migrates transparently from the legacy single-desktop [SavedSessionV2] format.
 */
object SessionManager {
    private val proto = ProtoBuf

    private val _isRestoringSession = MutableStateFlow(hasSavedSessionToRestore())
    val isRestoringSession: StateFlow<Boolean> = _isRestoringSession

    private fun sessionDir(): File {
        val root = File(FileKit.databasesDir.path, "session").apply { mkdirs() }
        return root
    }

    private fun legacySessionFile(): File = File(sessionDir(), "session_v2.pb")

    private fun desktopsFile(): File = File(sessionDir(), "desktops_v1.pb")

    private fun hasSavedSessionToRestore(): Boolean =
        AppSettings.isPersistSessionEnabled() && (desktopsFile().exists() || legacySessionFile().exists())

    /** Saves the current session snapshot if the user enabled persistence in settings. */
    fun saveIfEnabled(appGraph: AppGraph) {
        if (!AppSettings.isPersistSessionEnabled()) return

        val desktopManager: DesktopManager = appGraph.desktopManager
        val desktopsState = desktopManager.buildDesktopsState()

        debugln {
            buildString {
                append("[SessionManager] Saving desktops session: ${desktopsState.desktops.size} desktops, ")
                append("open=${desktopsState.openDesktopIds}, focused=${desktopsState.focusedDesktopId}\n")
                desktopsState.snapshots.forEach { (id, snap) ->
                    val desktopName = desktopsState.desktops.find { it.id == id }?.name ?: "?"
                    val windows = snap.effectiveWindows()
                    append("  Desktop '$desktopName': ${windows.size} windows, ")
                    append("${windows.sumOf { it.destinations.size }} tabs\n")
                }
            }
        }

        runCatching {
            val bytes = proto.encodeToByteArray(DesktopsState.serializer(), desktopsState)
            desktopsFile().writeBytes(bytes)
        }
    }

    /**
     * The saved session, decoded synchronously at boot so the first frame already has its windows:
     * an application composing no window at all is closed at once. Null when disabled or absent.
     */
    fun loadBootState(): DesktopsState? {
        if (!AppSettings.isPersistSessionEnabled()) return null
        return loadDesktopsState()?.takeIf { it.desktops.isNotEmpty() }
    }

    /** Fills in the titles the restored tabs were saved without (book names are looked up in the DB). */
    suspend fun restoreIfEnabled(appGraph: AppGraph) {
        try {
            if (!AppSettings.isPersistSessionEnabled()) return
            val state = appGraph.desktopManager.buildDesktopsState()
            val enriched = enrichMissingTabTitles(state, appGraph)
            val sessions = appGraph.desktopManager.sessions.value
            enriched.snapshots.values.flatMap { it.effectiveWindows() }.forEach { window ->
                window.titles.forEach { (tabId, title) ->
                    sessions.firstOrNull { it.item(tabId)?.title?.isBlank() == true }?.updateTitle(tabId, title.title, title.tabType)
                }
            }
            // Give Compose one recomposition cycle to create the restored tabs' ViewModels (whose
            // initial state has isLoading=true); clearing the flag earlier flashes the Home page.
            withContext(NonCancellable) { delay(150) }
        } finally {
            _isRestoringSession.value = false
        }
    }

    /**
     * Loads [DesktopsState], migrating from legacy [SavedSessionV2] if needed.
     */
    private fun loadDesktopsState(): DesktopsState? {
        val desktopsF = desktopsFile()
        val legacyF = legacySessionFile()

        // Try new format first
        if (desktopsF.exists()) {
            val bytes = desktopsF.readBytes()
            return runCatching {
                proto.decodeFromByteArray(DesktopsState.serializer(), bytes)
            }.getOrElse {
                runCatching { desktopsF.delete() }
                null
            }
        }

        // Migrate from legacy format
        if (legacyF.exists()) {
            val bytes = legacyF.readBytes()
            val saved =
                runCatching {
                    proto.decodeFromByteArray(SavedSessionV2.serializer(), bytes)
                }.getOrElse {
                    runCatching { legacyF.delete() }
                    return null
                }

            if (saved.tabs.isEmpty()) return null

            // Strip ephemeral lineId
            val destinations =
                saved.tabs.map { dest ->
                    when (dest) {
                        is TabsDestination.BookContent -> dest.copy(lineId = null)
                        else -> dest
                    }
                }

            val desktopId = "migrated-desktop"
            val snapshot =
                DesktopTabsSnapshot(
                    destinations = destinations,
                    selectedIndex = saved.selectedIndex,
                    titles = emptyMap(),
                    tabStates = saved.tabStates,
                )

            val state =
                DesktopsState(
                    desktops =
                        listOf(
                            VirtualDesktop(
                                id = desktopId,
                                name = "\u05DE\u05E8\u05D7\u05D1 \u05D0׳",
                            ),
                        ),
                    activeDesktopId = desktopId,
                    snapshots = mapOf(desktopId to snapshot),
                )

            // Save in new format and delete legacy file
            runCatching {
                val newBytes = proto.encodeToByteArray(DesktopsState.serializer(), state)
                desktopsF.writeBytes(newBytes)
                legacyF.delete()
            }

            return state
        }

        return null
    }

    private suspend fun computeTabTitles(
        destinations: List<TabsDestination>,
        tabStates: Map<String, TabPersistedState>,
        appGraph: AppGraph,
    ): Map<String, Pair<String, TabType>> {
        val titles = mutableMapOf<String, Pair<String, TabType>>()
        for (dest in destinations) {
            currentCoroutineContext().ensureActive()
            val tabId = dest.tabId
            when (dest) {
                is TabsDestination.Search -> {
                    val q = tabStates[tabId]?.search?.query?.takeIf { it.isNotBlank() } ?: dest.searchQuery
                    if (q.isNotBlank()) {
                        titles[tabId] = q to TabType.SEARCH
                    }
                }

                is TabsDestination.BookContent -> {
                    val bookId = tabStates[tabId]?.bookContent?.selectedBookId?.takeIf { it > 0 } ?: dest.bookId
                    if (bookId > 0) {
                        val book = withContext(Dispatchers.IO) { appGraph.repository.getBookCore(bookId) }
                        if (book != null) {
                            titles[tabId] = book.title to TabType.BOOK
                        }
                    }
                }

                is TabsDestination.Home -> {
                    // No-op: Home titles are localized in the UI.
                }

                is TabsDestination.History -> {
                    // No-op: the History screen localizes its own title.
                }

                is TabsDestination.Favorites -> {
                    // No-op: the Favorites screen localizes its own title.
                }
            }
        }
        return titles
    }

    private suspend fun enrichMissingTabTitles(
        state: DesktopsState,
        appGraph: AppGraph,
    ): DesktopsState {
        val enrichedSnapshots =
            state.snapshots.mapValues { (_, snapshot) ->
                // Normalize to the multi-window layout, then enrich each window's titles.
                val enrichedWindows =
                    snapshot.effectiveWindows().map { windowSnapshot ->
                        val destinationsMissingTitles =
                            windowSnapshot.destinations.filter { destination ->
                                destination !is TabsDestination.Home &&
                                    windowSnapshot.titles[destination.tabId]?.title.isNullOrBlank()
                            }
                        if (destinationsMissingTitles.isEmpty()) {
                            windowSnapshot
                        } else {
                            val computedTitles = computeTabTitles(destinationsMissingTitles, snapshot.tabStates, appGraph)
                            if (computedTitles.isEmpty()) {
                                windowSnapshot
                            } else {
                                val mergedTitles = windowSnapshot.titles.toMutableMap()
                                computedTitles.forEach { (tabId, pair) ->
                                    mergedTitles[tabId] = SerializableTabTitle(title = pair.first, tabType = pair.second)
                                }
                                windowSnapshot.copy(titles = mergedTitles)
                            }
                        }
                    }
                snapshot.copy(windows = enrichedWindows)
            }

        return state.copy(snapshots = enrichedSnapshots)
    }
}
