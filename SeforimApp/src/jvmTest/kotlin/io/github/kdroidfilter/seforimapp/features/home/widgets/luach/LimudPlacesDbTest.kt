package io.github.kdroidfilter.seforimapp.features.home.widgets.luach

import org.junit.Assume
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every limud of six years of days lands on a line of the library, the way the card opens it: its book by title, its
 * TOC headings, then the line of its reference. Needs a books DB: SeforimLibrary/build/seforim.db, or $SEFORIM_DB.
 */
class LimudPlacesDbTest {
    /** A book's TOC entries (id, parent, text), each one's line index, and its lines' references, spaces collapsed. */
    private class BookLines(
        val tocLines: List<Triple<Long, Long?, String>>,
        val tocIndex: Map<Long, Int>,
        val refs: List<String?>,
    )

    private fun openDb(): Connection {
        val path =
            (listOfNotNull(System.getenv("SEFORIM_DB")) + listOf("SeforimLibrary/build/seforim.db", "../SeforimLibrary/build/seforim.db"))
                .map(Path::of)
                .firstOrNull(Files::exists)
        Assume.assumeTrue("No books DB (SeforimLibrary/build/seforim.db or \$SEFORIM_DB)", path != null)
        return DriverManager.getConnection("jdbc:sqlite:$path")
    }

    @Test
    fun `every limud place is found in the library`() {
        openDb().use { db ->
            val books = mutableMapOf<String, Pair<Long, BookLines>?>()

            fun book(title: String) =
                books.getOrPut(title) {
                    val id =
                        db.prepareStatement("SELECT id FROM book WHERE title = ?").run {
                            setString(1, title)
                            executeQuery().takeIf { it.next() }?.getLong(1)
                        } ?: return@getOrPut null
                    val toc = mutableListOf<Triple<Long, Long?, String>>()
                    val tocIndex = mutableMapOf<Long, Int>()
                    db
                        .prepareStatement(
                            "SELECT t.id, t.parentId, tt.text, l.lineIndex FROM tocEntry t JOIN tocText tt ON tt.id = t.textId " +
                                "LEFT JOIN line l ON l.id = t.lineId WHERE t.bookId = ? ORDER BY t.id",
                        ).run {
                            setLong(1, id)
                            val rows = executeQuery()
                            while (rows.next()) {
                                val entry = rows.getLong(1)
                                toc += Triple(entry, rows.getLong(2).takeIf { !rows.wasNull() }, rows.getString(3))
                                tocIndex[entry] = rows.getInt(4)
                            }
                        }
                    val refs = mutableListOf<String?>()
                    db.prepareStatement("SELECT heRef FROM line WHERE bookId = ? ORDER BY lineIndex").run {
                        setLong(1, id)
                        val rows = executeQuery()
                        while (rows.next()) refs += rows.getString(1)?.replace(Regex("""\s+"""), " ")
                    }
                    id to BookLines(toc, tocIndex, refs)
                }

            val missing = sortedSetOf<String>()
            var date = LocalDate.of(2026, 1, 1)
            while (date.isBefore(LocalDate.of(2032, 1, 1))) {
                for (item in limudOfDay(date, inIsrael = false, shown = Limud.entries.toSet())) {
                    val place = item.place ?: continue
                    val lines = book(place.bookTitle)?.second
                    if (lines == null) {
                        missing += "${item.kicker}: no book ${place.bookTitle}"
                        continue
                    }
                    var entry: Triple<Long, Long?, String>? = null
                    var from = 0
                    for (heading in place.toc) {
                        val parent = entry?.first
                        entry = lines.tocLines.firstOrNull { (_, p, text) -> text == heading && (parent == null || p == parent) }
                        if (entry == null) {
                            missing += "${item.kicker}: no TOC ${place.toc} in ${place.bookTitle}"
                            break
                        }
                        from = lines.tocIndex.getValue(entry.first)
                    }

                    fun lineOf(
                        ref: String,
                        after: Int,
                    ) = (after until lines.refs.size).firstOrNull { lines.refs[it]?.let { r -> r == ref || r.startsWith("$ref,") } == true }
                    var start = from
                    // The library's edition ends או״ח קפט at its seif ז: the card opens the siman
                    place.ref?.takeIf { it !in EDITION_GAPS }?.let { ref ->
                        val at = lineOf(ref, from)
                        if (at == null) missing += "${item.kicker} ${item.value}: no line $ref" else start = at
                    }
                    // Where it ends, for the book to mark it: after its start, one of its alternatives
                    val ends = place.endRefs.filterNot { it in EDITION_GAPS }
                    if (ends.isNotEmpty() && ends.none { lineOf(it, start) != null }) {
                        missing += "${item.kicker} ${item.value}: no end line ${ends.first()}"
                    }
                    if (place.endTocs.isNotEmpty() &&
                        place.endTocs.none { heading ->
                            lines.tocLines.any { (id, p, text) -> text == heading && p == entry?.second && id >= (entry?.first ?: 0) }
                        }
                    ) {
                        missing += "${item.kicker} ${item.value}: no end TOC ${place.endTocs.first()}"
                    }
                }
                date = date.plusDays(1)
            }
            assertTrue(missing.isEmpty(), "${missing.size} places not found:\n" + missing.take(60).joinToString("\n"))
        }
    }

    private companion object {
        val EDITION_GAPS = setOf("ערוך השולחן, אורח חיים, קפט, ח")
    }
}
