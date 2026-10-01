package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Home page is centred: when its height changes (a widget moved or resized), it glides to its new centre. */
@OptIn(ExperimentalTestApi::class)
class FreezableCenterTest {
    @Test
    fun `content of a new height glides to its centre, a new window size puts it there at once`() =
        runDesktopComposeUiTest(width = 400, height = 1000) {
            var contentHeight by mutableIntStateOf(200)
            var windowHeight by mutableIntStateOf(800)
            setContent {
                Box(Modifier.width(400.dp).height(windowHeight.dp)) {
                    FreezableCenter(frozen = false) { Box(Modifier.testTag("page").size(300.dp, contentHeight.dp)) }
                }
            }
            waitForIdle()

            fun top() = onNodeWithTag("page").fetchSemanticsNode().positionInRoot.y
            assertEquals(300f, top(), 1f)
            mainClock.autoAdvance = false
            // A row of widgets more: 100 dp taller, so 50 dp higher, not at once
            contentHeight = 300
            var last = top()
            var frames = 0
            repeat(60) {
                mainClock.advanceTimeByFrame()
                val now = top()
                assertTrue(abs(now - last) < 25f, "jumped ${last - now} px in a frame")
                if (now != last) frames++
                last = now
            }
            assertEquals(250f, last, 1f)
            assertTrue(frames > 5, "it moved in $frames frames only")
            // The window resized: at once
            windowHeight = 600
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            assertEquals(150f, top(), 1f)
        }
}
