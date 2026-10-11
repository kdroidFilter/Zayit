package io.github.kdroidfilter.seforimapp.luach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import java.time.LocalDate

/**
 * [LuachMonth] in a popup under its anchor (the layout it's composed in), Hebrew first: picking a day, or today
 * through [onToday], closes it.
 */
@Composable
fun LuachPopup(
    selected: LocalDate,
    inIsrael: Boolean,
    accent: Color,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    today: LocalDate = LocalDate.now(),
    onToday: (() -> Unit)? = null,
) {
    var civil by remember { mutableStateOf(false) }
    Popup(
        popupPositionProvider = BelowAnchor,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val shape = RoundedCornerShape(10.dp)
        LuachMonth(
            selected = selected,
            today = today,
            inIsrael = inIsrael,
            civil = civil,
            onSwap = { civil = !civil },
            onSelect = {
                onSelect(it)
                onDismiss()
            },
            accent = accent,
            onToday =
                onToday?.let { back ->
                    {
                        back()
                        onDismiss()
                    }
                },
            modifier =
                Modifier
                    .size(420.dp, 380.dp)
                    .clip(shape)
                    .background(JewelTheme.globalColors.panelBackground)
                    .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                    .padding(12.dp),
        )
    }
}

/** Under the anchor, from its start edge; above it when the window has no room below. Kept inside the window. */
private object BelowAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Rtl) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val below = anchorBounds.bottom
        val y = if (below + popupContentSize.height <= windowSize.height) below else anchorBounds.top - popupContentSize.height
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

/** An outlined button showing [label], the selected day: a click opens [LuachPopup] under it. */
@Composable
fun LuachDateButton(
    label: String,
    selected: LocalDate,
    inIsrael: Boolean,
    accent: Color,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    onToday: (() -> Unit)? = null,
) {
    var picking by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { picking = !picking }) {
            // The drop-down arrow of the split buttons beside it (the location picker)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Icon(key = AllIconsKeys.General.ChevronDown, contentDescription = null)
            }
        }
        if (picking) {
            LuachPopup(
                selected = selected,
                inIsrael = inIsrael,
                accent = accent,
                onSelect = onSelect,
                onDismiss = { picking = false },
                today = today,
                onToday = onToday,
            )
        }
    }
}
