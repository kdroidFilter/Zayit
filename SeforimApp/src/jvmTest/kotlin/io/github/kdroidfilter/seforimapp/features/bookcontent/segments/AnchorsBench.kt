package io.github.kdroidfilter.seforimapp.features.bookcontent.segments

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.presentation.text.forEachRowSpan
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.ContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.withPassageOfItsSelection
import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test

/**
 * Cost of the commentary passages on the reader's paths, on the installed DB: parsing with and without the anchors,
 * the passages of a line, the trays' geometry per hovered frame, the passage invariant, the commentaries' keys.
 * Opt-in: BENCH_ANCHORS=1 (and SEFORIMAPP_DATABASE_PATH); writes BENCH_OUT (default /tmp/anchors-bench.txt).
 */
class AnchorsBench {
    private val anchor = Regex("<i data-commentator=\"[^\"]*\" data-order=\"[^\"]*\"></i>")

    @Test
    fun bench() {
        if (System.getenv("BENCH_ANCHORS") == null) return
        val dbPath = System.getenv("SEFORIMAPP_DATABASE_PATH") ?: error("SEFORIMAPP_DATABASE_PATH")
        val out = StringBuilder()

        fun log(s: String) = out.appendLine(s).also { println(s) }
        val repository = SeforimRepository(dbPath, PersistentSqliteDriver("jdbc:sqlite:$dbPath"))
        runBlocking {
            for ((title, bookId) in listOf("Tur" to 380L, "SA OC" to 382L)) {
                val lines = repository.getLines(bookId, 0, 4000)
                val raw = lines.map { it.content }
                val stripped = raw.map { it.replace(anchor, "") }
                val anchored = raw.count { it.contains("data-commentator") }
                log("== $title: ${lines.size} lines, $anchored with anchors, ${raw.sumOf { it.length } / lines.size} chars avg")
                val parseRaw = perLine(raw) { buildAnnotatedFromHtml(it, 20f, anchorCommentators = ANCHORED_COMMENTATORS) }
                val parseStripped = perLine(stripped) { buildAnnotatedFromHtml(it, 20f, anchorCommentators = ANCHORED_COMMENTATORS) }
                log(
                    "parse   with anchors %.1f us/line, without %.1f us/line (+%.1f us, %+.0f%%)".format(
                        parseRaw,
                        parseStripped,
                        parseRaw - parseStripped,
                        100 * (parseRaw / parseStripped - 1),
                    ),
                )
                val markup = raw.sumOf { it.length } - stripped.sumOf { it.length }
                log(
                    "anchor markup: %.0f chars/line (%.0f%% of the HTML)".format(
                        markup.toDouble() / lines.size,
                        100.0 * markup / raw.sumOf { it.length },
                    ),
                )
                val jsoupRaw = perLine(raw) { org.jsoup.Jsoup.parse(it) }
                val jsoupStripped = perLine(stripped) { org.jsoup.Jsoup.parse(it) }
                val parserRaw =
                    perLine(raw) {
                        io.github.kdroidfilter.seforim.htmlparser
                            .HtmlParser()
                            .parse(it)
                    }
                val parserStripped =
                    perLine(stripped) {
                        io.github.kdroidfilter.seforim.htmlparser
                            .HtmlParser()
                            .parse(it)
                    }
                log(
                    "  jsoup %.1f vs %.1f us; HtmlParser.parse (jsoup incl.) %.1f vs %.1f us".format(
                        jsoupRaw,
                        jsoupStripped,
                        parserRaw,
                        parserStripped,
                    ),
                )
                val annotated = raw.map { buildAnnotatedFromHtml(it, 20f, anchorCommentators = ANCHORED_COMMENTATORS) }
                val segs = perLine(annotated) { it.commentSegments() }
                log("commentSegments %.2f us/line".format(segs))

                // Trays' geometry on a real layout: all passages of a line, as on a hovered frame
                val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Rtl)
                val sample = annotated.filter { it.length > 600 }.take(200)
                val layouts =
                    sample.map {
                        measurer.measure(it, TextStyle(fontSize = 20.sp), constraints = Constraints(maxWidth = 900))
                    }
                val segmentsOf = sample.map { it.commentSegments() }
                val frame =
                    perLine(layouts.indices.toList()) { i ->
                        val layout = layouts[i]
                        var acc = 0f
                        for (segment in segmentsOf[i]) {
                            layout.forEachRowSpan(segment.start, segment.end) { _, _, _, a, b -> acc += a + b }
                        }
                        acc
                    }
                val hit =
                    perLine(layouts.indices.toList()) { i ->
                        segmentsOf[i].at(layouts[i].getOffsetForPosition(Offset(450f, 30f)))
                    }
                log(
                    "hover frame: trays geometry %.1f us/line (%.1f passages avg), hit test %.2f us".format(
                        frame,
                        segmentsOf.sumOf { it.size }.toDouble() / sample.size,
                        hit,
                    ),
                )

                // Commentaries' keys of one line (the pane's query + mapping), on the first anchored lines
                val withLinks = lines.filter { it.content.contains("data-commentator") }.take(50)
                var queried = 0
                val t = System.nanoTime()
                for (line in withLinks) {
                    val links = repository.getCommentariesForLines(listOf(line.id)).take(60)
                    val targets = repository.getLinesByIds(links.map { it.link.targetLineId } + line.id).associateBy { it.id }
                    links.forEach { commentKeyOf(line.heRef, targets[it.link.targetLineId]?.heRef, it.targetBookTitle) }
                    queried += links.size
                }
                log(
                    "keys: %.2f ms/line (%d commentaries avg)".format(
                        (System.nanoTime() - t) / 1e6 / withLinks.size,
                        queried / withLinks.size,
                    ),
                )
            }

            // The invariant on every state write while a passage is selected
            val line = repository.getLines(380L, 25, 26).first()
            val section = repository.getLines(380L, 0, 128).toSet()
            val passage = SelectedPassage(line.id, setOf(CommentKey("פרישה", 2)))
            for ((label, lines) in listOf("1 line" to setOf(line), "128-line section" to section)) {
                val before =
                    BookContentState(
                        content = ContentState(selectedLines = lines, primarySelectedLineId = line.id, selectedPassage = passage),
                    )
                val sameSet = before.copy(content = before.content.copy(scrollIndex = 3))
                val newSet = before.copy(content = before.content.copy(selectedLines = lines.toSet(), scrollIndex = 3))
                val a = perLine(List(20_000) { it }) { sameSet.withPassageOfItsSelection(before) }
                val b = perLine(List(2_000) { it }) { newSet.withPassageOfItsSelection(before) }
                log("invariant ($label): same set %.3f us, equal new set %.2f us".format(a, b))
            }
        }
        File(System.getenv("BENCH_OUT") ?: "/tmp/anchors-bench.txt").writeText(out.toString())
    }

    // Median over rounds of the mean time per item, in microseconds, after a warm-up
    private fun <T> perLine(
        items: List<T>,
        block: (T) -> Any?,
    ): Double {
        repeat(3) { items.forEach { block(it) } }
        val rounds =
            List(7) {
                val t = System.nanoTime()
                items.forEach { block(it) }
                (System.nanoTime() - t) / 1e3 / items.size
            }
        return rounds.sorted()[rounds.size / 2]
    }
}
