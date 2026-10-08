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
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.tao.DockSide
import dev.nucleusframework.window.tao.DockSplitterScope
import dev.nucleusframework.window.tao.SatellitePlacement
import dev.nucleusframework.window.tao.SatelliteScope
import dev.nucleusframework.window.tao.SatelliteWorkspace
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.rememberSearchShellActions
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.tabBookViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.tabSearchViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.tabUi
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
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import kotlin.math.roundToInt

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
        SatellitePlacement.Docked(DockSide.Bottom, extent = 220.dp, weight = 2f),
        toggle = BookContentEvent.ToggleCommentaries,
        navigation = false,
    ),
    Sources(
        "sources",
        Res.string.sources,
        SatellitePlacement.Docked(DockSide.Bottom, order = 1, extent = 120.dp),
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
fun WindowPanes(window: OpenWindow) {
    val session = window.session
    val groupId = window.groupId
    PaneSync(window, session, groupId)
    for (pane in ReaderPane.entries) {
        key(pane) {
            Satellite(
                workspace = pane.workspaceIn(session, groupId),
                id = pane.idIn(groupId),
                title = stringResource(pane.title),
                initialPlacement = pane.home,
                initiallyOpen = false,
                dockSides = if (pane.navigation) NavigationDockSides else LineDockSides,
                floatable = !pane.fixed,
                reorderable = !pane.fixed,
                // Zayit's windows are usually maximized: a pane pulled out stays on screen.
                hideWhileOwnerFullscreenOrMaximized = false,
                // The pane draws its own header (PaneHeader), which is also its grip.
                header = {},
                // On Wayland the floating bar moves the window; the pane docks from its header's Dock button.
                floatingBarMovesWindow = true,
                // The controls where the main window has them: the OS side, not the RTL content's.
                controlButtonsDirection = ControlButtonsDirection.SystemNative,
            ) {
                CompositionLocalProvider(LocalPaneSatellite provides this) {
                    PaneBody(session, groupId, pane)
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

/** One reading of everything the sync depends on. */
private class PaneFrame(
    val demand: PaneDemand,
    val registered: Set<ReaderPane>,
    val open: Set<ReaderPane>,
    val sizes: DockSizes?,
    /** Docked thickness of every open pane, in px. */
    val extents: Map<ReaderPane, Int>,
    // Read in the snapshot so a percentage change re-emits.
    val positions: List<Float>,
)

/**
 * Keeps the window's docks equal to the selected tab, as its split panes were: which panes are
 * open (the tab's visibility flags) and how big (the tab's split percentages, with SplitPane's own
 * px mapping). A tab change or a toolbar toggle sizes then opens / closes satellites — a pane opens
 * at its size; a pane closed from the dock itself, or a splitter dragged, is written back to the tab.
 * [OpenWindow.panesReadyFor] names the tab once the docks have laid out as planned: its text lays
 * out only then, in its final frame.
 */
@OptIn(ExperimentalSplitPaneApi::class)
@Composable
private fun PaneSync(
    window: OpenWindow,
    session: DesktopSession,
    groupId: String,
) {
    val demand = rememberUpdatedState(selectedTabDemand(session, groupId))
    val splitterDp = 0f
    LaunchedEffect(window, session, groupId, splitterDp) {
        val effectScope = this
        var saveJob: Job? = null

        fun workspace(pane: ReaderPane) = pane.workspaceIn(session, groupId)

        fun entry(pane: ReaderPane) = workspace(pane).satellite(pane.idIn(groupId))

        // The tab's split percentages size a line pane only on its home side: moved elsewhere, its size is the
        // dock's own, else two panes sharing one percentage would resize each other.
        fun sizedByTab(pane: ReaderPane): Boolean =
            pane.navigation || (entry(pane)?.placement as? SatellitePlacement.Docked)?.side == pane.home.side

        fun extentDpOf(pane: ReaderPane): Float? {
            val docked = entry(pane)?.takeIf { it.isOpen }?.placement as? SatellitePlacement.Docked ?: return null
            // The navigation column is layered (each pane its own width); the inner sides are split.
            return if (pane.navigation) docked.extent?.value else workspace(pane).dockExtent(docked.side).value
        }

        fun setExtent(
            pane: ReaderPane,
            px: Int,
            density: Float,
        ) {
            val extent = (px / density).dp
            if (pane.navigation) {
                workspace(pane).setDockedExtent(pane.idIn(groupId), extent)
            } else {
                val side = (entry(pane)?.placement as? SatellitePlacement.Docked)?.side ?: pane.home.side
                workspace(pane).setDockExtent(side, extent)
            }
        }

        var applied: Pair<String?, Set<ReaderPane>>? = null
        var appliedRegistered = emptySet<ReaderPane>()
        var sized = HashMap<ReaderPane, Int>()
        // The tab's panes the last follow-up was sent for: the toggles apply at once, but `demand` only catches up at
        // the next recomposition, and every frame until then (the toggles move split states) would toggle back.
        var followed: Set<ReaderPane>? = null
        window.panesReadyFor = null
        snapshotFlow {
            val registered = ReaderPane.entries.filter { entry(it) != null }.toSet()
            val open = registered.filter { entry(it)?.isOpen == true }.toSet()
            val sizes = window.dockSizes
            val extents =
                if (sizes == null) {
                    emptyMap()
                } else {
                    open.mapNotNull { pane -> extentDpOf(pane)?.let { pane to (it * sizes.density).roundToInt() } }.toMap()
                }
            PaneFrame(
                demand.value,
                registered,
                open,
                sizes,
                extents,
                demand.value.layout
                    ?.let(::splitPositions)
                    .orEmpty(),
            )
        }.collect { frame ->
            val current = frame.demand
            val sizes = frame.sizes
            val layout = current.layout
            val wanted = current.tabId to current.panes
            val splitter = sizes?.let { (splitterDp * it.density).roundToInt() } ?: 0
            if (wanted != applied || frame.registered != appliedRegistered) {
                // Wait for the dock to be measured: a pane opens at its size or not at all.
                if (sizes == null && current.panes.isNotEmpty()) return@collect
                window.panesReadyFor = null
                val planned = current.panes intersect frame.registered
                if (sizes != null && layout != null) {
                    sized = HashMap(plannedExtents(layout, sizes, planned.filter(::sizedByTab).toSet(), splitter))
                    sized.forEach { (pane, px) -> setExtent(pane, px, sizes.density) }
                }
                for (pane in frame.registered) {
                    if (pane in current.panes) workspace(pane).open(pane.idIn(groupId)) else workspace(pane).close(pane.idIn(groupId))
                }
                applied = wanted
                appliedRegistered = frame.registered
                // Nothing moved (a tab with the same panes at the same sizes): no new frame will come, so go on to
                // mark the tab ready now — waiting would leave its text on the loader for good.
                val unchanged =
                    frame.open == planned &&
                        (sizes == null || layout == null || sized.all { (pane, px) -> frame.extents[pane] == px })
                if (!unchanged) return@collect
            }
            val expected = current.panes intersect frame.registered
            if (frame.open != expected) {
                // The user closed (or reopened) a pane from the dock: follow on the tab, once per tab state.
                if (followed != current.panes) {
                    followed = current.panes
                    (frame.open - expected).plus(expected - frame.open).forEach { current.onEvent(it.toggle) }
                }
                return@collect
            }
            followed = null
            if (sizes == null || layout == null) {
                window.panesReadyFor = current.tabId
                return@collect
            }
            val targets = plannedExtents(layout, sizes, frame.open.filter(::sizedByTab).toSet(), splitter)
            var dragged = false
            var settled = true
            for (pane in frame.open) {
                val extent = frame.extents[pane] ?: continue
                val target = targets[pane] ?: continue
                val last = sized[pane]
                if (last != null && extent != last) {
                    // A splitter drag: the tab takes the new proportion.
                    writeBack(pane, extent, layout, sizes, frame.extents, splitter)
                    sized[pane] = extent
                    dragged = true
                } else if (extent != target) {
                    setExtent(pane, target, sizes.density)
                    sized[pane] = target
                    settled = false
                }
            }
            // Saved once the drag settles, as the split panes did (300 ms debounce).
            if (dragged) {
                saveJob?.cancel()
                saveJob = saveLater(effectScope) { current.onEvent(BookContentEvent.SaveState) }
            }
            // Ready once the inner dock has been measured around the planned columns.
            val innerPlanned = innerWidthPx(sizes, targets, frame.open, splitter)
            if (settled && sizes.innerWidthPx == innerPlanned) window.panesReadyFor = current.tabId
        }
    }
}

@Composable
private fun selectedTabDemand(
    session: DesktopSession,
    groupId: String,
): PaneDemand {
    val item = session.group(groupId)?.selectedId?.let(session::item) ?: return PaneDemand(null, emptySet(), onEvent = {})
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

private fun saveLater(
    @StructuredScope scope: CoroutineScope,
    save: () -> Unit,
): Job =
    scope.launch {
        delay(SAVE_DEBOUNCE_MS)
        save()
    }

private const val SAVE_DEBOUNCE_MS = 300L

@OptIn(ExperimentalSplitPaneApi::class)
private fun splitPositions(layout: LayoutState): List<Float> =
    listOf(
        layout.mainSplitState.positionPercentage,
        layout.tocSplitState.positionPercentage,
        layout.notesSplitState.positionPercentage,
        layout.targumSplitState.positionPercentage,
        layout.contentSplitState.positionPercentage,
    )

/** The inner dock's width once the navigation columns [open] have [extents]: the old content column. */
private fun innerWidthPx(
    sizes: DockSizes,
    extents: Map<ReaderPane, Int>,
    open: Set<ReaderPane>,
    splitter: Int,
): Int =
    sizes.outerWidthPx -
        ReaderPane.entries.filter { it.navigation && it in open }.sumOf { (extents[it] ?: 0) + splitter }

/**
 * The px thickness of every pane in [open] for [layout]'s split positions — Compose `SplitPane`'s
 * own mapping: the first pane is `round(min₁ × (1 − p) + max × p)` with
 * `max = container − min₂ − splitter` (never under `min₁`). Each navigation column splits what
 * the previous left; the line panes were the second half of their split, in the content column.
 */
@OptIn(ExperimentalSplitPaneApi::class)
private fun plannedExtents(
    layout: LayoutState,
    sizes: DockSizes,
    open: Set<ReaderPane>,
    splitter: Int,
): Map<ReaderPane, Int> {
    fun px(dp: Float): Int = (dp * sizes.density).roundToInt()

    fun first(
        pane: ReaderPane,
        container: Int,
    ): Int {
        val min = px(pane.firstMin)
        val max = (container - px(SPLIT_SECOND_MIN_DP) - splitter).coerceAtLeast(min)
        val p = pane.position(layout)
        return (min * (1 - p) + max * p).roundToInt()
    }
    val result = HashMap<ReaderPane, Int>()
    var column = sizes.outerWidthPx
    for (pane in listOf(ReaderPane.Tree, ReaderPane.Toc, ReaderPane.Notes)) {
        if (pane !in open) continue
        val extent = first(pane, column)
        result[pane] = extent
        column -= extent + splitter
    }
    for (pane in listOf(ReaderPane.Targum, ReaderPane.Comments, ReaderPane.Sources)) {
        if (pane !in open) continue
        val container = if (pane == ReaderPane.Targum) column else sizes.innerHeightPx
        result[pane] = container - splitter - first(pane, container)
    }
    return result
}

@OptIn(ExperimentalSplitPaneApi::class)
private fun writeBack(
    pane: ReaderPane,
    extent: Int,
    layout: LayoutState,
    sizes: DockSizes,
    extents: Map<ReaderPane, Int>,
    splitter: Int,
) {
    fun px(dp: Float): Int = (dp * sizes.density).roundToInt()
    var column = sizes.outerWidthPx
    for (nav in listOf(ReaderPane.Tree, ReaderPane.Toc, ReaderPane.Notes)) {
        if (nav == pane) break
        extents[nav]?.let { column -= it + splitter }
    }
    val container =
        when {
            pane.navigation -> column
            pane == ReaderPane.Targum -> sizes.innerWidthPx
            else -> sizes.innerHeightPx
        }
    val min = px(pane.firstMin)
    val max = (container - px(SPLIT_SECOND_MIN_DP) - splitter).coerceAtLeast(min)
    val first = if (pane.navigation) extent else container - splitter - extent
    val ratio = if (max == min) 0f else ((first - min).toFloat() / (max - min)).coerceIn(0f, 1f)
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

/** The first pane's minimum of this pane's split (the text, for the line panes), in dp. */
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
            if (uiState.content.showCommentaries) add(ReaderPane.Comments)
            if (uiState.content.showSources) add(ReaderPane.Sources)
        }
    }

/** One pane's content for the window's selected tab. */
@Composable
private fun SatelliteScope.PaneBody(
    session: DesktopSession,
    groupId: String,
    pane: ReaderPane,
) {
    val item = session.group(groupId)?.selectedId?.let(session::item) ?: return
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
    val diacritics by viewModel.diacritics.collectAsState()
    val onEvent = viewModel::onEvent
    val tabUi = tabUi(owner)
    val modifier = Modifier.fillMaxSize()

    BookTextMenus(uiState = uiState, onEvent = onEvent, diacritics = diacritics, tabUi = tabUi) {
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
                    ReaderPane.Targum -> TargumPane(uiState, onEvent, connections, diacritics, modifier)
                    ReaderPane.Comments -> CommentsPane(uiState, onEvent, connections, diacritics, modifier)
                    else -> SourcesPane(uiState, onEvent, connections, diacritics, modifier)
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
 * The reader's splitter: no visible line (the cards' gaps are the dividers), just a 5 dp grip.
 */
@Composable
fun DockSplitterScope.ReaderSplitter() {
    val horizontal = orientation == Orientation.Horizontal
    val line = if (horizontal) Modifier.fillMaxHeight().width(0.dp) else Modifier.fillMaxWidth().height(0.dp)
    Box(line, contentAlignment = Alignment.Center) {
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
 * The frame of a docked pane (and of the text): a rounded card, 3 dp from a neighbour above or
 * below (the vertical split's gap) and 6 dp from the window edge.
 */
@Composable
fun PaneCard(
    top: Dp = 6.dp,
    bottom: Dp = 6.dp,
    content: @Composable () -> Unit,
) {
    val modifier =
        Modifier
            .fillMaxSize()
            .padding(top = top, bottom = bottom, start = 4.dp, end = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(JewelTheme.globalColors.panelBackground)
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
