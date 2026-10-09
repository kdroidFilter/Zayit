@file:OptIn(ExperimentalJewelApi::class)

package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.code.highlighting.NoOpCodeHighlighter
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.intui.markdown.standalone.ProvideMarkdownStyling
import org.jetbrains.jewel.intui.markdown.standalone.styling.dark
import org.jetbrains.jewel.intui.markdown.standalone.styling.light
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.extensions.autolink.AutolinkProcessorExtension
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.DefaultMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.InlinesStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.notoserifhebrew

/**
 * Markdown prose laid out in place (no scrolling of its own), for pages that scroll as a whole:
 * the app's Hebrew serif at [fontSize] with a reading [lineHeight], links in the accent color,
 * underlined on hover, opened by [onUrlClick]; paragraphs are justified.
 */
@Composable
fun ProseMarkdown(
    markdown: String,
    onUrlClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 26.sp,
    color: Color = Color.Unspecified,
) {
    val isDark = JewelTheme.isDark
    val accent = JewelTheme.globalColors.outlines.focused
    val serif = FontFamily(Font(resource = Res.font.notoserifhebrew))
    val base = LocalTextStyle.current
    val textStyle = base.copy(fontFamily = serif, fontSize = fontSize, lineHeight = lineHeight, color = color)
    val h2 = base.copy(fontFamily = serif, fontSize = JewelTheme.typography.h2TextStyle.fontSize, color = color)
    val h3 = base.copy(fontFamily = serif, fontSize = JewelTheme.typography.h3TextStyle.fontSize, color = color)

    val styling =
        remember(isDark, textStyle, accent) {
            val link = SpanStyle(color = accent)
            val hovered = SpanStyle(color = accent, textDecoration = TextDecoration.Underline)
            val inlines =
                if (isDark) {
                    InlinesStyling.dark(textStyle, link = link, linkHovered = hovered, linkVisited = link)
                } else {
                    InlinesStyling.light(textStyle, link = link, linkHovered = hovered, linkVisited = link)
                }
            val heading =
                buildHeading(
                    isDark,
                    textStyle,
                    h2,
                    PaddingValues(top = 20.dp, bottom = 6.dp),
                    h3,
                    PaddingValues(top = 14.dp, bottom = 4.dp),
                    true,
                )
            if (isDark) {
                MarkdownStyling.dark(baseTextStyle = textStyle, inlinesStyling = inlines, blockVerticalSpacing = 12.dp, heading = heading)
            } else {
                MarkdownStyling.light(baseTextStyle = textStyle, inlinesStyling = inlines, blockVerticalSpacing = 12.dp, heading = heading)
            }
        }
    val renderer = remember(styling) { JustifiedRenderer(styling) }
    val processor = remember { MarkdownProcessor(listOf(AutolinkProcessorExtension)) }
    val blocks by produceState(emptyList(), markdown) { value = processor.processMarkdownDocument(markdown) }

    ProvideMarkdownStyling(styling, renderer, NoOpCodeHighlighter) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(styling.blockVerticalSpacing)) {
            blocks.forEach { block ->
                renderer.RenderBlock(block = block, enabled = true, onUrlClick = onUrlClick, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Jewel's renderer (the same for light and dark), with justified paragraphs. */
private class JustifiedRenderer(
    styling: MarkdownStyling,
) : DefaultMarkdownBlockRenderer(styling) {
    @Composable
    override fun RenderParagraph(
        block: MarkdownBlock.Paragraph,
        styling: MarkdownStyling.Paragraph,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        modifier: Modifier,
    ) {
        val text =
            remember(block, styling, enabled, onUrlClick) {
                inlineRenderer.renderAsAnnotatedString(block.inlineContent, styling.inlinesStyling, enabled, onUrlClick)
            }
        val style = styling.inlinesStyling.textStyle
        Text(
            text,
            modifier,
            style = style.copy(color = style.color.takeOrElse { LocalContentColor.current }),
            textAlign = TextAlign.Justify,
        )
    }
}
