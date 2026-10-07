package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseInjectionScope
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.zacsweers.metro.createGraph
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.FreezableCenter
import io.github.kdroidfilter.seforimapp.features.home.widgets.calendar.CalendarWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.earth.EarthWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.sky.SkyWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.solarsystem.SolarSystemWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.temple.TempleCountdownWidget
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.Community
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.testAppSettings
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Moves and resizes widgets with the mouse, as a hand would: in steps, the pointer never quite still while it rests,
 * real time passing (the reorder alarm's 650 ms included).
 */
@OptIn(ExperimentalTestApi::class)
class WidgetDragUiTest {
    private val appSettings = testAppSettings()
    private val layout = HomeWidgetsLayout(appSettings)
    private val graph by lazy { createGraph<AppGraph>() }
    private val pitch = CellPitch(1000.dp)

    private fun saved() = layout.current()

    private fun List<WidgetPlacement>.cellOf(widget: HomeWidget) = first { it.widget.id == widget.id }.cell

    private fun assertNoOverlap(placements: List<WidgetPlacement>) {
        for ((i, a) in placements.withIndex()) {
            for (b in placements.drop(
                i + 1,
            )) {
                if (a.cell.overlaps(b.cell)) fail("${a.widget.id} ${a.cell} overlaps ${b.widget.id} ${b.cell}")
            }
        }
    }

    private fun home(
        editing: Boolean = true,
        width: Int = 1000,
        widthNow: () -> Int = { width },
        height: Int = 1300,
        rtl: Boolean = false,
        header: Int = 0,
        gridTop: Int = 0,
        widgets: ((String?) -> List<WidgetPlacement>) = ::decodeLayout,
        test: ComposeUiTest.(HomeWidgetsState) -> Unit,
    ) {
        appSettings.setHomeWidgetsLayout(encodeLayout(homeWidgets))
        val state = HomeWidgetsState(HomeUserLocation.preview, Community.SEPHARADE, layout).also { it.editingWidgets = editing }
        runDesktopComposeUiTest(width = 1100, height = 1400) {
            setContent {
                IntUiTheme {
                    CompositionLocalProvider(
                        LocalAppGraph provides graph,
                        LocalTabSelected provides false,
                        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    ) {
                        val raw by appSettings.homeWidgetsLayoutFlow.collectAsState()
                        Box(Modifier.testTag("home").width(widthNow().dp).height(height.dp)) {
                            // As on the Home: centred, held still while a widget is dragged; room above it for the rest of
                            // the app, which a widget may be dragged over
                            Box(Modifier.padding(top = gridTop.dp)) {
                                FreezableCenter(frozen = state.pageHeld) {
                                    HomeWidgetsGrid(state = state, widgets = widgets(raw), scrollState = rememberScrollState()) {
                                        // The Home's search, above the widgets
                                        if (header > 0) FullWidthSection { Box(Modifier.testTag("header").height(header.dp)) }
                                    }
                                }
                            }
                            HomeWidgetsOverlay(state, widgets(raw))
                        }
                    }
                }
            }
            waitForIdle()
            test(state)
        }
    }

    private fun ComposeUiTest.centreOf(tag: String) = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center

    /** A hand moving to [to] in steps, then resting there [restMs], a pixel or two of tremble. */
    private fun MouseInjectionScope.glide(
        from: Offset,
        to: Offset,
        restMs: Int,
        random: Random = Random(1),
    ) {
        val steps = 24
        repeat(steps) { moveTo(from + (to - from) * ((it + 1) / steps.toFloat())) }
        repeat(restMs / 16) { moveTo(to + Offset(random.nextInt(-2, 3).toFloat(), random.nextInt(-2, 3).toFloat())) }
    }

    @Test
    fun `a widget let go over an empty area lands there, the others staying put`() =
        home { _ ->
            val start = centreOf("widget-earth")
            // Below every widget: two rows under the last one
            val below = Offset(start.x, centreOf("widget-sky").y + pitch.y * 5)
            onNodeWithTag("home").performMouseInput {
                moveTo(start)
                press()
                glide(start, below, restMs = 200)
                release()
            }
            waitForIdle()
            val after = saved()
            assertTrue(after.cellOf(EarthWidget).y >= 7, "${after.cellOf(EarthWidget)}")
            for (p in homeWidgets.filterNot { it.widget.id == EarthWidget.id }) assertEquals(p.cell, after.cellOf(p.widget))
            assertNoOverlap(after)
        }

    @Test
    fun `a widget resting over another makes it room, and is let go there`() =
        home { _ ->
            val start = centreOf("widget-temple_countdown")
            val earth = centreOf("widget-earth")
            onNodeWithTag("home").performMouseInput {
                moveTo(start)
                press()
                glide(start, earth, restMs = 1000)
                release()
            }
            waitForIdle()
            val after = saved()
            assertTrue(
                after.cellOf(TempleCountdownWidget).overlaps(homeWidgets.cellOf(EarthWidget)),
                "${after.cellOf(TempleCountdownWidget)}",
            )
            assertNotEquals(homeWidgets.cellOf(EarthWidget), after.cellOf(EarthWidget))
            assertNoOverlap(after)
        }

    @Test
    fun `out of edit mode, a widget can't be moved`() =
        home(editing = false) { state ->
            val start = centreOf("widget-earth")
            onNodeWithTag("home").performMouseInput {
                moveTo(start)
                press()
                glide(start, start + Offset(0f, pitch.y * 5), restMs = 200)
                release()
            }
            waitForIdle()
            assertNull(state.drag.movingId)
            assertEquals(homeWidgets, saved())
            assertTrue(onAllNodesWithTag("widget-grip-earth", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        }

    @Test
    fun `a resize handle resizes the widget cell by cell, the widgets in the way making room`() =
        home { state ->
            // A click selects the Temple: its resize frame
            val temple = centreOf("widget-temple_countdown")
            onNodeWithTag("home").performMouseInput { click(temple) }
            waitForIdle()
            assertEquals(TempleCountdownWidget.id, state.selectedWidget)
            val handle = centreOf("widget-resize-temple_countdown-end")
            // Three columns further, and a tremble
            onNodeWithTag("home").performMouseInput {
                moveTo(handle)
                press()
                glide(handle, handle + Offset(pitch.x * 3, 0f), restMs = 200)
                release()
            }
            waitForIdle()
            val after = saved()
            assertEquals(CellRect(0, 4, 9, 3), after.cellOf(TempleCountdownWidget))
            // The solar system was in the way
            assertTrue(!after.cellOf(SolarSystemWidget).overlaps(after.cellOf(TempleCountdownWidget)))
            assertNoOverlap(after)
        }

    @Test
    fun `two resizes in a row both stay, the second worked out from the first`() =
        home { state ->
            fun resize(
                widget: HomeWidget,
                cells: Int,
            ) {
                onNodeWithTag("home").performMouseInput { click(centreOf("widget-${widget.id}")) }
                waitForIdle()
                val handle = centreOf("widget-resize-${widget.id}-bottom")
                onNodeWithTag("home").performMouseInput {
                    moveTo(handle)
                    press()
                    glide(handle, handle + Offset(0f, pitch.y * cells), restMs = 200)
                    release()
                }
                waitForIdle()
            }
            resize(TempleCountdownWidget, 2)
            resize(SkyWidget, 1)
            val after = saved()
            assertEquals(5, after.cellOf(TempleCountdownWidget).h)
            assertEquals(4, after.cellOf(SkyWidget).h)
            assertNoOverlap(after)
            assertEquals(SkyWidget.id, state.selectedWidget)
        }

    @Test
    fun `a resize after a widget is removed doesn't bring it back`() =
        home { _ ->
            // Removed with its badge: the layout changes under the grid
            onNodeWithTag("widget-remove-sky", useUnmergedTree = true).performMouseInput { click(center) }
            waitForIdle()
            assertTrue(saved().none { it.widget.id == SkyWidget.id })
            onNodeWithTag("home").performMouseInput { click(centreOf("widget-temple_countdown")) }
            waitForIdle()
            val handle = centreOf("widget-resize-temple_countdown-bottom")
            onNodeWithTag("home").performMouseInput {
                moveTo(handle)
                press()
                glide(handle, handle + Offset(0f, pitch.y), restMs = 200)
                release()
            }
            waitForIdle()
            assertEquals(4, saved().cellOf(TempleCountdownWidget).h)
            assertTrue(saved().none { it.widget.id == SkyWidget.id }, "the Sky came back")
        }

    @Test
    fun `two moves in a row both stay`() =
        home { _ ->
            fun moveBelow(widget: HomeWidget) {
                val start = centreOf("widget-${widget.id}")
                val below = Offset(start.x, (saved().bottom() + 1) * pitch.y + 40f)
                onNodeWithTag("home").performMouseInput {
                    moveTo(start)
                    press()
                    glide(start, below, restMs = 200)
                    release()
                }
                waitForIdle()
            }
            moveBelow(EarthWidget)
            val first = saved().cellOf(EarthWidget)
            moveBelow(SkyWidget)
            val after = saved()
            assertEquals(first, after.cellOf(EarthWidget))
            assertTrue(after.cellOf(SkyWidget).y >= 7, "${after.cellOf(SkyWidget)}")
            assertNoOverlap(after)
        }

    @Test
    fun `back over an area the others made room on, they make it again`() =
        home { state ->
            val start = centreOf("widget-temple_countdown")
            val earth = centreOf("widget-earth")
            val below = Offset(start.x, centreOf("widget-sky").y + pitch.y * 5)
            onNodeWithTag("home").performMouseInput {
                moveTo(start)
                press()
                glide(start, earth, restMs = 1000)
            }
            waitForIdle()
            assertTrue(state.drag.preview != null, "the others make room over the Earth")
            // Straight there and back, over nothing else on the way
            onNodeWithTag("home").performMouseInput { glide(below, below, restMs = 300) }
            waitForIdle()
            assertNull(state.drag.preview, "nothing in the way below")
            onNodeWithTag("home").performMouseInput { glide(earth, earth, restMs = 1000) }
            waitForIdle()
            assertTrue(state.drag.preview != null, "the others make room again")
            onNodeWithTag("home").performMouseInput { release() }
            waitForIdle()
            assertNoOverlap(saved())
        }

    @Test
    fun `a widget this platform can't show takes no cells`() {
        val hiddenEarth =
            object : HomeWidget by EarthWidget {
                override val isSupported = false
            }
        home(widgets = { raw ->
            decodeLayout(raw).map {
                if (it.widget.id ==
                    EarthWidget.id
                ) {
                    it.copy(widget = hiddenEarth)
                } else {
                    it
                }
            }
        }) { state ->
            val start = centreOf("widget-temple_countdown")
            // Where the hidden Earth is: free, so the Temple shows there at once, nothing pushed
            val earthArea = homeWidgets.cellOf(EarthWidget)
            val zmanim = onNodeWithTag("widget-zmanim", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val there = Offset(zmanim.right + pitch.x * 3.5f, zmanim.top + pitch.y * 1.5f)
            onNodeWithTag("home").performMouseInput {
                moveTo(start)
                press()
                glide(start, there, restMs = 200)
            }
            waitForIdle()
            assertNull(state.drag.preview)
            assertTrue(state.drag.outline?.overlaps(earthArea) == true, "${state.drag.outline}")
            onNodeWithTag("home").performMouseInput { release() }
            waitForIdle()
            // The hidden one kept in the save, where it was
            val raw = appSettings.homeWidgetsLayoutFlow.value!!
            assertTrue("earth@13,0,7,4" in raw, raw)
        }
    }

    @Test
    fun `too narrow a window for the grid, a widget let go from the gallery is still added`() =
        home(width = 600) { state ->
            val placed = saved().map { it.widget.id }
            assertTrue(CalendarWidget.id !in placed)
            state.drag.galleryBounds =
                androidx.compose.ui.geometry
                    .Rect(0f, 1000f, 600f, 1300f)
            state.drag.startAdd(CalendarWidget, Offset(300f, 1100f))
            state.drag.moveTo(Offset(300f, 200f))
            waitForIdle()
            state.drag.drop()
            state.drag.end()
            waitForIdle()
            assertTrue(saved().any { it.widget.id == CalendarWidget.id })
        }

    /** Where [tag] shows, through the layers moving and scaling it, unclipped. */
    private fun ComposeUiTest.shown(tag: String): androidx.compose.ui.geometry.Rect {
        val coordinates = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
        return coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
    }

    @Test
    fun `a widget resized by its start side grows that way only, its other side still, frame by frame`() {
        // Right to left, as the app: the Sky is the far left widget, its start (right) side dragged right; then left to right
        for (rtl in listOf(true, false)) {
            home(rtl = rtl) { _ ->
                onNodeWithTag("home").performMouseInput { click(centreOf("widget-sky")) }
                waitForIdle()
                val before = shown("widget-sky")
                val handle = centreOf("widget-resize-sky-start")
                mainClock.autoAdvance = false
                onNodeWithTag("home").performMouseInput {
                    moveTo(handle)
                    press()
                }
                // Towards the start: right in right to left, left in left to right
                val towards = if (rtl) 1f else -1f
                repeat(30) { step ->
                    onNodeWithTag("home").performMouseInput { moveTo(handle + Offset(towards * pitch.x * 3 * (step + 1) / 30f, 0f)) }
                    mainClock.advanceTimeByFrame()
                    val now = shown("widget-sky")
                    // The end side stays where it was, every frame: left in right to left, right in left to right
                    val drift = if (rtl) now.left - before.left else now.right - before.right
                    assertTrue(kotlin.math.abs(drift) < 1.5f, "rtl=$rtl step $step: its other side moved by $drift px")
                }
                onNodeWithTag("home").performMouseInput { release() }
                repeat(40) { mainClock.advanceTimeByFrame() }
                val after = shown("widget-sky")
                assertTrue(after.width > before.width + pitch.x * 2, "rtl=$rtl: ${before.width} → ${after.width}")
                assertTrue(kotlin.math.abs(if (rtl) after.left - before.left else after.right - before.right) < 1.5f, "rtl=$rtl after")
            }
        }
    }

    @Test
    fun `let go, a widget glides from where it was let go, never back where it started first`() {
        for (rtl in listOf(true, false)) {
            // Moved: from under the pointer into its new area
            home(rtl = rtl) { _ ->
                val start = centreOf("widget-earth")
                val below = Offset(start.x, centreOf("widget-sky").y + pitch.y * 5)
                mainClock.autoAdvance = false
                onNodeWithTag("home").performMouseInput {
                    moveTo(start)
                    press()
                }
                repeat(24) { step ->
                    onNodeWithTag("home").performMouseInput { moveTo(start + (below - start) * ((step + 1) / 24f)) }
                    mainClock.advanceTimeByFrame()
                }
                repeat(5) { mainClock.advanceTimeByFrame() }
                var last = shown("widget-earth")
                onNodeWithTag("home").performMouseInput { release() }
                repeat(40) { frame ->
                    mainClock.advanceTimeByFrame()
                    val now = shown("widget-earth")
                    val jump = (now.topLeft - last.topLeft).getDistance()
                    assertTrue(jump < pitch.y * 2, "rtl=$rtl move, frame $frame after letting go: jumped ${jump.toInt()} px")
                    // Put down as it glides, its lift easing off rather than popping back
                    val pop = kotlin.math.abs(now.width - last.width)
                    assertTrue(pop < 5f, "rtl=$rtl move, frame $frame after letting go: its size popped by ${pop.toInt()} px")
                    last = now
                }
            }
            // Resized: no jump either
            home(rtl = rtl) { _ ->
                onNodeWithTag("home").performMouseInput { click(centreOf("widget-sky")) }
                waitForIdle()
                val handle = centreOf("widget-resize-sky-start")
                val towards = if (rtl) 1f else -1f
                mainClock.autoAdvance = false
                onNodeWithTag("home").performMouseInput {
                    moveTo(handle)
                    press()
                }
                repeat(20) { step ->
                    onNodeWithTag("home").performMouseInput { moveTo(handle + Offset(towards * pitch.x * 3 * (step + 1) / 20f, 0f)) }
                    mainClock.advanceTimeByFrame()
                }
                var last = shown("widget-sky")
                onNodeWithTag("home").performMouseInput { release() }
                repeat(40) { frame ->
                    mainClock.advanceTimeByFrame()
                    val now = shown("widget-sky")
                    val jump = maxOf((now.topLeft - last.topLeft).getDistance(), kotlin.math.abs(now.width - last.width))
                    assertTrue(jump < 2f, "rtl=$rtl resize, frame $frame after letting go: jumped ${jump.toInt()} px")
                    last = now
                }
            }
        }
    }

    @Test
    fun `a widget scrolled half out of view is taken from where it really is, not from the page's edge`() =
        home(height = 420) { _ ->
            // Scrolled until its top is out of view, most of it still in
            val pageTop = shown("home").top
            var steps = 0
            while (shown("widget-zmanim").top > pageTop - 60f && steps++ < 50) {
                onNodeWithTag("home").performMouseInput { scroll(5f) }
                waitForIdle()
            }
            val before = shown("widget-zmanim")
            assertTrue(before.top < pageTop - 40f && before.bottom > pageTop + 150f, "the zmanim's top only is scrolled out: $before")
            // Its visible part, which the clipped bounds would take for the whole widget
            val visible = centreOf("widget-zmanim")
            onNodeWithTag("home").performMouseInput {
                moveTo(visible)
                press()
                repeat(10) { moveBy(Offset(0f, 4f)) }
            }
            waitForIdle()
            val now = shown("widget-zmanim")
            // 40 px moved, less the drag's touch slop: not the scrolled-out part it would jump by, taken from the page's edge
            assertTrue(kotlin.math.abs(now.top - (before.top + 40f)) < 12f, "followed the pointer from ${before.top}: now ${now.top}")
            onNodeWithTag("home").performMouseInput { release() }
        }

    @Test
    fun `edit mode closed mid-drag lets the widget go, nothing left held`() =
        home { state ->
            val start = centreOf("widget-earth")
            onNodeWithTag("home").performMouseInput {
                moveTo(start)
                press()
                repeat(6) { moveBy(Offset(0f, 12f)) }
            }
            waitForIdle()
            assertEquals(EarthWidget.id, state.drag.movingId)
            // Escape on the gallery: the moving handle goes with edit mode
            state.editingWidgets = false
            waitForIdle()
            assertNull(state.drag.movingId)
            onNodeWithTag("home").performMouseInput { release() }
            waitForIdle()
            assertEquals(homeWidgets.toSet(), saved().toSet())
        }

    @Test
    fun `a widget held at the bottom of the page scrolls it`() =
        home(height = 700) { _ ->
            val grip = centreOf("widget-earth")
            val zmanimBefore = shown("widget-zmanim").top
            // Just above the gallery, which covers the page's bottom in edit mode
            val bottom = Offset(grip.x, shown("home").bottom - WIDGET_GALLERY_HEIGHT.value - 8f)
            onNodeWithTag("home").performMouseInput {
                moveTo(grip)
                press()
                glide(grip, bottom, restMs = 800)
            }
            waitForIdle()
            assertTrue(
                shown("widget-zmanim").top < zmanimBefore - 20f,
                "the page didn't scroll: ${shown("widget-zmanim").top} vs $zmanimBefore",
            )
            onNodeWithTag("home").performMouseInput { release() }
        }

    @Test
    fun `over a widget, the outline shows where the held one will land at once, the others making room soon after`() =
        home { state ->
            val start = centreOf("widget-temple_countdown")
            val earth = centreOf("widget-earth")
            mainClock.autoAdvance = false
            onNodeWithTag("home").performMouseInput {
                moveTo(start)
                press()
            }
            // Straight onto the Earth, then frame by frame
            onNodeWithTag("home").performMouseInput { moveTo(start + Offset(0f, -10f)) }
            mainClock.advanceTimeByFrame()
            onNodeWithTag("home").performMouseInput { moveTo(earth) }
            var outlineFrame = -1
            var roomFrame = -1
            repeat(40) { frame ->
                mainClock.advanceTimeByFrame()
                val outline = state.drag.outline
                if (outlineFrame < 0 && outline != null && outline.overlaps(homeWidgets.cellOf(EarthWidget))) outlineFrame = frame
                if (roomFrame < 0 && state.drag.preview != null) roomFrame = frame
            }
            assertTrue(outlineFrame in 0..2, "the outline came at frame $outlineFrame")
            // Quickly, but not before the outline: about 200 ms, a dozen frames
            assertTrue(roomFrame in outlineFrame + 1..outlineFrame + 16, "the others made room at frame $roomFrame")
            val outline = state.drag.outline
            onNodeWithTag("home").performMouseInput { release() }
            repeat(40) { mainClock.advanceTimeByFrame() }
            assertEquals(outline, saved().cellOf(TempleCountdownWidget))
            assertNoOverlap(saved())
        }

    /** The zIndex the grid gives [tag]'s widget, as its modifiers tell it. */
    private fun ComposeUiTest.zIndexOf(tag: String): Float {
        val modifiers = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.getModifierInfo()
        val zIndex = modifiers.map { it.modifier.toString() }.firstOrNull { it.startsWith("ZIndexElement") } ?: return 0f
        return Regex("zIndex=([0-9.]+)").find(zIndex)!!.groupValues[1].toFloat()
    }

    @Test
    fun `the widget held stays above the ones making room, and lands above them too`() =
        home { _ ->
            val start = centreOf("widget-temple_countdown")
            val earth = centreOf("widget-earth")
            mainClock.autoAdvance = false
            onNodeWithTag("home").performMouseInput {
                moveTo(start)
                press()
            }
            onNodeWithTag("home").performMouseInput { moveTo(start + Offset(0f, -10f)) }
            mainClock.advanceTimeByFrame()
            onNodeWithTag("home").performMouseInput { moveTo(earth) }
            var crossed = 0
            repeat(40) {
                mainClock.advanceTimeByFrame()
                val moving = zIndexOf("widget-earth")
                if (moving > 0f) crossed++
                assertTrue(
                    zIndexOf("widget-temple_countdown") > moving,
                    "held ${zIndexOf("widget-temple_countdown")} vs making room $moving",
                )
            }
            assertTrue(crossed > 0, "the Earth never moved aside")
            onNodeWithTag("home").performMouseInput { release() }
            repeat(30) {
                mainClock.advanceTimeByFrame()
                val landing = zIndexOf("widget-temple_countdown")
                val other = zIndexOf("widget-earth")
                if (landing > 0f || other > 0f) assertTrue(landing > other, "landing $landing vs $other")
            }
        }

    @Test
    fun `a widget dragged out of the widgets, above or below them, follows the pointer and lands without a jump`() {
        for (rtl in listOf(true, false)) {
            for (above in listOf(true, false)) {
                run {
                    home(rtl = rtl, header = 200, height = 1300) { _ ->
                        val start = centreOf("widget-temple_countdown")
                        val header = shown("header")
                        val out = if (above) Offset(start.x, header.center.y) else Offset(start.x, shown("widget-sky").bottom + 260f)
                        val grab = start - shown("widget-temple_countdown").topLeft
                        mainClock.autoAdvance = false
                        onNodeWithTag("home").performMouseInput {
                            moveTo(start)
                            press()
                        }
                        val others = listOf("widget-zmanim", "widget-earth", "widget-solar_system", "widget-sky")
                        var last = others.associateWith { shown(it).topLeft }
                        var pointer = start
                        repeat(40) { step ->
                            pointer = start + (out - start) * ((step + 1) / 40f)
                            onNodeWithTag("home").performMouseInput { moveTo(pointer) }
                            mainClock.advanceTimeByFrame()
                            val held = shown("widget-temple_countdown")
                            // Under the pointer, once the drag has started (past the touch slop)
                            if ((pointer - start).getDistance() > 30f) {
                                assertTrue(
                                    (held.topLeft + grab - pointer).getDistance() < 30f,
                                    "rtl=$rtl above=$above step $step: held at ${held.topLeft}, pointer $pointer",
                                )
                            }
                            val now = others.associateWith { shown(it).topLeft }
                            for (id in others) {
                                val jump = (now.getValue(id) - last.getValue(id)).getDistance()
                                assertTrue(
                                    jump < pitch.y * 2,
                                    "rtl=$rtl above=$above step $step: $id jumped ${jump.toInt()} px",
                                )
                            }
                            last = now
                        }
                        repeat(20) { mainClock.advanceTimeByFrame() }
                        var landing = shown("widget-temple_countdown").topLeft
                        onNodeWithTag("home").performMouseInput { release() }
                        repeat(40) { frame ->
                            mainClock.advanceTimeByFrame()
                            val now = shown("widget-temple_countdown").topLeft
                            val jump = (now - landing).getDistance()
                            assertTrue(
                                jump < pitch.y * 2,
                                "rtl=$rtl above=$above landing frame $frame: jumped ${jump.toInt()} px",
                            )
                            landing = now
                        }
                        assertNoOverlap(saved())
                    }
                }
            }
        }
    }

    @Test
    fun `let go, a widget starts landing at once, without holding still first`() {
        for (rtl in listOf(true, false)) {
            home(rtl = rtl) { _ ->
                val start = centreOf("widget-earth")
                val below = Offset(start.x + 40f, centreOf("widget-sky").y + pitch.y * 5 + 30f)
                mainClock.autoAdvance = false
                onNodeWithTag("home").performMouseInput {
                    moveTo(start)
                    press()
                }
                repeat(24) { step ->
                    onNodeWithTag("home").performMouseInput { moveTo(start + (below - start) * ((step + 1) / 24f)) }
                    mainClock.advanceTimeByFrame()
                }
                repeat(5) { mainClock.advanceTimeByFrame() }
                val letGo = shown("widget-earth").topLeft
                onNodeWithTag("home").performMouseInput { release() }
                mainClock.advanceTimeByFrame()
                val first = shown("widget-earth").topLeft
                mainClock.advanceTimeByFrame()
                val second = shown("widget-earth").topLeft
                assertTrue((second - letGo).getDistance() > 4f, "rtl=$rtl: still at $letGo two frames after letting go ($first, $second)")
                // And there within about a quarter of a second
                repeat(18) { mainClock.advanceTimeByFrame() }
                val settled = shown("widget-earth").topLeft
                repeat(10) { mainClock.advanceTimeByFrame() }
                assertTrue((shown("widget-earth").topLeft - settled).getDistance() < 2f, "rtl=$rtl: still landing after 20 frames")
            }
        }
    }

    @Test
    fun `a widget dragged over the rest of the app shows there, and lands from there`() =
        home(gridTop = 300, height = 1300) { _ ->
            val start = centreOf("widget-temple_countdown")
            // Above the grid, over what stands for the rest of the app
            val outside = Offset(start.x, 150f)
            val background = onRoot().captureToImage().toPixelMap()[outside.x.toInt(), outside.y.toInt()]
            mainClock.autoAdvance = false
            onNodeWithTag("home").performMouseInput {
                moveTo(start)
                press()
            }
            repeat(30) { step ->
                onNodeWithTag("home").performMouseInput { moveTo(start + (outside - start) * ((step + 1) / 30f)) }
                mainClock.advanceTimeByFrame()
            }
            repeat(5) { mainClock.advanceTimeByFrame() }
            // Seen there, not clipped away by the grid
            val there = onRoot().captureToImage().toPixelMap()[outside.x.toInt(), outside.y.toInt()]
            assertNotEquals(background, there, "the widget isn't seen over the rest of the app")
            val lifted = shown("widget-lifted")
            assertTrue(lifted.contains(outside), "drawn above the page where it's held: $lifted")
            var last = lifted.topLeft
            onNodeWithTag("home").performMouseInput { release() }
            var frames = 0
            repeat(40) {
                mainClock.advanceTimeByFrame()
                val nodes = onAllNodesWithTag("widget-lifted").fetchSemanticsNodes()
                if (nodes.isEmpty()) return@repeat
                val now = nodes.first().boundsInRoot.topLeft
                assertTrue((now - last).getDistance() < pitch.y * 2, "the landing jumped from $last to $now")
                if (now != last) frames++
                last = now
            }
            assertTrue(frames > 3, "it landed in $frames frames")
            assertTrue(onAllNodesWithTag("widget-lifted").fetchSemanticsNodes().isEmpty(), "still lifted once landed")
            assertNoOverlap(saved())
        }

    @Test
    fun `the widget held is drawn above the page right where it is, following the pointer both ways`() {
        for (rtl in listOf(true, false)) {
            run {
                home(rtl = rtl, header = 60) { state ->
                    val start = centreOf("widget-temple_countdown")
                    mainClock.autoAdvance = false
                    onNodeWithTag("home").performMouseInput {
                        moveTo(start)
                        press()
                    }
                    var previous: Pair<Offset, Offset>? = null
                    for (move in listOf(Offset(40f, -40f), Offset(80f, -120f), Offset(-60f, 60f), Offset(-120f, 140f))) {
                        onNodeWithTag("home").performMouseInput { moveTo(start + move) }
                        repeat(3) { mainClock.advanceTimeByFrame() }
                        // Unclipped, as it may leave the window: from its layout, as the grid's own copy
                        val coordinates = onNodeWithTag("widget-lifted").fetchSemanticsNode().layoutInfo.coordinates
                        val lifted = coordinates.positionInRoot()
                        // Where the grid lays it out, before its lift (scale), as the copy (whose picture holds the lift) is
                        val grid =
                            state.drag.bounds
                                .getValue(TempleCountdownWidget.id)
                                .topLeft
                        assertTrue(
                            (lifted - grid).getDistance() < 2f,
                            "rtl=$rtl $move: drawn at $lifted, the widget is at $grid",
                        )
                        // The way the pointer went since the step before, on both axes
                        previous?.let { (beforeMove, beforeLifted) ->
                            val asked = move - beforeMove
                            val went = lifted - beforeLifted
                            assertTrue(
                                abs(went.x - asked.x) < 3f && abs(went.y - asked.y) < 3f,
                                "rtl=$rtl: moved $went for the pointer's $asked",
                            )
                        }
                        previous = move to lifted
                    }
                    onNodeWithTag("home").performMouseInput { release() }
                }
            }
        }
    }

    @Test
    fun `the remove badge sticking out of a widget's corner shows whole, at rest and while it's dragged`() =
        home(gridTop = 60) { _ ->
            val badge = onNodeWithTag("widget-remove-sky", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val sky = shown("widget-sky")
            // The part of the badge outside the widget
            assertTrue(badge.left < sky.left || badge.top < sky.top, "the badge sticks out: $badge of $sky")

            // In the round badge, up and towards the start of its centre: out of the widget it sits on the corner of
            fun outsidePoint(of: androidx.compose.ui.geometry.Rect) = of.center - Offset(7f, 7f)
            val point = outsidePoint(badge)
            val grey =
                androidx.compose.ui.graphics
                    .Color(0xFF8E8E93)

            fun seen(at: Offset): Boolean {
                val c = onRoot().captureToImage().toPixelMap()[at.x.toInt(), at.y.toInt()]
                return abs(c.red - grey.red) < 0.08f && abs(c.green - grey.green) < 0.08f && abs(c.blue - grey.blue) < 0.08f
            }
            assertTrue(seen(point), "at rest: the badge's corner outside the widget isn't drawn")
            val start = centreOf("widget-sky")
            mainClock.autoAdvance = false
            onNodeWithTag("home").performMouseInput {
                moveTo(start)
                press()
            }
            repeat(10) { step ->
                onNodeWithTag("home").performMouseInput { moveTo(start + Offset(0f, 4f * (step + 1))) }
                mainClock.advanceTimeByFrame()
            }
            repeat(20) { mainClock.advanceTimeByFrame() }
            val moved = onNodeWithTag("widget-remove-sky", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(seen(outsidePoint(moved)), "dragged: the badge's corner outside the widget isn't drawn")
            onNodeWithTag("home").performMouseInput { release() }
        }

    @Test
    fun `a corner handle resizes both ways at once, the opposite corner still`() {
        for (rtl in listOf(true, false)) {
            home(rtl = rtl) { _ ->
                onNodeWithTag("home").performMouseInput { click(centreOf("widget-temple_countdown")) }
                waitForIdle()
                val handle = centreOf("widget-resize-temple_countdown-end-bottom")
                // Towards the end (left right to left) and down: two columns and a row
                val towards = if (rtl) -1f else 1f
                onNodeWithTag("home").performMouseInput {
                    moveTo(handle)
                    press()
                    glide(handle, handle + Offset(towards * pitch.x * 2, pitch.y), restMs = 200)
                    release()
                }
                waitForIdle()
                val after = saved()
                val before = homeWidgets.cellOf(TempleCountdownWidget)
                assertEquals(CellRect(before.x, before.y, before.w + 2, before.h + 1), after.cellOf(TempleCountdownWidget), "rtl=$rtl")
                assertNoOverlap(after)
            }
        }
    }

    @Test
    fun `a widget moved is the one selected, its resize frame shown once it's put down`() =
        home { state ->
            assertNull(state.selectedWidget)
            val start = centreOf("widget-sky")
            onNodeWithTag("home").performMouseInput {
                moveTo(start)
                press()
                glide(start, start + Offset(0f, pitch.y * 4), restMs = 200)
                release()
            }
            waitForIdle()
            assertEquals(SkyWidget.id, state.selectedWidget)
            assertTrue(onAllNodesWithTag("widget-resize-sky-end-bottom", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        }

    @Test
    fun `the gallery offers only the widgets not on the page`() =
        home { _ ->
            fun offered() =
                availableHomeWidgets.filter {
                    onAllNodesWithTag(
                        "gallery-${it.id}",
                        useUnmergedTree = true,
                    ).fetchSemanticsNodes().isNotEmpty()
                }
            // The default page has all but the calendar
            assertEquals(listOf(CalendarWidget), offered())
            // Added with a click: on the page, gone from the gallery
            onNodeWithTag("gallery-calendar", useUnmergedTree = true).performMouseInput { click(center) }
            waitForIdle()
            assertTrue(saved().any { it.widget.id == CalendarWidget.id })
            assertTrue(offered().isEmpty())
            // Removed from the page: offered again
            onNodeWithTag("widget-remove-sky", useUnmergedTree = true).performMouseInput { click(center) }
            waitForIdle()
            assertEquals(listOf(SkyWidget), offered())
        }

    private fun ComposeUiTest.widthOf(tag: String) =
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let {
            val c = it.layoutInfo.coordinates
            c.findRootCoordinates().localBoundingBoxOf(c, clipBounds = false).width
        }

    @Test
    fun `a widget removed shrinks away where it was, as on iOS`() =
        home { _ ->
            val full = widthOf("widget-sky")!!
            mainClock.autoAdvance = false
            onNodeWithTag("widget-remove-sky", useUnmergedTree = true).performMouseInput { click(center) }
            val widths = mutableListOf<Float>()
            repeat(30) {
                mainClock.advanceTimeByFrame()
                widthOf("widget-leaving-sky")?.let(widths::add)
            }
            // Off the layout at once, but seen leaving for a few frames, ever smaller, then gone
            assertTrue(saved().none { it.widget.id == SkyWidget.id })
            assertTrue(widths.size in 5..25, "seen leaving for ${widths.size} frames")
            assertTrue(widths.zipWithNext().all { (a, b) -> b <= a + 0.5f }, "it grew while leaving: $widths")
            assertTrue(widths.last() < full * 0.7f, "it ended at ${widths.last()} of $full")
            assertNull(widthOf("widget-leaving-sky"))
            assertNull(widthOf("widget-sky"))
        }

    @Test
    fun `a widget added by a click pops in, and so does one back in the gallery`() =
        home { _ ->
            mainClock.autoAdvance = false
            onNodeWithTag("gallery-calendar", useUnmergedTree = true).performMouseInput { click(center) }
            val widths = mutableListOf<Float>()
            repeat(40) {
                mainClock.advanceTimeByFrame()
                widthOf("widget-calendar")?.let(widths::add)
            }
            assertTrue(widths.first() < widths.last() * 0.9f, "it didn't pop in: $widths")
            assertTrue(widths.max() > widths.last(), "no bounce: $widths")
            // Removed from the page, the Sky pops in the gallery
            onNodeWithTag("widget-remove-sky", useUnmergedTree = true).performMouseInput { click(center) }
            val offered = mutableListOf<Float>()
            repeat(40) {
                mainClock.advanceTimeByFrame()
                widthOf("gallery-sky")?.let(offered::add)
            }
            assertTrue(offered.first() < offered.last() * 0.9f, "it didn't pop in the gallery: $offered")
        }

    @Test
    fun `a widget put down from the gallery lands, it doesn't pop in`() =
        home { _ ->
            val start = centreOf("gallery-calendar")
            val target = Offset(centreOf("widget-sky").x, centreOf("widget-sky").y + pitch.y * 4)
            mainClock.autoAdvance = false
            onNodeWithTag("home").performMouseInput {
                moveTo(start)
                press()
            }
            repeat(30) { step ->
                onNodeWithTag("home").performMouseInput { moveTo(start + (target - start) * ((step + 1) / 30f)) }
                mainClock.advanceTimeByFrame()
            }
            repeat(20) { mainClock.advanceTimeByFrame() }
            onNodeWithTag("home").performMouseInput { release() }
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            val first = widthOf("widget-calendar")!!
            repeat(40) { mainClock.advanceTimeByFrame() }
            assertEquals(widthOf("widget-calendar")!!, first, 2f)
        }

    @Test
    fun `selected, a widget's remove badge stays clear of its resize frame's corner handle`() {
        for (rtl in listOf(true, false)) {
            home(rtl = rtl) { _ ->
                onNodeWithTag("home").performMouseInput { click(centreOf("widget-temple_countdown")) }
                waitForIdle()
                val badge = onNodeWithTag("widget-remove-temple_countdown", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val corner =
                    onNodeWithTag(
                        "widget-resize-temple_countdown-start-top",
                        useUnmergedTree = true,
                    ).fetchSemanticsNode().boundsInRoot
                assertTrue(!badge.overlaps(corner), "rtl=$rtl: the badge $badge covers the corner handle $corner")
            }
        }
    }

    @Test
    fun `let go as the window is resized, a widget lands on its area, not stuck where it was let go`() {
        var width by mutableIntStateOf(1000)
        home(widthNow = { width }) { state ->
            val start = centreOf("widget-earth")
            onNodeWithTag("home").performMouseInput {
                moveTo(start)
                press()
                glide(start, start + Offset(0f, pitch.y * 5), restMs = 100)
            }
            waitForIdle()
            width = 900
            waitForIdle()
            onNodeWithTag("home").performMouseInput { release() }
            waitForIdle()
            val cell = saved().cellOf(EarthWidget)
            val at =
                state.drag.bounds
                    .getValue(EarthWidget.id)
                    .topLeft - state.drag.gridOrigin
            val narrower = CellPitch(900.dp)
            val shouldBe = with(density) { Offset((cell.x * narrower.x).dp.toPx(), (cell.y * narrower.y).dp.toPx()) }
            assertTrue(abs(at.y - shouldBe.y) < 3f, "stuck at $at, its area is at $shouldBe")
        }
    }
}
