package io.github.kdroidfilter.seforimapp.core.presentation.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RefLinkUriHandlerTest {
    @Test
    fun readsTheTargetLine() {
        assertEquals(123L, refLinkLineId("seforim://line/123"))
    }

    @Test
    fun ignoresOtherUris() {
        assertNull(refLinkLineId("https://example.com/line/123"))
        assertNull(refLinkLineId("seforim://line/abc"))
    }
}
