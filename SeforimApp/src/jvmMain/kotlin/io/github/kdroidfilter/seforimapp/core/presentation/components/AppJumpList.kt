package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.kdroid.gematria.converter.toHebrewNumeral
import io.github.kdroidfilter.nucleus.launcher.windows.JumpListCategory
import io.github.kdroidfilter.nucleus.launcher.windows.JumpListItem
import io.github.kdroidfilter.nucleus.launcher.windows.WindowsJumpListManager
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.desktop_default_name
import seforimapp.seforimapp.generated.resources.desktop_new
import seforimapp.seforimapp.generated.resources.desktops_label
import seforimapp.seforimapp.generated.resources.home
import seforimapp.seforimapp.generated.resources.menu_new_tab
import seforimapp.seforimapp.generated.resources.open_tabs
import seforimapp.seforimapp.generated.resources.search_results_tab_title

private const val SCHEME_TAB = "seforim://tab/"
private const val SCHEME_DESKTOP = "seforim://desktop/"
private const val SCHEME_NEW_TAB = "seforim://new-tab"
private const val SCHEME_NEW_DESKTOP = "seforim://new-desktop"

@Composable
fun AppJumpList(
    desktopManager: DesktopManager,
    tabsViewModel: TabsViewModel,
    pendingDeepLink: StateFlow<String?>,
    onClearDeepLink: () -> Unit,
) {
    if (!PlatformInfo.isWindows || !WindowsJumpListManager.isAvailable) return

    val desktops by desktopManager.desktops.collectAsState()
    val activeDesktopId by desktopManager.activeDesktopId.collectAsState()
    val tabsState by tabsViewModel.state.collectAsState()

    val openTabsLabel = stringResource(Res.string.open_tabs)
    val desktopsLabel = stringResource(Res.string.desktops_label)
    val homeLabel = stringResource(Res.string.home)
    val searchResultsFormat = stringResource(Res.string.search_results_tab_title, "%1\$s")
    val newTabLabel = stringResource(Res.string.menu_new_tab)
    val newDesktopLabel = stringResource(Res.string.desktop_new)
    val nextHebrewIndex = (desktops.size + 1).toHebrewNumeral(includeGeresh = false) + "׳"
    val nextDesktopName = stringResource(Res.string.desktop_default_name, nextHebrewIndex)
    val currentClearDeepLink by rememberUpdatedState(onClearDeepLink)

    // Re-register handler when nextDesktopName changes so the captured name stays current
    LaunchedEffect(nextDesktopName) {
        pendingDeepLink.filterNotNull().collect { action ->
            when {
                action.startsWith(SCHEME_TAB) -> {
                    val index = action.removePrefix(SCHEME_TAB).toIntOrNull() ?: return@collect
                    val tabs = tabsViewModel.state.value.tabs
                    if (index in tabs.indices) tabsViewModel.onEvent(TabsEvents.OnSelect(index))
                }
                action.startsWith(SCHEME_DESKTOP) -> {
                    val index = action.removePrefix(SCHEME_DESKTOP).toIntOrNull() ?: return@collect
                    val desktop = desktopManager.desktops.value.getOrNull(index)
                    if (desktop != null) desktopManager.switchTo(desktop.id)
                }
                action == SCHEME_NEW_TAB -> tabsViewModel.onEvent(TabsEvents.OnAdd)
                action == SCHEME_NEW_DESKTOP -> desktopManager.createDesktop(nextDesktopName)
            }
            currentClearDeepLink()
        }
    }

    // Rebuild jump list whenever desktops, active desktop, or tabs change
    LaunchedEffect(desktops, activeDesktopId, tabsState) {
        val tabs = tabsState.tabs

        val tabItems =
            tabs.mapIndexed { index, tab ->
                val rawTitle = tab.title
                val title =
                    when {
                        rawTitle.isEmpty() -> homeLabel
                        tab.tabType == TabType.SEARCH -> searchResultsFormat.replace("%1\$s", rawTitle)
                        else -> rawTitle
                    }
                JumpListItem(title = title, arguments = "$SCHEME_TAB$index", description = title)
            }

        val desktopItems =
            desktops.mapIndexed { index, desktop ->
                JumpListItem(
                    title = desktop.name,
                    arguments = "$SCHEME_DESKTOP$index",
                    description = desktop.name,
                )
            }

        val categories =
            buildList {
                if (tabItems.isNotEmpty()) add(JumpListCategory(name = openTabsLabel, items = tabItems))
                if (desktopItems.isNotEmpty()) add(JumpListCategory(name = desktopsLabel, items = desktopItems))
            }

        val tasks =
            listOf(
                JumpListItem(title = newTabLabel, arguments = SCHEME_NEW_TAB),
                JumpListItem(title = newDesktopLabel, arguments = SCHEME_NEW_DESKTOP),
            )

        WindowsJumpListManager.setJumpList(categories = categories, tasks = tasks)
    }

    DisposableEffect(Unit) {
        onDispose { WindowsJumpListManager.clearJumpList() }
    }
}
