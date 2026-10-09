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
