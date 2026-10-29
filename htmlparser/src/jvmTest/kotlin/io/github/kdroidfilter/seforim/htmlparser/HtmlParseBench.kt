package io.github.kdroidfilter.seforim.htmlparser

import java.io.File
import kotlin.test.Test

/**
 * buildAnnotatedFromHtml on exported book lines (one file per book, lines separated by U+0001), to compare two
 * versions of the parser on the same input. Opt-in: BENCH_HTML_DIR (with lines-*.txt); writes BENCH_OUT.
 */
class HtmlParseBench {
    @Test
    fun bench() {
        val dir = System.getenv("BENCH_HTML_DIR") ?: return
        val out = StringBuilder()
        // The reader's anchored commentators (the Tur's), when BENCH_ANCHORED is set; none otherwise
        val anchored =
            System
                .getenv(
                    "BENCH_ANCHORED",
                )?.let { setOf("Beit Yosef", "Beit_Yosef", "Bach", "Prisha", "Drisha", "Darkhei Moshe", "Darchei Moshe") }
                ?: emptySet()
        for (file in File(dir).listFiles { f -> f.name.startsWith("lines-") && f.name.endsWith(".txt") }.orEmpty().sorted()) {
            val lines = file.readText().split('\u0001')
            repeat(5) { lines.forEach { buildAnnotatedFromHtml(it, 20f, anchorCommentators = anchored) } }
            val rounds =
                List(9) {
                    val t = System.nanoTime()
                    lines.forEach { buildAnnotatedFromHtml(it, 20f, anchorCommentators = anchored) }
                    (System.nanoTime() - t) / 1e3 / lines.size
                }.sorted()
            out.appendLine("%s: median %.2f us/line (min %.2f, max %.2f)".format(file.name, rounds[4], rounds.first(), rounds.last()))
        }
        File(System.getenv("BENCH_OUT") ?: "/tmp/html-bench.txt").writeText(out.toString())
    }
}
