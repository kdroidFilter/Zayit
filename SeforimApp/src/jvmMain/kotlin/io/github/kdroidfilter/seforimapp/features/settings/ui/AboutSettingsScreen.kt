@file:OptIn(ExperimentalJewelApi::class)

package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
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
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.typography
import seforimapp.seforimapp.generated.resources.*
import java.awt.Desktop.getDesktop
import java.net.URI.create

@Composable
fun AboutSettingsScreen() {
    AboutSettingsView()
}

@Composable
private fun AboutSettingsView() {
    val isDark = JewelTheme.isDark

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

    val markdownStyling =
        remember(isDark, textStyle) {
            if (isDark) {
                MarkdownStyling.dark(
                    baseTextStyle = textStyle,
                    inlinesStyling = InlinesStyling.dark(textStyle),
                    blockVerticalSpacing = 8.dp,
                    heading =
                        MarkdownStyling.Heading.dark(
                            baseTextStyle = textStyle,
                            h2 =
                                MarkdownStyling.Heading.H2.dark(
                                    baseTextStyle = h2TextStyle,
                                    padding = h2Padding,
                                ),
                            h3 =
                                MarkdownStyling.Heading.H3.dark(
                                    baseTextStyle = h3TextStyle,
                                    padding = h3Padding,
                                ),
                        ),
                )
            } else {
                MarkdownStyling.light(
                    baseTextStyle = textStyle,
                    inlinesStyling = InlinesStyling.light(textStyle),
                    blockVerticalSpacing = 8.dp,
                    heading =
                        MarkdownStyling.Heading.light(
                            baseTextStyle = textStyle,
                            h2 =
                                MarkdownStyling.Heading.H2.light(
                                    baseTextStyle = h2TextStyle,
                                    padding = h2Padding,
                                ),
                            h3 =
                                MarkdownStyling.Heading.H3.light(
                                    baseTextStyle = h3TextStyle,
                                    padding = h3Padding,
                                ),
                        ),
                )
            }
        }

    var bytes by remember { mutableStateOf(ByteArray(0)) }
    var blocks by remember { mutableStateOf(emptyList<MarkdownBlock>()) }
    val processor =
        remember {
            MarkdownProcessor(
                listOf(
                    AutolinkProcessorExtension,
                ),
            )
        }
    val blockRenderer =
        remember(markdownStyling) {
            if (isDark) {
                MarkdownBlockRenderer.dark(
                    styling = markdownStyling,
                )
            } else {
                MarkdownBlockRenderer.light(
                    styling = markdownStyling,
                )
            }
        }

    LaunchedEffect(Unit) {
        bytes = Res.readBytes("files/ABOUT.md")
        blocks = processor.processMarkdownDocument(bytes.decodeToString())
    }

    ProvideMarkdownStyling(markdownStyling, blockRenderer, NoOpCodeHighlighter) {
        val lazyListState = rememberLazyListState()
        VerticallyScrollableContainer(lazyListState as ScrollableState) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
            }
        }
    }
}

@Composable
@Preview
private fun AboutSettingsViewPreview() {
    PreviewContainer { AboutSettingsView() }
}
