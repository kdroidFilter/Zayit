package io.github.kdroidfilter.seforimapp.features.bookcontent.segments

import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommentSegmentsTest {
    // Tur, Orach Chaim 7, as the DB has it
    private val tur =
        """ <i data-commentator="Bach" data-order="1.1"></i><i data-commentator="Beit Yosef" data-order="1.1"></i>""" +
            """וכל היום כשעושה צרכיו <i data-commentator="Drisha" data-order="1.1"></i>י"א שלקטנים מברך אשר יצר """ +
            """<i data-commentator="Prisha" data-order="2.1"></i>ולגדולים ענט"י <i data-commentator="Mystery" data-order="1.1"></i>""" +
            """<i data-commentator="Beit Yosef" data-order="2.1"></i>וי"א שבין לגדולים"""

    @Test
    fun theLinePassagesCarryTheirCommentaries() {
        val annotated = buildAnnotatedFromHtml(tur, 16f, anchorCommentators = ANCHORED_COMMENTATORS)
        val segments = annotated.commentSegments()

        assertEquals(
            listOf(
                "וכל היום כשעושה צרכיו" to setOf(CommentKey("ב\"ח", 1), CommentKey("בית יוסף", 1)),
                "י\"א שלקטנים מברך אשר יצר" to setOf(CommentKey("דרישה", 1)),
                "ולגדולים ענט\"י" to setOf(CommentKey("פרישה", 2)),
                // An unknown commentator's anchor shares the spot of a known one's: only the known one remains
                "וי\"א שבין לגדולים" to setOf(CommentKey("בית יוסף", 2)),
            ),
            segments.map { annotated.text.substring(it.start, it.end).trim() to it.keys },
        )
        assertEquals(segments[2], segments.at(annotated.text.indexOf("ענט")))
    }

    @Test
    fun aCommentKeyComesFromItsReference() {
        val base = "טור, אורח חיים,  ז, א"
        assertEquals(CommentKey("בית יוסף", 2), commentKeyOf(base, "בית יוסף, אורח חיים,  ז, ב, א", "בית יוסף"))
        assertEquals(CommentKey("פרישה", 15), commentKeyOf(base, "פרישה, אורח חיים,  ז, טו, ג", "פרישה"))
        // Another siman, or another part, is not this line's
        assertNull(commentKeyOf(base, "בית יוסף, אורח חיים,  ח, ב, א", "בית יוסף"))
        assertNull(commentKeyOf(base, "בית יוסף, יורה דעה,  ז, ב, א", "בית יוסף"))
        // In חושן משפט the Beit Yosef is numbered by סעיף: no key, while the Prisha keeps its order
        assertNull(commentKeyOf("טור, חושן משפט,  א, ד", "בית יוסף, חושן משפט,  א, ד, א", "בית יוסף"))
        assertEquals(CommentKey("פרישה", 4), commentKeyOf("טור, חושן משפט,  א, ד", "פרישה, חושן משפט,  א, ד, א", "פרישה"))
    }
}
