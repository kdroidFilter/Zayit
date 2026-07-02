package io.github.kdroidfilter.seforimapp.framework.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Cross-window tab drag & drop, backend-agnostic (works on both the AWT and Tao backends of
 * Nucleus — no AWT event plumbing involved).
 *
 * IntelliJ-DockManager-inspired, but driven entirely by Compose pointer events: the source strip
 * forwards the raw pointer stream of an in-flight tab drag (it keeps receiving it even outside the
 * window, thanks to the platform pointer capture during a button-held drag). While the pointer
 * stays inside the source strip, the strip's own ReorderableRow keeps doing in-place reordering;
 * once it leaves, a floating ghost window follows the pointer and the release either drops the tab
 * on another window's registered strip or detaches it into a new window of the same desktop.
 *
 * Screen coordinates are logical (dp), obtained through [OpenWindow.boundsOnScreen].
 */
class TabDockManager(
    private val desktopManager: DesktopManager,
) {
    /** One window's tab strip, with lazily-evaluated geometry accessors. */
    class StripTarget(
        val windowId: String,
        /** Strip bounds in the window's px space, or null before first layout. */
        val boundsInWindowPx: () -> Rect?,
        /** Converts window-px to logical screen coordinates; null while the window isn't realized. */
        val windowPxToScreen: (Offset) -> Offset?,
        /** Strip bounds in logical screen coordinates. */
        val boundsOnScreen: () -> Rect?,
        /** Model insertion index for a drop at the given logical screen X (handles RTL). */
        val dropIndexFor: (screenX: Float) -> Int,
        /** Drop hit-test in the window's OWN px space (Wayland pending-drop path). */
        val dropAreaContainsWindowPx: (Offset) -> Boolean,
        /** Insertion index for a drop at the given window-px X (Wayland pending-drop path). */
        val dropIndexForWindowPx: (xPx: Float) -> Int,
    )

    /**
     * Floating drag preview: a small undecorated always-on-top window following the pointer.
     * Position is in logical screen coordinates.
     */
    data class GhostState(
        val title: String,
        val x: Float,
        val y: Float,
        val visible: Boolean,
        /**
         * Drag-source window. On Linux the ghost is created as a popup overlay of it
         * (wl_subsurface on native Wayland — the only client-positionable window kind
         * under xdg-shell, so the ghost can actually follow the pointer there).
         */
        val sourceWindow: dev.nucleusframework.application.NucleusWindow? = null,
    )

    private val targets = LinkedHashMap<String, StripTarget>()

    private val _ghost = MutableStateFlow<GhostState?>(null)
    val ghost: StateFlow<GhostState?> = _ghost.asStateFlow()

    private var session: Session? = null

    /**
     * Wayland-only deferred drop. Screen-coordinate hit-testing is meaningless on
     * native Wayland (every window reports a compositor-fake origin), so the drop
     * target is identified the only way the protocol allows: when the button-held
     * grab ends, the compositor re-evaluates pointer focus and the window under
     * the cursor receives a pointer-enter — with coordinates in its own space.
     * [onWindowPointerSample] resolves the pending drop from that sample; if no
     * window reports one within [PENDING_DROP_TIMEOUT_MS] the tab detaches into a
     * new window, as a void release always did.
     */
    private class PendingDrop(
        val tabId: String,
        val sourceWindowId: String,
        val lastScreen: Offset,
    )

    private var pendingDrop: PendingDrop? = null
    private var pendingDropJob: Job? = null

    /** App-lifetime scope for the pending-drop timeout (main thread: mutates window state). */
    @StructuredScope
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private class Session(
        val tabId: String,
        val sourceWindowId: String,
        val title: String,
    ) {
        var lastScreen: Offset? = null
    }

    fun registerStrip(target: StripTarget) {
        targets[target.windowId] = target
    }

    fun unregisterStrip(windowId: String) {
        targets.remove(windowId)
    }

    /** Called by the strip when a tab drag gesture starts. Safe to call redundantly. */
    fun startTracking(
        tabId: String,
        sourceWindowId: String,
        fallbackTitle: String,
    ) {
        if (session?.tabId == tabId) return
        val sourceWindow = desktopManager.window(sourceWindowId) ?: return
        val tab =
            sourceWindow.tabsViewModel.state.value.tabs
                .find { it.destination.tabId == tabId } ?: return
        session =
            Session(
                tabId = tabId,
                sourceWindowId = sourceWindowId,
                title = tab.title.ifBlank { fallbackTitle },
            )
        _ghost.value = null
        debugln { "[TabDockManager] session started: tab=$tabId source=$sourceWindowId" }
    }

    fun hasActiveSession(windowId: String): Boolean = session?.sourceWindowId == windowId

    /** Aborts the session if [windowId] is its source (window closed mid-drag). */
    fun cancelIfSource(windowId: String) {
        if (session?.sourceWindowId == windowId) {
            session = null
            _ghost.value = null
        }
    }

    /**
     * Pointer stream of the source strip during a tab drag. [positionInWindowPx] is in the source
     * window's px space; [released] marks the final up event.
     */
    fun onStripPointer(
        windowId: String,
        positionInWindowPx: Offset,
        released: Boolean,
    ) {
        val s = session ?: return
        if (s.sourceWindowId != windowId) return
        val target = targets[windowId] ?: return

        val stripBounds = target.boundsInWindowPx()
        // Without strip bounds never enter detach mode: reordering keeps working, cross-window
        // drag degrades to a no-op.
        val inside = stripBounds == null || expandForDetach(stripBounds).contains(positionInWindowPx)
        target.windowPxToScreen(positionInWindowPx)?.let { s.lastScreen = it }

        if (released) {
            finish(s, releasedInsideSource = inside)
            return
        }

        val screen = s.lastScreen
        _ghost.value =
            if (!inside && screen != null) {
                GhostState(
                    title = s.title,
                    x = screen.x + GHOST_POINTER_OFFSET,
                    y = screen.y + GHOST_POINTER_OFFSET,
                    visible = true,
                    sourceWindow = desktopManager.window(s.sourceWindowId)?.nucleusWindow,
                )
            } else {
                // Keep the ghost window alive (hidden) while the session lasts so re-crossing the
                // strip edge doesn't recreate a native window mid-drag.
                _ghost.value?.copy(visible = false)
            }
    }

    private fun finish(
        s: Session,
        releasedInsideSource: Boolean,
    ) {
        session = null
        _ghost.value = null
        if (releasedInsideSource) return
        val p = s.lastScreen ?: return

        debugln {
            buildString {
                append("[TabDockManager] release p=$p insideSource=false targets:\n")
                targets.values.forEach { t ->
                    append("  window=${t.windowId} strip=${t.boundsOnScreen()}\n")
                }
            }
        }

        val hit =
            targets.values.firstOrNull { t ->
                t.windowId != s.sourceWindowId &&
                    t.boundsOnScreen()?.let { expandForDrop(it).contains(p) } == true
            }
        debugln { "[TabDockManager] hit=${hit?.windowId ?: "none → detach"}" }
        if (hit != null) {
            desktopManager.moveTabToWindow(s.tabId, s.sourceWindowId, hit.windowId, hit.dropIndexFor(p.x))
        } else if (isNativeWayland && targets.size > 1) {
            // Defer: the pointer-enter the compositor is about to send to the
            // window under the cursor is the only reliable cross-window drop
            // signal on Wayland. See [PendingDrop].
            pendingDropJob?.cancel()
            pendingDrop = PendingDrop(s.tabId, s.sourceWindowId, p)
            pendingDropJob =
                scope.launch {
                    delay(PENDING_DROP_TIMEOUT_MS)
                    resolvePendingDropAsDetach()
                }
        } else {
            detach(s.tabId, s.sourceWindowId, p)
        }
    }

    /**
     * Pointer sample from any window (root-level Initial-pass observer in
     * MainAppWindow). Only consulted while a Wayland [PendingDrop] is armed.
     */
    fun onWindowPointerSample(
        windowId: String,
        positionInWindowPx: Offset,
    ) {
        val pd = pendingDrop ?: return
        if (windowId == pd.sourceWindowId) return
        val target = targets[windowId] ?: return
        pendingDropJob?.cancel()
        pendingDrop = null
        if (target.dropAreaContainsWindowPx(positionInWindowPx)) {
            debugln { "[TabDockManager] pending drop resolved onto $windowId" }
            desktopManager.moveTabToWindow(
                pd.tabId,
                pd.sourceWindowId,
                windowId,
                target.dropIndexForWindowPx(positionInWindowPx.x),
            )
        } else {
            // Released over another window's body — same outcome as the void.
            detach(pd.tabId, pd.sourceWindowId, pd.lastScreen)
        }
    }

    fun hasPendingDrop(): Boolean = pendingDrop != null

    private fun resolvePendingDropAsDetach() {
        val pd = pendingDrop ?: return
        pendingDrop = null
        debugln { "[TabDockManager] pending drop timed out → detach" }
        detach(pd.tabId, pd.sourceWindowId, pd.lastScreen)
    }

    private fun detach(
        tabId: String,
        sourceWindowId: String,
        p: Offset,
    ) {
        // ponytail: dragging a single-tab window's only tab is a no-op (detach returns null);
        // Chrome would move the whole window — add if users ask.
        desktopManager.detachTabToNewWindow(
            tabId = tabId,
            fromWindowId = sourceWindowId,
            screenX = (p.x - NEW_WINDOW_POINTER_OFFSET_X).roundToInt(),
            screenY = (p.y - NEW_WINDOW_POINTER_OFFSET_Y).roundToInt(),
        )
    }

    /** Leaving this area around the source strip switches from reorder to detach mode. */
    private fun expandForDetach(r: Rect): Rect =
        Rect(
            left = r.left - DETACH_SLACK_X,
            top = r.top - r.height,
            right = r.right + DETACH_SLACK_X,
            bottom = r.bottom + r.height,
        )

    /** Slightly generous drop area around a target strip (logical screen coordinates). */
    private fun expandForDrop(r: Rect): Rect =
        Rect(
            left = r.left,
            // The strip sits below the window's top edge (fullscreen controls / padding): accept
            // drops from the very top of the title bar.
            top = r.top - DROP_SLACK_TOP,
            right = r.right,
            bottom = r.bottom + DROP_SLACK_Y,
        )

    private companion object {
        /** True on a native Wayland session (Tao backend not forced onto XWayland). */
        val isNativeWayland: Boolean =
            System.getProperty("os.name").lowercase().contains("linux") &&
                !System.getenv("WAYLAND_DISPLAY").isNullOrEmpty() &&
                !"x11".equals(System.getenv("NUCLEUS_TAO_LINUX_RENDERER"), ignoreCase = true) &&
                !"x11".equals(System.getenv("GDK_BACKEND"), ignoreCase = true)

        const val PENDING_DROP_TIMEOUT_MS = 300L
        const val GHOST_POINTER_OFFSET = 14f
        const val NEW_WINDOW_POINTER_OFFSET_X = 120f
        const val NEW_WINDOW_POINTER_OFFSET_Y = 24f
        const val DETACH_SLACK_X = 48f
        const val DROP_SLACK_Y = 16f
        const val DROP_SLACK_TOP = 40f
    }
}
