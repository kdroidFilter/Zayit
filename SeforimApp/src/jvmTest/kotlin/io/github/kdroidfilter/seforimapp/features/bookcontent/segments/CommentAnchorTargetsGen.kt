package io.github.kdroidfilter.seforimapp.features.bookcontent.segments

import io.github.kdroidfilter.seforim.htmlparser.anchorOrder
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.ReferenceParser
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test

/**
 * Resolves the base texts' commentary anchors to their commentary lines, against the DB, and writes one
 * [BookAnchorTargets] per base book under the app's resources. Opt-in: BUILD_COMMENT_ANCHORS=1 with
 * SEFORIMAPP_DATABASE_PATH; ANCHORS_OUT overrides the output folder; the report goes to ANCHORS_REPORT.
 *
 * For each (base book, commentator as the anchors name it), among the books its anchored lines link to, the rule
 * that resolves the most anchors to exactly one commentary: the commentary's reference has the anchor's number at
 * position k, and (when it helps) at k-1 the same part as the base line's part j (its siman). A pair is kept, per
 * part of the base book, only when 90 % of its anchors resolve so; then only its anchors resolved to one commentary
 * are written, with all the lines of that commentary the base line links to. A target book must also be the one
 * [COMMENTATOR_BOOKS] names for the commentator (the structure proves the numbering, the name the book): a name
 * without an entry, or a book that does not answer it, is never linked. Nothing doubtful is written.
 */
class CommentAnchorTargetsGen {
    private val anchorTag = Regex("<i (data-commentator=\"[^\"]*\"[^>]*)>")
    private val nameAttr = Regex("data-commentator=\"([^\"]*)\"")
    private val orderAttr = Regex("data-order=\"([^\"]*)\"")
    private val labelAttr = Regex("data-label=\"([^\"]*)\"")

    private class Anchor(
        val lineId: Long,
        val order: Int,
        // The first words after it, to check a resolution against the commentary's opening words
        val following: List<String>,
    )

