package io.github.kdroidfilter.seforimapp.core.presentation.navigation

import androidx.navigation.NavOptionsBuilder
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsDestination

sealed interface NavigationAction {

    data class Navigate(
        val destination: TabsDestination,
        val navOptions: NavOptionsBuilder.() -> Unit = {}
    ): NavigationAction

    data object NavigateUp: NavigationAction
}