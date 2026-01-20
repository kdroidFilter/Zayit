package io.github.kdroidfilter.seforimapp.core

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TextSelectionStoreTest {
    @BeforeTest
    fun setup() {
        TextSelectionStore.clear()
    }

    @Test
    fun `initial selected text is empty`() {
        assertEquals("", TextSelectionStore.selectedText.value)
    }

    @Test
    fun `updateSelection sets selected text`() {
        TextSelectionStore.updateSelection("Hello World")
        assertEquals("Hello World", TextSelectionStore.selectedText.value)
    }

    @Test
    fun `updateSelection overwrites previous selection`() {
        TextSelectionStore.updateSelection("First")
        TextSelectionStore.updateSelection("Second")
        assertEquals("Second", TextSelectionStore.selectedText.value)
    }

    @Test
    fun `clear resets selected text to empty`() {
        TextSelectionStore.updateSelection("Some text")
        TextSelectionStore.clear()
        assertEquals("", TextSelectionStore.selectedText.value)
    }

    @Test
    fun `updateSelection with empty string works`() {
        TextSelectionStore.updateSelection("Text")
        TextSelectionStore.updateSelection("")
        assertEquals("", TextSelectionStore.selectedText.value)
    }

    @Test
    fun `updateSelection handles Hebrew text`() {
        TextSelectionStore.updateSelection("שלום עולם")
        assertEquals("שלום עולם", TextSelectionStore.selectedText.value)
    }

    @Test
    fun `updateSelection handles special characters`() {
        TextSelectionStore.updateSelection("Test\nWith\tSpecial")
        assertEquals("Test\nWith\tSpecial", TextSelectionStore.selectedText.value)
    }

    @Test
    fun `selectedText is StateFlow`() {
        val flow = TextSelectionStore.selectedText
        assertEquals("", flow.value)
        TextSelectionStore.updateSelection("Updated")
        assertEquals("Updated", flow.value)
    }
}
