package io.github.kdroidfilter.seforim.htmlparser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Unicode "object replacement character" used as placeholder text for inline images. */
const val INLINE_IMAGE_PLACEHOLDER: String = "\uFFFC"

/**
 * Where a commentary attaches to the text: an empty `<i data-commentator="Bach" data-order="2.1">` placed before the
 * words it explains. [order] is the comment's number in its section (the `2` of `2.1`).
 */
data class CommentAnchor(
    val commentator: String,
    val order: Int,
)

data class ParsedHtmlElement(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isSmall: Boolean = false,
    val isHeader: Boolean = false,
    val headerLevel: Int? = null,
    val isLineBreak: Boolean = false,
    val isFootnoteMarker: Boolean = false,
    val isFootnoteContent: Boolean = false,
    val imageBytes: ByteArray? = null,
    val imageMime: String? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    /** Target of a citation link (`<a class="ref" href="seforim://line/<id>">`), null for plain text. */
    val href: String? = null,
    /**
     * The anchors standing in this element's text, at their offset in it. An anchor leaves no text: it never splits
     * the words around it into two elements (two spans to lay out and draw).
     */
    val anchors: List<Pair<Int, CommentAnchor>> = emptyList(),
) {
    val isImage: Boolean get() = imageBytes != null
}

private val WHITESPACE_REGEX = Regex("\\s+")

/**
 * The elements parsed so far, and the anchors met since the last text, waiting for the text they stand before. Only
 * the anchors of [anchorCommentators] are kept: the others cost no more than skipping their empty tag.
 */
private class ParsedElements(
    val anchorCommentators: Set<String>,
) : ArrayList<ParsedHtmlElement>() {
    val pendingAnchors = mutableListOf<CommentAnchor>()

    fun takeAnchors(at: Int): List<Pair<Int, CommentAnchor>> =
        if (pendingAnchors.isEmpty()) emptyList() else pendingAnchors.map { at to it }.also { pendingAnchors.clear() }
}

class HtmlParser {
    /** Parses [html]; the commentary anchors of [anchorCommentators] are kept (none by default), the others skipped. */
    fun parse(
        html: String,
        anchorCommentators: Set<String> = emptySet(),
    ): List<ParsedHtmlElement> {
        val doc = Jsoup.parse(html)
        val out = ParsedElements(anchorCommentators)
        for (child in doc.body().childNodes()) {
            processNode(
                node = child,
                list = out,
                isBold = false,
                isItalic = false,
                isSmall = false,
                isHeader = false,
                headerLevel = null,
                isFootnoteMarker = false,
                isFootnoteContent = false,
                href = null,
            )
        }
        // Avoids a "line after": removes terminal <br> tags
        while (out.lastOrNull()?.isLineBreak == true) {
            out.removeAt(out.lastIndex)
        }
        return out
    }

