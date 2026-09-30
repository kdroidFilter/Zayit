package io.github.kdroidfilter.seforimapp.features.home.widgets

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberCursorPositionProvider
import dev.nucleusframework.window.tao.TaoPointerIcons
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widgets_edit
import seforimapp.seforimapp.generated.resources.home_widgets_remove
import sh.calvin.reorderable.ReorderableCollectionItemScope

/**
 * A widget on the grid with its macOS-style handles: a right click opens its menu (size, remove, edit widgets), and a
 * grip shown on hover drags it at any time, its content staying usable; the others make room (Reorderable) and
 * [onDrop] saves the order. In edit mode the whole widget drags (its content inert) and a "−" badge removes it.
 */
@Composable
internal fun ReorderableCollectionItemScope.WidgetFrame(
    placement: WidgetPlacement,
    state: HomeWidgetsState,
    dragging: Boolean,
    onDrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val widget = placement.widget
    val drag = state.drag
    var menuOpen by remember { mutableStateOf(false) }
    val editing = state.editingWidgets
    val shape = RoundedCornerShape(18.dp)
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val grabIcon = if (dragging) TaoPointerIcons.Grabbing else TaoPointerIcons.Grab

    // Min constraints reach the widget, so it fills its cell
    Box(
        propagateMinConstraints = true,
        modifier =
            modifier
                .onGloballyPositioned { drag.bounds[widget.id] = it.boundsInRoot() }
                .hoverable(hover)
                // Initial pass: seen before the widget's own handlers, which may consume the press
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                menuOpen = true
                            }
                        }
                    }
                }.graphicsLayer {
                    if (dragging) {
                        scaleX = 1.03f
                        scaleY = 1.03f
                    }
                }.then(if (dragging) Modifier.shadow(16.dp, shape) else Modifier),
    ) {
        widget.Content(state, Modifier)
        // matchParentSize: the handles take the widget's size instead of passing it their min constraints
        Box(Modifier.matchParentSize()) {
            if (drag.targetId == widget.id) {
                Box(Modifier.fillMaxSize().border(2.5.dp, JewelTheme.globalColors.borders.focused, shape))
            }
            if (editing) {
                // Swallows clicks meant for the widget and turns a press into a drag
                Box(Modifier.fillMaxSize().pointerHoverIcon(grabIcon).draggableHandle(onDragStopped = onDrop))
                RemoveBadge(
                    onClick = { HomeWidgetsLayout.remove(widget) },
                    modifier = Modifier.align(Alignment.TopStart).offset((-8).dp, (-8).dp),
                )
            } else if (hovered || dragging) {
                DragGrip(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .pointerHoverIcon(grabIcon)
                            .draggableHandle(onDragStopped = onDrop),
                )
            }
        }
        if (menuOpen) {
            WidgetMenu(placement, state, onDismiss = { menuOpen = false })
        }
    }
}

/** The ⠿ pill a widget is dragged by outside edit mode, dark enough to read over the black 3D cards and light ones. */
@Composable
private fun DragGrip(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = 4.dp)
            .size(width = 44.dp, height = 18.dp)
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(2) { Box(Modifier.size(3.dp).background(Color.White.copy(alpha = 0.9f), CircleShape)) }
                }
            }
        }
    }
}

@Composable
private fun RemoveBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.home_widgets_remove)
    Box(
        modifier
            .size(24.dp)
            .shadow(3.dp, CircleShape)
            .background(Color(0xFF8E8E93), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .pointerHoverIcon(PointerIcon.Default),
        contentAlignment = Alignment.Center,
    ) {
        // The macOS "−": a white bar, drawn so no font can miss it
        Box(Modifier.size(width = 10.dp, height = 2.dp).background(Color.White, RoundedCornerShape(1.dp)))
    }
}

@Composable
private fun WidgetMenu(
    placement: WidgetPlacement,
    state: HomeWidgetsState,
    onDismiss: () -> Unit,
) {
    val widget = placement.widget
    // Resource strings must be resolved outside MenuScope
    val sizeLabels = widget.sizes.keys.associateWith { stringResource(it.label) }
    val removeLabel = stringResource(Res.string.home_widgets_remove)
    val editLabel = stringResource(Res.string.home_widgets_edit)
    PopupMenu(
        onDismissRequest = {
            onDismiss()
            true
        },
        popupPositionProvider = rememberCursorPositionProvider(),
    ) {
        if (widget.sizes.size > 1) {
            WidgetSize.entries.filter { it in widget.sizes }.forEach { size ->
                selectableItem(
                    selected = false,
                    iconKey = if (size == placement.size) AllIconsKeys.Actions.Checked else null,
                    onClick = {
                        onDismiss()
                        HomeWidgetsLayout.resize(widget, size)
                    },
                ) { Text(sizeLabels.getValue(size)) }
            }
            separator()
        }
        selectableItem(
            selected = false,
            iconKey = AllIconsKeys.General.Remove,
            onClick = {
                onDismiss()
                HomeWidgetsLayout.remove(widget)
            },
        ) { Text(removeLabel) }
        if (!state.editingWidgets) {
            selectableItem(
                selected = false,
                iconKey = AllIconsKeys.Actions.Edit,
                onClick = {
                    onDismiss()
                    state.editingWidgets = true
                },
            ) { Text(editLabel) }
        }
    }
}
