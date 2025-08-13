package io.github.kdroidfilter.seforim.navigation

import androidx.navigation.NavOptionsBuilder
import io.github.kdroidfilter.seforim.tabs.TabsDestination

sealed interface NavigationAction {

    data class Navigate(
        val destination: TabsDestination,
        val navOptions: NavOptionsBuilder.() -> Unit = {}
    ): NavigationAction

    data object NavigateUp: NavigationAction
}