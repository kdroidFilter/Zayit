package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.icons.MaterialSymbolsMagicButton
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.ToggleableIconButton
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.find_close
import seforimapp.seforimapp.generated.resources.find_next_match
import seforimapp.seforimapp.generated.resources.find_previous_match
import seforimapp.seforimapp.generated.resources.find_smart_mode
import seforimapp.seforimapp.generated.resources.search_in_page

/**
 * Find-in-page bar, laid out as Chromium's and IntelliJ's: one floating container holding the
 * borderless field and its match counter, then the actions after a divider.
 */
@Composable
fun FindInPageBar(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    onEnterNext: () -> Unit = {},
    onEnterPrev: () -> Unit = {},
    onClose: () -> Unit = {},
    // Ctrl+Enter, as in Chromium: ends the session acting on the current match
    onActivate: () -> Unit = onClose,
    // When true, requests focus on the text field when composed
    autoFocus: Boolean = true,
    // Bumped to focus the field again with its text selected (Ctrl/Cmd+F while shown)
    focusRequest: Int = 0,
    // Matches of the query, and the rank of the current one from 1; a null count hides the counter
    matchCount: Int? = null,
    matchPosition: Int? = null,
    // Meaning-based find; null hides its toggle where it isn't available
    smartModeEnabled: Boolean = false,
    onToggleSmartMode: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus, focusRequest) {
        if (autoFocus) {
            // Small delay ensures the node is placed before requesting focus
            delay(10)
            focusRequester.requestFocus()
            // As in Chromium, typing replaces the previous query
            state.edit { selection = TextRange(0, length) }
        }
    }

    Row(
        modifier =
            modifier
                .shadow(6.dp, shape)
                .background(JewelTheme.globalColors.panelBackground, shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .height(36.dp)
                .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            key = AllIconsKeys.Actions.Find,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        TextField(
            state = state,
            undecorated = true,
            modifier =
                Modifier
                    .width(200.dp)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { ev ->
                        // On key press as in Chromium's FindBarView, so holding Enter keeps stepping
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (ev.key) {
                            Key.Enter, Key.NumPadEnter -> {
                                when {
                                    ev.isCtrlPressed -> onActivate()
                                    ev.isShiftPressed -> onEnterPrev()
                                    else -> onEnterNext()
                                }
                                true
                            }

                            Key.Escape -> {
                                onClose()
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    },
            placeholder = { Text(stringResource(Res.string.search_in_page)) },
            textStyle = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp),
        )
        if (matchCount != null) {
            Spacer(Modifier.width(8.dp))
            // "3/861" once a match is current, like a browser's find bar; red when nothing matches
            Text(
                text = matchPosition?.let { "$it/$matchCount" } ?: matchCount.toString(),
                color = if (matchCount == 0) JewelTheme.globalColors.text.error else JewelTheme.globalColors.text.info,
                fontSize = 12.sp,
            )
        }
        Divider(
            orientation = Orientation.Vertical,
            modifier = Modifier.padding(horizontal = 8.dp).height(20.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onToggleSmartMode != null) {
                val smartLabel = stringResource(Res.string.find_smart_mode)
                ShortcutTooltip(smartLabel) {
                    ToggleableIconButton(
                        value = smartModeEnabled,
                        onValueChange = { onToggleSmartMode() },
                        focusable = false,
                    ) {
                        Icon(
                            imageVector = MaterialSymbolsMagicButton,
                            contentDescription = smartLabel,
                            modifier = Modifier.size(16.dp),
                            tint =
                                if (smartModeEnabled) {
                                    JewelTheme.globalColors.outlines.focused
                                } else {
                                    JewelTheme.globalColors.text.info
                                },
                        )
                    }
                }
            }
            // Up/down, not left/right: unambiguous in a right-to-left layout
            val hasMatches = matchCount != 0
            FindActionButton(
                key = AllIconsKeys.Actions.PreviousOccurence,
                label = stringResource(Res.string.find_previous_match),
                shortcut = "Shift+Enter",
                enabled = hasMatches,
                onClick = onEnterPrev,
            )
            FindActionButton(
                key = AllIconsKeys.Actions.NextOccurence,
                label = stringResource(Res.string.find_next_match),
                shortcut = "Enter",
                enabled = hasMatches,
                onClick = onEnterNext,
            )
            FindActionButton(
                key = AllIconsKeys.Actions.Close,
                label = stringResource(Res.string.find_close),
                shortcut = "Esc",
                onClick = onClose,
            )
        }
    }
}

@Composable
private fun FindActionButton(
    key: org.jetbrains.jewel.ui.icon.IconKey,
    label: String,
    shortcut: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    ShortcutTooltip(label, shortcut) {
        // Not focusable: the keyboard stays in the field, as in Chromium
        IconActionButton(key = key, contentDescription = label, onClick = onClick, enabled = enabled, focusable = false)
    }
}

/** Tooltip with the action's name and, dimmed, its shortcut, as the title bar's buttons. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutTooltip(
    label: String,
    shortcut: String? = null,
    content: @Composable () -> Unit,
) {
    Tooltip(
        tooltip = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label)
                if (shortcut != null) Text(shortcut, color = JewelTheme.globalColors.text.disabled)
            }
        },
        content = content,
    )
}

/**
 * Copies the [persisted] query into the find field, selected as Chromium does with a prefilled
 * query, so typing replaces it. The field's own short text (under 2 letters, persisted as "")
 * isn't wiped: that's the user still typing.
 */
fun syncFindField(
    field: TextFieldState,
    persisted: String,
) {
    val current = field.text.toString()
    if (current == persisted || (persisted.isEmpty() && current.trim().length < 2)) return
    field.edit {
        replace(0, length, persisted)
        selection = TextRange(0, length)
    }
}
