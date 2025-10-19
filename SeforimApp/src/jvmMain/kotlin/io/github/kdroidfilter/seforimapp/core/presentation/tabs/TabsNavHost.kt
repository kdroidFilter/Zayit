package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.kdroidfilter.seforim.navigation.NavigationAction
import io.github.kdroidfilter.seforim.navigation.Navigator
import io.github.kdroidfilter.seforim.navigation.nonAnimatedComposable
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforim.utils.ObserveAsEvents

import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentScreen
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.Text
import java.util.UUID

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
    val scope = rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = navigator.startDestination,
        modifier = Modifier
    ) {
        nonAnimatedComposable<TabsDestination.Home> { backStackEntry ->
            val destination = backStackEntry.toRoute<TabsDestination.Home>()
            // Pass the tabId to the savedStateHandle
            backStackEntry.savedStateHandle["tabId"] = destination.tabId

            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Button({
                    scope.launch {
                        navigator.navigate(TabsDestination.Search("Search Page", UUID.randomUUID().toString()))
                    }
                }) {
                    Text("Click me")
                }

            }
        }

        nonAnimatedComposable<TabsDestination.Search> { backStackEntry ->
            val destination = backStackEntry.toRoute<TabsDestination.Search>()
            // Pass the tabId to the savedStateHandle
            backStackEntry.savedStateHandle["tabId"] = destination.tabId

            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column {
                    Text("Search Page: ${destination.searchQuery}")
                    Text(selectedTabIndex.toString())
                    Button({
                        scope.launch {
                            navigator.navigate(TabsDestination.BookContent(123, UUID.randomUUID().toString()))
                        }
                    }) {
                        Text("Open Book Content")
                    }
                }
            }
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