    private fun processNode(
        node: Node,
        list: ParsedElements,
        isBold: Boolean,
        isItalic: Boolean,
        isSmall: Boolean,
        isHeader: Boolean,
        headerLevel: Int?,
        isFootnoteMarker: Boolean,
        isFootnoteContent: Boolean,
        href: String?,
    ) {
        when (node) {
            is TextNode -> {
                appendSegment(
                    list = list,
                    textRaw = node.text(),
                    isBold = isBold,
                    isItalic = isItalic,
                    isSmall = isSmall,
                    isHeader = isHeader,
                    headerLevel = headerLevel,
                    isFootnoteMarker = isFootnoteMarker,
                    isFootnoteContent = isFootnoteContent,
                    href = href,
                )
            }
            is Element -> {
                val tag = node.normalName()

                if (tag == "br") {
                    appendLineBreak(list)
                    return
                }

                if (tag == "img") {
                    appendImage(list, node)
                    return
                }

                if (tag == "i" && node.hasAttr("data-commentator")) {
                    val commentator = node.attr("data-commentator")
                    if (commentator in list.anchorCommentators) {
                        orderOf(node.attr("data-order"))?.let { list.pendingAnchors += CommentAnchor(commentator, it) }
                    }
                    return
                }

                // Detect footnote marker: <sup class="footnote-marker">
                val isFootnoteMarkerTag = tag == "sup" && node.hasClass("footnote-marker")
                // Detect footnote content: <i class="footnote">
                val isFootnoteContentTag = tag == "i" && node.hasClass("footnote")

                val nextBold = isBold || tag == "b" || tag == "strong"
                // Don't set italic for footnote content tags (we handle them separately)
                val nextItalic = isItalic || ((tag == "i" || tag == "em") && !isFootnoteContentTag)
                val nextSmall = isSmall || tag == "small"
                val isHeaderTag = tag.length == 2 && tag[0] == 'h' && tag[1].isDigit()
                val nextHeader = isHeader || isHeaderTag
                val nextHeaderLevel = if (isHeaderTag) tag.substring(1).toInt() else headerLevel
                val nextFootnoteMarker = isFootnoteMarker || isFootnoteMarkerTag
                val nextFootnoteContent = isFootnoteContent || isFootnoteContentTag
                val nextHref = if (tag == "a" && node.hasClass("ref")) node.attr("href").ifBlank { null } else href

                if (node.childNodeSize() == 1 && node.childNode(0) is TextNode) {
                    appendSegment(
                        list = list,
                        textRaw = (node.childNode(0) as TextNode).text(),
                        isBold = nextBold,
                        isItalic = nextItalic,
                        isSmall = nextSmall,
                        isHeader = nextHeader,
                        headerLevel = nextHeaderLevel,
                        isFootnoteMarker = nextFootnoteMarker,
                        isFootnoteContent = nextFootnoteContent,
                        href = nextHref,
                    )
                    return
                }

                for (child in node.childNodes()) {
                    processNode(
                        node = child,
                        list = list,
                        isBold = nextBold,
                        isItalic = nextItalic,
                        isSmall = nextSmall,
                        isHeader = nextHeader,
                        headerLevel = nextHeaderLevel,
                        isFootnoteMarker = nextFootnoteMarker,
                        isFootnoteContent = nextFootnoteContent,
                        href = nextHref,
                    )
                }
            }
        }
    }

    private fun appendSegment(
        list: ParsedElements,
        textRaw: String,
        isBold: Boolean,
        isItalic: Boolean,
        isSmall: Boolean,
        isHeader: Boolean,
        headerLevel: Int?,
        isFootnoteMarker: Boolean,
        isFootnoteContent: Boolean,
        href: String?,
    ) {
        // Normalizes multiple spaces into a single space, but preserves leading/trailing spaces
        val normalizedText = textRaw.replace(WHITESPACE_REGEX, " ")

        // Preserve whitespace-only nodes so inline tags do not collapse words
        if (normalizedText.isBlank()) {
            if (textRaw.isNotEmpty() && list.isNotEmpty()) {
                val last = list.last()
                if (!last.isLineBreak && !last.isImage && !last.text.endsWith(" ")) {
                    val updated = last.copy(text = last.text + " ")
                    list[list.lastIndex] = updated
                }
            }
            return
        }

        // Determines whether to preserve leading and trailing spaces
        val hasLeadingSpace = textRaw.isNotEmpty() && textRaw.first().isWhitespace()
        val hasTrailingSpace = textRaw.isNotEmpty() && textRaw.last().isWhitespace()

        // Trim le texte pour le contenu réel
        val trimmedText = normalizedText.trim()
        if (trimmedText.isEmpty()) return

        if (list.isNotEmpty()) {
            val last = list.last()
            val sameStyle =
                !last.isLineBreak &&
                    !last.isImage &&
                    last.isBold == isBold &&
                    last.isItalic == isItalic &&
                    last.isSmall == isSmall &&
                    last.isHeader == isHeader &&
                    last.headerLevel == headerLevel &&
                    last.isFootnoteMarker == isFootnoteMarker &&
                    last.isFootnoteContent == isFootnoteContent &&
                    last.href == href

            if (sameStyle) {
                // Fusion avec le segment précédent du même style
                val lastEndsWithWhitespace = last.text.lastOrNull()?.isWhitespace() == true
                val separator =
                    when {
                        // Préserve explicitement un espace de tête si le texte brut en contenait un
                        hasLeadingSpace && !lastEndsWithWhitespace -> " "
                        lastEndsWithWhitespace -> ""
                        // Sinon, vérifie si on a besoin d'un espace entre les deux (texte latin uniquement)
                        needsSpaceBetween(last.text, trimmedText) -> " "
                        else -> ""
                    }

                val newText = last.text + separator + trimmedText
                // Anchors met since: at the end of the text so far (most merges have none, and allocate nothing)
                val anchors = if (list.pendingAnchors.isEmpty()) last.anchors else last.anchors + list.takeAnchors(at = last.text.length)
                list[list.lastIndex] = last.copy(text = newText, anchors = anchors)

                // Gère l'espace de fin si nécessaire
                if (hasTrailingSpace && !newText.endsWith(" ")) {
                    list[list.lastIndex] = list.last().copy(text = newText + " ")
                }
                return
            }
        }

        // New segment with a different style
        // Adds a space at the beginning if necessary and if the previous segment does not end with a space
        val needsLeadingSpace =
            hasLeadingSpace &&
                list.isNotEmpty() &&
                !list.last().isLineBreak &&
                !list.last().text.endsWith(" ")

        val finalText =
            when {
                needsLeadingSpace && hasTrailingSpace -> " $trimmedText "
                needsLeadingSpace -> " $trimmedText"
                hasTrailingSpace -> "$trimmedText "
                else -> trimmedText
            }

        list.add(
            ParsedHtmlElement(
                text = finalText,
                isBold = isBold,
                isItalic = isItalic,
                isSmall = isSmall,
                isHeader = isHeader,
                headerLevel = headerLevel,
                isFootnoteMarker = isFootnoteMarker,
                isFootnoteContent = isFootnoteContent,
                href = href,
                anchors = list.takeAnchors(at = 0),
            ),
        )
    }