    // k >= 0: the anchor's number at position k of the commentary's reference (and, with j, the base's part j at k-1);
    // k = OPENING: the commentary line opens on the number, as "(12)" (notes numbered through a chapter)
    private data class Rule(
        val k: Int,
        val j: Int?,
    )

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun generate() {
        if (System.getenv("BUILD_COMMENT_ANCHORS") == null) return
        val dbPath = System.getenv("SEFORIMAPP_DATABASE_PATH") ?: error("SEFORIMAPP_DATABASE_PATH")
        val outDir = File(System.getenv("ANCHORS_OUT") ?: "src/jvmMain/resources$RESOURCE_DIR").apply { mkdirs() }
        val report = StringBuilder()
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { db ->
            val titles = db.query("select id, title from book") { it.getLong(1) to it.getString(2) }.toMap()

            // Anchors by (base book, commentator), and the base lines' references
            val pairs = HashMap<Pair<Long, String>, MutableList<Anchor>>()
            val baseRefs = HashMap<Long, List<String>>()
            db.query("select id, bookId, heRef, content from line where content like '%data-commentator%'") { rs ->
                val lineId = rs.getLong(1)
                baseRefs[lineId] = refParts(rs.getString(3))
                val content = rs.getString(4)
                for (m in anchorTag.findAll(content)) {
                    val attrs = m.groupValues[1]
                    val name = nameAttr.find(attrs)!!.groupValues[1]
                    val order =
                        anchorOrder(orderAttr.find(attrs)?.groupValues?.get(1), labelAttr.find(attrs)?.groupValues?.get(1))
                            ?: continue
                    val following = words(content.substring(m.range.last + 1, minOf(content.length, m.range.last + 400))).take(3)
                    pairs.getOrPut(rs.getLong(2) to name) { mutableListOf() } += Anchor(lineId, order, following)
                }
            }

            // What the anchored lines link to, and those lines' references
            val links = HashMap<Long, HashMap<Long, MutableList<Long>>>()
            baseRefs.keys.chunked(900).forEach { chunk ->
                db.query("select sourceLineId, targetBookId, targetLineId from link where sourceLineId in (${chunk.joinToString(",")})") {
                    links.getOrPut(it.getLong(1)) { HashMap() }.getOrPut(it.getLong(2)) { mutableListOf() } += it.getLong(3)
                }
            }
            val targetRefs = HashMap<Long, List<String>>()
            val openingNumbers = HashMap<Long, Int>()
            links.values
                .flatMap { it.values.flatten() }
                .distinct()
                .chunked(900)
                .forEach { chunk ->
                    db.query("select id, heRef, substr(content, 1, 40) from line where id in (${chunk.joinToString(",")})") {
                        targetRefs[it.getLong(1)] = refParts(it.getString(2))
                        openingNumber(it.getString(3))?.let { n -> openingNumbers[it.getLong(1)] = n }
                    }
                }

            // The opening words of commentary lines, for the report's check
            fun openingsOf(ids: Collection<Long>): Map<Long, List<String>> {
                val out = HashMap<Long, List<String>>()
                ids.chunked(900).forEach { chunk ->
                    db.query("select id, substr(content, 1, 300) from line where id in (${chunk.joinToString(",")})") {
                        out[it.getLong(1)] = words(it.getString(2)).take(8)
                    }
                }
                return out
            }

            // The commentaries (by reference up to k) a rule finds for an anchor, with their lines linked from the base line
            fun matches(
                anchor: Anchor,
                book: Long,
                rule: Rule,
            ): Map<List<String>, List<Long>> {
                val base = baseRefs[anchor.lineId].orEmpty()
                val linked = links[anchor.lineId]?.get(book).orEmpty()
                if (rule.k == OPENING) return linked.filter { openingNumbers[it] == anchor.order }.groupBy { listOf(it.toString()) }
                return linked
                    .filter { line ->
                        val ref = targetRefs[line].orEmpty()
                        ref.size > rule.k &&
                            numeralValue(ref[rule.k]) == anchor.order &&
                            (rule.j == null || (rule.k > 0 && base.getOrNull(rule.j) == ref[rule.k - 1]))
                    }.groupBy { targetRefs[it].orEmpty().subList(0, rule.k + 1) }
            }

            val byBook = HashMap<Long, MutableList<Triple<String, Long, List<Pair<Anchor, List<Long>>>>>>()
            var total = 0
            var written = 0
            for ((key, anchors) in pairs.entries.sortedByDescending { it.value.size }) {
                val (bookId, name) = key
                total += anchors.size
                val names = COMMENTATOR_BOOKS[name]
                val candidates =
                    anchors
                        .flatMap { links[it.lineId]?.keys.orEmpty() }
                        .filter { book -> names != null && titles[book]?.let { title -> names.any { it.matches(title) } } == true }
                        .groupingBy { it }
                        .eachCount()
                        .entries
                        .sortedByDescending { it.value }
                        .take(12)
                        .map { it.key }
                val sample = anchors.filterIndexed { i, _ -> i % maxOf(1, anchors.size / 1500) == 0 }
                var best: Triple<Long, Rule, Int>? = null
                for (book in candidates) {
                    val rules = HashSet<Rule>()
                    if (sample.any { a -> links[a.lineId]?.get(book).orEmpty().any { openingNumbers[it] == a.order } }) {
                        rules +=
                            Rule(OPENING, null)
                    }
                    for (anchor in sample.take(300)) {
                        val base = baseRefs[anchor.lineId].orEmpty()
                        for (line in links[anchor.lineId]?.get(book).orEmpty()) {
                            val ref = targetRefs[line].orEmpty()
                            ref.forEachIndexed { k, part ->
                                if (numeralValue(part) != anchor.order) return@forEachIndexed
                                rules += Rule(k, null)
                                if (k > 0) base.forEachIndexed { j, b -> if (b == ref[k - 1]) rules += Rule(k, j) }
                            }
                        }
                    }
                    for (rule in rules) {
                        val exact = sample.count { matches(it, book, rule).size == 1 }
                        val current = best
                        val better =
                            current == null ||
                                exact > current.third ||
                                (exact == current.third && rule.j != null && current.second.j == null)
                        if (better) best = Triple(book, rule, exact)
                    }
                }
                val (target, rule) = best?.let { it.first to it.second } ?: (null to null)
                if (target == null || rule == null) {
                    val why = if (names == null) "no book named for this commentator" else "no linked book of that name"
                    report.appendLine("${anchors.size}\t${titles[bookId]}\t$name\t-\t$why")
                    continue
                }
                // Per part of the base book (the Tur's four): kept only where the rule holds
                val resolved = anchors.map { it to matches(it, target, rule) }
                val kept = mutableListOf<Pair<Anchor, List<Long>>>()
                val parts = resolved.groupBy { (a, _) -> partOf(baseRefs[a.lineId].orEmpty()) }
                val partReport = StringBuilder()
                for ((part, inPart) in parts) {
                    val exact = inPart.filter { it.second.size == 1 }
                    val rate = exact.size.toDouble() / inPart.size
                    partReport.append("$part=${"%.3f".format(rate)}(${inPart.size}) ")
                    if (rate >= MIN_RATE) kept += exact.map { (a, m) -> a to m.values.single().sorted() }
                }
                written += kept.size
                // Of the kept anchors, the share whose commentary opens on the words after the anchor
                val opening =
                    if (kept.isEmpty()) {
                        0.0
                    } else {
                        val openings = openingsOf(kept.map { it.second.first() }.toSet())
                        kept.count { (a, lines) -> opensOn(openings[lines.first()].orEmpty(), a.following) }.toDouble() / kept.size
                    }
                report.appendLine(
                    "${anchors.size}\t${titles[bookId]}\t$name\t${titles[target]}\tk=${rule.k} j=${rule.j}\tkept=${kept.size}\t" +
                        "opening=${"%.2f".format(opening)}\t$partReport",
                )
                if (kept.isNotEmpty()) byBook.getOrPut(bookId) { mutableListOf() } += Triple(name, target, kept)
            }

            outDir.listFiles { f -> f.name.endsWith(".pb") }?.forEach { it.delete() }
            for ((bookId, entries) in byBook) {
                val commentators = entries.map { it.first }
                val lines =
                    entries
                        .flatMapIndexed { index, (_, _, kept) ->
                            kept.map { (a, targets) ->
                                a.lineId to
                                    AnchorTargets(index, a.order, targets)
                            }
                        }.groupBy({ it.first }, { it.second })
                        .toSortedMap()
                        .map { (lineId, anchors) ->
                            LineAnchorTargets(lineId, anchors.sortedWith(compareBy({ it.commentator }, { it.order })))
                        }
                val table = BookAnchorTargets(commentators, entries.map { it.second }, lines)
                File(outDir, "$bookId.pb").writeBytes(ProtoBuf.encodeToByteArray(BookAnchorTargets.serializer(), table))
            }
            report.insert(0, "anchors $total, written $written (${"%.1f".format(100.0 * written / total)} %), books ${byBook.size}\n")
        }
        File(System.getenv("ANCHORS_REPORT") ?: "/tmp/comment-anchors-report.tsv").writeText(report.toString())
        println(report.lineSequence().first())
    }

