package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kdroid.gematria.converter.toHebrewNumeral
import io.github.kdroidfilter.nucleus.launcher.macos.DockMenuItem
import io.github.kdroidfilter.nucleus.launcher.macos.DockMenuListener
import io.github.kdroidfilter.nucleus.launcher.macos.MacOsDockMenu
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.desktop_default_name
import seforimapp.seforimapp.generated.resources.desktop_new
import seforimapp.seforimapp.generated.resources.desktops_label
import seforimapp.seforimapp.generated.resources.home
import seforimapp.seforimapp.generated.resources.search_results_tab_title

private const val DESKTOP_ID_BASE = 1000
private const val TAB_ID_BASE = 2000
private const val NEW_DESKTOP_ID = 900
private const val SEPARATOR_ID = 999

@Composable
fun AppDockMenu(
    desktopManager: DesktopManager,
    tabsViewModel: TabsViewModel,
) {
    if (!MacOsDockMenu.isAvailable) return

    val desktops by desktopManager.desktops.collectAsState()
    val activeDesktopId by desktopManager.activeDesktopId.collectAsState()
    val tabsState by tabsViewModel.state.collectAsState()
    val desktopsLabel = stringResource(Res.string.desktops_label)
    val homeLabel = stringResource(Res.string.home)
    val newDesktopLabel = stringResource(Res.string.desktop_new)
    val searchResultsFormat = stringResource(Res.string.search_results_tab_title, "%1\$s")
    val nextHebrewIndex = (desktops.size + 1).toHebrewNumeral(includeGeresh = false) + "׳"
    val nextDesktopName = stringResource(Res.string.desktop_default_name, nextHebrewIndex)

    // Re-register listener when nextDesktopName changes so the captured name stays current
    LaunchedEffect(nextDesktopName) {
        MacOsDockMenu.listener =
            DockMenuListener { itemId ->
                when {
                    itemId == NEW_DESKTOP_ID -> {
                        desktopManager.createDesktop(nextDesktopName)
                    }
                    itemId in DESKTOP_ID_BASE until TAB_ID_BASE -> {
                        val desktopIndex = itemId - DESKTOP_ID_BASE
                        val desktop = desktopManager.desktops.value.getOrNull(desktopIndex)
                        if (desktop != null) {
                            desktopManager.switchTo(desktop.id)
                        }
                    }
                    itemId >= TAB_ID_BASE -> {
                        val tabIndex = itemId - TAB_ID_BASE
                        val tabs = tabsViewModel.state.value.tabs
                        if (tabIndex in tabs.indices) {
                            tabsViewModel.onEvent(TabsEvents.OnSelect(tabIndex))
                        }
                    }
                }
            }
    }

    // Rebuild dock menu whenever desktops, active desktop, or tabs change
    LaunchedEffect(desktops, activeDesktopId, tabsState) {
        val items = mutableListOf<DockMenuItem>()

        // Current desktop tabs
        val tabs = tabsState.tabs
        val selectedIndex = tabsState.selectedTabIndex
        tabs.forEachIndexed { index, tab ->
            val rawTitle = tab.title
            val title =
                when {
                    rawTitle.isEmpty() -> homeLabel
                    tab.tabType == TabType.SEARCH -> searchResultsFormat.replace("%1\$s", rawTitle)
                    else -> rawTitle
                }
            items.add(
                DockMenuItem(
                    id = TAB_ID_BASE + index,
                    title = if (index == selectedIndex) "✓ $title" else title,
                ),
            )
        }

        // Desktop switcher submenu
        items.add(DockMenuItem.separator(id = SEPARATOR_ID))

        val desktopChildren =
            buildList {
                desktops.forEachIndexed { index, desktop ->
                    add(
                        DockMenuItem(
                            id = DESKTOP_ID_BASE + index,
                            title =
                                if (desktop.id == activeDesktopId) "✓ ${desktop.name}" else desktop.name,
                            enabled = desktop.id != activeDesktopId,
                        ),
                    )
                }
                add(DockMenuItem.separator(id = SEPARATOR_ID + 1))
                add(DockMenuItem(id = NEW_DESKTOP_ID, title = newDesktopLabel))
            }

        items.add(
            DockMenuItem(
                id = DESKTOP_ID_BASE + desktops.size,
                title = desktopsLabel,
                children = desktopChildren,
            ),
        )

        MacOsDockMenu.setDockMenu(items)
    }

    DisposableEffect(Unit) {
        onDispose {
            MacOsDockMenu.clearDockMenu()
        }
    }
}
