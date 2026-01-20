package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.savedstate.SavedState
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.savedState
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.kdroidfilter.seforim.navigation.TabNavControllerRegistry
import io.github.kdroidfilter.seforim.navigation.nonAnimatedComposable
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentScreen
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentViewModel
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.StateKeys
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.HomeSearchCallbacks
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeNavigationEvent
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeUiState
import io.github.kdroidfilter.seforimapp.features.search.SearchResultInBookShellMvi
import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchShellActions
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.session.SessionManager
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme

@Composable
fun TabsNavHost() {
    val appGraph = LocalAppGraph.current
    val tabsViewModel: TabsViewModel = appGraph.tabsViewModel
    val searchHomeViewModel = appGraph.searchHomeViewModel
    val persistedStore = appGraph.tabPersistedStateStore

    val tabs by tabsViewModel.tabs.collectAsState()
    val selectedTabIndex by tabsViewModel.selectedTabIndex.collectAsState()
    val ramSaverEnabled by AppSettings.ramSaverEnabledFlow.collectAsState()
    val isRestoringSession by SessionManager.isRestoringSession.collectAsState()

    val searchUi: SearchHomeUiState by remember(searchHomeViewModel) { searchHomeViewModel.uiState }.collectAsState()
    val scope = rememberCoroutineScope()

    // Helper to get current tab ID
    val currentTabId by remember {
        derivedStateOf {
            tabs.getOrNull(selectedTabIndex)?.destination?.tabId
        }
    }

    val homeSearchCallbacks =
        remember(searchHomeViewModel, scope) {
            HomeSearchCallbacks(
                onReferenceQueryChanged = searchHomeViewModel::onReferenceQueryChanged,
                onTocQueryChanged = searchHomeViewModel::onTocQueryChanged,
                onFilterChange = searchHomeViewModel::onFilterChange,
                onGlobalExtendedChange = searchHomeViewModel::onGlobalExtendedChange,
                onSubmitTextSearch = { query ->
                    val tabId = currentTabId ?: return@HomeSearchCallbacks
                    scope.launch { searchHomeViewModel.submitSearch(query, tabId) }
                },
                onOpenReference = {
                    val tabId = currentTabId ?: return@HomeSearchCallbacks
                    scope.launch { searchHomeViewModel.openSelectedReferenceInCurrentTab(tabId) }
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

    val registry: TabNavControllerRegistry = appGraph.tabNavControllerRegistry
    val tabOwners = remember { mutableMapOf<String, TabViewModelOwner>() }

    LaunchedEffect(tabs) {
        val activeTabIds = tabs.map { it.destination.tabId }.toSet()
        val removed = tabOwners.keys.toSet() - activeTabIds
        removed.forEach { tabId ->
            tabOwners.remove(tabId)?.clear()
            persistedStore.remove(tabId)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            tabOwners.values.forEach { it.clear() }
            tabOwners.clear()
        }
    }

    if (ramSaverEnabled) {
        // RAM Saver: single NavHost shared across tabs, navigate on selection/destination change
        val navController = rememberNavController()
        var lastSelectedId by remember { mutableStateOf<Int?>(null) }
        var lastNavigatedDest by remember { mutableStateOf<TabsDestination?>(null) }

        // Register controller for the currently selected tab id
        LaunchedEffect(selectedTabIndex, tabs) {
            val current = tabs.getOrNull(selectedTabIndex)
            if (current != null) {
                lastSelectedId?.let { registry.remove(it) }
                registry.set(current.id, navController)
            }
        }

        // Navigate when selection changes
        LaunchedEffect(selectedTabIndex) {
            val current = tabs.getOrNull(selectedTabIndex) ?: return@LaunchedEffect
            navController.navigate(current.destination)
            lastNavigatedDest = current.destination
        }
        // Navigate when the destination of the selected tab changes
        LaunchedEffect(tabs, selectedTabIndex) {
            val current = tabs.getOrNull(selectedTabIndex) ?: return@LaunchedEffect
            if (current.destination != lastNavigatedDest) {
                navController.navigate(current.destination)
                lastNavigatedDest = current.destination
            }
        }

        NavHost(
            navController = navController,
            startDestination = tabs.firstOrNull()?.destination ?: TabsDestination.Home(tabId = "default"),
            modifier =
                Modifier
                    .trackActivation()
                    .fillMaxSize()
                    .background(JewelTheme.globalColors.panelBackground),
        ) {
            // Home destination renders the BookContent screen shell.
            nonAnimatedComposable<TabsDestination.Home> { backStackEntry ->
                val destination = backStackEntry.toRoute<TabsDestination.Home>()
                backStackEntry.savedStateHandle["tabId"] = destination.tabId
                val tabOwner = tabOwners.getOrPut(destination.tabId) { TabViewModelOwner(destination.tabId) }
                tabOwner.setDefaultArgs(savedState { putString(StateKeys.TAB_ID, destination.tabId) })
                val viewModel: BookContentViewModel = assistedMetroViewModel(viewModelStoreOwner = tabOwner)
                val uiState by viewModel.uiState.collectAsState()
                val showDiacritics by viewModel.showDiacritics.collectAsState()
                BookContentScreen(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    showDiacritics = showDiacritics,
                    isRestoringSession = isRestoringSession,
                    searchUi = searchUi,
                    searchCallbacks = homeSearchCallbacks,
                )
            }
            nonAnimatedComposable<TabsDestination.Search> { backStackEntry ->
                val destination = backStackEntry.toRoute<TabsDestination.Search>()
                backStackEntry.savedStateHandle["tabId"] = destination.tabId
                backStackEntry.savedStateHandle["searchQuery"] = destination.searchQuery
                val tabOwner = tabOwners.getOrPut(destination.tabId) { TabViewModelOwner(destination.tabId) }
                tabOwner.setDefaultArgs(
                    savedState {
                        putString(StateKeys.TAB_ID, destination.tabId)
                        putString("searchQuery", destination.searchQuery)
                    },
                )
                val viewModel: SearchResultViewModel = assistedMetroViewModel(viewModelStoreOwner = tabOwner)
                val bookVm: BookContentViewModel = assistedMetroViewModel(viewModelStoreOwner = tabOwner)
                // Mark Search UI as visible only while this destination is composed
                DisposableEffect(destination.tabId) {
                    viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetUiVisible(true))
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
            nonAnimatedComposable<TabsDestination.BookContent> { backStackEntry ->
                val destination = backStackEntry.toRoute<TabsDestination.BookContent>()
                backStackEntry.savedStateHandle["tabId"] = destination.tabId
                if (destination.bookId > 0) backStackEntry.savedStateHandle["bookId"] = destination.bookId
                destination.lineId?.let { backStackEntry.savedStateHandle["lineId"] = it }
                val tabOwner = tabOwners.getOrPut(destination.tabId) { TabViewModelOwner(destination.tabId) }
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
                // React to destination changes when ViewModel is reused (e.g., Home -> BookContent)
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
                    searchCallbacks = homeSearchCallbacks,
                )
            }
        }
    } else {
        // Classic: one NavHost per tab
        Box(
            modifier =
                Modifier
                    .trackActivation()
                    .fillMaxSize()
                    .background(JewelTheme.globalColors.panelBackground),
        ) {
            tabs.forEachIndexed { index, tabItem ->
                key(tabItem.id) {
                    val navController = rememberNavController()
                    DisposableEffect(tabItem.id) {
                        registry.set(tabItem.id, navController)
                        onDispose { registry.remove(tabItem.id) }
                    }
                    val isSelected = index == selectedTabIndex
                    var lastNavigatedDestination = remember(tabItem.id) { tabItem.destination }
                    LaunchedEffect(tabItem.destination) {
                        if (tabItem.destination != lastNavigatedDestination) {
                            navController.navigate(tabItem.destination)
                            lastNavigatedDestination = tabItem.destination
                        }
                    }
                    NavHost(
                        navController = navController,
                        startDestination = tabItem.destination,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = if (isSelected) 1f else 0f }
                                .zIndex(if (isSelected) 1f else 0f),
                    ) {
                        nonAnimatedComposable<TabsDestination.Home> { backStackEntry ->
                            val destination = backStackEntry.toRoute<TabsDestination.Home>()
                            backStackEntry.savedStateHandle["tabId"] = destination.tabId
                            val tabOwner = tabOwners.getOrPut(destination.tabId) { TabViewModelOwner(destination.tabId) }
                            tabOwner.setDefaultArgs(savedState { putString(StateKeys.TAB_ID, destination.tabId) })
                            val viewModel: BookContentViewModel = assistedMetroViewModel(viewModelStoreOwner = tabOwner)
                            val uiState by viewModel.uiState.collectAsState()
                            val showDiacritics by viewModel.showDiacritics.collectAsState()
                            BookContentScreen(
                                uiState = uiState,
                                onEvent = viewModel::onEvent,
                                showDiacritics = showDiacritics,
                                isRestoringSession = isRestoringSession,
                                searchUi = searchUi,
                                searchCallbacks = homeSearchCallbacks,
                                isSelected = isSelected,
                            )
                        }
                        nonAnimatedComposable<TabsDestination.Search> { backStackEntry ->
                            val destination = backStackEntry.toRoute<TabsDestination.Search>()
                            backStackEntry.savedStateHandle["tabId"] = destination.tabId
                            backStackEntry.savedStateHandle["searchQuery"] = destination.searchQuery
                            val tabOwner = tabOwners.getOrPut(destination.tabId) { TabViewModelOwner(destination.tabId) }
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
                        nonAnimatedComposable<TabsDestination.BookContent> { backStackEntry ->
                            val destination = backStackEntry.toRoute<TabsDestination.BookContent>()
                            backStackEntry.savedStateHandle["tabId"] = destination.tabId
                            if (destination.bookId > 0) backStackEntry.savedStateHandle["bookId"] = destination.bookId
                            destination.lineId?.let { backStackEntry.savedStateHandle["lineId"] = it }
                            val tabOwner = tabOwners.getOrPut(destination.tabId) { TabViewModelOwner(destination.tabId) }
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
                            // React to destination changes when ViewModel is reused (e.g., Home -> BookContent)
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
                                searchCallbacks = homeSearchCallbacks,
                                isSelected = isSelected,
                            )
                        }
                    }
                }
            }
        }
    }
}

private class TabViewModelOwner(
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
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