    private fun refParts(heRef: String?): List<String> =
        heRef
            ?.split(',')
            ?.drop(1)
            ?.map { it.trim() }
            .orEmpty()

    // The named part a base line belongs to (the Tur's אורח חיים, also in "אורח חיים א" where the siman follows), or ""
    private fun partOf(ref: List<String>): String {
        val words =
            ref
                .firstOrNull()
                ?.split(' ')
                .orEmpty()
                .toMutableList()
        while (words.isNotEmpty() && words.last().length <= 4 && numeralValue(words.last()) != null) words.removeAt(words.lastIndex)
        return words.joinToString(" ")
    }

    // Hebrew words of an HTML fragment, without nikud, marks or tags
    private fun words(html: String): List<String> =
        html
            .replace(Regex("<[^>]+>"), " ")
            .filter { it in 'א'..'ת' || it == ' ' }
            .split(' ')
            .filter { it.isNotEmpty() }

    // A commentary opening (its first words, maybe after a lead-in like "ומ"ש רבינו") on the words after the anchor
    private fun opensOn(
        opening: List<String>,
        following: List<String>,
    ): Boolean {
        if (following.isEmpty() || opening.isEmpty()) return false
        val first = following.first()
        return opening.take(5).any { it == first || (first.length >= 3 && it.length >= 3 && it.take(3) == first.take(3)) }
    }

