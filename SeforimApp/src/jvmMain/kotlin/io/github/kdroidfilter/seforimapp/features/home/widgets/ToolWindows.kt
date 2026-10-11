package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.LocalNucleusApplicationScope
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.jewel.JewelDecoratedWindow
import dev.nucleusframework.window.jewel.JewelTitleBar
import dev.nucleusframework.window.newFullscreenControls
import dev.nucleusframework.window.styling.LocalTitleBarStyle
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.presentation.utils.rememberWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.Community
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/** The widgets that also open alone in a window, from the app's Tools menu (see [HomeWidget.toolWindowSize]). */
internal val availableTools: List<HomeWidget>
    get() = availableHomeWidgets.filter { it.toolWindowSize != null && it.isSupported }

/** The tools open in their own window, app-wide: one window per tool, brought back rather than opened twice. */
@Inject
@SingleIn(AppScope::class)
class ToolWindows {
    internal val open = mutableStateListOf<HomeWidget>()

    /** How many times each open tool was asked for again: its window comes back to the front at each one. */
    internal val reopened = mutableStateMapOf<String, Int>()

    internal fun open(tool: HomeWidget) {
        if (tool in open) {
            reopened[tool.id] = (reopened[tool.id] ?: 0) + 1
        } else {
            open += tool
        }
    }
}

/** Every open tool's window; composed once, at the app's level. */
@Composable
fun ToolWindowsHost() {
    val toolWindows = LocalAppGraph.current.toolWindows
    if (toolWindows.open.isEmpty()) return
    val state = rememberToolsState()
    toolWindows.open.toList().forEach { tool ->
        key(tool.id) {
            tool.ToolWindow(state) {
                toolWindows.open -= tool
                toolWindows.reopened -= tool.id
            }
        }
    }
    state.optionsOpen?.let { WidgetOptionsDialog(it, state, onClose = { state.optionsOpen = null }) }
}

/** The state the tools share, as the Home's: the user's place and community, the widgets' options. */
@Composable
private fun rememberToolsState(): HomeWidgetsState {
    val appGraph = LocalAppGraph.current
    val viewModel: HomeUserLocationViewModel = metroViewModel(viewModelStoreOwner = rememberWindowViewModelStoreOwner())
    val userLocation by viewModel.state.collectAsState()
    val communityCode by appGraph.appSettings.userCommunityCodeFlow.collectAsState()
    val state =
        remember(userLocation, communityCode) {
            val community = communityCode?.let { code -> runCatching { Community.valueOf(code) }.getOrNull() }
            HomeWidgetsState(userLocation, community, HomeWidgetsLayout(appGraph.appSettings))
        }
    SideEffect {
        state.openTab = { destination ->
            appGraph.desktopManager
                .focusedWindow()
                ?.tabsViewModel
                ?.openTab(destination)
        }
    }
    return state
}

@Composable
internal fun DefaultToolWindow(
    tool: HomeWidget,
    state: HomeWidgetsState,
    onClose: () -> Unit,
) {
    val reopened = LocalAppGraph.current.toolWindows.reopened[tool.id] ?: 0
    val title = stringResource(tool.title)
    val windowState = rememberWindowState(position = WindowPosition(Alignment.Center), size = tool.toolWindowSize!!)
    with(LocalNucleusApplicationScope.current) {
        JewelDecoratedWindow(
            onCloseRequest = onClose,
            title = title,
            state = windowState,
            minimumSize = tool.toolWindowMinSize,
        ) {
            val window = nucleusWindow
            LaunchedEffect(reopened) {
                if (reopened == 0) return@LaunchedEffect
                window.setMinimized(false)
                window.toFront()
                window.requestFocus()
            }
            // A tool whose content decides its height (its heightAt) gets just that, and follows its options
            val titleBarHeight = LocalTitleBarStyle.current.metrics.height
            val contentHeight = tool.heightAt(windowState.size.width - TOOL_PADDING * 2)
            if (contentHeight != null) {
                LaunchedEffect(contentHeight) {
                    windowState.size = windowState.size.copy(height = contentHeight + titleBarHeight + TOOL_PADDING * 2)
                }
            }
            JewelTitleBar(
                modifier = Modifier.newFullscreenControls(),
                gradientStartColor = ThemeUtils.titleBarGradientColor(),
                controlButtonsDirection = ControlButtonsDirection.SystemNative,
            ) {
                Text(title)
            }
            Box(
                Modifier
                    .trackActivation()
                    .fillMaxSize()
                    .background(JewelTheme.globalColors.panelBackground)
                    .padding(TOOL_PADDING),
            ) {
                tool.Content(state, Modifier.fillMaxSize())
            }
            tool.Detached(state)
        }
    }
}

private val TOOL_PADDING = 8.dp
