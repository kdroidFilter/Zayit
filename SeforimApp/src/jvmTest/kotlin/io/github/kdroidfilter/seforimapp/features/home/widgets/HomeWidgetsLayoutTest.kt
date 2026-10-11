package io.github.kdroidfilter.seforimapp.features.home.widgets

import io.github.kdroidfilter.seforimapp.features.home.widgets.calendar.CalendarWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.earth.EarthWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.temple.TempleCountdownWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.zmanim.ZmanimWidget
import io.github.kdroidfilter.seforimapp.testAppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeWidgetsLayoutTest {
    private fun ids(layout: List<WidgetPlacement>) = layout.map { it.widget.id }

    @Test
    fun `no saved layout is the default one`() {
        assertEquals(homeWidgets, decodeLayout(null))
    }

    @Test
    fun `a saved layout round-trips, areas included`() {
        val layout = listOf(WidgetPlacement(CalendarWidget, CellRect(3, 1, 9, 6)), WidgetPlacement(ZmanimWidget, CellRect(0, 7, 8, 4)))
        assertEquals(layout, decodeLayout(encodeLayout(layout)))
    }

    @Test
    fun `an empty save means every widget was removed`() {
        assertEquals(emptyList(), decodeLayout(""))
    }

    @Test
    fun `a save of the format before areas keeps its widgets, in their order, removed ones staying removed`() {
        val layout = decodeLayout("calendar:LARGE,zmanim:MEDIUM,gone:SMALL,zmanim:LARGE")
        assertEquals(listOf("calendar", "zmanim"), ids(layout))
        assertEquals(CellRect(0, 0, 7, 5), layout[0].cell)
        assertEquals(CellRect(7, 0, 13, 4), layout[1].cell)
    }

    @Test
    fun `where some widgets can't be shown, the default layout deals the others out with no hole`() {
        val layout = defaultLayout { it.id in setOf("zmanim", "temple_countdown") }
        assertEquals(listOf("zmanim", "temple_countdown"), ids(layout))
        // The Temple beside the zmanim, where the Earth would have been, not left alone on the next row
        assertEquals(CellRect(13, 0, 6, 3), layout[1].cell)
        assertEquals(homeWidgets, defaultLayout { true })
    }

    @Test
    fun `unknown widgets, broken areas and duplicates never break a save`() {
        val layout = decodeLayout("gone@0,0,4,4;zmanim@0,0,13,4;calendar@x,0,7,5;zmanim@0,9,8,4;earth@13,0,7")
        assertEquals(listOf("zmanim"), ids(layout))
    }

    @Test
    fun `an area beyond its widget's limits is brought within them, one overlapping moved aside`() {
        val layout = decodeLayout("zmanim@0,0,2,1;earth@3,0,7,4;temple_countdown@18,0,6,3")
        assertEquals(CellRect(0, 0, ZmanimWidget.minSpan.w, ZmanimWidget.minSpan.h), layout[0].cell)
        // The Earth overlapped the zmanim brought to its minimum: moved to the vacant area nearest
        assertTrue(layout.drop(1).all { p -> layout.none { it !== p && it.cell.overlaps(p.cell) } })
        assertEquals(HOME_GRID_COLUMNS, layout.first { it.widget.id == TempleCountdownWidget.id }.cell.right)
    }

    @Test
    fun `a widget removed leaves its area empty, and comes back on it`() {
        val appSettings = testAppSettings()
        val layout = HomeWidgetsLayout(appSettings)
        appSettings.setHomeWidgetsLayout(encodeLayout(homeWidgets))
        val removed = layout.remove(TempleCountdownWidget)!!
        assertNull(layout.current().firstOrNull { it.widget.id == TempleCountdownWidget.id })
        // Nothing moved into the hole
        assertEquals(homeWidgets - removed.placement, layout.current())
        layout.restore(removed)
        assertEquals(homeWidgets.toSet(), layout.current().toSet())
    }

    @Test
    fun `a widget added takes the first hole it fits`() {
        val appSettings = testAppSettings()
        val layout = HomeWidgetsLayout(appSettings)
        appSettings.setHomeWidgetsLayout(encodeLayout(homeWidgets.filterNot { it.widget.id == EarthWidget.id }))
        layout.add(TempleCountdownWidget) // already there: nothing
        layout.add(EarthWidget)
        assertEquals(CellRect(13, 0, 7, 4), layout.current().first { it.widget.id == EarthWidget.id }.cell)
    }

    @Test
    fun `a corrupted row far down is brought back within reach`() {
        val layout = decodeLayout("zmanim@0,2000000000,13,4;earth@13,0,7,2000000000;temple_countdown@0,999999,6,3")
        assertTrue(layout.all { it.cell.y <= 200 }, "$layout")
        assertEquals(EarthWidget.maxSpan.h, layout[1].cell.h)
        // Still a layout to work with: a widget added finds its place at once
        assertEquals(CellRect(0, 0, 7, 5), layout.firstVacant(CalendarWidget.defaultSpan))
    }

    @Test
    fun `a save of the format before areas leaves no hole for the widgets this platform can't show`() {
        val layout = decodeOrderLayout("zmanim:LARGE,earth:MEDIUM,temple_countdown:MEDIUM") { it.id != "earth" }
        // The Temple beside the zmanim, where the Earth would have been; the Earth after them
        assertEquals(CellRect(13, 0, 6, 3), layout.first { it.widget.id == "temple_countdown" }.cell)
        assertTrue(layout.first { it.widget.id == "earth" }.cell.y >= 3)
    }

    @Test
    fun `arranging leaves no hole, every widget within its limits`() {
        val pitch = CellPitch(MAX_GRID_WIDTH)
        val layouts =
            listOf(
                homeWidgets,
                availableHomeWidgets.fold(emptyList<WidgetPlacement>()) { l, w -> l + WidgetPlacement(w, l.firstVacant(w.defaultSpan)) },
            )
        for (layout in layouts) {
            val arranged = layout.arranged(pitch)
            assertEquals(ids(layout), ids(arranged))
            arranged.forEach { assertEquals(it.widget.clamp(it.cell, pitch), it.cell, it.widget.id) }
            assertTrue(arranged.indices.all { i -> arranged.indices.none { j -> i != j && arranged[i].cell.overlaps(arranged[j].cell) } })
            assertEquals(arranged.bottom() * HOME_GRID_COLUMNS, arranged.sumOf { it.cell.w * it.cell.h }, "holes in ${ids(layout)}")
        }
    }

    @Test
    fun `arranging again gives another layout as tight`() {
        val pitch = CellPitch(MAX_GRID_WIDTH)
        val first = homeWidgets.rearranged(pitch, 0)
        val second = first.rearranged(pitch, 1)
        assertTrue(first != homeWidgets && second != first)
        assertEquals(homeWidgets.bottom(), second.bottom())
        assertEquals(second.bottom() * HOME_GRID_COLUMNS, second.sumOf { it.cell.w * it.cell.h })
    }

    @Test
    fun `where holes can't be avoided, arranging leaves the fewest`() {
        // The zmanim are 4 rows, the calendar 5 at least and 14 columns at most: the zmanim across, the calendar below
        val layout = listOf(WidgetPlacement(CalendarWidget, CellRect(10, 3, 7, 5)), WidgetPlacement(ZmanimWidget, CellRect(0, 12, 13, 4)))
        val arranged = layout.arranged(CellPitch(MAX_GRID_WIDTH))
        assertEquals(listOf(CellRect(0, 4, 14, 5), CellRect(0, 0, 20, 4)).toSet(), arranged.map { it.cell }.toSet())
    }
}
