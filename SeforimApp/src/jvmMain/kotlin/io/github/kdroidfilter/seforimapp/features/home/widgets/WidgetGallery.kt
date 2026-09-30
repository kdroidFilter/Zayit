package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widgets_add
import seforimapp.seforimapp.generated.resources.home_widgets_added
import seforimapp.seforimapp.generated.resources.home_widgets_done
import seforimapp.seforimapp.generated.resources.home_widgets_gallery_hint
import seforimapp.seforimapp.generated.resources.home_widgets_gallery_title
import seforimapp.seforimapp.generated.resources.home_widgets_reset
import kotlin.math.roundToInt

private const val PREVIEW_SCALE = 0.42f

/** The macOS widget gallery: every widget previewed at the size it would be added at, one click to add it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WidgetGallery(
    state: HomeWidgetsState,
    placed: List<WidgetPlacement>,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.92f))
            .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.home_widgets_gallery_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(Res.string.home_widgets_gallery_hint),
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.info,
                )
            }
            Link(stringResource(Res.string.home_widgets_reset), onClick = { HomeWidgetsLayout.reset() })
            DefaultButton(onClick = { state.editingWidgets = false }) {
                Text(stringResource(Res.string.home_widgets_done))
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            availableHomeWidgets.filter { it.isSupported }.forEach { widget ->
                GalleryItem(widget, state, added = placed.any { it.widget.id == widget.id })
            }
        }
    }
}

@Composable
private fun GalleryItem(
    widget: HomeWidget,
    state: HomeWidgetsState,
    added: Boolean,
) {
    var size by remember(widget) { mutableStateOf(widget.defaultSize) }
    val grid = widget.sizes.getValue(size)
    val add = { HomeWidgetsLayout.add(widget, size) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.alpha(if (added) 0.45f else 1f)) {
            WidgetPreview(widget, grid, state)
            // On top of the preview, so its own buttons never get the click
            Box(
                Modifier
                    .matchParentSize()
                    .then(if (added) Modifier else Modifier.pointerHoverIcon(PointerIcon.Hand))
                    .pointerInput(added) { detectTapGestures { if (!added) add() } },
            )
        }
        Text(stringResource(widget.title), fontWeight = FontWeight.SemiBold)
        if (widget.sizes.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                WidgetSize.entries.filter { it in widget.sizes }.forEach { option ->
                    SizeChip(stringResource(option.label), selected = option == size, onClick = { size = option })
                }
            }
        }
        if (added) {
            Text(stringResource(Res.string.home_widgets_added), fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
        } else {
            OutlinedButton(onClick = add) { Text("+ " + stringResource(Res.string.home_widgets_add)) }
        }
    }
}

/** The widget itself at its real size, scaled down; its content stays inert (the frame above takes the clicks). */
@Composable
private fun WidgetPreview(
    widget: HomeWidget,
    grid: GridSize,
    state: HomeWidgetsState,
) {
    Box(
        propagateMinConstraints = true,
        modifier =
            Modifier
                .clip(RoundedCornerShape(18.dp * PREVIEW_SCALE))
                .layout { measurable, _ ->
                    val width = grid.width().roundToPx()
                    val height = grid.height().roundToPx()
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout((width * PREVIEW_SCALE).roundToInt(), (height * PREVIEW_SCALE).roundToInt()) {
                        placeable.placeWithLayer(0, 0) {
                            scaleX = PREVIEW_SCALE
                            scaleY = PREVIEW_SCALE
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                    }
                },
    ) {
        // No live Filament scene in a thumbnail: those cards show their frame only
        CompositionLocalProvider(LocalTabSelected provides false) {
            widget.Content(state, Modifier)
        }
    }
}

@Composable
private fun SizeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Text(
        text = label,
        fontSize = 11.sp,
        color = if (selected) JewelTheme.globalColors.text.selected else JewelTheme.globalColors.text.normal,
        modifier =
            Modifier
                .clip(shape)
                .background(
                    if (selected) {
                        JewelTheme.globalColors.outlines.focused
                            .copy(alpha = 0.25f)
                    } else {
                        JewelTheme.globalColors.panelBackground
                    },
                ).border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