    // The number a commentary line opens on: "(12)", "[12]", "12." or "(יב)"; a bare Hebrew word is no number
    private fun openingNumber(opening: String?): Int? {
        val text = opening?.replace(Regex("<[^>]+>"), "")?.trimStart() ?: return null
        Regex("^[(\\[]?\\s*(\\d{1,4})\\s*[)\\].]").find(text)?.let { return it.groupValues[1].toInt() }
        Regex("^[(\\[]\\s*([א-ת\"'״׳]{1,5})\\s*[)\\]]").find(text)?.let { return numeralValue(it.groupValues[1]) }
        return null
    }

    // A reference part's number: digits, or a Hebrew numeral written canonically (a word like חיים is no number)
    private fun numeralValue(part: String): Int? {
        val value = part.filter { it !in "\"'״׳ " }
        if (value.isNotEmpty() && value.all { it in '0'..'9' }) return value.toIntOrNull()
        return ReferenceParser.hebrewNumeralValue(value)
    }

    private fun <T> java.sql.Connection.query(
        sql: String,
        row: (java.sql.ResultSet) -> T,
    ): List<T> =
        createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                buildList { while (rs.next()) add(row(rs)) }
            }
        }

    // A commentary book named by a title fragment: its title starts with it, or (exact) is it
    private class Named(
        val fragment: String,
        val exact: Boolean = false,
    ) {
        fun matches(title: String): Boolean = if (exact) title == fragment else title.startsWith(fragment)
    }

    private companion object {
        const val MIN_RATE = 0.9
        const val OPENING = -1

        private fun named(vararg fragments: String) = fragments.map { Named(it) }

        private fun exactly(title: String) = listOf(Named(title, exact = true))

        /**
         * The commentary book(s) each commentator, as the anchors name it, may lead to: checked by hand against the
         * DB's titles. Names absent here (Variants, Notes, Endnotes, Mystery, the Tur's Hagahot, Guggenheimer's
         * notes…) have no book of theirs in the DB, or none that can be told; their anchors stay unlinked.
         */
        val COMMENTATOR_BOOKS: Map<String, List<Named>> =
            mapOf(
                "Ateret Zekenim" to named("עטרת זקנים"),
                "Bach" to exactly("ב\"ח"),
                "Ba'er Hetev" to named("באר היטב"),
                "Be'er HaGolah" to named("באר הגולה על"),
                "Beit Shmuel" to named("בית שמואל"),
                "Beit Yosef" to exactly("בית יוסף"),
                "Beit_Yosef" to exactly("בית יוסף"),
                "Beur HaGra" to named("ביאור הגר\"א"),
                "Beur HaRe'em" to named("באור הרא\"ם"),
                "Binat Adam" to named("בינת אדם"),
                "Chelkat Mechokek" to named("חלקת מחוקק"),
                "Chiddushei Anshei Shem" to named("חידושי אנשי שם"),
                "Chiddushei Hilkhot Niddah" to named("חדושי הלכות נדה"),
                "Chok Yaakov" to named("חק יעקב"),
                "Darkhei Moshe" to exactly("דרכי משה"),
                "Darchei Moshe" to exactly("דרכי משה"),
                "Drisha" to exactly("דרישה"),
                "Eshel Avraham" to named("אשל אברהם"),
                "Gra's Nuschah" to named("נוסחת הגר״א"),
                "Haamek Sheilah" to named("העמק שאלה"),
                "Hagahot Chavot Yair" to named("הגהות חוות יאיר"),
                "Hagahot HaBach" to named("הגהות הב\"ח"),
                "Hagahot MeAlfas Yashan" to named("הגהות מאלפס ישן"),
                "Hagahot Menachem Tzion" to named("הגהות מנחם ציון"),
                "Hagahot Sheerit Tzion" to named("הגהות שארית ציון"),
                "Haggahot" to named("הגהות על מסכת"),
                "Haggahot and Marei Mekomot" to named("הגהות ומראה מקומות"),
                "Haggahot Chadashot on Sefer Mitzvot Katan" to named("הגהות חדשות על ספר מצוות קטן"),
                "Haggahot of R' David Luria on Sefer HaParnas" to named("הגהות הרד\"ל"),
                "Haggahot R' Yeshaya Berlin" to named("הגהות הגרי״ב"),
                "Haggahot Rabbeinu Peretz on Sefer Mitzvot Katan" to named("הגהות רבנו פרץ"),
                "Hasagot HaRa'avad" to named("השגות הראב\"ד"),
                "IkkarTosfotYomTov" to named("עיקר תוספות יום טוב"),
                "Katuv Sham" to named("כתוב שם"),
                "Kereti" to named("כרתי"),
                "Ketzot HaChoshen" to named("קצות החושן"),
                "Korban Netanel" to named("קרבן נתנאל"),
                "Kuntres Acharon" to named("קונטרס אחרון"),
                "Lishkat HaSofer" to named("לשכת הסופר"),
                "Magen Avraham" to exactly("מגן אברהם"),
                "Mechokekei Yehudah; Karnei Ohr" to exactly("מחוקקי יהודה קרני אור"),
                "Mechokekei Yehudah; Yahel Ohr" to exactly("מחוקקי יהודה יהל אור"),
                "Me'irat Einayim" to named("מאירת עיניים"),
                "Mekor Mayim Chayim" to named("מקור מים חיים"),
                "Mesorat HaShas" to named("מסורת הש״ס"),
                "Mishnah Berurah" to exactly("משנה ברורה"),
                "Mitzpeh Etan" to named("מצפה איתן"),
                "Nekudat HaKesef" to named("נקודות הכסף"),
                "Nekudot HaKesef" to named("נקודות הכסף"),
                "Netiv Chesed" to named("נתיב חסד"),
                "Netivot HaMishpat, Beurim" to named("נתיבות המשפט, ביאורים"),
                "Netivot HaMishpat, Hidushim" to named("נתיבות המשפט, חידושים"),
                "New Nuschah" to named("נוסחא חדשה"),
                "Nishmat Adam" to exactly("נשמת אדם"),
                "Notes and Corrections" to named("הערות ותיקונים על"),
                "Notes and Corrections on Midrash Aggadah" to named("הערות ותיקונים על מדרש אגדה"),
                "Notes and Corrections on Midrash Lekach Tov" to named("הערות ותיקונים על מדרש לקח טוב"),
                "Notes by Rabbi Yehoshua Hartman" to named("פירושים והערות מהרב יהושע הרטמן", "הערות ומקורות על תפארת ישראל"),
                "Nuschaot Ktav Yad" to named("נוסחאות כתב יד"),
                "Peleti" to named("פלתי"),
                "Pithei Teshuva" to named("פתחי תשובה"),
                "Prisha" to exactly("פרישה"),
                "Rabbeinu Efrayim" to named("רבינו אפרים"),
                "Rishon Letzion" to named("ראשון לציון"),
                "Sela HaMachlakot" to named("סלע המחלקות"),
                "Sha'arei Teshuvah" to named("שערי תשובה"),
                "Sheilat Shalom" to named("שאילת שלום"),
                "Shiltei HaGiborim" to named("שלטי הגיבורים"),
                "Siftei Kohen" to named("שפתי כהן"),
                "Tiferet Shmuel" to named("תפארת שמואל"),
                "Toafot Re'em" to named("תועפות ראם"),
                "Torat HaShlamim" to named("תורת השלמים"),
                "Tur" to exactly("טור"),
                "Turei Zahav" to named("טורי זהב"),
                "Urim VeTumim, Tumim" to exactly("אורים ותומים, תומים"),
                "Urim VeTumim, Urim" to exactly("אורים ותומים, אורים"),
            )
    }
}
