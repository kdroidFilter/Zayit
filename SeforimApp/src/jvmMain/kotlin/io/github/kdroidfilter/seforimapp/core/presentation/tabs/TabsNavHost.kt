package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.kdroidfilter.seforim.navigation.NavigationAction
import io.github.kdroidfilter.seforim.navigation.nonAnimatedComposable
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforim.utils.ObserveAsEvents
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentScreen
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme

@Composable
fun TabsNavHost() {
    val appGraph = LocalAppGraph.current
    val navigator = appGraph.navigator
    val tabsViewModel: TabsViewModel = appGraph.tabsViewModel
    val navController = rememberNavController()

    val tabs by tabsViewModel.tabs.collectAsState()
    val selectedTabIndex by tabsViewModel.selectedTabIndex.collectAsState()

    // Observer les changements d'onglets et naviguer vers la destination
    LaunchedEffect(selectedTabIndex) {
        if (tabs.isNotEmpty() && selectedTabIndex < tabs.size) {
            val currentTab = tabs[selectedTabIndex]
            navController.navigate(currentTab.destination)
        }
    }

    // Also react when the tab list changes (e.g., destination replaced in-place)
    LaunchedEffect(tabs) {
        if (tabs.isNotEmpty() && selectedTabIndex < tabs.size) {
            val currentTab = tabs[selectedTabIndex]
            navController.navigate(currentTab.destination)
        }
    }

    ObserveAsEvents(flow = navigator.navigationActions) { action ->
        when (action) {
            is NavigationAction.Navigate -> {
                // La navigation est maintenant gérée par les onglets
                // On pourrait ignorer cela ou l'utiliser pour d'autres cas
            }

            NavigationAction.NavigateUp -> navController.navigateUp()
        }
    }

    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect {
            navigator.setCanGoBack(navController.previousBackStackEntry != null)
        }
    }

    NavHost(
        navController = navController,
        startDestination = navigator.startDestination,
        modifier = Modifier.trackActivation() .fillMaxSize().background(JewelTheme.globalColors.panelBackground),
    ) {

        nonAnimatedComposable<TabsDestination.Search> { backStackEntry ->
            val destination = backStackEntry.toRoute<TabsDestination.Search>()
            // Pass the tabId and initial query to the savedStateHandle
            backStackEntry.savedStateHandle["tabId"] = destination.tabId
            backStackEntry.savedStateHandle["searchQuery"] = destination.searchQuery

            val viewModel = remember(appGraph, destination) {
                appGraph.searchResultViewModel(backStackEntry.savedStateHandle)
            }
            io.github.kdroidfilter.seforimapp.features.search.SearchResultScreen(viewModel)
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
