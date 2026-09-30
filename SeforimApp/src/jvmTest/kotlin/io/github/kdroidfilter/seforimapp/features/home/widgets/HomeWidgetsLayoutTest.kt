package io.github.kdroidfilter.seforimapp.features.home.widgets

import io.github.kdroidfilter.seforimapp.features.home.widgets.calendar.CalendarWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.zmanim.ZmanimWidget
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeWidgetsLayoutTest {
    private fun ids(layout: List<WidgetPlacement>) = layout.map { it.widget.id }

    @Test
    fun `no saved layout is the default one`() {
        assertEquals(homeWidgets, decodeLayout(null))
    }

    @Test
    fun `a saved layout round-trips, sizes included`() {
        val layout = listOf(WidgetPlacement(CalendarWidget, WidgetSize.LARGE), WidgetPlacement(ZmanimWidget, WidgetSize.MEDIUM))
        assertEquals(layout, decodeLayout(encodeLayout(layout)))
    }

    @Test
    fun `an empty save means every widget was removed`() {
        assertEquals(emptyList(), decodeLayout(""))
    }

    @Test
    fun `unknown widgets, unknown sizes and duplicates never break an old save`() {
        val layout = decodeLayout("gone:LARGE,zmanim:SMALL,calendar,zmanim:MEDIUM")
        assertEquals(listOf("zmanim", "calendar"), ids(layout))
        assertEquals(ZmanimWidget.defaultSize, layout[0].size) // zmanim has no SMALL
        assertEquals(CalendarWidget.defaultSize, layout[1].size)
    }

    @Test
    fun `a widget dropped on another takes its place`() {
        val layout = homeWidgets // zmanim, earth, temple, solar_system, sky
        assertEquals(
            listOf("earth", "temple_countdown", "zmanim", "solar_system", "sky"),
            ids(moveBefore(layout, "zmanim", "temple_countdown")),
        )
        assertEquals(
            listOf("sky", "zmanim", "earth", "temple_countdown", "solar_system"),
            ids(moveBefore(layout, "sky", "zmanim")),
        )
        assertEquals(ids(layout), ids(moveBefore(layout, "zmanim", "zmanim")))
    }
}
