package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField as JewelTextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import androidx.compose.ui.input.key.*
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_in_page
import seforimapp.seforimapp.generated.resources.chevron_icon_description
import kotlinx.coroutines.delay

@Composable
fun FindInPageBar(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    onEnterNext: () -> Unit = {},
    onEnterPrev: () -> Unit = {},
    onClose: () -> Unit = {},
    // Focus is handled by callers if needed; no internal autofocus logic
) {
    val panelColor = JewelTheme.globalColors.panelBackground
    val borderColor = JewelTheme.globalColors.borders.focused
    val shape = RoundedCornerShape(8.dp)
    // No internal focus management

    Row(
        modifier = modifier
            .background(panelColor, shape)
            .border(1.dp, borderColor, shape)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        JewelTextField(
            state = state,
            modifier = Modifier
                .widthIn(min = 220.dp, max = 380.dp)
                .height(36.dp)
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyUp && (ev.key == Key.Enter || ev.key == Key.NumPadEnter)) {
                        if (ev.isShiftPressed) onEnterPrev() else onEnterNext()
                        true
                    } else if (ev.type == KeyEventType.KeyUp && ev.key == Key.Escape) {
                        onClose(); true
                    } else false
                },
            placeholder = { Text(stringResource(Res.string.search_in_page)) },
            leadingIcon = {
                Icon(key = AllIconsKeys.Actions.Find, contentDescription = stringResource(Res.string.search_in_page))
            },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onEnterPrev) {
            Icon(key = AllIconsKeys.General.ChevronRight, contentDescription = stringResource(Res.string.chevron_icon_description))
        }
        IconButton(onClick = onEnterNext) {
            Icon(key = AllIconsKeys.General.ChevronLeft, contentDescription = stringResource(Res.string.chevron_icon_description))
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onClose) {
            Icon(key = AllIconsKeys.Windows.Close, contentDescription = stringResource(Res.string.search_in_page))
        }
    }
}
