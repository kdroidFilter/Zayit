package io.github.kdroidfilter.seforimapp.navigation

import io.github.kdroidfilter.seforim.tabs.TabsEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class TabsEventsTest {
    @Test
    fun `OnClose holds correct index`() {
        val event = TabsEvents.OnClose(5)
        assertEquals(5, event.index)
    }

    @Test
    fun `OnSelect holds correct index`() {
        val event = TabsEvents.OnSelect(3)
        assertEquals(3, event.index)
    }

    @Test
    fun `OnAdd is singleton object`() {
        val event1 = TabsEvents.OnAdd
        val event2 = TabsEvents.OnAdd
        assertEquals(event1, event2)
    }

    @Test
    fun `OnReorder holds correct indices`() {
        val event = TabsEvents.OnReorder(fromIndex = 2, toIndex = 5)
        assertEquals(2, event.fromIndex)
        assertEquals(5, event.toIndex)
    }

    @Test
    fun `CloseAll is singleton object`() {
        val event1 = TabsEvents.CloseAll
        val event2 = TabsEvents.CloseAll
        assertEquals(event1, event2)
    }

    @Test
    fun `CloseOthers holds correct index`() {
        val event = TabsEvents.CloseOthers(4)
        assertEquals(4, event.index)
    }

    @Test
    fun `CloseLeft holds correct index`() {
        val event = TabsEvents.CloseLeft(2)
        assertEquals(2, event.index)
    }

    @Test
    fun `CloseRight holds correct index`() {
        val event = TabsEvents.CloseRight(3)
        assertEquals(3, event.index)
    }

    @Test
    fun `OnClose instances with same index are equal`() {
        val event1 = TabsEvents.OnClose(1)
        val event2 = TabsEvents.OnClose(1)
        assertEquals(event1, event2)
    }

    @Test
    fun `OnClose instances with different index are not equal`() {
        val event1 = TabsEvents.OnClose(1)
        val event2 = TabsEvents.OnClose(2)
        assertNotEquals(event1, event2)
    }

    @Test
    fun `OnSelect instances with same index are equal`() {
        val event1 = TabsEvents.OnSelect(1)
        val event2 = TabsEvents.OnSelect(1)
        assertEquals(event1, event2)
    }

    @Test
    fun `OnReorder instances with same indices are equal`() {
        val event1 = TabsEvents.OnReorder(1, 2)
        val event2 = TabsEvents.OnReorder(1, 2)
        assertEquals(event1, event2)
    }

    @Test
    fun `events are sealed class subtypes`() {
        val onClose: TabsEvents = TabsEvents.OnClose(0)
        val onSelect: TabsEvents = TabsEvents.OnSelect(0)
        val onAdd: TabsEvents = TabsEvents.OnAdd
        val onReorder: TabsEvents = TabsEvents.OnReorder(0, 1)
        val closeAll: TabsEvents = TabsEvents.CloseAll
        val closeOthers: TabsEvents = TabsEvents.CloseOthers(0)
        val closeLeft: TabsEvents = TabsEvents.CloseLeft(0)
        val closeRight: TabsEvents = TabsEvents.CloseRight(0)

        assertIs<TabsEvents>(onClose)
        assertIs<TabsEvents>(onSelect)
        assertIs<TabsEvents>(onAdd)
        assertIs<TabsEvents>(onReorder)
        assertIs<TabsEvents>(closeAll)
        assertIs<TabsEvents>(closeOthers)
        assertIs<TabsEvents>(closeLeft)
        assertIs<TabsEvents>(closeRight)
    }
}
