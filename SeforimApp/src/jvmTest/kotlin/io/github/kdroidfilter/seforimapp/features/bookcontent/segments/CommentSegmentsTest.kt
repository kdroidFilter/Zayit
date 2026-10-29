package io.github.kdroidfilter.seforimapp.features.bookcontent.segments

import io.github.kdroidfilter.seforim.htmlparser.CommentAnchor
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
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
    private val turCommentators = setOf("Beit Yosef", "Bach", "Prisha", "Drisha")

    @Test
    fun theLinePassagesCarryTheirAnchors() {
        val annotated = buildAnnotatedFromHtml(tur, 16f, anchorCommentators = turCommentators)
        val segments = annotated.commentSegments()

        assertEquals(
            listOf(
                "וכל היום כשעושה צרכיו" to setOf(CommentAnchor("Bach", 1), CommentAnchor("Beit Yosef", 1)),
                "י\"א שלקטנים מברך אשר יצר" to setOf(CommentAnchor("Drisha", 1)),
                "ולגדולים ענט\"י" to setOf(CommentAnchor("Prisha", 2)),
                // A commentator not asked for leaves no anchor: the Beit Yosef's shares its spot alone
                "וי\"א שבין לגדולים" to setOf(CommentAnchor("Beit Yosef", 2)),
            ),
            segments.map { annotated.text.substring(it.start, it.end).trim() to it.anchors },
        )
        assertEquals(segments[2], segments.at(annotated.text.indexOf("ענט")))
    }

    @Test
    fun aPassageLeadsToTheCommentaryLinesOfItsOpenBooks() {
        val segment = CommentSegment(0, 5, setOf(CommentAnchor("Bach", 1), CommentAnchor("Beit Yosef", 1)))
        val targets =
            mapOf(
                CommentAnchor("Bach", 1) to AnchorTarget(bookId = 5059, lineIds = listOf(77)),
                CommentAnchor("Beit Yosef", 1) to AnchorTarget(bookId = 5062, lineIds = listOf(139, 140)),
            )
        assertEquals(
            SelectedPassage(7, segment.anchors, targetLineIds = setOf(77, 139, 140), targetBookIds = setOf(5059, 5062)),
            passageOf(7, segment, targets, openBooks = setOf(5059, 5062)),
        )
        // Only the open books' lines; none open, no passage
        assertEquals(setOf(77L), passageOf(7, segment, targets, openBooks = setOf(5059))?.targetLineIds)
        assertNull(passageOf(7, segment, targets, openBooks = setOf(1)))
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun aBookTableReadsBackAsItsAnchorsTargets() {
        val table =
            BookAnchorTargets(
                commentators = listOf("Bach", "Beit Yosef"),
                commentatorBooks = listOf(5059, 5062),
                lines = listOf(LineAnchorTargets(7, listOf(AnchorTargets(0, 1, listOf(77)), AnchorTargets(1, 2, listOf(141))))),
            )
        val bytes = ProtoBuf.encodeToByteArray(BookAnchorTargets.serializer(), table)
        val read = ProtoBuf.decodeFromByteArray(BookAnchorTargets.serializer(), bytes)
        assertEquals(listOf("Bach", "Beit Yosef"), read.commentators)
        assertEquals(
            listOf(141L),
            read.lines
                .single()
                .anchors[1]
                .targetLineIds,
        )
    }
}
