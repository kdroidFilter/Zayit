package io.github.kdroidfilter.seforim.htmlparser

/** A node of a parsed HTML body: what [HtmlParser] reads of jsoup's DOM (JVM, Android) or Ksoup's (iOS). */
internal sealed interface HtmlNode

internal class HtmlText(
    val text: String,
) : HtmlNode

internal class HtmlElement(
    /** The tag's name, lower-case. */
    val tag: String,
    private val attributes: Map<String, String>,
    val children: List<HtmlNode>,
) : HtmlNode {
    /** The attribute [name]'s value, empty when absent. */
    fun attr(name: String): String = attributes[name].orEmpty()

    /** Whether the `class` attribute lists [name] (case-insensitive, as jsoup). */
    fun hasClass(name: String): Boolean = attr("class").split(CLASS_SEPARATOR).any { it.equals(name, ignoreCase = true) }

    private companion object {
        val CLASS_SEPARATOR = Regex("\\s+")
    }
}

/** The nodes of [html]'s body, parsed as a browser would. */
internal expect fun parseHtmlBody(html: String): List<HtmlNode>
