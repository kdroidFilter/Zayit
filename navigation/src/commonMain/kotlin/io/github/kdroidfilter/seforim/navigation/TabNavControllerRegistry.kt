package io.github.kdroidfilter.seforim.navigation

import androidx.navigation.NavHostController

/**
 * DI-provided registry that holds one NavHostController per open tab.
 * Keys use the internal tab id (TabsViewModel.TabItem.id) which is stable
 * for the lifetime of the tab.
 */
class TabNavControllerRegistry {
    private val controllers = mutableMapOf<Int, NavHostController>()

    fun set(
        tabInternalId: Int,
        controller: NavHostController,
    ) {
        controllers[tabInternalId] = controller
    }

    fun get(tabInternalId: Int): NavHostController? = controllers[tabInternalId]

    fun remove(tabInternalId: Int) {
        controllers.remove(tabInternalId)
    }

    fun snapshot(): Map<Int, NavHostController> = controllers.toMap()
}