    // Emits an image element if the `src` is a supported `data:image/...;base64,...` URI.
    // Non-data URIs are ignored (no network fetch — images must be pre-embedded in the DB).
    private fun appendImage(
        list: ParsedElements,
        node: Element,
    ) {
        val src = node.attr("src").takeIf { it.isNotBlank() } ?: return
        val decoded = decodeDataUri(src) ?: return
        list.add(
            ParsedHtmlElement(
                text = INLINE_IMAGE_PLACEHOLDER,
                imageBytes = decoded.second,
                imageMime = decoded.first,
                imageWidth = node.attr("width").toIntOrNull(),
                imageHeight = node.attr("height").toIntOrNull(),
            ),
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeDataUri(src: String): Pair<String, ByteArray>? {
        if (!src.startsWith("data:", ignoreCase = true)) return null
        val comma = src.indexOf(',')
        if (comma < 0) return null
        val header = src.substring("data:".length, comma)
        // Only base64 payloads are supported; URL-encoded data URIs are rare for images and skipped.
        val base64Marker = header.indexOf(";base64", ignoreCase = true)
        if (base64Marker < 0) return null
        val mime = header.substring(0, base64Marker).ifBlank { "image/png" }
        val payload = src.substring(comma + 1)
        val bytes = runCatching { Base64.decode(payload) }.getOrNull() ?: return null
        return mime to bytes
    }

    // The `2` of `data-order="2.1"`, read without allocating
    private fun orderOf(value: String): Int? {
        var order = 0
        var digits = 0
        for (c in value) {
            if (c !in '0'..'9') break
            order = order * 10 + (c - '0')
            digits++
        }
        return order.takeIf { digits > 0 }
    }

    // Adds a line break element only if necessary
    private fun appendLineBreak(list: ParsedElements) {
        if (list.isEmpty()) return // no <br> at the beginning
        val last = list.last()
        if (!last.isLineBreak) { // avoids <br><br> → double line
            list.add(ParsedHtmlElement(text = "", isLineBreak = true))
        }
    }

    private fun needsSpaceBetween(
        a: String,
        b: String,
    ): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false

        val lastChar = a.last()
        val firstChar = b.first()

        // Don't add space if either side already has whitespace
        if (lastChar.isWhitespace() || firstChar.isWhitespace()) return false

        // Don't add space if we're dealing with Hebrew or other RTL/non-Latin text
        // Hebrew Unicode range: U+0590 to U+05FF
        // Hebrew Extended: U+FB1D to U+FB4F
        if (isHebrewOrRTL(lastChar) || isHebrewOrRTL(firstChar)) return false

        // Only add space for Latin text
        return true
    }

    private fun isHebrewOrRTL(char: Char): Boolean {
        val code = char.code
        return (code in 0x0590..0x05FF) ||
            // Hebrew
            (code in 0xFB1D..0xFB4F) ||
            // Hebrew Presentation Forms
            (code in 0x0600..0x06FF) ||
            // Arabic
            (code in 0x0750..0x077F) ||
            // Arabic Supplement
            (code >= 0x0300 && code <= 0x036F) // Combining Diacritical Marks (niqqud, etc.)
    }
}
