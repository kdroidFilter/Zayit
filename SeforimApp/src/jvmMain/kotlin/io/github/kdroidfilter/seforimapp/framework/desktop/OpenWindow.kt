package io.github.kdroidfilter.seforimapp.framework.desktop

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewModelScope
import dev.nucleusframework.application.NucleusWindow
import dev.nucleusframework.application.NucleusWindowBounds
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One live OS window: a group of its desktop's `TabWorkspace` ([id] is the group id). It exists
 * while the workspace shows the group, which is while the group holds tabs. Window-scoped
 * ViewModels ([tabsViewModel], [searchHomeViewModel]) live and die with it.
 */
@Stable
class OpenWindow internal constructor(
    val id: String,
    val session: DesktopSession,
    val tabsViewModel: TabsViewModel,
    val searchHomeViewModel: SearchHomeViewModel,
) {
    /** The desktop this window displays; windows never change desktop. */
    val desktopId: StateFlow<String> = MutableStateFlow(session.desktopId)

    /** Visibility of this window's tab-search popup (title-bar button / Cmd+Shift+A). */
    val tabSearchVisible = MutableStateFlow(false)

    /** Attached by the window composable once the native window exists. */
    var nucleusWindow: NucleusWindow? by mutableStateOf(null)

    /** Outer window bounds in logical screen coordinates, or null while the native window isn't realized. */
    fun boundsOnScreen(): NucleusWindowBounds? = nucleusWindow?.boundsOnScreen()

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

/** The window hosting the current composition. */
val LocalOpenWindow =
    staticCompositionLocalOf<OpenWindow> { error("No OpenWindow provided") }
