package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedState
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.savedState
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentScreen
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentViewModel
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.StateKeys
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.HomeSearchCallbacks
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeNavigationEvent
import io.github.kdroidfilter.seforimapp.features.search.SearchResultInBookShellMvi
import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchShellActions
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.session.SessionManager
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * Whether the current tab is selected/visible. Used to skip animations on background tabs.
 */
val LocalTabSelected = compositionLocalOf { true }

/**
 * Stable, rename-safe discriminator for a destination, used to key the per-tab
 * saveable UI state so it resets when a tab navigates to a different destination type.
 */
private fun TabsDestination.typeKey(): String =
    when (this) {
        is TabsDestination.Home -> "home"
        is TabsDestination.Search -> "search"
        is TabsDestination.BookContent -> "book"
    }

private fun saveableKeyFor(destination: TabsDestination): String = "${destination.tabId}:${destination.typeKey()}"

private fun saveableKeysFor(tabId: String): List<String> = listOf("$tabId:home", "$tabId:search", "$tabId:book")

/**
 * Simplified tab content renderer without Compose Navigation.
 *
 * Every open tab is composed and kept alive; switching never tears a tab down. Only the selected
 * tab is measured and placed, so hidden tabs incur no layout/draw cost while their ViewModel,
 * paging flow and scroll state stay hot. This is what makes switching instant and glitch-free:
 * the paged content list is never re-collected from empty, so there is no reload-and-jump and no
 * need for any alpha/crossfade masking. The cost is RAM proportional to the number of open tabs.
 */
