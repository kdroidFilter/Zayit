package io.github.kdroidfilter.seforimapp.framework.desktop

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.WindowState
import androidx.lifecycle.viewModelScope
import dev.nucleusframework.application.NucleusWindow
import dev.nucleusframework.application.NucleusWindowBounds
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One live OS window. A window always displays exactly one virtual desktop; a desktop can span
 * several windows. Window-scoped ViewModels ([tabsViewModel], [searchHomeViewModel]) live and die
 * with the window — per-tab state stays in the app-wide TabPersistedStateStore, keyed by tabId.
 */
@Stable
class OpenWindow internal constructor(
    val id: String,
    desktopId: String,
    val tabsViewModel: TabsViewModel,
    val searchHomeViewModel: SearchHomeViewModel,
    val windowState: WindowState,
) {
    private val _desktopId = MutableStateFlow(desktopId)
    val desktopId: StateFlow<String> = _desktopId.asStateFlow()

    /** True while this window's tab set is being swapped to another desktop (shows a loader). */
    private val _isSwitching = MutableStateFlow(false)
    val isSwitching: StateFlow<Boolean> = _isSwitching.asStateFlow()

    /** Attached by the window composable once the native window exists; used for toFront/focus. */
    @Volatile
    var nucleusWindow: NucleusWindow? = null

    /**
     * Outer window bounds in logical screen coordinates (backend-agnostic: AWT and Tao), or null
     * while the native window isn't realized. Used for cross-window drag & drop hit-testing.
     */
    fun boundsOnScreen(): NucleusWindowBounds? = nucleusWindow?.boundsOnScreen()

    internal fun setDesktop(desktopId: String) {
        _desktopId.value = desktopId
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
