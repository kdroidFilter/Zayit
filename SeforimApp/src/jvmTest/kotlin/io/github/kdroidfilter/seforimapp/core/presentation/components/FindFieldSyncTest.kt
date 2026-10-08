package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class FindFieldSyncTest {
    @Test
    fun `a query set from outside fills the field, selected`() {
        val field = TextFieldState("ab")
        syncFindField(field, "בראשית")
        assertEquals("בראשית", field.text.toString())
        assertEquals(TextRange(0, 6), field.selection)
    }

    @Test
    fun `a single letter still being typed isn't wiped`() {
        // Persisted as "" while under 2 letters, e.g. after erasing back to one
        val field = TextFieldState("ב")
        syncFindField(field, "")
        assertEquals("ב", field.text.toString())
    }
}
