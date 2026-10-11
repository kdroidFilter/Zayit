package io.github.kdroidfilter.seforimapp.framework.desktop

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.WindowState
import androidx.lifecycle.viewModelScope
import dev.nucleusframework.application.NucleusWindow
import dev.nucleusframework.application.NucleusWindowBounds
import dev.nucleusframework.window.tao.TabWindowGroup
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import io.github.kdroidfilter.seforimapp.framework.session.SavedGeometry
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * One live OS window. A window always displays exactly one virtual desktop; a desktop can span
 * several windows. The window shows one group of its desktop's `TabWorkspace` ([session] +
 * [groupId]); switching desktop in place rebinds it to a group of the other desktop, so the OS
 * window itself stays. Window-scoped ViewModels ([tabsViewModel], [searchHomeViewModel]) live and
 * die with the window — per-tab state stays in the app-wide TabPersistedStateStore, keyed by tabId.
 */
@Stable
class OpenWindow internal constructor(
    val id: String,
    session: DesktopSession,
    groupId: String,
    val searchHomeViewModel: SearchHomeViewModel,
    val windowState: WindowState,
) {
    /** The desktop this window shows. */
    var session: DesktopSession by mutableStateOf(session)
        private set

    /** The group of [session]'s workspace this window shows. */
    var groupId: String by mutableStateOf(groupId)
        private set

    val tabsViewModel = TabsViewModel({ this.session }, { this.groupId })

    private val _desktopId = MutableStateFlow(session.desktopId)
    val desktopId: StateFlow<String> = _desktopId.asStateFlow()

    /** True while this window's tab set is being swapped to another desktop (shows a loader). */
    private val _isSwitching = MutableStateFlow(false)
    val isSwitching: StateFlow<Boolean> = _isSwitching.asStateFlow()

    /** Visibility of this window's tab-search popup (title-bar button / Cmd+Shift+A). */
    val tabSearchVisible = MutableStateFlow(false)

    /**
     * The window's last floating size. While maximized or fullscreen the native window reports the
     * screen-sized frame; the session, and windows cascaded from this one, want the size the window
     * returns to (see MainAppWindow).
     */
    var floatingSize: DpSize = windowState.size

    /** [windowState] for the session file, with the floating size. */
    fun savedGeometry(): SavedGeometry {
        val geometry = windowState.toSavedGeometry()
        if (!floatingSize.isSpecified) return geometry
        return geometry.copy(width = floatingSize.width.value.roundToInt(), height = floatingSize.height.value.roundToInt())
    }

    /** Measured size of this window's pane docks (see WindowBody); null until first laid out. */
    var dockSizes: DockSizes? by mutableStateOf(null)

    /** The tab whose panes the docks have laid out as planned: its text may lay out (see PaneSync). */
    var panesReadyFor: String? by mutableStateOf(null)

    /** Whether the pointer is over this window's tab strip (where a click may leave the tab). */
    var pointerOnStrip: Boolean by mutableStateOf(false)

    /** Attached by the window composable once the native window exists; used for toFront/focus. */
    @Volatile
    var nucleusWindow: NucleusWindow? = null

    /** The workspace group shown here, or null while a restored one waits for its tabs. */
    fun group(): TabWindowGroup? = session.group(groupId)

    /**
     * Outer window bounds in logical screen coordinates, or null while the native window isn't
     * realized. Used for cascading new windows.
     */
    fun boundsOnScreen(): NucleusWindowBounds? = nucleusWindow?.boundsOnScreen()

    internal fun bind(
        session: DesktopSession,
        groupId: String,
    ) {
        this.session = session
        this.groupId = groupId
        _desktopId.value = session.desktopId
    }

    internal fun markSwitching() {
        _isSwitching.value = true
    }

    /** Cleared by TabsContent after the first frame of the restored desktop has rendered. */
    fun clearSwitching() {
        _isSwitching.value = false
    }

    fun requestFocus() {
        nucleusWindow?.let {
            it.setMinimized(false)
            it.toFront()
            it.requestFocus()
        }
    }

    internal fun dispose() {
        tabsViewModel.dispose()
        searchHomeViewModel.viewModelScope.cancel()
    }
}

/** The window hosting the current composition. Provided by MainAppWindow. */
val LocalOpenWindow =
    staticCompositionLocalOf<OpenWindow> { error("No OpenWindow provided") }