@Composable
fun TabsContent() {
    val appGraph = LocalAppGraph.current
    val tabsViewModel: TabsViewModel = appGraph.tabsViewModel
    val searchHomeViewModel = appGraph.searchHomeViewModel
    val persistedStore = appGraph.tabPersistedStateStore

    val tabsState by tabsViewModel.state.collectAsState()
    val tabs = tabsState.tabs
    val selectedTabIndex = tabsState.selectedTabIndex
    val isRestoringSession by SessionManager.isRestoringSession.collectAsState()
    val isSwitchingDesktop by appGraph.desktopManager.isSwitching.collectAsState()
    val isTransitioning = isRestoringSession || isSwitchingDesktop

    val searchUi by remember(searchHomeViewModel) { searchHomeViewModel.uiState }.collectAsState()
    val scope = rememberCoroutineScope()

    val currentTabId = tabs.getOrNull(selectedTabIndex)?.destination?.tabId
    val latestCurrentTabId by rememberUpdatedState(currentTabId)

    fun launchSubmitSearch(
        @StructuredScope scope: CoroutineScope,
        query: String,
        tabId: String,
    ) {
        scope.launch { searchHomeViewModel.submitSearch(query, tabId) }
    }

    fun launchOpenReference(
        @StructuredScope scope: CoroutineScope,
        tabId: String,
    ) {
        scope.launch { searchHomeViewModel.openSelectedReferenceInCurrentTab(tabId) }
    }

    val homeSearchCallbacks =
        remember(searchHomeViewModel, scope) {
            HomeSearchCallbacks(
                onReferenceQueryChanged = searchHomeViewModel::onReferenceQueryChanged,
                onTocQueryChanged = searchHomeViewModel::onTocQueryChanged,
                onFilterChange = searchHomeViewModel::onFilterChange,
                onGlobalExtendedChange = searchHomeViewModel::onGlobalExtendedChange,
                onSubmitTextSearch = { query ->
                    val tabId = latestCurrentTabId ?: return@HomeSearchCallbacks
                    launchSubmitSearch(scope, query, tabId)
                },
                onOpenReference = {
                    val tabId = latestCurrentTabId ?: return@HomeSearchCallbacks
                    launchOpenReference(scope, tabId)
                },
                onPickCategory = searchHomeViewModel::onPickCategory,
                onPickBook = searchHomeViewModel::onPickBook,
                onPickToc = searchHomeViewModel::onPickToc,
            )
        }

    // Dismiss suggestions when navigating away from Home
    LaunchedEffect(tabs, selectedTabIndex) {
        val dest = tabs.getOrNull(selectedTabIndex)?.destination
        val isHome = dest is TabsDestination.Home
        if (!isHome) {
            searchHomeViewModel.dismissSuggestions()
        }
    }

    // Collect navigation events from SearchHomeViewModel and perform navigation
    LaunchedEffect(searchHomeViewModel, tabsViewModel) {
        searchHomeViewModel.navigationEvents.collect { event ->
            when (event) {
                is SearchHomeNavigationEvent.NavigateToSearch -> {
                    tabsViewModel.replaceCurrentTabDestination(
                        TabsDestination.Search(
                            searchQuery = event.query,
                            tabId = event.tabId,
                        ),
                    )
                }
                is SearchHomeNavigationEvent.NavigateToBookContent -> {
                    tabsViewModel.replaceCurrentTabDestination(
                        TabsDestination.BookContent(
                            bookId = event.bookId,
                            tabId = event.tabId,
                            lineId = event.lineId,
                        ),
                    )
                }
            }
        }
    }

    // ViewModel owners per tab - manages lifecycle and state. Owners survive while the
    // tab is open so re-selecting a tab needs no DB refetch (data is hot in the ViewModel).
    val tabOwners = remember { mutableMapOf<String, SimpleTabViewModelOwner>() }
    val knownTabIds = remember { mutableSetOf<String>() }
    // Holds per-tab saveable UI state across the teardown/rebuild that happens on switch.
    val saveableStateHolder = rememberSaveableStateHolder()

    // Cleanup removed tabs
    LaunchedEffect(tabs) {
        val activeTabIds = tabs.map { it.destination.tabId }.toSet()
        val removed = (knownTabIds + tabOwners.keys) - activeTabIds
        removed.forEach { tabId ->
            tabOwners.remove(tabId)?.clear()
            persistedStore.remove(tabId)
            saveableKeysFor(tabId).forEach(saveableStateHolder::removeState)
        }
        knownTabIds.clear()
        knownTabIds.addAll(activeTabIds)
    }

    DisposableEffect(Unit) {
        onDispose {
            tabOwners.values.forEach { it.clear() }
            tabOwners.clear()
        }
    }

    // Clear the desktop-switching flag after the first frame so the loader disappears
    LaunchedEffect(isSwitchingDesktop) {
        if (isSwitchingDesktop) {
            // Wait one frame for ViewModels to initialize with persisted state
            kotlinx.coroutines.delay(100)
            appGraph.desktopManager.clearSwitching()
        }
    }

    val isIslands = ThemeUtils.isIslandsStyle()
    val canvasBg =
        if (isIslands) {
            JewelTheme.globalColors.toolwindowBackground
        } else {
            JewelTheme.globalColors.panelBackground
        }

    Box(
        modifier =
            Modifier
                .trackActivation()
                .fillMaxSize()
                .background(canvasBg),
    ) {
        // Keep every open tab's composition alive; a tab is never torn down on switch. Each tab is
        // measured and drawn only while selected — hidden tabs stay composed (their ViewModel,
        // paging flow and LazyListState all hot) but are not measured or placed, so they cost no
        // layout/draw. Crucially, because nothing is disposed, the paged content list never reloads
        // from empty: switching back is instant, with no reload-and-jump and no alpha/crossfade
        // masking. The trade-off is RAM proportional to the number of open tabs.
        tabs.forEach { tabItem ->
            val tabId = tabItem.destination.tabId
            val isSelected = tabId == currentTabId
            val saveableKey = saveableKeyFor(tabItem.destination)
            key(saveableKey) {
                saveableStateHolder.SaveableStateProvider(saveableKey) {
                    val tabOwner = tabOwners.getOrPut(tabId) { SimpleTabViewModelOwner(tabId) }
                    CompositionLocalProvider(LocalTabSelected provides isSelected) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .layout { measurable, constraints ->
                                        // Selected: measure + place normally. Hidden: skip both so
                                        // the subtree stays composed (alive) but incurs no layout
                                        // or draw cost.
                                        if (isSelected) {
                                            val placeable = measurable.measure(constraints)
                                            layout(placeable.width, placeable.height) {
                                                placeable.place(0, 0)
                                            }
                                        } else {
                                            layout(0, 0) {}
                                        }
                                    },
                        ) {
                            when (val destination = tabItem.destination) {
                                is TabsDestination.Home -> {
                                    HomeTabContent(
                                        tabOwner = tabOwner,
                                        tabId = tabId,
                                        isSelected = isSelected,
                                        isRestoringSession = isTransitioning,
                                        searchUi = searchUi,
                                        searchCallbacks = homeSearchCallbacks,
                                    )
                                }

                                is TabsDestination.Search -> {
                                    SearchTabContent(
                                        tabOwner = tabOwner,
                                        destination = destination,
                                        isSelected = isSelected,
                                    )
                                }

                                is TabsDestination.BookContent -> {
                                    BookContentTabContent(
                                        tabOwner = tabOwner,
                                        destination = destination,
                                        isSelected = isSelected,
                                        isRestoringSession = isTransitioning,
                                        searchUi = searchUi,
                                        searchCallbacks = homeSearchCallbacks,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTabContent(
    tabOwner: SimpleTabViewModelOwner,
    tabId: String,
    isSelected: Boolean,
    isRestoringSession: Boolean,
    searchUi: io.github.kdroidfilter.seforimapp.features.search.SearchHomeUiState,
    searchCallbacks: HomeSearchCallbacks,
) {
    tabOwner.setDefaultArgs(savedState { putString(StateKeys.TAB_ID, tabId) })
    val viewModel: BookContentViewModel = assistedMetroViewModel(viewModelStoreOwner = tabOwner)
    val uiState by viewModel.uiState.collectAsState()
    val showDiacritics by viewModel.showDiacritics.collectAsState()
    val bookCharCounts by viewModel.bookCharCounts.collectAsState()

    BookContentScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        showDiacritics = showDiacritics,
        isRestoringSession = isRestoringSession,
        searchUi = searchUi,
        searchCallbacks = searchCallbacks,
        isSelected = isSelected,
        bookCharCounts = bookCharCounts,
    )
}

@Composable
private fun SearchTabContent(
    tabOwner: SimpleTabViewModelOwner,
    destination: TabsDestination.Search,
    isSelected: Boolean,
) {
    tabOwner.setDefaultArgs(
        savedState {
            putString(StateKeys.TAB_ID, destination.tabId)
            putString("searchQuery", destination.searchQuery)
        },
    )

    val viewModel: SearchResultViewModel = assistedMetroViewModel(viewModelStoreOwner = tabOwner)
    val bookVm: BookContentViewModel = assistedMetroViewModel(viewModelStoreOwner = tabOwner)

    // Keep tree computation disabled when tab is not selected
    LaunchedEffect(isSelected) {
        viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetUiVisible(isSelected))
    }
    DisposableEffect(destination.tabId) {
        onDispose { viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetUiVisible(false)) }
    }

    val bcUiState by bookVm.uiState.collectAsState()
    val showDiacritics by bookVm.showDiacritics.collectAsState()
    val searchUi by viewModel.uiState.collectAsState()
    val visibleResults by viewModel.visibleResultsFlow.collectAsState()
    val isFiltering by viewModel.isFilteringFlow.collectAsState()
    val breadcrumbs by viewModel.breadcrumbsFlow.collectAsState()
    val searchTree by viewModel.searchTreeFlow.collectAsState()
    val selectedCategoryIds by viewModel.selectedCategoryIdsFlow.collectAsState()
    val selectedBookIds by viewModel.selectedBookIdsFlow.collectAsState()
    val selectedTocIds by viewModel.selectedTocIdsFlow.collectAsState()
    val tocCounts by viewModel.tocCountsFlow.collectAsState()
    val tocTree by viewModel.tocTreeFlow.collectAsState()

    val actions =
        remember(viewModel) {
            SearchShellActions(
                onSubmit = { q ->
                    viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetQuery(q))
                    viewModel.onEvent(SearchResultViewModel.SearchResultEvents.ExecuteSearch)
                },
                onQueryChange = { q -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetQuery(q)) },
                onGlobalExtendedChange = { extended ->
                    viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetGlobalExtended(extended))
                    viewModel.onEvent(SearchResultViewModel.SearchResultEvents.ExecuteSearch)
                },
                onScroll = { anchorId, anchorIndex, index, offset ->
                    viewModel.onEvent(
                        SearchResultViewModel.SearchResultEvents.OnScroll(anchorId, anchorIndex, index, offset),
                    )
                },
                onCancelSearch = {
                    viewModel.onEvent(SearchResultViewModel.SearchResultEvents.CancelSearch)
                },
                onOpenResult = { r, newTab ->
                    viewModel.onEvent(SearchResultViewModel.SearchResultEvents.OpenResult(r, newTab))
                },
                onRequestBreadcrumb = { r ->
                    viewModel.onEvent(SearchResultViewModel.SearchResultEvents.RequestBreadcrumb(r))
                },
                onLoadMore = { viewModel.loadMore() },
                onCategoryCheckedChange = { id, checked ->
                    viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetCategoryChecked(id, checked))
                },
                onBookCheckedChange = { id, checked ->
                    viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetBookChecked(id, checked))
                },
                onEnsureScopeBookForToc = { id ->
                    viewModel.onEvent(SearchResultViewModel.SearchResultEvents.EnsureScopeBookForToc(id))
                },
                onTocToggle = { entry, checked ->
                    viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetTocChecked(entry.id, checked))
                },
                onTocFilter = { entry ->
                    viewModel.onEvent(SearchResultViewModel.SearchResultEvents.FilterByTocId(entry.id))
                },
            )
        }

    SearchResultInBookShellMvi(
        bookUiState = bcUiState,
        onEvent = bookVm::onEvent,
        showDiacritics = showDiacritics,
        searchUi = searchUi,
        visibleResults = visibleResults,
        isFiltering = isFiltering,
        breadcrumbs = breadcrumbs,
        searchTree = searchTree,
        selectedCategoryIds = selectedCategoryIds,
        selectedBookIds = selectedBookIds,
        selectedTocIds = selectedTocIds,
        tocCounts = tocCounts,
        tocTree = tocTree,
        actions = actions,
    )
}

