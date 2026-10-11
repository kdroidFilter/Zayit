package io.github.kdroidfilter.seforimapp.core

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import org.jsoup.Jsoup
import java.net.URLEncoder

private const val SEFARIA_SOURCE = "Sefaria"
private const val SEFARIA_CORRECTIONS_EMAIL = "corrections@sefaria.org"
private const val OTZARIA_CORRECTIONS_EMAIL = "otzaria.200@gmail.com"

// Mail clients and OS URL handlers cap a mailto's length, and every Hebrew letter or mark takes 6 encoded characters
private const val MAX_QUOTED_CHARS = 400

/**
 * The mailto reporting an error in [book] at [lines] (the selection's first and last lines), quoting [selectedText] or,
 * without one, the first line. Sefaria's books go to Sefaria with a pre-filled message for the user to complete;
 * every other library (WikiSource, Ben-Yehuda, Dicta, …) came through Otzaria and goes to its team.
 */
suspend fun bookErrorReportMailto(
    repository: SeforimRepository,
    book: Book,
    rootTitle: String?,
    lines: Pair<Line, Line>?,
    selectedText: String,
): String {
    val reference =
        lines?.let { formatHebrewSourceReference(book, rootTitle, it.first, it.second) }
            ?: book.heRef?.takeIf { it.isNotBlank() }
            ?: book.title
    if (repository.getSourceById(book.sourceId)?.name != SEFARIA_SOURCE) {
        return mailto(OTZARIA_CORRECTIONS_EMAIL, "טעות ב$reference", body = null)
    }
    val quoted = selectedText.trim().ifEmpty { lines?.first?.let { Jsoup.parse(it.content).text() }.orEmpty() }
    val versions =
        repository
            .getBookEditions(book.id)
            .map { it.title }
            .distinct()
    return mailto(SEFARIA_CORRECTIONS_EMAIL, "טעות ב$reference", sefariaReportBody(reference, versions, quoted))
}

/** The message to Sefaria: where the error is, what the text reads, and blanks for the user to fill. */
internal fun sefariaReportBody(
    reference: String,
    versions: List<String>,
    quoted: String,
): String =
    buildString {
        appendLine("שלום לצוות ספריא,")
        appendLine()
        appendLine("מצאתי טעות בטקסט הבא.")
        appendLine()
        appendLine("מקור: $reference")
        if (versions.isNotEmpty()) appendLine("מהדורה: ${versions.joinToString(" / ")}")
        appendLine()
        appendLine("הנוסח הנוכחי:")
        appendLine(if (quoted.length > MAX_QUOTED_CHARS) quoted.take(MAX_QUOTED_CHARS) + "…" else quoted)
        appendLine()
        appendLine("הטעות:")
        appendLine("[נא לתאר כאן את הטעות]")
        appendLine()
        appendLine("התיקון המוצע:")
        appendLine("[נא לכתוב כאן את הנוסח הנכון]")
        appendLine()
        append("נשלח מתוך זית")
    }

private fun mailto(
    email: String,
    subject: String,
    body: String?,
): String {
    val query = listOfNotNull("subject" to subject, body?.let { "body" to it })
    return "mailto:$email?" + query.joinToString("&") { (key, value) -> "$key=${encode(value)}" }
}

// URLEncoder writes spaces as '+', which mail clients keep verbatim in a mailto
private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
