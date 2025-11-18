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
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentViewModel
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.search.SearchResultInBookShellMvi
import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchShellActions
import io.github.kdroidfilter.seforimapp.features.search.SearchUiState
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.session.SessionManager
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme

@Composable
fun TabsNavHost() {
    val appGraph = LocalAppGraph.current
    val tabsViewModel: TabsViewModel = appGraph.tabsViewModel

    val tabs by tabsViewModel.tabs.collectAsState()
    val selectedTabIndex by tabsViewModel.selectedTabIndex.collectAsState()
    val ramSaverEnabled by AppSettings.ramSaverEnabledFlow.collectAsState()
    val isRestoringSession by SessionManager.isRestoringSession.collectAsState()

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
                BookContentScreen(viewModel, isRestoringSession = isRestoringSession)
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
                val bcUiState = rememberBookShellState(bookVm)
                val ss = rememberSearchShellState(viewModel)
                SearchResultInBookShellMvi(
                    bookUiState = bcUiState,
                    onEvent = bookVm::onEvent,
                    searchUi = ss.searchUi,
                    visibleResults = ss.visibleResults,
                    isFiltering = ss.isFiltering,
                    breadcrumbs = ss.breadcrumbs,
                    searchTree = ss.searchTree,
                    selectedCategoryIds = ss.selectedCategoryIds,
                    selectedBookIds = ss.selectedBookIds,
                    selectedTocIds = ss.selectedTocIds,
                    tocCounts = ss.tocCounts,
                    tocTree = ss.tocTree,
                    actions = SearchShellActions(
                        onSubmit = { q, near ->
                            viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetQuery(q))
                            viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetNear(near))
                            viewModel.onEvent(SearchResultViewModel.SearchResultEvents.ExecuteSearch)
                        },
                        onNearChange = { n -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetNear(n)) },
                        onQueryChange = { q -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetQuery(q)) },
                        onGlobalExtendedChange = { extended ->
                            viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetGlobalExtended(extended))
                            viewModel.onEvent(SearchResultViewModel.SearchResultEvents.ExecuteSearch)
                        },
                        onScroll = { anchorId, anchorIndex, index, offset -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.OnScroll(anchorId, anchorIndex, index, offset)) },
                        onCancelSearch = { viewModel.onEvent(SearchResultViewModel.SearchResultEvents.CancelSearch) },
                        onOpenResult = { r, newTab -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.OpenResult(r, newTab)) },
                        onRequestBreadcrumb = { r -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.RequestBreadcrumb(r)) },
                        onLoadMore = { viewModel.loadMore() },
                        onCategoryCheckedChange = { id, checked -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetCategoryChecked(id, checked)) },
                        onBookCheckedChange = { id, checked -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetBookChecked(id, checked)) },
                        onEnsureScopeBookForToc = { id -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.EnsureScopeBookForToc(id)) },
                        onTocToggle = { entry, checked -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetTocChecked(entry.id, checked)) },
                        onTocFilter = { entry -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.FilterByTocId(entry.id)) },
                    )
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
                BookContentScreen(viewModel, isRestoringSession = isRestoringSession)
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
	                                viewModel,
	                                isRestoringSession = isRestoringSession
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
                            val bcUiState = rememberBookShellState(bookVm)
                            val ss = rememberSearchShellState(viewModel)
                            SearchResultInBookShellMvi(
                                bookUiState = bcUiState,
                                onEvent = bookVm::onEvent,
                                searchUi = ss.searchUi,
                                visibleResults = ss.visibleResults,
                                isFiltering = ss.isFiltering,
                                breadcrumbs = ss.breadcrumbs,
                                searchTree = ss.searchTree,
                                selectedCategoryIds = ss.selectedCategoryIds,
                                selectedBookIds = ss.selectedBookIds,
                                selectedTocIds = ss.selectedTocIds,
                                tocCounts = ss.tocCounts,
                                tocTree = ss.tocTree,
                                actions = SearchShellActions(
                                    onSubmit = { q, near ->
                                        viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetQuery(q))
                                        viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetNear(near))
                                        viewModel.onEvent(SearchResultViewModel.SearchResultEvents.ExecuteSearch)
                                    },
                                    onNearChange = { n -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetNear(n)) },
                                    onQueryChange = { q -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetQuery(q)) },
                                    onGlobalExtendedChange = { extended ->
                                        viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetGlobalExtended(extended))
                                        viewModel.onEvent(SearchResultViewModel.SearchResultEvents.ExecuteSearch)
                                    },
                                    onScroll = { anchorId, anchorIndex, index, offset -> viewModel.onEvent(
                                        SearchResultViewModel.SearchResultEvents.OnScroll(anchorId, anchorIndex, index, offset)) },
                                    onCancelSearch = { viewModel.onEvent(SearchResultViewModel.SearchResultEvents.CancelSearch) },
                                    onOpenResult = { r, newTab -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.OpenResult(r, newTab)) },
                                    onRequestBreadcrumb = { r -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.RequestBreadcrumb(r)) },
                                    onLoadMore = { viewModel.loadMore() },
                                    onCategoryCheckedChange = { id, checked -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetCategoryChecked(id, checked)) },
                                    onBookCheckedChange = { id, checked -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetBookChecked(id, checked)) },
                                    onEnsureScopeBookForToc = { id -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.EnsureScopeBookForToc(id)) },
                                    onTocToggle = { entry, checked -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetTocChecked(entry.id, checked)) },
                                    onTocFilter = { entry -> viewModel.onEvent(SearchResultViewModel.SearchResultEvents.FilterByTocId(entry.id)) },
                                )
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
                                viewModel,
                                isRestoringSession = isRestoringSession
                            )
                    }
                }
            }
        }
        }
    }
}