@Composable
private fun BookContentTabContent(
    tabOwner: SimpleTabViewModelOwner,
    destination: TabsDestination.BookContent,
    isSelected: Boolean,
    isRestoringSession: Boolean,
    searchUi: io.github.kdroidfilter.seforimapp.features.search.SearchHomeUiState,
    searchCallbacks: HomeSearchCallbacks,
) {
    tabOwner.setDefaultArgs(
        savedState {
            putString(StateKeys.TAB_ID, destination.tabId)
            if (destination.bookId > 0) putLong(StateKeys.BOOK_ID, destination.bookId)
            destination.lineId?.let { putLong(StateKeys.LINE_ID, it) }
        },
    )

    val viewModel: BookContentViewModel = assistedMetroViewModel(viewModelStoreOwner = tabOwner)
    val uiState by viewModel.uiState.collectAsState()
    val showDiacritics by viewModel.showDiacritics.collectAsState()
    val bookCharCounts by viewModel.bookCharCounts.collectAsState()

    // React to destination changes when ViewModel is reused
    LaunchedEffect(destination.bookId, destination.lineId) {
        if (destination.bookId > 0) {
            val lineId = destination.lineId
            if (lineId != null && lineId > 0) {
                viewModel.onEvent(BookContentEvent.OpenBookAtLine(destination.bookId, lineId))
            } else {
                viewModel.onEvent(BookContentEvent.OpenBookById(destination.bookId))
            }
        }
    }

    BookContentScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        showDiacritics = showDiacritics,
        isRestoringSession = isRestoringSession,
        searchUi = searchUi,
        searchCallbacks = searchCallbacks,
        isSelected = isSelected,
        bookCharCounts = bookCharCounts,
    )
}

/**
 * Simplified ViewModel owner that manages lifecycle and state for a tab.
 * No Navigation dependency - just pure ViewModel lifecycle management.
 */
internal class SimpleTabViewModelOwner(
    private val tabId: String,
) : ViewModelStoreOwner,
    SavedStateRegistryOwner,
    HasDefaultViewModelProviderFactory {
    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val creationExtras = MutableCreationExtras()

    init {
        lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        enableSavedStateHandles()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        creationExtras[SAVED_STATE_REGISTRY_OWNER_KEY] = this
        creationExtras[VIEW_MODEL_STORE_OWNER_KEY] = this
        creationExtras[DEFAULT_ARGS_KEY] = savedState { putString(StateKeys.TAB_ID, tabId) }
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
        ViewModelProvider.NewInstanceFactory()

    override val defaultViewModelCreationExtras: CreationExtras get() = creationExtras

    fun setDefaultArgs(defaultArgs: SavedState) {
        creationExtras[DEFAULT_ARGS_KEY] = defaultArgs
    }

    fun clear() {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
