package io.github.kdroidfilter.seforim.tabs

/**
 * Finds the tabs of the window holding a tab, for a per-tab ViewModel (no composition) to open or replace tabs next to
 * its own. The desktop resolves it at call time, across windows and virtual desktops (`DesktopManager`).
 */
fun interface TabsResolver {
    fun tabsViewModelFor(tabId: String): TabsViewModel?
}
