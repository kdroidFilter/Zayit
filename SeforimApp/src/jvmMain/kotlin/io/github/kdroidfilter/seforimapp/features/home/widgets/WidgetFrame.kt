package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Offset
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
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widgets_edit
import seforimapp.seforimapp.generated.resources.home_widgets_remove

/**
 * A widget on the grid with its macOS-style handles: a right click opens its menu (size, remove, edit widgets); in
 * edit mode a "−" badge removes it, and a drag drops it in another widget's place while its content stays inert.
 */
@Composable
internal fun WidgetFrame(
    placement: WidgetPlacement,
    state: HomeWidgetsState,
    drag: WidgetDrag,
) {
    val widget = placement.widget
    var menuOpen by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val dragging = drag.draggedId == widget.id
    val editing = state.editingWidgets
    val shape = RoundedCornerShape(18.dp)

    // Min constraints reach the widget, so it fills its cell (or grows past it when it wraps its content)
    Box(
        propagateMinConstraints = true,
        modifier =
            Modifier
                .onGloballyPositioned { drag.bounds[widget.id] = it.boundsInRoot() }
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
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                    if (dragging) {
                        scaleX = 1.03f
                        scaleY = 1.03f
                    }
                }.then(if (dragging) Modifier.shadow(16.dp, shape) else Modifier),
    ) {
        widget.Content(state, Modifier)
        if (editing) {
            // matchParentSize: the handles take the widget's size instead of passing it their min constraints
            Box(Modifier.matchParentSize()) {
                // Swallows clicks meant for the widget and turns a press into a drag
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (drag.targetId == widget.id) {
                                Modifier.border(2.5.dp, JewelTheme.globalColors.borders.focused, shape)
                            } else {
                                Modifier
                            },
                        ).pointerHoverIcon(PointerIcon.Hand)
                        .pointerInput(widget.id) {
                            var press = Offset.Zero
                            detectDragGestures(
                                onDragStart = {
                                    press = it
                                    drag.draggedId = widget.id
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount
                                    val origin = drag.bounds[widget.id]?.topLeft ?: Offset.Zero
                                    drag.targetId = drag.widgetAt(origin + press + dragOffset)
                                },
                                onDragEnd = {
                                    val target = availableHomeWidgets.firstOrNull { it.id == drag.targetId }
                                    if (target != null) HomeWidgetsLayout.move(widget, target)
                                    drag.draggedId = null
                                    drag.targetId = null
                                    dragOffset = Offset.Zero
                                },
                                onDragCancel = {
                                    drag.draggedId = null
                                    drag.targetId = null
                                    dragOffset = Offset.Zero
                                },
                            )
                        },
                )
                RemoveBadge(
                    onClick = { HomeWidgetsLayout.remove(widget) },
                    modifier = Modifier.align(Alignment.TopStart).offset((-8).dp, (-8).dp),
                )
            }
        }
        if (menuOpen) {
            WidgetMenu(placement, state, onDismiss = { menuOpen = false })
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
