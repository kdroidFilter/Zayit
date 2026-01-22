package org.jetbrains.jewel.window

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jetbrains.JBR
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.window.styling.TitleBarStyle

@Composable
internal fun DecoratedDialogScope.DialogTitleBarOnWindows(
    modifier: Modifier = Modifier,
    gradientStartColor: Color = Color.Unspecified,
    style: TitleBarStyle = JewelTheme.defaultTitleBarStyle,
    content: @Composable TitleBarScope.(DecoratedDialogState) -> Unit,
) {
    val titleBar = remember { JBR.getWindowDecorations().createCustomTitleBar() }
    val layoutDirection = LocalLayoutDirection.current

    DialogTitleBarImpl(
        modifier = modifier.customTitleBarMouseEventHandler(titleBar),
        gradientStartColor = gradientStartColor,
        style = style,
        applyTitleBar = { height, _ ->
            titleBar.height = height.value
            JBR.getWindowDecorations().setCustomTitleBar(window, titleBar)
            if (layoutDirection == LayoutDirection.Ltr) {
                PaddingValues(start = titleBar.leftInset.dp, end = titleBar.rightInset.dp)
            } else {
                PaddingValues(start = titleBar.rightInset.dp, end = titleBar.leftInset.dp)
            }
        },
        content = content,
    )
}
