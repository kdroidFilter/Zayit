package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.home.widgets.calendar.CalendarWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.earth.EarthWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.sky.SkyWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.temple.TempleCountdownWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.zmanim.ZmanimWidget
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class CellLayoutTest {
    private val pitch = CellPitch(1000.dp)

    private fun List<WidgetPlacement>.cellOf(widget: HomeWidget) = first { it.widget.id == widget.id }.cell

    private fun assertWellFormed(
        layout: List<WidgetPlacement>,
        context: String = "",
    ) {
        for ((i, a) in layout.withIndex()) {
            assertTrue(a.cell.x >= 0 && a.cell.y >= 0 && a.cell.right <= HOME_GRID_COLUMNS, "$context off the grid: $a")
            for (b in layout.drop(i + 1)) {
                if (a.cell.overlaps(b.cell)) fail("$context ${a.widget.id} ${a.cell} overlaps ${b.widget.id} ${b.cell}")
            }
        }
        assertEquals(layout.size, layout.distinctBy { it.widget.id }.size, "$context a widget twice")
    }

    @Test
    fun `the default layout fills its rows, nothing overlapping`() {
        assertWellFormed(homeWidgets)
        assertEquals(7, homeWidgets.bottom())
        val cells = homeWidgets.sumOf { it.cell.w * it.cell.h }
        assertEquals(HOME_GRID_COLUMNS * 4 + HOME_GRID_COLUMNS * 3, cells)
    }

    @Test
    fun `the nearest area is where the centre is, occupied or not`() {
        // The Temple's centre over the Earth's area
        val centre = pitch.centre(homeWidgets.cellOf(EarthWidget))
        val anywhere = homeWidgets.nearestArea(centre, CellSpan(6, 3), pitch, ignoreOccupied = true)
        assertTrue(anywhere.overlaps(homeWidgets.cellOf(EarthWidget)))
        // Vacant only: below every widget, the grid growing down
        val vacant = homeWidgets.nearestArea(centre, CellSpan(6, 3), pitch)
        assertTrue(homeWidgets.isVacant(vacant), "$vacant")
        assertEquals(7, vacant.y)
    }

    @Test
    fun `a widget added goes in the first hole big enough, row after row`() {
        val withHole = homeWidgets.filterNot { it.widget.id == TempleCountdownWidget.id }
        assertEquals(CellRect(0, 4, 6, 3), withHole.firstVacant(CellSpan(6, 3)))
        // Too big for the hole: below everything
        assertEquals(CellRect(0, 7, 7, 5), withHole.firstVacant(CalendarWidget.defaultSpan))
    }

    @Test
    fun `a widget put over others moves only the ones in its way, to the vacant area nearest where they were`() {
        val sky = homeWidgets.first { it.widget.id == SkyWidget.id }
        val onTheTemple = sky.copy(cell = sky.cell.at(0, 4))
        val result = homeWidgets.reorder(onTheTemple, pitch)
        assertWellFormed(result)
        assertEquals(CellRect(0, 4, 5, 3), result.cellOf(SkyWidget))
        // The Temple was in the way; the zmanim, the Earth and the solar system weren't
        for (w in listOf(ZmanimWidget, EarthWidget)) assertEquals(homeWidgets.cellOf(w), result.cellOf(w))
        assertTrue(result.cellOf(TempleCountdownWidget) != homeWidgets.cellOf(TempleCountdownWidget))
        assertEquals(homeWidgets.map { it.widget.id }, result.map { it.widget.id })
    }

    @Test
    fun `a widget put where it was changes nothing`() {
        for (placement in homeWidgets) assertEquals(homeWidgets, homeWidgets.reorder(placement, pitch))
    }

    @Test
    fun `a resize handle moves its side cell by cell, the other kept, within the widget's limits and the grid`() {
        val temple = CellRect(4, 2, 6, 3)
        val widget = TempleCountdownWidget
        assertEquals(CellRect(4, 2, 8, 3), widget.resized(temple, ResizeEdge.END, 2, pitch))
        assertEquals(CellRect(2, 2, 8, 3), widget.resized(temple, ResizeEdge.START, -2, pitch))
        assertEquals(CellRect(4, 2, 6, 5), widget.resized(temple, ResizeEdge.BOTTOM, 2, pitch))
        assertEquals(CellRect(4, 1, 6, 4), widget.resized(temple, ResizeEdge.TOP, -1, pitch))
        // Its minimum and maximum
        assertEquals(widget.minSpan.w, widget.resized(temple, ResizeEdge.END, -10, pitch).w)
        assertEquals(widget.maxSpan.w, widget.resized(temple, ResizeEdge.END, 20, pitch).w)
        assertEquals(widget.maxSpan.h, widget.resized(temple, ResizeEdge.BOTTOM, 20, pitch).h)
        // The grid's edges
        assertEquals(HOME_GRID_COLUMNS, CalendarWidget.resized(CellRect(10, 0, 7, 5), ResizeEdge.END, 9, pitch).right)
        assertEquals(0, widget.resized(temple, ResizeEdge.TOP, -9, pitch).y)
        assertEquals(0, widget.resized(temple, ResizeEdge.START, -9, pitch).x)
    }

    @Test
    fun `a widget whose content needs the height can't be made shorter than it`() {
        val tall =
            object : HomeWidget by TempleCountdownWidget {
                override val id = "tall"

                override fun heightAt(width: Dp) = 300.dp
            }
        // 300 dp: more than four rows of 63 and their gaps
        assertEquals(5, tall.minRows(6, pitch))
        assertEquals(5, tall.resized(CellRect(0, 0, 6, 5), ResizeEdge.BOTTOM, -2, pitch).h)
    }

    @Test
    fun `random moves and resizes never overlap, never leave the grid, and put the widget where asked`() {
        val random = Random(42)
        var layout = homeWidgets + WidgetPlacement(CalendarWidget, homeWidgets.firstVacant(CalendarWidget.defaultSpan))
        repeat(3000) { step ->
            val placement = layout.random(random)
            val widget = placement.widget
            val asked =
                when (random.nextInt(3)) {
                    0 -> widget.resized(placement.cell, ResizeEdge.entries.random(random), random.nextInt(-6, 7), pitch)
                    else -> {
                        val w = random.nextInt(widget.minSpan.w, minOf(widget.maxSpan.w, HOME_GRID_COLUMNS) + 1)
                        widget.clamp(CellRect(random.nextInt(0, HOME_GRID_COLUMNS), random.nextInt(0, 10), w, placement.cell.h), pitch)
                    }
                }
            layout = layout.reorder(placement.copy(cell = asked), pitch)
            assertWellFormed(layout, "step $step:")
            assertEquals(asked, layout.cellOf(widget), "step $step")
        }
    }

    @Test
    fun `any handle dragged any way, at any grid width, gives an area on the grid within the widget's limits`() {
        // And one whose content wants more rows the narrower it is: at the top, more than there are above it
        val tallerNarrow =
            object : HomeWidget by TempleCountdownWidget {
                override val id = "taller-narrow"

                override fun heightAt(width: Dp) = if (width < 250.dp) 450.dp else 200.dp
            }
        for (gridWidth in listOf(670, 800, 900, 1000)) {
            val at = CellPitch(gridWidth.dp)
            for (widget in availableHomeWidgets + tallerNarrow) {
                for (y in listOf(0, 1, 5)) {
                    // As saved: checked on the full-width grid, then resized on this one
                    val start = widget.clamp(CellRect(0, y, widget.defaultSpan.w, widget.defaultSpan.h), pitch)
                    for (edge in ResizeEdge.entries) {
                        for (cells in -25..25) {
                            val area = widget.resized(start, edge, cells, at)
                            val context = "${widget.id} at $gridWidth dp, $edge by $cells from $start: $area"
                            assertTrue(area.x >= 0 && area.y >= 0 && area.right <= HOME_GRID_COLUMNS, context)
                            assertTrue(area.w >= widget.minSpan.w && area.h >= widget.minRows(area.w, at), context)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `every widget's content fits its smallest area at every width the grid shows`() {
        // Below COMPACT_GRID_WIDTH the widgets are stacked at the height their content asks
        for (gridWidth in listOf(670, 750, 850, 1000)) {
            val at = CellPitch(gridWidth.dp)
            for (widget in availableHomeWidgets) {
                for (w in widget.minSpan.w..minOf(widget.maxSpan.w, HOME_GRID_COLUMNS)) {
                    assertEquals(widget.minSpan.h, widget.minRows(w, at), "${widget.id} $w columns at $gridWidth dp would be clipped")
                }
            }
        }
    }
}
