package io.github.kdroidfilter.seforim.htmlparser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal actual fun parseHtmlBody(html: String): List<HtmlNode> =
    Jsoup
        .parse(html)
        .body()
        .childNodes()
        .mapNotNull(::toHtmlNode)

// Text and elements: comments and the like carry nothing to render
private fun toHtmlNode(node: Node): HtmlNode? =
    when (node) {
        is TextNode -> HtmlText(node.text())
        is Element ->
            HtmlElement(
                tag = node.normalName(),
                attributes = node.attributes().associate { it.key to it.value },
                children = node.childNodes().mapNotNull(::toHtmlNode),
            )
        else -> null
    }

actual fun htmlToText(html: String): String = Jsoup.parse(html).text()
