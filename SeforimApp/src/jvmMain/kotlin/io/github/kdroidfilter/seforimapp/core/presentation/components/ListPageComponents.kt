package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupPositionProvider
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.settings_cancel

/**
 * Shared visual primitives for list-style tab pages (history, favorites, etc.).
 * These components follow the Jewel/Chrome settings-page aesthetic: cards, hover
 * highlights, icon buttons, and a centered content column.
 */

@Composable
fun ListPageContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
fun PageHeader(
    title: String,
    actions: @Composable () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = JewelTheme.globalColors.text.normal,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun CardSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(10.dp))
                .background(JewelTheme.globalColors.panelBackground)
                .padding(8.dp),
    ) {
        content()
    }
}

@Composable
fun PageSearchField(
    query: String,
    placeholder: StringResource,
    onQueryChange: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
                .background(JewelTheme.globalColors.panelBackground)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            key = AllIconsKeys.Actions.Find,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = JewelTheme.globalColors.text.info,
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = JewelTheme.globalColors.text.normal),
            cursorBrush = SolidColor(JewelTheme.globalColors.outlines.focused),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(placeholder),
                            fontSize = 14.sp,
                            color = JewelTheme.globalColors.text.info,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotBlank()) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    key = AllIconsKeys.Actions.Close,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = JewelTheme.globalColors.text.info,
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    count: Int? = null,
    expanded: Boolean = true,
    onToggleExpand: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    leadingIconKey: IconKey? = null,
    leadingIconVector: ImageVector? = null,
) {
    val headerInteraction = remember { MutableInteractionSource() }
    val isHeaderHovered by headerInteraction.collectIsHoveredAsState()
    val accent = JewelTheme.globalColors.outlines.focused
    val headerBackground by animateColorAsState(
        targetValue = if (isHeaderHovered) accent.copy(alpha = 0.06f) else Color.Transparent,
        animationSpec = tween(durationMillis = 150),
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 180),
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(headerBackground)
                .then(
                    if (onToggleExpand != null) {
                        Modifier.clickable { onToggleExpand() }.hoverable(headerInteraction)
                    } else {
                        Modifier
                    },
                ).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onToggleExpand != null) {
            Icon(
                key = AllIconsKeys.General.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(12.dp).rotate(chevronRotation),
                tint = JewelTheme.globalColors.text.info,
            )
        }
        if (leadingIconKey != null) {
            Icon(
                key = leadingIconKey,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = JewelTheme.globalColors.text.info,
            )
        } else if (leadingIconVector != null) {
            Image(
                painter = rememberVectorPainter(leadingIconVector),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.info),
            )
        }
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = JewelTheme.globalColors.text.normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Text(
                text = count.toString(),
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.info,
            )
        }
        if (onDelete != null) {
            DeleteButton(
                visible = isHeaderHovered,
                onClick = onDelete,
            )
        }
    }
}

@Composable
fun ListRow(
    title: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    leadingIconKey: IconKey? = null,
    leadingIconVector: ImageVector? = null,
    leadingTint: Color = JewelTheme.globalColors.outlines.focused,
    leadingContent: @Composable (RowScope.() -> Unit)? = null,
    showDivider: Boolean = true,
    compact: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val accent = JewelTheme.globalColors.outlines.focused
    val background by animateColorAsState(
        targetValue = if (isHovered) accent.copy(alpha = 0.06f) else Color.Transparent,
        animationSpec = tween(durationMillis = 140),
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(background)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onOpen,
                    ).hoverable(interactionSource)
                    .padding(horizontal = 8.dp, vertical = if (compact) 5.dp else 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leadingContent?.invoke(this)
            if (leadingIconKey != null) {
                Icon(
                    key = leadingIconKey,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 13.dp else 15.dp),
                    tint = leadingTint,
                )
            } else if (leadingIconVector != null) {
                Image(
                    painter = rememberVectorPainter(leadingIconVector),
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 13.dp else 15.dp),
                    colorFilter = ColorFilter.tint(leadingTint),
                )
            }
            Text(
                text = title,
                fontSize = if (compact) 12.sp else 14.sp,
                color = JewelTheme.globalColors.text.normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            DeleteButton(visible = isHovered, onClick = onDelete)
        }
        if (showDivider) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(start = 28.dp)
                        .background(
                            JewelTheme.globalColors.borders.normal
                                .copy(alpha = 0.5f),
                        ).align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
