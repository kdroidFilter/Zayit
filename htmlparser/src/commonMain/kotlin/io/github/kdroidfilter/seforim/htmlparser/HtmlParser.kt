package io.github.kdroidfilter.seforim.htmlparser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

data class ParsedHtmlElement(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isHeader: Boolean = false,
    val headerLevel: Int? = null,
    val commentator: String? = null,
    val commentatorOrder: String? = null,
    val isLineBreak: Boolean = false
)

class HtmlParser {

    fun parse(html: String): List<ParsedHtmlElement> {
        val doc = Jsoup.parse(html)
        val out = mutableListOf<ParsedHtmlElement>()
        for (child in doc.body().childNodes()) {
            processNode(
                node = child,
                list = out,
                isBold = false,
                isItalic = false,
                isHeader = false,
                headerLevel = null,
                commentator = null,
                commentatorOrder = null
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
        list: MutableList<ParsedHtmlElement>,
        isBold: Boolean,
        isItalic: Boolean,
        isHeader: Boolean,
        headerLevel: Int?,
        commentator: String?,
        commentatorOrder: String?
    ) {
        when (node) {
            is TextNode -> {
                appendSegment(
                    list = list,
                    textRaw = node.text(),
                    isBold = isBold,
                    isItalic = isItalic,
                    isHeader = isHeader,
                    headerLevel = headerLevel,
                    commentator = commentator,
                    commentatorOrder = commentatorOrder
                )
            }
            is Element -> {
                val tag = node.normalName()

                if (tag == "br") {
                    appendLineBreak(list)
                    return
                }

                val nextBold = isBold || tag == "b" || tag == "strong"
                val nextItalic = isItalic || tag == "i" || tag == "em"
                val isHeaderTag = tag.length == 2 && tag[0] == 'h' && tag[1].isDigit()
                val nextHeader = isHeader || isHeaderTag
                val nextHeaderLevel = if (isHeaderTag) tag.substring(1).toInt() else headerLevel

                if (node.childNodeSize() == 1 && node.childNode(0) is TextNode) {
                    appendSegment(
                        list = list,
                        textRaw = (node.childNode(0) as TextNode).text(),
                        isBold = nextBold,
                        isItalic = nextItalic,
                        isHeader = nextHeader,
                        headerLevel = nextHeaderLevel,
                        commentator = commentator,
                        commentatorOrder = commentatorOrder
                    )
                    return
                }

                for (child in node.childNodes()) {
                    processNode(
                        node = child,
                        list = list,
                        isBold = nextBold,
                        isItalic = nextItalic,
                        isHeader = nextHeader,
                        headerLevel = nextHeaderLevel,
                        commentator = commentator,
                        commentatorOrder = commentatorOrder
                    )
                }
            }
        }
    }

    private fun appendSegment(
        list: MutableList<ParsedHtmlElement>,
        textRaw: String,
        isBold: Boolean,
        isItalic: Boolean,
        isHeader: Boolean,
        headerLevel: Int?,
        commentator: String?,
        commentatorOrder: String?
    ) {
        // Normalizes multiple spaces into a single space, but preserves leading/trailing spaces
        val normalizedText = textRaw.replace(Regex("\\s+"), " ")

        // Preserve whitespace-only nodes so inline tags do not collapse words
        if (normalizedText.isBlank()) {
            if (textRaw.isNotEmpty() && list.isNotEmpty()) {
                val last = list.last()
                if (!last.isLineBreak && !last.text.endsWith(" ")) {
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
                        last.isBold == isBold &&
                        last.isItalic == isItalic &&
                        last.isHeader == isHeader &&
                        last.headerLevel == headerLevel &&
                        last.commentator == commentator &&
                        last.commentatorOrder == commentatorOrder

            if (sameStyle) {
                // Fusion avec le segment précédent du même style
                val lastEndsWithWhitespace = last.text.lastOrNull()?.isWhitespace() == true
                val separator = when {
                    // Préserve explicitement un espace de tête si le texte brut en contenait un
                    hasLeadingSpace && !lastEndsWithWhitespace -> " "
                    lastEndsWithWhitespace -> ""
                    // Sinon, vérifie si on a besoin d'un espace entre les deux (texte latin uniquement)
                    needsSpaceBetween(last.text, trimmedText) -> " "
                    else -> ""
                }

                val newText = last.text + separator + trimmedText
                list[list.lastIndex] = last.copy(text = newText)

                // Gère l'espace de fin si nécessaire
                if (hasTrailingSpace && !newText.endsWith(" ")) {
                    list[list.lastIndex] = last.copy(text = newText + " ")
                }
                return
            }
        }

        // New segment with a different style
        // Adds a space at the beginning if necessary and if the previous segment does not end with a space
        val needsLeadingSpace = hasLeadingSpace &&
                list.isNotEmpty() &&
                !list.last().isLineBreak &&
                !list.last().text.endsWith(" ")

        val finalText = when {
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
                isHeader = isHeader,
                headerLevel = headerLevel,
                commentator = commentator,
                commentatorOrder = commentatorOrder
            )
        )
    }

    // Adds a line break element only if necessary
    private fun appendLineBreak(list: MutableList<ParsedHtmlElement>) {
        if (list.isEmpty()) return                         // no <br> at the beginning
        val last = list.last()
        if (!last.isLineBreak) {                           // avoids <br><br> → double line
            list.add(ParsedHtmlElement(text = "", isLineBreak = true))
        }
    }

    private fun needsSpaceBetween(a: String, b: String): Boolean {
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
        return (code in 0x0590..0x05FF) ||  // Hebrew
                (code in 0xFB1D..0xFB4F) ||  // Hebrew Presentation Forms
                (code in 0x0600..0x06FF) ||  // Arabic
                (code in 0x0750..0x077F) ||  // Arabic Supplement
                (code >= 0x0300 && code <= 0x036F)  // Combining Diacritical Marks (niqqud, etc.)
    }
}
