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

    @Volatile
    private var cachedStateForRestore: DesktopsState? = null

    /**
     * Boot-time peek at the focused window's saved geometry, so the very first window is created
     * directly with the right placement instead of flashing maximized before the async session
     * restore applies the real geometry. Decodes the session file once and caches the result for
     * [restoreIfEnabled].
     */
    fun peekInitialWindowGeometry(): SavedGeometry? {
        if (!AppSettings.isPersistSessionEnabled()) return null
        val file = desktopsFile()
        if (!file.exists()) return null
        val state =
            runCatching { proto.decodeFromByteArray(DesktopsState.serializer(), file.readBytes()) }
                .getOrNull() ?: return null
        cachedStateForRestore = state
        val openIds = state.effectiveOpenDesktopIds()
        val focusedId = state.focusedDesktopId.takeIf { it in openIds } ?: openIds.firstOrNull() ?: return null
        return state.snapshots[focusedId]
            ?.effectiveWindows()
            ?.firstOrNull()
            ?.geometry
    }

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

    /** Restores a saved session snapshot if the user enabled persistence in settings. */
    suspend fun restoreIfEnabled(appGraph: AppGraph) {
        if (!AppSettings.isPersistSessionEnabled()) return

        _isRestoringSession.value = true
        try {
            val desktopsState = loadDesktopsState() ?: return
            if (desktopsState.desktops.isEmpty()) return

            val enrichedState = enrichMissingTabTitles(desktopsState, appGraph)

            debugln {
                buildString {
                    append("[SessionManager] Restoring desktops session: ${enrichedState.desktops.size} desktops, ")
                    append("active=${enrichedState.activeDesktopId}\n")
                }
            }

            appGraph.desktopManager.restoreFromDesktopsState(enrichedState)

            // Give Compose one recomposition cycle to process the restored tabs
            // and create ViewModels (whose initial state has isLoading=true).
            // Without this, the flag clears before ViewModels exist, causing
            // a brief Home page flash.
            withContext(NonCancellable) { delay(150) }
        } finally {
            _isRestoringSession.value = false
        }
    }

    /**
     * Loads [DesktopsState], migrating from legacy [SavedSessionV2] if needed.
     */
    private suspend fun loadDesktopsState(): DesktopsState? {
        // Already decoded at boot by peekInitialWindowGeometry — don't re-read the file.
        cachedStateForRestore?.let {
            cachedStateForRestore = null
            return it
        }

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
