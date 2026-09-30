package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

    @Test
    fun `every row of the lazy grid spans all its columns, its widgets one height`() {
        val cells = gridCells(homeWidgets, 1000.dp)
        assertEquals(listOf(13, 7, 6, 9, 5), cells.map { it.span })
        assertEquals(cells[0].height, cells[1].height) // earth matches the zmanim cards
        // Without the Earth, the Temple joins the zmanim and stretches to fill their row
        val noEarth = gridCells(homeWidgets.filterNot { it.widget.id == "earth" }, 1000.dp)
        assertEquals(20, noEarth[0].span + noEarth[1].span)
    }

    @Test
    fun `a widget taller than its cells makes its whole row taller`() {
        val tall =
            object : HomeWidget by TempleCountdownWidget {
                override val id = "tall"

                override fun heightAt(width: Dp) = 500.dp
            }
        val cells = gridCells(listOf(WidgetPlacement(tall), WidgetPlacement(SkyWidget)), 1000.dp)
        assertEquals(listOf(500.dp, 500.dp), cells.map { it.height })
    }
}
