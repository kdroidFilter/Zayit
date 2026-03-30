package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.kdroid.gematria.converter.toHebrewNumeral
import io.github.kdroidfilter.nucleus.launcher.linux.DbusmenuItem
import io.github.kdroidfilter.nucleus.launcher.linux.LauncherProperties
import io.github.kdroidfilter.nucleus.launcher.linux.LinuxLauncherEntry
import io.github.kdroidfilter.nucleus.launcher.linux.LinuxQuicklist
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.desktop_default_name
import seforimapp.seforimapp.generated.resources.desktop_new
import seforimapp.seforimapp.generated.resources.desktops_label
import seforimapp.seforimapp.generated.resources.home
import seforimapp.seforimapp.generated.resources.menu_new_tab
import seforimapp.seforimapp.generated.resources.search_results_tab_title

private const val NEW_TAB_ID = 50
private const val NEW_DESKTOP_ID = 100
private const val SEPARATOR_TABS_ACTIONS = 200
private const val SEPARATOR_ACTIONS_DESKTOPS = 201
private const val SEPARATOR_DESKTOP_NEW = 202
private const val DESKTOP_ID_BASE = 1000
private const val TAB_ID_BASE = 2000

private const val QUICKLIST_OBJECT_PATH = "/com/zayitapp/Menu"
private const val DESKTOP_FILE_ID = "zayit.desktop"

@Composable
fun AppLinuxQuicklist(
    desktopManager: DesktopManager,
    tabsViewModel: TabsViewModel,
) {
    if (!PlatformInfo.isLinux || !LinuxLauncherEntry.isAvailable) return

    val desktops by desktopManager.desktops.collectAsState()
    val activeDesktopId by desktopManager.activeDesktopId.collectAsState()
    val tabsState by tabsViewModel.state.collectAsState()
    val desktopsLabel = stringResource(Res.string.desktops_label)
    val homeLabel = stringResource(Res.string.home)
    val newTabLabel = stringResource(Res.string.menu_new_tab)
    val newDesktopLabel = stringResource(Res.string.desktop_new)
    val searchResultsFormat = stringResource(Res.string.search_results_tab_title, "%1\$s")
    val nextHebrewIndex = (desktops.size + 1).toHebrewNumeral(includeGeresh = false) + "׳"
    val nextDesktopName = stringResource(Res.string.desktop_default_name, nextHebrewIndex)

    val appUri = remember { LinuxLauncherEntry.appUri(DESKTOP_FILE_ID) }
    val quicklist = remember { LinuxQuicklist(QUICKLIST_OBJECT_PATH) }

    // Re-register listener when nextDesktopName changes so the captured name stays current
    LaunchedEffect(nextDesktopName) {
        quicklist.listener = LinuxQuicklist.Listener { itemId ->
            when {
                itemId == NEW_TAB_ID -> {
                    tabsViewModel.onEvent(TabsEvents.OnAdd)
                }
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

    // Rebuild quicklist whenever desktops, active desktop, or tabs change
    LaunchedEffect(desktops, activeDesktopId, tabsState) {
        val items = mutableListOf<DbusmenuItem>()

        // Current desktop tabs
        val tabs = tabsState.tabs
        val selectedIndex = tabsState.selectedTabIndex
        tabs.forEachIndexed { index, tab ->
            val rawTitle = tab.title
            val title = when {
                rawTitle.isEmpty() -> homeLabel
                tab.tabType == TabType.SEARCH -> searchResultsFormat.replace("%1\$s", rawTitle)
                else -> rawTitle
            }
            items.add(
                DbusmenuItem(
                    id = TAB_ID_BASE + index,
                    label = if (index == selectedIndex) "✓ $title" else title,
                ),
            )
        }

        // New tab action
        items.add(DbusmenuItem.separator(id = SEPARATOR_TABS_ACTIONS))
        items.add(DbusmenuItem(id = NEW_TAB_ID, label = newTabLabel))

        // Desktop switcher submenu
        items.add(DbusmenuItem.separator(id = SEPARATOR_ACTIONS_DESKTOPS))

        val desktopChildren = buildList {
            desktops.forEachIndexed { index, desktop ->
                add(
                    DbusmenuItem(
                        id = DESKTOP_ID_BASE + index,
                        label = if (desktop.id == activeDesktopId) "✓ ${desktop.name}" else desktop.name,
                        enabled = desktop.id != activeDesktopId,
                    ),
                )
            }
            add(DbusmenuItem.separator(id = SEPARATOR_DESKTOP_NEW))
            add(DbusmenuItem(id = NEW_DESKTOP_ID, label = newDesktopLabel))
        }

        items.add(
            DbusmenuItem(
                id = DESKTOP_ID_BASE + desktops.size,
                label = desktopsLabel,
                children = desktopChildren,
            ),
        )

        quicklist.setMenu(items)
        LinuxLauncherEntry.update(appUri, LauncherProperties(quicklist = quicklist.objectPath))
    }

    DisposableEffect(Unit) {
        onDispose {
            quicklist.dispose()
        }
    }
}
