package io.github.kdroidfilter.seforimapp.core.presentation.window

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.window.jewel.JewelDecoratedWindow
import io.github.kdroidfilter.seforimapp.framework.desktop.TabDockManager
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * Floating drag preview shown while a tab is dragged outside its strip: a small undecorated
 * (borderless, no macOS traffic lights), always-on-top, non-focusable window following the
 * pointer — IntelliJ's DragImageDialog equivalent, backend-agnostic through Nucleus. Created on
 * the first detach of a drag session and kept (hidden) until the session ends, so re-crossing
 * the strip edge doesn't recreate a native window mid-drag.
 */
@Composable
fun NucleusApplicationScope.TabDragGhostWindow(dockManager: TabDockManager) {
    val ghost by dockManager.ghost.collectAsState()
    val g = ghost ?: return

    val state =
        remember {
            WindowState(
                placement = WindowPlacement.Floating,
                position = WindowPosition.Absolute(g.x.dp, g.y.dp),
                size = DpSize(200.dp, 40.dp),
            )
        }
    SideEffect {
        state.position = WindowPosition.Absolute(g.x.dp, g.y.dp)
    }

    JewelDecoratedWindow(
        onCloseRequest = { /* not user-closable */ },
        state = state,
        visible = g.visible,
        title = "",
        resizable = false,
        focusable = false,
        alwaysOnTop = true,
        undecorated = true,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(10.dp))
                    .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = g.title,
                color = JewelTheme.globalColors.text.normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
