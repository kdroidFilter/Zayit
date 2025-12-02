package io.github.kdroidfilter.seforim.htmlparser

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlParserTest {

    @Test
    fun preservesLeadingSpaceBetweenSameStyleHebrewSegments() {
        val html = "<div>וַיִּקְרָ֖<small>א</small> אֶל־מֹשֶׁ֑ה</div>"

        val result = HtmlParser().parse(html)

        assertEquals(1, result.size)
        assertEquals("וַיִּקְרָ֖א אֶל־מֹשֶׁ֑ה", result.first().text)
    }
}
