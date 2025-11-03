package io.github.kdroidfilter.seforimapp.core.presentation.utils

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.logger.debugln
import java.awt.Toolkit

fun getCenteredWindowState(width: Int, height: Int): WindowState {
    val screenSize = Toolkit.getDefaultToolkit().screenSize
    val windowX = (screenSize.width - width) / 2
    val windowY = (screenSize.height - height) / 2

    return WindowState(
        size = DpSize(width.dp, height.dp),
        position = WindowPosition(windowX.dp, windowY.dp)
    )
}


fun processKeyShortcuts(keyEvent: KeyEvent, onNavigateTo: (String) -> Unit): Boolean {
    // Only process key down events
    if (keyEvent.type != KeyEventType.KeyDown) return false
    
    // Debug log the key event
    // debugln { "[DEBUG_LOG] Key event: key=${keyEvent.key}, isCtrlPressed=${keyEvent.isCtrlPressed}, isMetaPressed=${keyEvent.isMetaPressed}, isShiftPressed=${keyEvent.isShiftPressed}" }
    
    // Check for Ctrl/Cmd + and Ctrl/Cmd - for zooming
    val isCtrlOrCmdPressed = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
    if (isCtrlOrCmdPressed) {
        when (keyEvent.key) {
            Key.F -> {
                // Toggle global Find-in-page bar regardless of current focus
                if (AppSettings.findBarOpenFlow.value) AppSettings.closeFindBar() else AppSettings.openFindBar()
                return true
            }
            Key.Plus, Key.NumPadAdd -> {
                debugln { "[DEBUG_LOG] Detected Plus or NumPadAdd key, increasing text size" }
                AppSettings.increaseTextSize()
                return true
            }
            // Handle Equals key with Shift (which produces +)
            Key.Equals -> {
                if (keyEvent.isShiftPressed) {
                    debugln { "[DEBUG_LOG] Detected Equals key with Shift (+ key), increasing text size" }
                    AppSettings.increaseTextSize()
                    return true
                }
            }
            Key.Minus, Key.NumPadSubtract -> {
                debugln { "[DEBUG_LOG] Detected Minus or NumPadSubtract key, decreasing text size" }
                AppSettings.decreaseTextSize()
                return true
            }
        }
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
