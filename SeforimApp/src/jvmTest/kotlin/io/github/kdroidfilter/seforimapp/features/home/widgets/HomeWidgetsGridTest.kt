package io.github.kdroidfilter.seforimapp.features.home.widgets

import io.github.kdroidfilter.seforimapp.features.home.widgets.earth.EarthWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.sky.SkyWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.solarsystem.SolarSystemWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.temple.TempleCountdownWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.zmanim.ZmanimWidget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HomeWidgetsGridTest {
    private fun ids(rows: List<List<WidgetPlacement>>) = rows.map { row -> row.map { it.widget.id } }

    @Test
    fun `default layout fills two full rows`() {
        val rows = packRows(homeWidgets, compact = false)
        assertEquals(listOf(listOf("zmanim", "earth"), listOf("temple_countdown", "solar_system", "sky")), ids(rows))
        rows.forEach { row -> assertEquals(HOME_GRID_COLUMNS, row.sumOf { it.grid.columns }) }
    }

    @Test
    fun `a widget that doesn't fit starts a new row`() {
        val widgets = listOf(ZmanimWidget, TempleCountdownWidget, SolarSystemWidget, SkyWidget).map(::WidgetPlacement)
        assertEquals(
            listOf(listOf("zmanim", "temple_countdown"), listOf("solar_system", "sky")),
            ids(packRows(widgets, compact = false)),
        )
    }

    @Test
    fun `a smaller size lets another widget share the row`() {
        val widgets =
            listOf(
                WidgetPlacement(ZmanimWidget, WidgetSize.MEDIUM),
                WidgetPlacement(EarthWidget),
                WidgetPlacement(SkyWidget),
            )
        assertEquals(listOf(listOf("zmanim", "earth", "sky")), ids(packRows(widgets, compact = false)))
    }

    @Test
    fun `compact grid stacks every widget`() {
        assertEquals(homeWidgets.map { listOf(it.widget.id) }, ids(packRows(homeWidgets, compact = true)))
    }

    @Test
    fun `a row that isn't full stretches its widgets with no hole`() {
        // Zmanim (13) + Temple (6) leave one column: they share the whole width, in proportion
        val widths = rowWidths(listOf(13, 6), 1900)
        assertEquals(listOf(1300, 600), widths)
        assertEquals(1000, rowWidths(listOf(7, 7, 7), 1000).sum())
    }

    @Test
    fun `a widget can't be placed at a size it doesn't offer`() {
        assertFailsWith<IllegalArgumentException> { WidgetPlacement(ZmanimWidget, WidgetSize.SMALL) }
    }
}
