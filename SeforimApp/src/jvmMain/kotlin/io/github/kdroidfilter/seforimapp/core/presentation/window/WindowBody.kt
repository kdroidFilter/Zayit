package io.github.kdroidfilter.seforimapp.core.presentation.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import dev.nucleusframework.window.tao.DockLayout
import dev.nucleusframework.window.tao.DockSide
import dev.nucleusframework.window.tao.SatellitePlacement
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsContent
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.tabBookViewModel
import io.github.kdroidfilter.seforimapp.features.bookcontent.handleBookShortcut
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EndVerticalBar
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.StartVerticalBar
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.BookBreadcrumb
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.LocalBookTextReady
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.isBookTextShown
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.DockedPaneCard
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.LineSideOrder
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.NavigationLayeredSides
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.PaneCard
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.ReaderSplitter
import io.github.kdroidfilter.seforimapp.features.search.SearchBookDetailsToggle
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopSession
import io.github.kdroidfilter.seforimapp.framework.desktop.DockSizes
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * Under the tab strip, laid out as the reader's split panes were: the activity bars around the
 * outer dock (book tree, contents, notes — full height on the start side), whose centre is the
 * inner dock (links, commentaries, sources) around the selected tab's text, with the book's
 * breadcrumb under it. Every pane is a satellite drawing the selected tab.
 */
@Composable
fun WindowBody(openWindow: OpenWindow) {
    val session = openWindow.session
    val selected = openWindow.group()?.selectedId?.let(session::item)
    val destination = selected?.destination
    // Home, a book or a search: the tabs with the reader's bars and panes (not History / Favorites).
    val readerDestination =
        destination?.takeIf { it is TabsDestination.Home || it is TabsDestination.BookContent || it is TabsDestination.Search }
    val lineWorkspace = session.panesOf(openWindow.groupId, navigation = false)
    // The book view's shortcuts (Ctrl/Cmd+B, K, J) act on the selected reader tab, as they did
    // over the whole book screen.
    val readerEvents =
        if (readerDestination != null) {
            key(readerDestination.tabId) { tabBookViewModel(session.ownerOf(readerDestination.tabId), readerDestination)::onEvent }
        } else {
            null
        }
    val bottomOpen =
        lineWorkspace.satellites.any { entry ->
            entry.isOpen && (entry.placement as? SatellitePlacement.Docked)?.side == DockSide.Bottom
        }

    val density = LocalDensity.current
    var outerWidth by remember { mutableIntStateOf(0) }
    var innerSize by remember { mutableStateOf(IntSize.Zero) }
    // The panes are sized as fractions of these, as the split panes were (see PaneSizeSync).
    LaunchedEffect(outerWidth, innerSize, density) {
        if (outerWidth > 0 && innerSize.width > 0) {
            openWindow.dockSizes = DockSizes(outerWidth, innerSize.width, innerSize.height, density.density)
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .then(rememberTabThumbnails(openWindow))
            .background(canvasBackground())
            .onPreviewKeyEvent { event -> readerEvents?.let { handleBookShortcut(event, it) } ?: false },
    ) {
        if (readerDestination != null) {
            key(readerDestination.tabId) { ReaderBar(session, readerDestination, start = true) }
        }
        DockLayout(
            workspace = session.panesOf(openWindow.groupId, navigation = true),
            modifier = Modifier.weight(1f).fillMaxHeight().onSizeChanged { outerWidth = it.width },
            layeredSides = NavigationLayeredSides,
            splitter = { ReaderSplitter() },
            panel = { panel -> PaneCard { panel() } },
        ) {
            Column(Modifier.fillMaxSize()) {
                DockLayout(
                    workspace = lineWorkspace,
                    modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged { innerSize = it },
                    sideOrder = LineSideOrder,
                    splitter = { ReaderSplitter() },
                    panel = { panel -> DockedPaneCard(bottomOpen) { panel() } },
                ) {
                    // A book's text first lays out once its panes are in place, as with the split panes.
                    CompositionLocalProvider(LocalBookTextReady provides { tabId -> openWindow.panesReadyFor == tabId }) {
                        TabsContent()
                    }
                }
                if (readerDestination != null) {
                    key(readerDestination.tabId) { ReaderBreadcrumb(session, readerDestination) }
                }
            }
        }
        if (readerDestination != null) {
            key(readerDestination.tabId) { ReaderBar(session, readerDestination, start = false) }
        }
    }
}

@Composable
private fun ReaderBreadcrumb(
    session: DesktopSession,
    destination: TabsDestination,
) {
    val viewModel = tabBookViewModel(session.ownerOf(destination.tabId), destination)
    val uiState by viewModel.uiState.collectAsState()
    if (isBookTextShown(uiState)) BookBreadcrumb(uiState = uiState, onEvent = viewModel::onEvent)
}

/** One of the two activity bars of a reader tab (start: navigation panes; end: text and line panes). */
@Composable
private fun ReaderBar(
    session: DesktopSession,
    destination: TabsDestination,
    start: Boolean,
) {
    val viewModel = tabBookViewModel(session.ownerOf(destination.tabId), destination)
    val uiState by viewModel.uiState.collectAsState()
    val diacritics by viewModel.diacritics.collectAsState()
    // A search's results have no navigation bar (their tabs filter); the end bar keeps the zoom for the preview
    if (start && destination is TabsDestination.Search && !isBookTextShown(uiState)) return
    Box(Modifier.fillMaxHeight()) {
        if (start) {
            StartVerticalBar(uiState = uiState, onEvent = viewModel::onEvent, showBookTree = destination !is TabsDestination.Search)
        } else if (uiState.navigation.selectedBook != null || destination is TabsDestination.Search) {
            EndVerticalBar(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                diacritics = diacritics,
                extraBottomContent = {
                    if (destination is TabsDestination.Search && !isBookTextShown(uiState)) SearchBookDetailsToggle()
                },
            )
        }
    }
}

@Composable
internal fun canvasBackground() = JewelTheme.globalColors.toolwindowBackground
