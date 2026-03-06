package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.kdroidfilter.seforim.desktop.VirtualDesktop
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.MaterialSymbolsDesktop_landscape
import io.github.kdroidfilter.seforimapp.icons.MaterialSymbolsDesktop_landscape_add
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.desktop_new
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem

@Composable
fun DesktopSwitcher(modifier: Modifier = Modifier) {
    val appGraph = LocalAppGraph.current
    val desktopManager = appGraph.desktopManager
    val desktops by desktopManager.desktops.collectAsState()
    val immutableDesktops = remember(desktops) { desktops.toImmutableList() }
    val activeDesktopId by desktopManager.activeDesktopId.collectAsState()
    val activeDesktop = desktops.find { it.id == activeDesktopId }

    var showDropdown by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.CenterStart,
    ) {
        DesktopSwitcherTrigger(
            desktopName = activeDesktop?.name ?: "1",
            onClick = { showDropdown = !showDropdown },
        )

        if (showDropdown) {
            Popup(
                onDismissRequest = { showDropdown = false },
                popupPositionProvider = BelowAnchorPositionProvider,
                properties = PopupProperties(focusable = true),
            ) {
                DesktopDropdownContent(
                    desktops = immutableDesktops,
                    activeDesktopId = activeDesktopId,
                    onMove = desktopManager::moveDesktop,
                    onSwitch = desktopManager::switchTo,
                    onRename = desktopManager::renameDesktop,
                    onDelete = desktopManager::deleteDesktop,
                    onCreate = desktopManager::createDesktop,
                    onDismiss = { showDropdown = false },
                )
            }
        }
    }
}

@Composable
private fun DesktopSwitcherTrigger(
    desktopName: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val accent = JewelTheme.globalColors.outlines.focused
    val hoverBg = if (isHovered) accent.copy(alpha = 0.08f) else Color.Transparent
    val isIslands = ThemeUtils.isIslandsStyle()
    val cornerRadius = if (isIslands) 8.dp else 4.dp

    Row(
        modifier =
            Modifier
                .width(DESKTOP_SWITCHER_WIDTH)
                .fillMaxHeight()
                .then(
                    if (isIslands) {
                        Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
                    } else {
                        Modifier
                    },
                ).hoverable(interactionSource)
                .background(hoverBg, RoundedCornerShape(cornerRadius))
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(
            painter = rememberVectorPainter(MaterialSymbolsDesktop_landscape),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.normal),
        )
        Text(
            text = desktopName,
            color = JewelTheme.globalColors.text.normal,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Icon(
            key = AllIconsKeys.General.ChevronDown,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = JewelTheme.globalColors.text.normal,
        )
    }
}

@Composable
private fun DesktopDropdownContent(
    desktops: ImmutableList<VirtualDesktop>,
    activeDesktopId: String,
    onMove: (Int, Int) -> Unit,
    onSwitch: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onCreate: () -> String,
    onDismiss: () -> Unit,
) {
    val accent = JewelTheme.globalColors.outlines.focused

    var renamingDesktopId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier =
            Modifier
                .width(DROPDOWN_WIDTH)
                .background(JewelTheme.globalColors.panelBackground, DropdownShape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, DropdownShape)
                .padding(4.dp),
    ) {
        val desktopKeys = remember(desktops) { desktops.map { it.id } }

        ReorderableColumn(
            list = desktopKeys,
            onSettle = { fromIndex, toIndex -> onMove(fromIndex, toIndex) },
        ) { index, desktopId, isDragging ->
            val desktop = desktops.getOrNull(index) ?: return@ReorderableColumn
            val dragAlpha by animateFloatAsState(
                targetValue = if (isDragging) 0.7f else 1f,
                animationSpec = tween(durationMillis = 150),
            )

            ReorderableItem {
                Box(modifier = Modifier.alpha(dragAlpha).draggableHandle()) {
                    if (renamingDesktopId == desktop.id) {
                        DesktopRenameField(
                            currentName = desktop.name,
                            onRename = { newName ->
                                if (newName.isNotBlank()) onRename(desktop.id, newName)
                                renamingDesktopId = null
                            },
                            onCancel = { renamingDesktopId = null },
                        )
                    } else {
                        DesktopItem(
                            desktop = desktop,
                            isActive = desktop.id == activeDesktopId,
                            canDelete = desktops.size > 1,
                            onClick = {
                                onSwitch(desktop.id)
                                onDismiss()
                            },
                            onRename = { renamingDesktopId = desktop.id },
                            onDelete = {
                                onDelete(desktop.id)
                                if (desktops.size <= 2) onDismiss()
                            },
                        )
                    }
                }
            }
        }

        // New desktop button
        HoverableRow(
            onClick = {
                onCreate()
                onDismiss()
            },
            hoverColor = accent.copy(alpha = 0.06f),
        ) {
            Text(
                text = stringResource(Res.string.desktop_new),
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.info,
                modifier = Modifier.weight(1f),
            )
            Image(
                painter = rememberVectorPainter(MaterialSymbolsDesktop_landscape_add),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.info),
            )
        }
    }
}

