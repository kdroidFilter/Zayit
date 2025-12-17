package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.kdroidfilter.seforim.navigation.TabNavControllerRegistry
import io.github.kdroidfilter.seforim.navigation.nonAnimatedComposable
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentScreen
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.HomeSearchCallbacks
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeUiState
import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchShellActions
import io.github.kdroidfilter.seforimapp.features.search.SearchResultInBookShellMvi
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

    val tabs by tabsViewModel.tabs.collectAsState()
    val selectedTabIndex by tabsViewModel.selectedTabIndex.collectAsState()
    val ramSaverEnabled by AppSettings.ramSaverEnabledFlow.collectAsState()
    val isRestoringSession by SessionManager.isRestoringSession.collectAsState()

    val searchUi: SearchHomeUiState by remember(searchHomeViewModel) { searchHomeViewModel.uiState }.collectAsState()
    val scope = rememberCoroutineScope()
    val homeSearchCallbacks = remember(searchHomeViewModel, scope) {
        HomeSearchCallbacks(
            onReferenceQueryChanged = searchHomeViewModel::onReferenceQueryChanged,
            onTocQueryChanged = searchHomeViewModel::onTocQueryChanged,
            onFilterChange = searchHomeViewModel::onFilterChange,
            onGlobalExtendedChange = searchHomeViewModel::onGlobalExtendedChange,
            onSubmitTextSearch = { query ->
                scope.launch { searchHomeViewModel.submitSearch(query) }
            },
            onOpenReference = {
                scope.launch { searchHomeViewModel.openSelectedReferenceInCurrentTab() }
            },
            onPickCategory = searchHomeViewModel::onPickCategory,
            onPickBook = searchHomeViewModel::onPickBook,
            onPickToc = searchHomeViewModel::onPickToc,
        )
    }

    val registry: TabNavControllerRegistry = appGraph.tabNavControllerRegistry

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
            modifier = Modifier
                .trackActivation()
                .fillMaxSize()
                .background(JewelTheme.globalColors.panelBackground)
        ) {
            // Home destination renders the BookContent screen shell.
            nonAnimatedComposable<TabsDestination.Home> { backStackEntry ->
                val destination = backStackEntry.toRoute<TabsDestination.Home>()
                backStackEntry.savedStateHandle["tabId"] = destination.tabId
                // Use tabId as key to keep ViewModel stable across destination changes
                val viewModel = remember(appGraph, destination.tabId) {
                    appGraph.bookContentViewModel(backStackEntry.savedStateHandle)
                }
                BookContentScreen(
                    viewModel = viewModel,
                    isRestoringSession = isRestoringSession,
                    searchUi = searchUi,
                    searchCallbacks = homeSearchCallbacks,
                )
            }
            nonAnimatedComposable<TabsDestination.Search> { backStackEntry ->
                val destination = backStackEntry.toRoute<TabsDestination.Search>()
                backStackEntry.savedStateHandle["tabId"] = destination.tabId
                backStackEntry.savedStateHandle["searchQuery"] = destination.searchQuery
                // Use tabId as key to keep ViewModels stable
                val viewModel = remember(appGraph, destination.tabId) { appGraph.searchResultViewModel(backStackEntry.savedStateHandle) }
                val bookVm = remember(appGraph, destination.tabId) {
                    appGraph.bookContentViewModel(backStackEntry.savedStateHandle)
                }
                // Mark Search UI as visible only while this destination is composed
                DisposableEffect(destination.tabId) {
                    viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetUiVisible(true))
                    onDispose { viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetUiVisible(false)) }
                }
                val bcUiState by bookVm.uiState.collectAsState()
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

                val actions = remember(viewModel) {
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
                                SearchResultViewModel.SearchResultEvents.OnScroll(anchorId, anchorIndex, index, offset)
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
                // Use tabId as key to keep ViewModel stable
                val viewModel = remember(appGraph, destination.tabId) {
                    appGraph.bookContentViewModel(backStackEntry.savedStateHandle)
                }
                BookContentScreen(
                    viewModel = viewModel,
                    isRestoringSession = isRestoringSession,
                    searchUi = searchUi,
                    searchCallbacks = homeSearchCallbacks,
                )
            }
        }
    } else {
        // Classic: one NavHost per tab
        Box(
            modifier = Modifier
                .trackActivation()
                .fillMaxSize()
                .background(JewelTheme.globalColors.panelBackground)
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
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (isSelected) 1f else 0f }
                            .zIndex(if (isSelected) 1f else 0f)
                    ) {
                        nonAnimatedComposable<TabsDestination.Home> { backStackEntry ->
                            val destination = backStackEntry.toRoute<TabsDestination.Home>()
                            backStackEntry.savedStateHandle["tabId"] = destination.tabId
                            // Use tabId as key to keep ViewModel stable across destination changes
	                            val viewModel = remember(appGraph, destination.tabId) {
	                                appGraph.bookContentViewModel(backStackEntry.savedStateHandle)
	                            }
	                            BookContentScreen(
	                                viewModel = viewModel,
	                                isRestoringSession = isRestoringSession,
	                                searchUi = searchUi,
	                                searchCallbacks = homeSearchCallbacks,
	                            )
                        }
                        nonAnimatedComposable<TabsDestination.Search> { backStackEntry ->
                            val destination = backStackEntry.toRoute<TabsDestination.Search>()
                            backStackEntry.savedStateHandle["tabId"] = destination.tabId
                            backStackEntry.savedStateHandle["searchQuery"] = destination.searchQuery
                            // Use tabId as key to keep ViewModels stable
                            val viewModel = remember(appGraph, destination.tabId) { appGraph.searchResultViewModel(backStackEntry.savedStateHandle) }
                            val bookVm = remember(appGraph, destination.tabId) {
                                appGraph.bookContentViewModel(backStackEntry.savedStateHandle)
                            }
                            // Keep tree computation disabled when tab is not selected
                            LaunchedEffect(isSelected) {
                                viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetUiVisible(isSelected))
                            }
                            DisposableEffect(destination.tabId) {
                                onDispose { viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetUiVisible(false)) }
                            }
                            val bcUiState by bookVm.uiState.collectAsState()
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

                            val actions = remember(viewModel) {
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
                                            SearchResultViewModel.SearchResultEvents.OnScroll(anchorId, anchorIndex, index, offset)
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
                            // Use tabId as key to keep ViewModel stable
                            val viewModel = remember(appGraph, destination.tabId) {
                                appGraph.bookContentViewModel(backStackEntry.savedStateHandle)
                            }
                            BookContentScreen(
                                viewModel = viewModel,
                                isRestoringSession = isRestoringSession,
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
