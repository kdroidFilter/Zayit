package io.github.kdroidfilter.seforimapp.core.presentation.window

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.application.Tab
import dev.nucleusframework.application.TabDragGhostWindow
import io.github.kdroidfilter.seforim.tabs.TabItem
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.WindowPanes
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopSession
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.app_name
import seforimapp.seforimapp.generated.resources.home_tab_with_app

/**
 * What one open desktop declares at application scope, next to its windows: every tab (the
 * desktop's `TabWorkspace` decides which window shows it), the pane satellites of each of its
 * windows, and the window a dragged tab travels in.
 */
@Composable
fun NucleusApplicationScope.DesktopTabs(session: DesktopSession) {
    val desktopManager = LocalAppGraph.current.desktopManager
    val thumbnails = LocalAppGraph.current.tabThumbnailStore
    val homeLabel = stringResource(Res.string.home_tab_with_app, stringResource(Res.string.app_name))
    for (item in session.tabs) {
        val tabId = item.destination.tabId
        key(tabId) {
            // The body is drawn (and kept alive) by the window's TabsContent, not by the workspace.
            Tab(session.workspace, id = tabId, title = tabLabel(item, homeLabel), group = session.initialGroupOf(tabId)) {}
            // Keyed on the session too: a desktop restored in place is a new session under the same id.
            LaunchedEffect(session, tabId) {
                session.onDeclared(tabId)
                // A restored tab's hover card: the picture saved before the restart, until the tab
                // is shown again and takes a new one.
                val entry = session.workspace.tab(tabId) ?: return@LaunchedEffect
                if (entry.thumbnail != null) return@LaunchedEffect
                val saved = withContext(Dispatchers.IO) { thumbnails.load(tabId) }
                if (saved != null && entry.thumbnail == null) entry.thumbnail = saved
            }
            // Closing a tab is a workspace call (×, window close); a tab still declared once the
            // workspace dropped it would be registered again and hosted nowhere.
            val closed = session.workspace.tab(tabId) == null
            LaunchedEffect(closed) { if (closed) desktopManager.onTabClosed(session, tabId) }
        }
    }

    val windows by desktopManager.windows.collectAsState()
    for (window in windows) {
        if (window.session !== session) continue
        key(window.id, window.groupId) { WindowPanes(window) }
    }

    TabDragGhostWindow(session.workspace) { ghost -> TabDragGhostCard(ghost.tab.title) }
}

private fun tabLabel(
    item: TabItem,
    homeLabel: String,
): String = item.title.ifEmpty { homeLabel }

/** The card under the pointer while a tab is dragged out of its strip, and in the slot it would drop into. */
@Composable
internal fun TabDragGhostCard(
    title: String,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    Box(
        modifier =
            modifier
                .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(10.dp))
                .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = JewelTheme.globalColors.text.normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