@Composable
private fun DesktopItem(
    desktop: VirtualDesktop,
    isActive: Boolean,
    canDelete: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = JewelTheme.globalColors.outlines.focused
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val showActions = isHovered || isActive

    val bgColor =
        when {
            isActive -> accent.copy(alpha = 0.12f)
            isHovered -> accent.copy(alpha = 0.06f)
            else -> Color.Transparent
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .background(bgColor, ItemShape)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ActionIcon(
            key = AllIconsKeys.Actions.Edit,
            visible = showActions,
            onClick = onRename,
        )
        Text(
            text = desktop.name,
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.normal,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (canDelete) {
            ActionIcon(
                key = AllIconsKeys.Actions.Close,
                visible = showActions,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun ActionIcon(
    key: IconKey,
    visible: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val accent = JewelTheme.globalColors.outlines.focused
    val hoverBg = if (isHovered && visible) accent.copy(alpha = 0.12f) else Color.Transparent

    Box(
        modifier =
            Modifier
                .size(18.dp)
                .hoverable(interactionSource)
                .background(hoverBg, CircleShape)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            key = key,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (visible) JewelTheme.globalColors.text.normal else Color.Transparent,
        )
    }
}

@Composable
private fun HoverableRow(
    onClick: () -> Unit,
    hoverColor: Color,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .background(if (isHovered) hoverColor else Color.Transparent, ItemShape)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content()
    }
}

@Composable
private fun DesktopRenameField(
    currentName: String,
    onRename: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(currentName, selection = TextRange(0, currentName.length)))
    }
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { if (it.text.length <= MAX_DESKTOP_NAME_LENGTH) textFieldValue = it },
            singleLine = true,
            textStyle =
                TextStyle(
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.normal,
                ),
            cursorBrush = SolidColor(JewelTheme.globalColors.outlines.focused),
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onKeyEvent { keyEvent ->
                        when (keyEvent.key) {
                            Key.Enter -> {
                                onRename(textFieldValue.text)
                                true
                            }
                            Key.Escape -> {
                                onCancel()
                                true
                            }
                            else -> false
                        }
                    },
        )

        ActionIcon(
            key = AllIconsKeys.Actions.Checked,
            visible = true,
            onClick = { onRename(textFieldValue.text) },
        )
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

internal val DESKTOP_SWITCHER_WIDTH = 110.dp
private val DROPDOWN_WIDTH = 130.dp
private const val MAX_DESKTOP_NAME_LENGTH = 8
private val DropdownShape = RoundedCornerShape(6.dp)
private val ItemShape = RoundedCornerShape(4.dp)

/**
 * Positions the popup directly below the anchor, aligned to its right edge.
 */
private object BelowAnchorPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.right - popupContentSize.width
        val y = anchorBounds.bottom
        return IntOffset(
            x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y = y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}
