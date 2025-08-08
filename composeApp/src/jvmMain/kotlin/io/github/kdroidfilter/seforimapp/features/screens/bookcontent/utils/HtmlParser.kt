// HtmlParser.kt
package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.utils

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
        val text = textRaw.replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return

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
                val sep = if (needsSpaceBetween(last.text, text)) " " else ""
                list[list.lastIndex] = last.copy(text = last.text + sep + text)
                return
            }
        }

        list.add(
            ParsedHtmlElement(
                text = text,
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
        if (!last.isLineBreak) {                           // avoids <br><br> ⇒ double line
            list.add(ParsedHtmlElement(text = "", isLineBreak = true))
        }
    }

    private fun needsSpaceBetween(a: String, b: String): Boolean {
        return a.isNotEmpty() && !a.last().isWhitespace() &&
                b.isNotEmpty() && !b.first().isWhitespace()
    }
}