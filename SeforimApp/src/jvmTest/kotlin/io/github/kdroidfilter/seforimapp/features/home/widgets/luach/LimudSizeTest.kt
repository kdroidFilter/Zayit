package io.github.kdroidfilter.seforimapp.features.home.widgets.luach

import io.github.kdroidfilter.seforimapp.features.home.widgets.CellPitch
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellRect
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsLayout
import io.github.kdroidfilter.seforimapp.features.home.widgets.MAX_GRID_WIDTH
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetPlacement
import io.github.kdroidfilter.seforimapp.features.home.widgets.encodeLayout
import io.github.kdroidfilter.seforimapp.features.home.widgets.minRows
import io.github.kdroidfilter.seforimapp.testAppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Limud card is always tall enough for every limud picked, and half as tall on two columns. */
class LimudSizeTest {
    private val pitch = CellPitch(MAX_GRID_WIDTH)

    @Test
    fun `its height follows the limudim picked`() {
        val settings = testAppSettings()
        val layout = HomeWidgetsLayout(settings)
        val few = LimudWidget.minRows(4, pitch)
        layout.setOptions(LimudWidget, Limud.encode(Limud.entries.toSet()))
        val all = LimudWidget.minRows(4, pitch)
        assertTrue(all > few, "16 limudim need more rows than ${Limud.defaults.size}: $all, $few")
        // On two columns, about half
        assertTrue(LimudWidget.minRows(10, pitch) <= (all + 1) / 2 + 1)
        layout.setOptions(LimudWidget, null)
    }

    @Test
    fun `picking more grows the card on the grid`() {
        val settings = testAppSettings()
        settings.setHomeWidgetsLayout(encodeLayout(listOf(WidgetPlacement(LimudWidget, CellRect(0, 0, 4, 2)))))
        val layout = HomeWidgetsLayout(settings)
        layout.setOptions(LimudWidget, Limud.encode(Limud.entries.toSet()))
        val cell = layout.current().single().cell
        assertEquals(LimudWidget.minRows(4, pitch), cell.h)
        layout.setOptions(LimudWidget, null)
    }
}
