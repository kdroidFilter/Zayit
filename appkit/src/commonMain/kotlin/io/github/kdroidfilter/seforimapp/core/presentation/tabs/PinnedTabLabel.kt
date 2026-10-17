package io.github.kdroidfilter.seforimapp.core.presentation.tabs

/**
 * What a pinned book tab shows instead of the book icon, which every book shares: the book's
 * acronym (ראשי תיבות), else its title. The acronym table also holds search variants
 * ("משנב", "רמבם הלכות שבת"), so a real acronym — one with a gershayim — is preferred, single
 * word first, the shortest of them.
 */
fun pinnedTabLabel(
    bookTitle: String,
    acronyms: List<String>,
): String {
    val marked = acronyms.map(String::trim).filter { '"' in it || '״' in it }
    return (marked.filter { ' ' !in it }.ifEmpty { marked }).minByOrNull(String::length) ?: bookTitle
}
