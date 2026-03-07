@file:OptIn(ExperimentalJewelApi::class)

package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.code.highlighting.NoOpCodeHighlighter
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.intui.markdown.standalone.ProvideMarkdownStyling
import org.jetbrains.jewel.intui.markdown.standalone.dark
import org.jetbrains.jewel.intui.markdown.standalone.light
import org.jetbrains.jewel.intui.markdown.standalone.styling.dark
import org.jetbrains.jewel.intui.markdown.standalone.styling.light
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.extensions.autolink.AutolinkProcessorExtension
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.InlinesStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.scrollbarContentSafePadding
import org.jetbrains.jewel.ui.typography
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.notoserifhebrew
import java.awt.Desktop.getDesktop
import java.net.URI.create

/**
 * Renders a markdown resource file with accent-colored links and the app's Hebrew font.
 *
 * @param resourcePath path inside composeResources (e.g. "files/ABOUT.md")
 * @param modifier modifier for the outer container
 * @param includeH3 whether to include custom h3 heading styling (About/Conditions use it, Licence doesn't)
 * @param extraItems additional items appended after the markdown blocks (e.g. a checkbox)
 */
@Composable
fun AccentMarkdownView(
    resourcePath: String,
    modifier: Modifier = Modifier,
    includeH3: Boolean = true,
    extraItems: (LazyListScope.() -> Unit)? = null,
) {
    val isDark = JewelTheme.isDark
    val accent = JewelTheme.globalColors.outlines.focused

    val textStyle =
        LocalTextStyle.current.copy(
            fontFamily = FontFamily(Font(resource = Res.font.notoserifhebrew)),
            fontSize = 14.sp,
        )

    val h2TextStyle =
        LocalTextStyle.current.copy(
            fontFamily = FontFamily(Font(resource = Res.font.notoserifhebrew)),
            fontSize = JewelTheme.typography.h2TextStyle.fontSize,
        )

    val h3TextStyle =
        LocalTextStyle.current.copy(
            fontFamily = FontFamily(Font(resource = Res.font.notoserifhebrew)),
            fontSize = JewelTheme.typography.h3TextStyle.fontSize,
        )

    val h2Padding = PaddingValues(top = 12.dp, bottom = 8.dp)
    val h3Padding = PaddingValues(top = 10.dp, bottom = 6.dp)

    val linkStyle = SpanStyle(color = accent)
    val linkHoveredStyle = SpanStyle(color = accent, textDecoration = TextDecoration.Underline)

    val markdownStyling =
        remember(isDark, textStyle, accent, includeH3) {
            val inlines =
                if (isDark) {
                    InlinesStyling.dark(textStyle, link = linkStyle, linkHovered = linkHoveredStyle, linkVisited = linkStyle)
                } else {
                    InlinesStyling.light(textStyle, link = linkStyle, linkHovered = linkHoveredStyle, linkVisited = linkStyle)
                }

            val heading = buildHeading(isDark, textStyle, h2TextStyle, h2Padding, h3TextStyle, h3Padding, includeH3)

            if (isDark) {
                MarkdownStyling.dark(
                    baseTextStyle = textStyle,
                    inlinesStyling = inlines,
                    blockVerticalSpacing = 8.dp,
                    heading = heading,
                )
            } else {
                MarkdownStyling.light(
                    baseTextStyle = textStyle,
                    inlinesStyling = inlines,
                    blockVerticalSpacing = 8.dp,
                    heading = heading,
                )
            }
        }

    val processor =
        remember {
            MarkdownProcessor(listOf(AutolinkProcessorExtension))
        }

    val blockRenderer =
        remember(markdownStyling) {
            if (isDark) {
                MarkdownBlockRenderer.dark(styling = markdownStyling)
            } else {
                MarkdownBlockRenderer.light(styling = markdownStyling)
            }
        }

    var blocks by remember { mutableStateOf(emptyList<MarkdownBlock>()) }
    LaunchedEffect(resourcePath) {
        val bytes = Res.readBytes(resourcePath)
        blocks = processor.processMarkdownDocument(bytes.decodeToString())
    }

    ProvideMarkdownStyling(markdownStyling, blockRenderer, NoOpCodeHighlighter) {
        val lazyListState = rememberLazyListState()
        VerticallyScrollableContainer(lazyListState as ScrollableState) {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                state = lazyListState,
                contentPadding =
                    PaddingValues(
                        start = 8.dp,
                        top = 8.dp,
                        end = 8.dp + scrollbarContentSafePadding(),
                        bottom = 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(markdownStyling.blockVerticalSpacing),
            ) {
                items(blocks) { block ->
                    blockRenderer.RenderBlock(
                        block = block,
                        enabled = true,
                        onUrlClick = { url: String -> getDesktop().browse(create(url)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                extraItems?.invoke(this)
            }
        }
    }
}

private fun buildHeading(
    isDark: Boolean,
    textStyle: TextStyle,
    h2TextStyle: TextStyle,
    h2Padding: PaddingValues,
    h3TextStyle: TextStyle,
    h3Padding: PaddingValues,
    includeH3: Boolean,
): MarkdownStyling.Heading {
    val h2 =
        if (isDark) {
            MarkdownStyling.Heading.H2.dark(baseTextStyle = h2TextStyle, padding = h2Padding)
        } else {
            MarkdownStyling.Heading.H2.light(baseTextStyle = h2TextStyle, padding = h2Padding)
        }

    val h3 =
        if (includeH3) {
            if (isDark) {
                MarkdownStyling.Heading.H3.dark(baseTextStyle = h3TextStyle, padding = h3Padding)
            } else {
                MarkdownStyling.Heading.H3.light(baseTextStyle = h3TextStyle, padding = h3Padding)
            }
        } else {
            null
        }

    return if (isDark) {
        if (h3 != null) {
            MarkdownStyling.Heading.dark(baseTextStyle = textStyle, h2 = h2, h3 = h3)
        } else {
            MarkdownStyling.Heading.dark(baseTextStyle = textStyle, h2 = h2)
        }
    } else {
        if (h3 != null) {
            MarkdownStyling.Heading.light(baseTextStyle = textStyle, h2 = h2, h3 = h3)
        } else {
            MarkdownStyling.Heading.light(baseTextStyle = textStyle, h2 = h2)
        }
    }
}
