package io.github.kdroidfilter.seforim.htmlparser

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

// Ksoup is jsoup's Kotlin port: the same DOM as on the JVM and Android
internal actual fun parseHtmlBody(html: String): List<HtmlNode> =
    Ksoup
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

actual fun htmlToText(html: String): String = Ksoup.parse(html).text()