fun DeleteButton(
    visible: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val accent = JewelTheme.globalColors.outlines.focused
    Box(
        modifier =
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isHovered) accent.copy(alpha = 0.12f) else Color.Transparent)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            key = AllIconsKeys.Actions.Close,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = if (visible || isHovered) JewelTheme.globalColors.text.normal else Color.Transparent,
        )
    }
}

@Composable
fun EmptyState(
    iconKey: IconKey,
    message: String,
) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                key = iconKey,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = JewelTheme.globalColors.borders.normal,
            )
            Text(
                text = message,
                fontSize = 15.sp,
                color = JewelTheme.globalColors.text.info,
            )
        }
    }
}

@Composable
fun InlineTextField(
    value: String,
    placeholder: StringResource,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp, color = JewelTheme.globalColors.text.normal),
            cursorBrush = SolidColor(JewelTheme.globalColors.outlines.focused),
            modifier =
                Modifier
                    .widthIn(min = 160.dp, max = 220.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(6.dp))
                    .background(JewelTheme.globalColors.panelBackground)
                    .padding(horizontal = 10.dp)
                    .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize()) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(placeholder),
                            fontSize = 13.sp,
                            color = JewelTheme.globalColors.text.info,
                        )
                    }
                    innerTextField()
                }
            },
        )
        IconButton(
            onClick = onConfirm,
            modifier = Modifier.size(28.dp),
            enabled = value.isNotBlank(),
        ) {
            Icon(
                key = AllIconsKeys.Actions.Checked,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                key = AllIconsKeys.Actions.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Positions a popup directly below its anchor, aligning the popup's start edge with the
 * anchor's start edge. Coordinates are clamped to the window bounds.
 */
internal object StartBelowAnchorPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x =
            if (layoutDirection == LayoutDirection.Ltr) {
                anchorBounds.left
            } else {
                anchorBounds.right - popupContentSize.width
            }
        val y = anchorBounds.bottom
        return IntOffset(
            x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y = y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

/**
 * A compact popup for entering a single-line value. The text field receives focus automatically
 * and Enter/Escape confirm/cancel.
 */
@Composable
fun InputPopup(
    title: String,
    value: String,
    placeholder: StringResource,
    confirmText: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    CardSurface(modifier = Modifier.width(240.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = JewelTheme.globalColors.text.normal,
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = JewelTheme.globalColors.text.normal),
                cursorBrush = SolidColor(JewelTheme.globalColors.outlines.focused),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(6.dp))
                        .background(JewelTheme.globalColors.panelBackground)
                        .padding(horizontal = 10.dp)
                        .focusRequester(focusRequester)
                        .onKeyEvent { keyEvent ->
                            when (keyEvent.key) {
                                Key.Enter -> {
                                    if (value.isNotBlank()) onConfirm()
                                    true
                                }
                                Key.Escape -> {
                                    onCancel()
                                    true
                                }
                                else -> false
                            }
                        },
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize()) {
                        if (value.isEmpty()) {
                            Text(
                                text = stringResource(placeholder),
                                fontSize = 13.sp,
                                color = JewelTheme.globalColors.text.info,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(Res.string.settings_cancel), fontSize = 12.sp)
                }
                DefaultButton(onClick = onConfirm, enabled = value.isNotBlank()) {
                    Text(confirmText, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * A compact confirmation popup with title, message and cancel/confirm actions.
 */
@Composable
fun ConfirmPopup(
    title: String,
    message: String,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    CardSurface(modifier = Modifier.width(260.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = JewelTheme.globalColors.text.normal,
            )
            Text(
                text = message,
                fontSize = 13.sp,
                color = JewelTheme.globalColors.text.info,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onCancel) {
                    Text(cancelText, fontSize = 12.sp)
                }
                DefaultButton(onClick = onConfirm) {
                    Text(confirmText, fontSize = 12.sp)
                }
            }
        }
    }
}
