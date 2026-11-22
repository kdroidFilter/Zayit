package io.github.kdroidfilter.seforimapp.core.settings

import com.russhwolf.settings.PropertiesSettings
import kotlinx.serialization.Serializable
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SettingTest {
    private enum class Color { RED, GREEN, BLUE }

    @Serializable
    private data class Frame(
        val x: Float,
        val y: Float,
    )

    private object TestGroup : SettingsGroup("test") {
        val flag by boolean(true)
        val speed by float(2f)
        val names by stringSet(setOf("a", "b"))
        val colors by enumSet(setOf(Color.RED))
        val picked by enumOrNull<Color>()
        val frame by json<Frame?>(null)
    }

    private val store = PropertiesSettings(Properties())

    @Test
    fun `each setting is saved under its property name`() {
        assertEquals(
            listOf("test.flag", "test.speed", "test.names", "test.colors", "test.picked", "test.frame"),
            TestGroup.all.map { it.key },
        )
    }

    @Test
    fun `values round trip through the store and are observed`() {
        val settings = AppSettings(store)
        val flow = settings.flow(TestGroup.colors)
        settings[TestGroup.flag] = false
        settings[TestGroup.speed] = 0.5f
        settings[TestGroup.names] = emptySet()
        settings[TestGroup.colors] = setOf(Color.GREEN, Color.BLUE)
        settings[TestGroup.picked] = Color.BLUE
        settings[TestGroup.frame] = Frame(1f, 2f)
        assertEquals(setOf(Color.GREEN, Color.BLUE), flow.value)

        // A new instance reads them back from the store
        val reread = AppSettings(store)
        assertFalse(reread[TestGroup.flag])
        assertEquals(0.5f, reread[TestGroup.speed])
        assertEquals(emptySet(), reread[TestGroup.names])
        assertEquals(setOf(Color.GREEN, Color.BLUE), reread[TestGroup.colors])
        assertEquals(Color.BLUE, reread[TestGroup.picked])
        assertEquals(Frame(1f, 2f), reread[TestGroup.frame])
    }

    @Test
    fun `unreadable values fall back to the default`() {
        store.putString("test.speed", "fast")
        store.putString("test.picked", "PURPLE")
        store.putString("test.frame", "not json")
        val settings = AppSettings(store)
        assertEquals(2f, settings[TestGroup.speed])
        assertNull(settings[TestGroup.picked])
        assertNull(settings[TestGroup.frame])
    }

    @Test
    fun `reset brings the whole group back to its defaults`() {
        val settings = AppSettings(store)
        settings[TestGroup.flag] = false
        settings[TestGroup.picked] = Color.RED
        settings.reset(TestGroup)
        assertEquals(true, settings[TestGroup.flag])
        assertNull(settings[TestGroup.picked])
        assertFalse(store.hasKey("test.flag"))
    }

    @Test
    fun `the legacy widget option blobs are dropped`() {
        store.putString("home_widget_options_solar_system", "{}")
        AppSettings(store)
        assertFalse(store.hasKey("home_widget_options_solar_system"))
    }
}
