package io.github.kdroidfilter.seforimapp.core.presentation.text

/** Characters Markdown lets a backslash escape (CommonMark: any ASCII punctuation; these are the ones that matter here). */
private const val ESCAPABLE = "\\`*_{}[]()#+-.!>~|<"

/** Private-use base: an escaped or code-span character is parked at [PROTECT_BASE] + its code until the very end. */
private const val PROTECT_BASE = 0xE000

private val ESCAPE = Regex("""\\([\\`*_{}\[\]()#+\-.!>~|<])""")
private val CODE_SPAN = Regex("""(`+)(.+?)\1""")
private val FENCE = Regex("""^\s*(```|~~~).*$""")
private val HORIZONTAL_RULE = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
private val BLOCKQUOTE = Regex("""^\s*(>\s?)+""")
private val HEADING = Regex("""^\s*#{1,6}(\s+|$)""")
private val CLOSING_HASHES = Regex("""\s+#+\s*$""")
private val LIST_MARKER = Regex("""^\s*([-*+]|\d{1,9}[.)])\s+""")
private val TASK_BOX = Regex("""^\[[ xX]]\s+""")
private val IMAGE = Regex("""!\[([^\]]*)]\([^)]*\)""")
private val LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
private val AUTOLINK = Regex("""<((?:https?|mailto):[^>\s]+)>""")
private val STRONG_STARS = Regex("""\*\*(.+?)\*\*""")
private val EM_STAR = Regex("""\*(\S(?:.*?\S)?)\*""")

// Underscore emphasis only counts at word boundaries, so snake_case (or Hebrew joined by "_") stays intact.
private val STRONG_UNDERSCORES = Regex("""(?<![\p{L}\p{N}])__(.+?)__(?![\p{L}\p{N}])""")
private val EM_UNDERSCORE = Regex("""(?<![\p{L}\p{N}])_(\S(?:.*?\S)?)_(?![\p{L}\p{N}])""")
private val STRIKETHROUGH = Regex("""~~(.+?)~~""")
private val WHITESPACE = Regex("""\s+""")

/**
 * Flattens a Markdown note to a single line of plain text, for places that show it as a title or a
 * menu entry: drops headings, emphasis (`*`, `_`, `**`, `__`, `~~`), inline code, link and image syntax
 * (keeping the visible text), list markers, task boxes, blockquotes, fences and horizontal rules, then
 * joins the remaining lines with single spaces. Backslash escapes resolve to the literal character.
 */
fun markdownToPlainText(markdown: String): String {
    if (markdown.isBlank()) return ""
    val protected = ESCAPE.replace(markdown) { protect(it.groupValues[1]) }
    return protected
        .lineSequence()
        .filterNot { FENCE.matches(it) || HORIZONTAL_RULE.matches(it) }
        .map(::stripLine)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .replace(WHITESPACE, " ")
        .trim()
        .let(::unprotect)
}

private fun stripLine(line: String): String {
    // Code spans first: their content is literal, so park it out of reach of the emphasis rules.
    var text = CODE_SPAN.replace(line) { protect(it.groupValues[2].trim()) }
    text = BLOCKQUOTE.replace(text, "")
    if (HEADING.containsMatchIn(text)) {
        text = CLOSING_HASHES.replace(HEADING.replace(text, ""), "")
    }
    text = LIST_MARKER.replace(text, "")
    text = TASK_BOX.replace(text, "")
    text = IMAGE.replace(text) { it.groupValues[1] }
    text = LINK.replace(text) { it.groupValues[1] }
    text = AUTOLINK.replace(text) { it.groupValues[1] }
    text = STRONG_STARS.replace(text) { it.groupValues[1] }
    text = STRONG_UNDERSCORES.replace(text) { it.groupValues[1] }
    text = EM_STAR.replace(text) { it.groupValues[1] }
    text = EM_UNDERSCORE.replace(text) { it.groupValues[1] }
    text = STRIKETHROUGH.replace(text) { it.groupValues[1] }
    return text
}

/** Maps Markdown punctuation in [text] to private-use characters, so no later rule treats it as syntax. */
private fun protect(text: String): String =
    buildString(text.length) {
        for (c in text) {
            append(if (c in ESCAPABLE) (PROTECT_BASE + c.code).toChar() else c)
        }
    }

private fun unprotect(text: String): String =
    buildString(text.length) {
        for (c in text) {
            val original = c.code - PROTECT_BASE
            append(if (original in 0..0x7F && original.toChar() in ESCAPABLE) original.toChar() else c)
        }
    }
