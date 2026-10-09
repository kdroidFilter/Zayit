package io.github.kdroidfilter.seforimapp.features.search.domain

/**
 * How an author's name is shown: respectfully, never bare (`יוסף קארו` → `הרב יוסף קארו`).
 *
 * The database keeps the upstream (Sefaria, Otzaria) names untouched: the honorific is display only.
 * Acronyms (`רמב"ם`, `מהר"ם פדובה`: a quote in the first word) and names already carrying a title
 * (`רבי…`, `חכם…`) stay as they are; every other name gets `הרב`. Names stored the catalog way
 * (`ליבשיץ, יחזקאל`) are put back in reading order; nikud, bidi marks and extra spaces go.
 */
object AuthorNames {
    private val NIKUD = Regex("[֑-ׇ]")
    private val BIDI_MARKS = Regex("[\u200E\u200F\u202A-\u202E]")
    private val QUOTES = Regex("[\"״]")
    private val TITLES =
        listOf(
            "רבי",
            "רב",
            "רבן",
            "רבנו",
            "רבינו",
            "הרב",
            "ר'",
            "ר׳",
            "חכם",
            "החכם",
            "הגאון",
            "מרן",
            "מורנו",
            "האדמו",
        )
    private const val HONORIFIC = "הרב"

    fun display(raw: String): String {
        val name =
            readingOrder(
                raw
                    .replace(NIKUD, "")
                    .replace(BIDI_MARKS, "")
                    .replace(Regex("\\s+"), " ")
                    .trim(),
            )
        if (name.isEmpty() || isAcronym(name) || hasTitle(name)) return name
        return "$HONORIFIC $name"
    }

    /**
     * The alias a query was typed as (`חפץ חיים` for רבי ישראל מאיר הכהן), or null when it reads as
     * the [name] itself: every query word starts a word, as the lookup index matches them.
     */
    fun matchedAlias(
        query: String,
        name: String,
        aliases: List<String>,
    ): String? {
        val words = searchWords(withoutHonorific(query))
        if (words.isEmpty()) return null

        fun matches(candidate: String): Boolean {
            val candidateWords = searchWords(candidate)
            return words.all { word -> candidateWords.any { it.startsWith(word) } }
        }
        return if (matches(name)) null else aliases.firstOrNull(::matches)
    }

    private fun searchWords(text: String): List<String> =
        text
            .replace(NIKUD, "")
            .replace(BIDI_MARKS, "")
            .replace(Regex("[\"'״׳]"), "")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

    /** A query typed as the name is shown (`הרב יוסף`), without the display-only honorific. */
    fun withoutHonorific(query: String): String {
        val trimmed = query.trimStart()
        return if (trimmed.startsWith("$HONORIFIC ")) trimmed.removePrefix(HONORIFIC).trimStart() else query
    }

    // `רמב"ם`, `מהר"ם פדובה`; but not `יוסף בן משה באב"ד`, whose quote is in a later word
    private fun isAcronym(name: String): Boolean = QUOTES.containsMatchIn(name.substringBefore(' '))

    private fun hasTitle(name: String): Boolean =
        TITLES.any { title ->
            name == title ||
                name.startsWith("$title ") ||
                name.startsWith(title + "\"")
        }

    // `ליבשיץ, יחזקאל בן הילל` → `יחזקאל בן הילל ליבשיץ`: a short family name before a single comma
    private fun readingOrder(name: String): String {
        val parts = name.split(", ")
        if (parts.size != 2 || parts[0].split(' ').size > 2 || parts[1].isBlank()) return name
        return "${parts[1]} ${parts[0]}"
    }
}
