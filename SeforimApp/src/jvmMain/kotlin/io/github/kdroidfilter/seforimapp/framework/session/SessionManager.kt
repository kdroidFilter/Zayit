@file:OptIn(ExperimentalSerializationApi::class)

package io.github.kdroidfilter.seforimapp.framework.session

import io.github.kdroidfilter.seforim.desktop.VirtualDesktop
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import org.jetbrains.compose.resources.getString
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.desktop_default_name
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
                append("active=${desktopsState.activeDesktopId}\n")
                desktopsState.snapshots.forEach { (id, snap) ->
                    val desktopName = desktopsState.desktops.find { it.id == id }?.name ?: "?"
                    append("  Desktop '$desktopName': ${snap.destinations.size} tabs, selectedIndex=${snap.selectedIndex}\n")
                }
            }
        }

        runCatching {
            val bytes = proto.encodeToByteArray(DesktopsState.serializer(), desktopsState)
            desktopsFile().writeBytes(bytes)
        }
    }

    /** Restores a saved session snapshot if the user enabled persistence in settings. */
    suspend fun restoreIfEnabled(appGraph: AppGraph) {
        if (!AppSettings.isPersistSessionEnabled()) return

        _isRestoringSession.value = true
        try {
            val desktopsState = loadDesktopsState() ?: return
            if (desktopsState.desktops.isEmpty()) return

            // Compute titles for the active desktop snapshot
            val activeSnapshot = desktopsState.snapshots[desktopsState.activeDesktopId]
            val enrichedSnapshots = desktopsState.snapshots.toMutableMap()

            if (activeSnapshot != null) {
                val computedTitles = computeTabTitles(activeSnapshot.destinations, activeSnapshot.tabStates, appGraph)
                val mergedTitles = activeSnapshot.titles.toMutableMap()
                computedTitles.forEach { (tabId, pair) ->
                    mergedTitles[tabId] = SerializableTabTitle(title = pair.first, tabType = pair.second)
                }
                enrichedSnapshots[desktopsState.activeDesktopId] = activeSnapshot.copy(titles = mergedTitles)
            }

            val enrichedState = desktopsState.copy(snapshots = enrichedSnapshots)

            debugln {
                buildString {
                    append("[SessionManager] Restoring desktops session: ${enrichedState.desktops.size} desktops, ")
                    append("active=${enrichedState.activeDesktopId}\n")
                }
            }

            appGraph.desktopManager.restoreFromDesktopsState(enrichedState)
        } finally {
            _isRestoringSession.value = false
        }
    }

    /**
     * Loads [DesktopsState], migrating from legacy [SavedSessionV2] if needed.
     */
    private suspend fun loadDesktopsState(): DesktopsState? {
        val desktopsF = desktopsFile()
        val legacyF = legacySessionFile()

        // Try new format first
        if (desktopsF.exists()) {
            val bytes = withContext(Dispatchers.IO) { desktopsF.readBytes() }
            return runSuspendCatching {
                proto.decodeFromByteArray(DesktopsState.serializer(), bytes)
            }.getOrElse {
                runCatching { desktopsF.delete() }
                null
            }
        }

        // Migrate from legacy format
        if (legacyF.exists()) {
            val bytes = withContext(Dispatchers.IO) { legacyF.readBytes() }
            val saved =
                runSuspendCatching {
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
                    desktops = listOf(VirtualDesktop(id = desktopId, name = getString(Res.string.desktop_default_name, 1))),
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
            }
        }
        return titles
    }
}
