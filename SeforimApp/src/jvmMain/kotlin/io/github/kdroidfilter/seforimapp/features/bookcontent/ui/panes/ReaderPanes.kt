@file:OptIn(ExperimentalNucleusApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nucleusframework.application.Satellite
import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.tao.DockSide
import dev.nucleusframework.window.tao.DockSplitterScope
import dev.nucleusframework.window.tao.SatellitePlacement
import dev.nucleusframework.window.tao.SatelliteScope
import dev.nucleusframework.window.tao.SatelliteWorkspace
import dev.nucleusframework.window.tao.TabWindowGroup
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.rememberSearchShellActions
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.tabBookViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.tabSearchViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.tabUi
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookTextMenus
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.LayoutState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.SplitDefaults
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.LocalPaneSatellite
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.CommentsPane
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.SourcesPane
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.TargumPane
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.isBookTextShown
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.booktoc.BookTocPanel
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.booktoc.SearchBookTocPanel
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.categorytree.CategoryTreePanel
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.categorytree.SearchCategoryTreePanel
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.notes.NotesPanel
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopSession
import io.github.kdroidfilter.seforimapp.framework.desktop.DockSizes
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.FlowPreview
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.book_list
import seforimapp.seforimapp.generated.resources.commentaries
import seforimapp.seforimapp.generated.resources.links
import seforimapp.seforimapp.generated.resources.notes_pane
import seforimapp.seforimapp.generated.resources.sources
import seforimapp.seforimapp.generated.resources.table_of_contents
import kotlin.math.abs

/**
 * One pane of the reader: a dock satellite of its window, drawing the window's selected tab.
 *
 * The panes sit where the reader's split panes had them. [navigation] panes (book tree, contents,
 * notes) are columns of the window's outer dock, running its full height on the start side (the
 * right, in Hebrew); the line panes (links, commentaries, sources) belong to the inner dock around
 * the text, above its breadcrumb. [fixed] panes are the reader's furniture: they cannot float, move
 * side or be reordered, and no pane can be dropped in front of them — they can still be hidden and
 * resized.
 */
enum class ReaderPane(
    val id: String,
    val title: StringResource,
    val home: SatellitePlacement.Docked,
    val toggle: BookContentEvent,
    val navigation: Boolean,
    val fixed: Boolean = false,
) {
    Tree(
        "tree",
        Res.string.book_list,
        SatellitePlacement.Docked(DockSide.Right, order = 0, extent = 150.dp),
        toggle = BookContentEvent.ToggleBookTree,
        navigation = true,
        fixed = true,
    ),
    Toc(
        "toc",
        Res.string.table_of_contents,
        SatellitePlacement.Docked(DockSide.Right, order = 1, extent = 120.dp),
        toggle = BookContentEvent.ToggleToc,
        navigation = true,
        fixed = true,
    ),
    Notes(
        "notes",
        Res.string.notes_pane,
        SatellitePlacement.Docked(DockSide.Right, order = 2, extent = 220.dp),
        toggle = BookContentEvent.ToggleNotes,
        navigation = true,
    ),
    Targum(
        "targum",
        Res.string.links,
        SatellitePlacement.Docked(DockSide.Left, extent = 220.dp),
        toggle = BookContentEvent.ToggleTargum,
        navigation = false,
    ),
    Comments(
        "comments",
        Res.string.commentaries,
        SatellitePlacement.Docked(DockSide.Bottom, extent = 220.dp),
        toggle = BookContentEvent.ToggleCommentaries,
        navigation = false,
    ),
    Sources(
        "sources",
        Res.string.sources,
        SatellitePlacement.Docked(DockSide.Bottom, extent = 120.dp),
        toggle = BookContentEvent.ToggleSources,
        navigation = false,
    ),
    ;

    /** This pane's satellite id in the dock of window [groupId]: every window has its own docks. */
    fun idIn(groupId: String): String = "$groupId-$id"

    fun workspaceIn(
        session: DesktopSession,
        groupId: String,
    ): SatelliteWorkspace = session.panesOf(groupId, navigation)
}

/** The navigation column is the start side of the window, as the split panes had it. */
private val NavigationDockSides = setOf(DockSide.Right)

/** Around the text: anywhere but its top, which is the tab strip's side. */
private val LineDockSides = setOf(DockSide.Left, DockSide.Right, DockSide.Bottom)

/** The navigation panes are columns, not a stack. */
val NavigationLayeredSides = setOf(DockSide.Right)

/** The commentaries run under the text and the links, as the vertical split did. */
val LineSideOrder = listOf(DockSide.Bottom, DockSide.Left, DockSide.Right, DockSide.Top)

/**
 * The panes of one reader window, declared at application scope so they belong to the window, not
 * to a tab: a tab change creates or destroys no panel, it changes what the panels draw and which
 * are open — each tab keeps its own open panes (its ViewModel's visibility flags).
 */
@Composable
fun WindowPanes(
    session: DesktopSession,
    group: TabWindowGroup,
) {
    PaneVisibilitySync(session, group)
    for (pane in ReaderPane.entries) {
        key(pane) {
            Satellite(
                workspace = pane.workspaceIn(session, group.id),
                id = pane.idIn(group.id),
                title = stringResource(pane.title),
                initialPlacement = pane.home,
                initiallyOpen = false,
                dockSides = if (pane.navigation) NavigationDockSides else LineDockSides,
                floatable = !pane.fixed,
                reorderable = !pane.fixed,
                // The pane draws its own header (PaneHeader), which is also its grip.
                header = {},
            ) {
                CompositionLocalProvider(LocalPaneSatellite provides this) {
                    PaneBody(session, group, pane)
                }
            }
        }
    }
}

/** What the selected tab wants shown, and where its toggles go. */
private class PaneDemand(
    val tabId: String?,
    val panes: Set<ReaderPane>,
    val onEvent: (BookContentEvent) -> Unit,
    val layout: LayoutState? = null,
)

/**
 * Keeps the dock's open panes equal to the selected tab's visibility flags, both ways: a tab
 * change or a toolbar toggle opens / closes satellites; a pane closed from the dock itself (its
 * floating window's close button) toggles the flag back.
 */
@Composable
private fun PaneVisibilitySync(
    session: DesktopSession,
    group: TabWindowGroup,
) {
    val demandState = rememberUpdatedState(selectedTabDemand(session, group))
    val demand by demandState
    PaneSizeSync(session, group, demandState)
    LaunchedEffect(session, group.id) {
        fun entry(pane: ReaderPane) = pane.workspaceIn(session, group.id).satellite(pane.idIn(group.id))
        var applied: Pair<String?, Set<ReaderPane>>? = null
        var appliedRegistered = emptySet<ReaderPane>()
        snapshotFlow {
            val registered = ReaderPane.entries.filter { entry(it) != null }.toSet()
            val open = registered.filter { entry(it)?.isOpen == true }.toSet()
            Triple(demand, registered, open)
        }.collect { (current, registered, open) ->
            val wanted = current.tabId to current.panes
            if (wanted != applied || registered != appliedRegistered) {
                for (pane in registered) {
                    val workspace = pane.workspaceIn(session, group.id)
                    val id = pane.idIn(group.id)
                    if (pane in current.panes) workspace.open(id) else workspace.close(id)
                }
                applied = wanted
                appliedRegistered = registered
            } else {
                // The user closed (or reopened) a pane from the dock: follow on the tab.
                (open - current.panes).plus(current.panes - open).forEach { current.onEvent(it.toggle) }
            }
        }
    }
}

@Composable
private fun selectedTabDemand(
    session: DesktopSession,
    group: TabWindowGroup,
): PaneDemand {
    val item = group.selectedId?.let(session::item) ?: return PaneDemand(null, emptySet(), onEvent = {})
    val tabId = item.destination.tabId
    return key(tabId) {
        when (val destination = item.destination) {
            is TabsDestination.Home, is TabsDestination.BookContent, is TabsDestination.Search -> {
                val viewModel = tabBookViewModel(session.ownerOf(tabId), destination)
                val uiState by viewModel.uiState.collectAsState()
                PaneDemand(
                    tabId,
                    desiredPanes(uiState, isSearch = destination is TabsDestination.Search),
                    viewModel::onEvent,
                    uiState.layout,
                )
            }
            else -> PaneDemand(tabId, emptySet(), onEvent = {})
        }
    }
}

/**
 * Keeps the docked panes' sizes equal to the selected tab's split positions, both ways — each tab
 * keeps its own proportions, as its split panes did: a tab change or a window resize sizes the
 * panes from the tab's percentages, a splitter drag writes the percentage back (and saves it).
 */
@OptIn(ExperimentalSplitPaneApi::class, FlowPreview::class)
@Composable
private fun PaneSizeSync(
    session: DesktopSession,
    group: TabWindowGroup,
    demand: State<PaneDemand>,
) {
    val splitter = if (ThemeUtils.isIslandsStyle()) 0f else 1f
    LaunchedEffect(session, group.id, splitter) {
        var appliedTab: String? = null
        val applied = HashMap<ReaderPane, Float>()

        // The navigation column is layered (each pane its own width); the inner sides are split, their
        // panes sharing the side's thickness.
        fun extentOf(pane: ReaderPane): Float? {
            val workspace = pane.workspaceIn(session, group.id)
            val entry = workspace.satellite(pane.idIn(group.id))?.takeIf { it.isOpen } ?: return null
            val docked = entry.placement as? SatellitePlacement.Docked ?: return null
            return if (pane.navigation) docked.extent?.value else workspace.dockExtent(docked.side).value
        }

        fun setExtent(
            pane: ReaderPane,
            extent: Float,
        ) {
            val workspace = pane.workspaceIn(session, group.id)
            if (pane.navigation) {
                workspace.setDockedExtent(pane.idIn(group.id), extent.dp)
            } else {
                val side = (workspace.satellite(pane.idIn(group.id))?.placement as? SatellitePlacement.Docked)?.side ?: return
                workspace.setDockExtent(side, extent.dp)
            }
        }
        snapshotFlow {
            val current = demand.value
            val layout = current.layout
            val sizes = session.dockSizes[group.id]
            if (layout == null || sizes == null) {
                null
            } else {
                SizeFrame(current, layout, sizes, ReaderPane.entries.associateWith(::extentOf), splitPositions(layout), splitter)
            }
        }.collect { frame ->
            frame ?: return@collect
            if (frame.demand.tabId != appliedTab) {
                appliedTab = frame.demand.tabId
                applied.clear()
            }
            val targets = targetExtents(frame)
            var dragged = false
            for (pane in ReaderPane.entries) {
                val current = frame.extents[pane] ?: continue
                val target = targets[pane] ?: continue
                val last = applied[pane]
                if (last != null && abs(current - last) > EXTENT_TOLERANCE_DP) {
                    // A splitter drag: the tab takes the new proportion.
                    writeBack(pane, current, frame)
                    applied[pane] = current
                    dragged = true
                } else if (abs(current - target) > EXTENT_TOLERANCE_DP) {
                    setExtent(pane, target)
                    applied[pane] = target
                } else {
                    applied[pane] = current
                }
            }
            if (dragged) frame.demand.onEvent(BookContentEvent.SaveState)
        }
    }
}

private class SizeFrame(
    val demand: PaneDemand,
    val layout: LayoutState,
    val sizes: DockSizes,
    val extents: Map<ReaderPane, Float?>,
    // Read in the snapshot so a percentage change re-emits.
    val positions: List<Float>,
    /** The split panes' divider: 1 dp, none in the Islands style (see [ReaderSplitter]). */
    val splitter: Float,
)

@OptIn(ExperimentalSplitPaneApi::class)
private fun splitPositions(layout: LayoutState): List<Float> =
    listOf(
        layout.mainSplitState.positionPercentage,
        layout.tocSplitState.positionPercentage,
        layout.notesSplitState.positionPercentage,
        layout.targumSplitState.positionPercentage,
        layout.contentSplitState.positionPercentage,
    )

/** The split containers, as the nested split panes had them: each navigation column splits what the previous left. */
private fun containerOf(
    pane: ReaderPane,
    frame: SizeFrame,
): Float {
    val splitter = frame.splitter
    val tree = frame.extents[ReaderPane.Tree]?.plus(splitter) ?: 0f
    val toc = frame.extents[ReaderPane.Toc]?.plus(splitter) ?: 0f
    return when (pane) {
        ReaderPane.Tree -> frame.sizes.outerWidth
        ReaderPane.Toc -> frame.sizes.outerWidth - tree
        ReaderPane.Notes -> frame.sizes.outerWidth - tree - toc
        ReaderPane.Targum -> frame.sizes.innerWidth
        ReaderPane.Comments, ReaderPane.Sources -> frame.sizes.innerHeight
    }.coerceAtLeast(1f)
}

/**
 * The split pane's own mapping (Compose `SplitPane`): the first pane is
 * `min₁ + p × (container − min₂ − splitter − min₁)`. Navigation panes were the first half of their
 * split, line panes the second.
 */
@OptIn(ExperimentalSplitPaneApi::class)
private fun targetExtents(frame: SizeFrame): Map<ReaderPane, Float> =
    buildMap {
        for (pane in ReaderPane.entries) {
            if (frame.extents[pane] == null) continue
            val container = containerOf(pane, frame)
            val first = pane.firstMin + pane.position(frame.layout) * pane.travel(container, frame.splitter)
            put(pane, if (pane.navigation) first else container - frame.splitter - first)
        }
    }

@OptIn(ExperimentalSplitPaneApi::class)
private fun writeBack(
    pane: ReaderPane,
    extent: Float,
    frame: SizeFrame,
) {
    val container = containerOf(pane, frame)
    val first = if (pane.navigation) extent else container - frame.splitter - extent
    val ratio = ((first - pane.firstMin) / pane.travel(container, frame.splitter).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val layout = frame.layout
    when (pane) {
        ReaderPane.Tree -> layout.mainSplitState.positionPercentage = ratio
        ReaderPane.Toc -> layout.tocSplitState.positionPercentage = ratio
        ReaderPane.Notes -> layout.notesSplitState.positionPercentage = ratio
        ReaderPane.Targum -> layout.targumSplitState.positionPercentage = ratio
        ReaderPane.Comments, ReaderPane.Sources -> layout.contentSplitState.positionPercentage = ratio
    }
}

@OptIn(ExperimentalSplitPaneApi::class)
private fun ReaderPane.position(layout: LayoutState): Float =
    when (this) {
        ReaderPane.Tree -> layout.mainSplitState.positionPercentage
        ReaderPane.Toc -> layout.tocSplitState.positionPercentage
        ReaderPane.Notes -> layout.notesSplitState.positionPercentage
        ReaderPane.Targum -> layout.targumSplitState.positionPercentage
        ReaderPane.Comments, ReaderPane.Sources -> layout.contentSplitState.positionPercentage
    }

/** How far the split's divider can travel in [container]. */
private fun ReaderPane.travel(
    container: Float,
    splitter: Float,
): Float = (container - SPLIT_SECOND_MIN_DP - splitter - firstMin).coerceAtLeast(0f)

/** The first pane's minimum of this pane's split (the text, for the line panes). */
private val ReaderPane.firstMin: Float
    get() =
        when (this) {
            ReaderPane.Tree -> SplitDefaults.MIN_MAIN
            ReaderPane.Toc -> SplitDefaults.MIN_TOC
            ReaderPane.Notes -> SplitDefaults.MIN_NOTES
            else -> SPLIT_FIRST_MIN_DP
        }

// EnhancedHorizontalSplitPane / EnhancedVerticalSplitPane defaults.
private const val SPLIT_FIRST_MIN_DP = 200f
private const val SPLIT_SECOND_MIN_DP = 200f
private const val EXTENT_TOLERANCE_DP = 1f

private fun desiredPanes(
    uiState: BookContentState,
    isSearch: Boolean,
): Set<ReaderPane> =
    buildSet {
        if (uiState.navigation.isVisible) add(ReaderPane.Tree)
        if (uiState.toc.isVisible) add(ReaderPane.Toc)
        if (!isSearch && uiState.notes.isVisible) add(ReaderPane.Notes)
        // The line panes exist only while a book is on screen.
        if (isBookTextShown(uiState)) {
            if (uiState.content.showTargum) add(ReaderPane.Targum)
            when {
                uiState.content.showCommentaries -> add(ReaderPane.Comments)
                uiState.content.showSources -> add(ReaderPane.Sources)
            }
        }
    }

/** One pane's content for the window's selected tab. */
@Composable
private fun SatelliteScope.PaneBody(
    session: DesktopSession,
    group: TabWindowGroup,
    pane: ReaderPane,
) {
    val item = group.selectedId?.let(session::item) ?: return
    val tabId = item.destination.tabId
    key(tabId) {
        when (val destination = item.destination) {
            is TabsDestination.Home, is TabsDestination.BookContent -> BookPaneBody(session, destination, pane, search = null)
            is TabsDestination.Search -> BookPaneBody(session, destination, pane, search = destination)
            else -> Unit
        }
    }
}

@Composable
private fun BookPaneBody(
    session: DesktopSession,
    destination: TabsDestination,
    pane: ReaderPane,
    search: TabsDestination.Search?,
) {
    val owner = session.ownerOf(destination.tabId)
    val viewModel = tabBookViewModel(owner, destination)
    val uiState by viewModel.uiState.collectAsState()
    val showDiacritics by viewModel.showDiacritics.collectAsState()
    val onEvent = viewModel::onEvent
    val tabUi = tabUi(owner)
    val modifier = Modifier.fillMaxSize()

    BookTextMenus(uiState = uiState, onEvent = onEvent, showDiacritics = showDiacritics, tabUi = tabUi) {
        when (pane) {
            ReaderPane.Tree ->
                if (search != null) {
                    SearchTreePane(owner = owner, destination = search, uiState = uiState, onEvent = onEvent, modifier = modifier)
                } else {
                    CategoryTreePanel(uiState = uiState, onEvent = onEvent, modifier = modifier)
                }
            ReaderPane.Toc ->
                if (search != null) {
                    SearchTocPane(owner = owner, destination = search, uiState = uiState, onEvent = onEvent, modifier = modifier)
                } else {
                    BookTocPanel(uiState = uiState, onEvent = onEvent, modifier = modifier)
                }
            ReaderPane.Notes ->
                NotesPanel(
                    uiState = uiState,
                    onEvent = onEvent,
                    bookId = uiState.navigation.selectedBook?.id ?: 0L,
                    noteStore = LocalAppGraph.current.noteStore,
                    selectedLineIds = uiState.content.selectedLineIds,
                    primarySelectedLine = uiState.content.primaryLine,
                    draft = tabUi.noteDraft,
                    onConsumeDraft = { tabUi.noteDraft = null },
                    modifier = modifier,
                )
            ReaderPane.Targum, ReaderPane.Comments, ReaderPane.Sources -> {
                val book = uiState.navigation.selectedBook ?: return@BookTextMenus
                val connections = tabUi.connections(book.id)
                when (pane) {
                    ReaderPane.Targum -> TargumPane(uiState, onEvent, connections, showDiacritics, modifier)
                    ReaderPane.Comments -> CommentsPane(uiState, onEvent, connections, showDiacritics, modifier)
                    else -> SourcesPane(uiState, onEvent, connections, showDiacritics, modifier)
                }
            }
        }
    }
}

@Composable
private fun SearchTreePane(
    owner: io.github.kdroidfilter.seforimapp.core.presentation.tabs.SimpleTabViewModelOwner,
    destination: TabsDestination.Search,
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = tabSearchViewModel(owner, destination)
    val actions = rememberSearchShellActions(viewModel)
    val searchTree by viewModel.searchTreeFlow.collectAsState()
    val isFiltering by viewModel.isFilteringFlow.collectAsState()
    val selectedCategoryIds by viewModel.selectedCategoryIdsFlow.collectAsState()
    val selectedBookIds by viewModel.selectedBookIdsFlow.collectAsState()
    SearchCategoryTreePanel(
        uiState = uiState,
        onEvent = onEvent,
        searchTree = searchTree,
        isFiltering = isFiltering,
        selectedCategoryIds = selectedCategoryIds,
        selectedBookIds = selectedBookIds,
        onCategoryCheckedChange = actions.onCategoryCheckedChange,
        onBookCheckedChange = actions.onBookCheckedChange,
        onEnsureScopeBookForToc = actions.onEnsureScopeBookForToc,
        modifier = modifier,
    )
}

@Composable
private fun SearchTocPane(
    owner: io.github.kdroidfilter.seforimapp.core.presentation.tabs.SimpleTabViewModelOwner,
    destination: TabsDestination.Search,
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = tabSearchViewModel(owner, destination)
    val actions = rememberSearchShellActions(viewModel)
    val searchUi by viewModel.uiState.collectAsState()
    val tocTree by viewModel.tocTreeFlow.collectAsState()
    val tocCounts by viewModel.tocCountsFlow.collectAsState()
    val selectedTocIds by viewModel.selectedTocIdsFlow.collectAsState()
    SearchBookTocPanel(
        uiState = uiState,
        onEvent = onEvent,
        searchUi = searchUi,
        tocTree = tocTree,
        tocCounts = tocCounts,
        selectedTocIds = selectedTocIds,
        onToggle = actions.onTocToggle,
        onTocFilter = actions.onTocFilter,
        modifier = modifier,
    )
}

/**
 * The reader's splitter, as the split panes drew it: a 1 dp divider (none in the Islands style,
 * where the cards' gaps are the dividers) carrying a 5 dp grip.
 */
@Composable
fun DockSplitterScope.ReaderSplitter() {
    val horizontal = orientation == Orientation.Horizontal
    val thickness = if (ThemeUtils.isIslandsStyle()) 0.dp else 1.dp
    val line = if (horizontal) Modifier.fillMaxHeight().width(thickness) else Modifier.fillMaxWidth().height(thickness)
    Box(line.background(JewelTheme.globalColors.borders.disabled), contentAlignment = Alignment.Center) {
        val grip =
            if (horizontal) {
                Modifier
                    .requiredWidth(
                        GRIP_DP.dp,
                    ).fillMaxHeight()
            } else {
                Modifier.requiredHeight(GRIP_DP.dp).fillMaxWidth()
            }
        Box(grip.dockSplitterHandle())
    }
}

/**
 * The frame of a docked pane (and of the text), as the split panes had it: flush in Classic; in
 * Islands a rounded card, 3 dp from a neighbour above or below (the vertical split's gap) and 6 dp
 * from the window edge.
 */
@Composable
fun PaneCard(
    top: Dp = 6.dp,
    bottom: Dp = 6.dp,
    content: @Composable () -> Unit,
) {
    val modifier =
        if (ThemeUtils.isIslandsStyle()) {
            Modifier
                .fillMaxSize()
                .padding(top = top, bottom = bottom, start = 4.dp, end = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(JewelTheme.globalColors.panelBackground)
        } else {
            Modifier.fillMaxSize()
        }
    Box(modifier) { content() }
}

/** A docked pane's frame by its side of the text: [bottomOpen] is whether a pane is docked under it. */
@Composable
fun SatelliteScope.DockedPaneCard(
    bottomOpen: Boolean,
    content: @Composable () -> Unit,
) {
    val side = (satellite.placement as? SatellitePlacement.Docked)?.side
    when {
        side == DockSide.Bottom -> PaneCard(top = 3.dp, content = content)
        side == DockSide.Left || side == DockSide.Right -> PaneCard(bottom = if (bottomOpen) 3.dp else 6.dp, content = content)
        else -> PaneCard(content = content)
    }
}

private const val GRIP_DP = 5
