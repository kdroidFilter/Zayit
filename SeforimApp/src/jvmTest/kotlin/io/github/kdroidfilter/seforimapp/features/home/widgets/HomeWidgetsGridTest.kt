package io.github.kdroidfilter.seforimapp.features.home.widgets

import io.github.kdroidfilter.seforimapp.features.home.widgets.earth.EarthWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.sky.SkyWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.solarsystem.SolarSystemWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.temple.TempleCountdownWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.zmanim.ZmanimWidget
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeWidgetsGridTest {
    @Test
    fun `default widgets fill two full rows`() {
        val rows = packRows(homeWidgets, compact = false)
        assertEquals(
            listOf(listOf(ZmanimWidget, EarthWidget), listOf(TempleCountdownWidget, SolarSystemWidget, SkyWidget)),
            rows,
        )
        rows.forEach { row -> assertEquals(HOME_GRID_COLUMNS, row.sumOf { it.columns }) }
    }

    @Test
    fun `a widget that doesn't fit starts a new row`() {
        assertEquals(
            listOf(listOf(ZmanimWidget, TempleCountdownWidget), listOf(SolarSystemWidget, SkyWidget)),
            packRows(listOf(ZmanimWidget, TempleCountdownWidget, SolarSystemWidget, SkyWidget), compact = false),
        )
    }

    @Test
    fun `compact grid stacks every widget`() {
        assertEquals(homeWidgets.map { listOf(it) }, packRows(homeWidgets, compact = true))
    }
}
