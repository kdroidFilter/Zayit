package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.jewel.ui.component.Text

/**
 * A reusable warning banner component with customizable message
 *
 * @param message The warning message to display
 * @param onClose Callback when the close button is clicked
 * @param modifier Modifier for the banner
 */
@Composable
fun WarningBanner(
    message: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = Color(0xFFFFF4E5), // Light orange background
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0xFFFFB74D), // Orange border
                shape = RoundedCornerShape(4.dp)
            )
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Warning icon
            Text(
                text = "⚠️",
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            
            // Warning message
            Text(
                text = message,
                color = Color(0xFF7A4F01), // Dark orange text
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            
            // Close button (small cross)
            Text(
                text = "✕",
                fontSize = 14.sp,
                color = Color(0xFF7A4F01), // Dark orange text to match the message
                modifier = Modifier
                    .padding(start = 8.dp)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            onClose()
                        }
                    }
            )
        }
    }
}

@Preview
@Composable
fun WarningBannerPreview() {
    PreviewContainer {
        WarningBanner(
            message = "This is a warning message",
            onClose = {}
        )
    }
}