package io.github.kdroidfilter.seforimapp.core.presentation.utils

import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimapp.logger.debugln
import java.awt.Toolkit

fun getCenteredWindowState(
    width: Int,
    height: Int,
): WindowState {
    val screenSize = Toolkit.getDefaultToolkit().screenSize
    val windowX = (screenSize.width - width) / 2
    val windowY = (screenSize.height - height) / 2

    return WindowState(
        size = DpSize(width.dp, height.dp),
        position = WindowPosition(windowX.dp, windowY.dp),
    )
}

/** Whether this destination shows a find-in-page bar: the search results, and a book once one is open. */
fun TabsDestination.hasFindInPage(store: TabPersistedStateStore): Boolean =
    when (this) {
        is TabsDestination.Search -> true
        is TabsDestination.BookContent -> (store.get(tabId)?.bookContent?.selectedBookId ?: -1L) > 0L
        else -> false
    }

fun processKeyShortcuts(
    keyEvent: KeyEvent,
    appSettings: AppSettings,
    onNavigateTo: (String) -> Unit,
    tabId: String = "",
    // Whether the current tab has a find-in-page bar: find keys are left alone elsewhere
    findAvailable: Boolean = true,
    selectedText: (tabId: String) -> String = { "" },
): Boolean {
    // Only process key down events
    if (keyEvent.type != KeyEventType.KeyDown) return false

    // Debug log the key event
    // debugln { "[DEBUG_LOG] Key event: key=${keyEvent.key}, isCtrlPressed=${keyEvent.isCtrlPressed}, isMetaPressed=${keyEvent.isMetaPressed}, isShiftPressed=${keyEvent.isShiftPressed}" }

    // Check for Ctrl/Cmd + and Ctrl/Cmd - for zooming
    val isCtrlOrCmdPressed = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
    if (isCtrlOrCmdPressed) {
        when (keyEvent.key) {
            Key.F -> {
                if (!findAvailable) return false
                // Find-in-page of the current tab, as in Chromium: shows and focuses it, never closes it
                appSettings.showFindBar(tabId, selectedText(tabId))
                return true
            }
            Key.G -> {
                if (!findAvailable) return false
                appSettings.findNext(tabId, forward = !keyEvent.isShiftPressed)
                return true
            }
            Key.Plus, Key.NumPadAdd -> {
                debugln { "[DEBUG_LOG] Detected Plus or NumPadAdd key, increasing text size" }
                appSettings.increaseTextSize()
                return true
            }
            // Handle Equals key too: many layouts send '=' for zoom-in (with or without Shift)
            Key.Equals -> {
                debugln { "[DEBUG_LOG] Detected Equals key, increasing text size" }
                appSettings.increaseTextSize()
                return true
            }
            Key.Minus, Key.NumPadSubtract -> {
                debugln { "[DEBUG_LOG] Detected Minus or NumPadSubtract key, decreasing text size" }
                appSettings.decreaseTextSize()
                return true
            }
        }
    }

    // F3 / Shift+F3: find next / previous, outside macOS as in Chromium
    if (findAvailable && keyEvent.key == Key.F3 && !isCtrlOrCmdPressed && !keyEvent.isAltPressed && !PlatformInfo.isMacOS) {
        appSettings.findNext(tabId, forward = !keyEvent.isShiftPressed)
        return true
    }

    // Process Alt key shortcuts for navigation
    if (keyEvent.isAltPressed) {
        return when (keyEvent.key) {
            Key.W -> {
                onNavigateTo("Welcome")
                true
            }

            Key.M -> {
                onNavigateTo("Markdown")
                true
            }

            Key.C -> {
                onNavigateTo("Components")
                true
            }

            else -> false
        }
    }

    return false
}
