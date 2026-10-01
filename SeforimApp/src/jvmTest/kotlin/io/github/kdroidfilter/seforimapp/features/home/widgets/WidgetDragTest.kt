package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.github.kdroidfilter.seforimapp.features.home.widgets.calendar.CalendarWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.temple.TempleCountdownWidget
import io.github.kdroidfilter.seforimapp.testAppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetDragTest {
    private val appSettings = testAppSettings()
    private val layout = HomeWidgetsLayout(appSettings)

    @Test
    fun `a gallery drag is over the grid off the gallery`() {
        val drag = WidgetDrag().apply { galleryBounds = Rect(0f, 500f, 1000f, 800f) }
        drag.startAdd(CalendarWidget, Offset(500f, 600f))
        assertFalse(drag.overGrid)
        drag.moveTo(Offset(50f, 50f))
        assertTrue(drag.overGrid)
        drag.end()
        assertNull(drag.newWidget)
        assertFalse(drag.overGrid)
    }

    @Test
    fun `a removed widget comes back where it was`() {
        val saved = appSettings.homeWidgetsLayoutFlow.value
        try {
            appSettings.setHomeWidgetsLayout(encodeLayout(homeWidgets))
            val removed = layout.remove(TempleCountdownWidget)!!
            assertTrue(layout.current().none { it.widget.id == "temple_countdown" })
            layout.restore(removed)
            assertEquals(homeWidgets.toSet(), layout.current().toSet())
        } finally {
            appSettings.setHomeWidgetsLayout(saved)
        }
    }
}