private data class SearchShellState(
    val searchUi: SearchUiState,
    val visibleResults: List<io.github.kdroidfilter.seforimlibrary.core.models.SearchResult>,
    val isFiltering: Boolean,
    val breadcrumbs: Map<Long, List<String>>,
    val searchTree: List<SearchResultViewModel.SearchTreeCategory>,
    val selectedCategoryIds: Set<Long>,
    val selectedBookIds: Set<Long>,
    val selectedTocIds: Set<Long>,
    val tocCounts: Map<Long, Int>,
    val tocTree: SearchResultViewModel.TocTree?
)

@Composable
private fun rememberSearchShellState(viewModel: SearchResultViewModel): SearchShellState {
    val searchUi by remember(viewModel) { viewModel.uiState }.collectAsState()
    val visibleResults by remember(viewModel) { viewModel.visibleResultsFlow }.collectAsState()
    val isFiltering by remember(viewModel) { viewModel.isFilteringFlow }.collectAsState()
    val breadcrumbs by remember(viewModel) { viewModel.breadcrumbsFlow }.collectAsState()
    val searchTree by remember(viewModel) { viewModel.searchTreeFlow }.collectAsState()
    val selectedCategoryIds by remember(viewModel) { viewModel.selectedCategoryIdsFlow }.collectAsState()
    val selectedBookIds by remember(viewModel) { viewModel.selectedBookIdsFlow }.collectAsState()
    val selectedTocIds by remember(viewModel) { viewModel.selectedTocIdsFlow }.collectAsState()
    val tocCounts by remember(viewModel) { viewModel.tocCountsFlow }.collectAsState()
    val tocTree by remember(viewModel) { viewModel.tocTreeFlow }.collectAsState()
    
    return remember(
        searchUi, visibleResults, isFiltering, breadcrumbs, searchTree,
        selectedCategoryIds, selectedBookIds, selectedTocIds, tocCounts, tocTree
    ) {
        SearchShellState(
            searchUi = searchUi,
            visibleResults = visibleResults,
            isFiltering = isFiltering,
            breadcrumbs = breadcrumbs,
            searchTree = searchTree,
            selectedCategoryIds = selectedCategoryIds,
            selectedBookIds = selectedBookIds,
            selectedTocIds = selectedTocIds,
            tocCounts = tocCounts,
            tocTree = tocTree
        )
    }
}

@Composable
private fun rememberBookShellState(viewModel: BookContentViewModel): BookContentState {
    return viewModel.uiState.collectAsState().value
}
