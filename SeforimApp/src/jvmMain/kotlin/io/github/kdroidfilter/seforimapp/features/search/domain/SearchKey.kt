package io.github.kdroidfilter.seforimapp.features.search.domain

private val NIKUD = Regex("[\u0591-\u05C7]")
private val QUOTES = Regex("[\"'״׳]")
private val SPACES = Regex("\\s+")

/** A name as typed, to compare: without nikud, quotes and extra spaces (`שו"ע` = `שוע`). */
internal fun String.searchKey(): String =
    replace(NIKUD, "")
        .replace(QUOTES, "")
        .replace(SPACES, " ")
        .trim()
