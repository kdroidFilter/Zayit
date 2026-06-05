package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextDecoration
import io.github.kdroidfilter.seforimapp.core.presentation.utils.UrlOpener
import org.jetbrains.jewel.ui.component.Text

@Composable
fun TextWithLink(
    text: String,
    link: String,
) {
    Text(
        text,
        textDecoration = TextDecoration.Underline,
        modifier =
            Modifier.pointerHoverIcon(PointerIcon.Hand).clickable {
                UrlOpener.open(link)
            },
    )
}
