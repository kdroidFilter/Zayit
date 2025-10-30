package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.kdroidfilter.seforim.navigation.TabNavControllerRegistry
import io.github.kdroidfilter.seforim.navigation.nonAnimatedComposable
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentScreen
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme

@Composable
fun TabsNavHost() {
    val appGraph = LocalAppGraph.current
    val tabsViewModel: TabsViewModel = appGraph.tabsViewModel

    val tabs by tabsViewModel.tabs.collectAsState()
    val selectedTabIndex by tabsViewModel.selectedTabIndex.collectAsState()

    val registry: TabNavControllerRegistry = appGraph.tabNavControllerRegistry

    // Keep track of the selected tab's controller for external usage if needed
    val selectedController: NavHostController? = tabs.getOrNull(selectedTabIndex)?.let { registry.get(it.id) }
    // External components can access registry to perform navigateUp if required.

    Box(
        modifier = Modifier
            .trackActivation()
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        tabs.forEachIndexed { index, tabItem ->
            key(tabItem.id) {
                val navController = rememberNavController()

                // Register controller for this tab id and remove when disposed
                androidx.compose.runtime.DisposableEffect(tabItem.id) {
                    registry.set(tabItem.id, navController)
                    onDispose { registry.remove(tabItem.id) }
                }

                val isSelected = index == selectedTabIndex

                // Navigate when this tab's destination changes (avoid duplicate initial nav)
                var lastNavigatedDestination = remember(tabItem.id) { tabItem.destination }
                LaunchedEffect(tabItem.destination) {
                    if (tabItem.destination != lastNavigatedDestination) {
                        navController.navigate(tabItem.destination)
                        lastNavigatedDestination = tabItem.destination
                    }
                }

                // Each tab has its own NavHost and graph
                NavHost(
                    navController = navController,
                    startDestination = tabItem.destination,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (isSelected) 1f else 0f }
                        .zIndex(if (isSelected) 1f else 0f)
                ) {
                    // Home destination renders the BookContent screen shell.
                    // Since no book is selected in state, the shell displays HomeView.
                    nonAnimatedComposable<TabsDestination.Home> { backStackEntry ->
                        val destination = backStackEntry.toRoute<TabsDestination.Home>()
                        // Pass the tabId to the savedStateHandle
                        backStackEntry.savedStateHandle["tabId"] = destination.tabId

                        val viewModel = remember(appGraph, destination) {
                            appGraph.bookContentViewModel(backStackEntry.savedStateHandle)
                        }
                        BookContentScreen(viewModel)
                    }

                    nonAnimatedComposable<TabsDestination.Search> { backStackEntry ->
                        val destination = backStackEntry.toRoute<TabsDestination.Search>()
                        // Pass the tabId and initial query to the savedStateHandle
                        backStackEntry.savedStateHandle["tabId"] = destination.tabId
                        backStackEntry.savedStateHandle["searchQuery"] = destination.searchQuery

                        val viewModel = remember(appGraph, destination) {
                            appGraph.searchResultViewModel(backStackEntry.savedStateHandle)
                        }
                        // Reuse the BookContent shell so Search renders inside the same panes
                        val bookVm = remember(appGraph, destination) {
                            appGraph.bookContentViewModel(backStackEntry.savedStateHandle)
                        }
                        val bcUiState by bookVm.uiState.collectAsState()
                        io.github.kdroidfilter.seforimapp.features.search.SearchResultInBookShell(
                            bookUiState = bcUiState,
                            onEvent = bookVm::onEvent,
                            viewModel = viewModel
                        )
                    }

                    nonAnimatedComposable<TabsDestination.BookContent> { backStackEntry ->
                        val destination = backStackEntry.toRoute<TabsDestination.BookContent>()
                        // Pass the tabId to the savedStateHandle
                        backStackEntry.savedStateHandle["tabId"] = destination.tabId
                        // Pass the bookId to the savedStateHandle only if valid (> 0)
                        if (destination.bookId > 0) {
                            backStackEntry.savedStateHandle["bookId"] = destination.bookId
                        }
                        // Pass the lineId to the savedStateHandle if it exists
                        destination.lineId?.let { lineId ->
                            backStackEntry.savedStateHandle["lineId"] = lineId
                        }
                        val viewModel = remember(appGraph, destination) {
                            appGraph.bookContentViewModel(backStackEntry.savedStateHandle)
                        }
                        BookContentScreen(viewModel)
                    }
                }
            }
        }
    }
}
