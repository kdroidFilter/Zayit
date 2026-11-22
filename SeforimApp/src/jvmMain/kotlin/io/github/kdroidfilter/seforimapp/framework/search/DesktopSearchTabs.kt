package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.features.search.SearchTabs
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import kotlinx.coroutines.flow.Flow

/** The search's tabs on the desktop: those of the window that holds the searching tab, across virtual desktops. */
class DesktopSearchTabs(
    private val desktopManager: DesktopManager,
) : SearchTabs {
    override fun openTab(
        fromTabId: String,
        destination: TabsDestination,
    ) {
        desktopManager.tabsViewModelFor(fromTabId)?.openTab(destination)
    }

    override fun replace(
        tabId: String,
        destination: TabsDestination,
    ) {
        desktopManager.tabsViewModelFor(tabId)?.replaceCurrentTabDestination(destination)
    }

    override fun tabExistsFlow(tabId: String): Flow<Boolean> = desktopManager.tabExistsFlow(tabId)

    override fun isTabOpen(tabId: String): Boolean = desktopManager.isTabOpen(tabId)
}
