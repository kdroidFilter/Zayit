package io.github.kdroidfilter.seforimapp.core.e2e

import androidx.compose.ui.window.WindowPlacement
import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * macOS: a window started maximized restores to its floating size, not to a screen-sized frame
 * pushed down by the title bar (what the title-bar double-click and the green button do). Reads
 * the real NSWindow frame, since the WindowState only reports what the window was told.
 */
object E2eZoomScenario {
    @Structure.FieldOrder("x", "y", "w", "h")
    class Rect :
        Structure(),
        Structure.ByValue {
        @JvmField var x = 0.0

        @JvmField var y = 0.0

        @JvmField var w = 0.0

        @JvmField var h = 0.0

        override fun toString() = "(%.0f,%.0f %.0fx%.0f)".format(x, y, w, h)
    }

    private val objc by lazy { NativeLibrary.getInstance("objc") }
    private val send: Function by lazy { objc.getFunction("objc_msgSend") }

    private fun frame(
        target: Pointer?,
        selector: String,
    ): Rect = send.invoke(Rect::class.java, arrayOf(target, objc.getFunction("sel_registerName").invokePointer(arrayOf(selector)))) as Rect

    suspend fun run(sc: E2eScenario) {
        if (E2e.scenario != "zoom") return
        val w =
            sc
                .graph()
                .desktopManager.windows.value
                .first()
        val win =
            Pointer(
                w.nucleusWindow
                    ?.unsafe
                    ?.taoWindow
                    ?.nsWindowHandle ?: error("no native window"),
            )
        val floating = w.floatingSize
        w.windowState.placement = WindowPlacement.Maximized
        delay(1500)
        sc.note("maximized frame=${frame(win, "frame")}")
        w.windowState.placement = WindowPlacement.Floating
        delay(1500)
        val restored = frame(win, "frame")
        sc.note("restored frame=$restored, floating size $floating")
        sc.step("zoom-restored")
        check(abs(restored.w - floating.width.value) < 2 && abs(restored.h - floating.height.value) < 2) {
            "restored to $restored instead of $floating"
        }
    }
}
