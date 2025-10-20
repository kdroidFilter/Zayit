@file:OptIn(ExperimentalJewelApi::class)

package io.github.kdroidfilter.seforimapp.features.onboarding.screens.licence

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
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
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.InlinesStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.typography
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.license_accept_checkbox
import seforimapp.seforimapp.generated.resources.license_accept_cta
import seforimapp.seforimapp.generated.resources.license_screen_title
import seforimapp.seforimapp.generated.resources.notoserifhebrew
import java.awt.Desktop.getDesktop
import java.net.URI.create

@Composable
fun LicenceScreen(navController: NavController) {
    LicenceView()
}

@Composable
private fun LicenceView() {
    var isChecked by remember { mutableStateOf(false) }

    val isDark = JewelTheme.isDark

    val textStyle = LocalTextStyle.current.copy(
        fontFamily = FontFamily(Font(resource = Res.font.notoserifhebrew)),
        fontSize = 14.sp,
    )

    val h2TextStyle = LocalTextStyle.current.copy(
        fontFamily = FontFamily(Font(resource = Res.font.notoserifhebrew)),
        fontSize = JewelTheme.typography.h2TextStyle.fontSize,
    )

    val h2Padding = PaddingValues(top = 12.dp, bottom = 8.dp)

    val markdownStyling = remember(isDark, textStyle) {
        // Make Markdown more compact: smaller block spacing and tighter heading paddings.
        if (isDark) {
            MarkdownStyling.dark(
                baseTextStyle = textStyle,
                inlinesStyling = InlinesStyling.dark(textStyle),
                blockVerticalSpacing = 8.dp,
                heading = MarkdownStyling.Heading.dark(
                    baseTextStyle = textStyle,
                    h2 = MarkdownStyling.Heading.H2.dark(
                        baseTextStyle = h2TextStyle,
                        padding = h2Padding
                    ),
                ),
            )
        } else {
            MarkdownStyling.light(
                baseTextStyle = textStyle,
                inlinesStyling = InlinesStyling.light(textStyle),
                blockVerticalSpacing = 8.dp,
                heading = MarkdownStyling.Heading.light(
                    baseTextStyle = textStyle,
                    h2 = MarkdownStyling.Heading.H2.light(
                        baseTextStyle = h2TextStyle,
                        padding = h2Padding
                    ),
                ),
            )
        }
    }

    OnBoardingScaffold(
        title = stringResource(Res.string.license_screen_title),
        bottomAction = {
            DefaultButton(onClick = { /* TODO: handle accept */ }, enabled = isChecked) {
                Text(text = stringResource(Res.string.license_accept_cta))
            }
        }
    ) {
        var bytes by remember { mutableStateOf(ByteArray(0)) }
        var blocks by remember { mutableStateOf(emptyList<MarkdownBlock>()) }
        val processor = remember {
            MarkdownProcessor(
                listOf(
                    AutolinkProcessorExtension,
                )
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
            bytes = Res.readBytes("files/CONDITIONS.md")
            // Process the loaded markdown into renderable blocks
            blocks = processor.processMarkdownDocument(bytes.decodeToString())
        }
        ProvideMarkdownStyling(markdownStyling, blockRenderer, NoOpCodeHighlighter) {
            val lazyListState = rememberLazyListState()
            VerticallyScrollableContainer(lazyListState as ScrollableState) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    state = lazyListState,
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        top = 8.dp,
                        end = 8.dp + scrollbarContentSafePadding(),
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(markdownStyling.blockVerticalSpacing)
                ) {
                    items(blocks) { block ->
                        blockRenderer.RenderBlock(
                            block = block,
                            enabled = true,
                            onUrlClick = { url: String -> getDesktop().browse(create(url)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Checkbox(checked = isChecked, onCheckedChange = { isChecked = it })
                            Text(text = stringResource(Res.string.license_accept_checkbox))
                        }
                    }
                }
            }

        }
    }
}


@Composable
@Preview
private fun LicenceViewPreview() {
    PreviewContainer { LicenceView() }
}
