package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextDecoration
import org.jetbrains.jewel.ui.component.Text
import java.awt.Desktop
import java.net.URI.create

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
                Desktop.getDesktop().browse(create(link))
            },
    )
}
