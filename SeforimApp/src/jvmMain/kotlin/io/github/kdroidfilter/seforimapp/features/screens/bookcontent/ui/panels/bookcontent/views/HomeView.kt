package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.texteffects.TypewriterPlaceholder
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import seforimapp.seforimapp.generated.resources.*

// Enum pour les filtres
enum class SearchFilter() {
    REFERENCE,
    TEXT
}

@Composable
fun HomeView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(16.dp).fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Image(
                painterResource(Res.drawable.zayit_transparent),
                contentDescription = null,
                modifier = Modifier.size(256.dp)
            )
//            Text(stringResource(Res.string.select_book))
            val searchState = remember { TextFieldState() }

            var selectedFilter by remember {
                mutableStateOf(SearchFilter.REFERENCE)

            }

            SearchBar(
                state = searchState,
                selectedFilter = selectedFilter,
                onFilterChange = { selectedFilter = it }
            )
        }
    }

}

@Composable
private fun SearchBar(
    state: TextFieldState,
    selectedFilter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    // Hints from string resources
    val referenceHints = listOf(
        stringResource(Res.string.reference_hint_1),
        stringResource(Res.string.reference_hint_2),
        stringResource(Res.string.reference_hint_3),
        stringResource(Res.string.reference_hint_4),
        stringResource(Res.string.reference_hint_5)
    )

    val textHints = listOf(
        stringResource(Res.string.text_hint_1),
        stringResource(Res.string.text_hint_2),
        stringResource(Res.string.text_hint_3),
        stringResource(Res.string.text_hint_4),
        stringResource(Res.string.text_hint_5)
    )

    val hints = if (selectedFilter == SearchFilter.REFERENCE) referenceHints else textHints

    // Restart animation cleanly when switching filter
    var filterVersion by remember { mutableStateOf(0) }
    LaunchedEffect(selectedFilter) { filterVersion++ }

    // Disable placeholder animation while user is typing
    val isUserTyping by remember { derivedStateOf { state.text.isNotEmpty() } }

    TextField(
        state = state,
        modifier = modifier.width(600.dp).height(40.dp),
        placeholder = {
            key(filterVersion) {
                TypewriterPlaceholder(
                    hints = hints,
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF9AA0A6)),
                    typingDelayPerChar = 155L,
                    deletingDelayPerChar = 45L,
                    holdDelayMs = 1600L,
                    preTypePauseMs = 500L,
                    postDeletePauseMs = 450L,
                    speedMultiplier = 1.15f, // a tad slower overall
                    enabled = !isUserTyping
                )
            }
        },
        trailingIcon = {
            IntegratedSwitch(
                selectedFilter = selectedFilter,
                onFilterChange = onFilterChange
            )
        },
        textStyle = TextStyle(fontSize = 13.sp)
    )
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IntegratedSwitch(
    selectedFilter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier
) {

        Row(
            modifier = modifier
                .clip(RoundedCornerShape(20.dp))
                .background(JewelTheme.globalColors.panelBackground)
                .border(
                    width = 1.dp,
                    color = JewelTheme.globalColors.borders.disabled,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            SearchFilter.entries.forEach { filter ->
                Tooltip(
                    tooltip = {
                        Text(
                            text = when (filter) {
                                SearchFilter.REFERENCE -> stringResource(Res.string.search_mode_reference_explicit)
                                SearchFilter.TEXT -> stringResource(Res.string.search_mode_text_explicit)
                            },
                            fontSize = 13.sp
                        )
                    }
                ) {
                    FilterButton(
                        text = when (filter) {
                            SearchFilter.REFERENCE -> stringResource(Res.string.search_mode_reference)
                            SearchFilter.TEXT -> stringResource(Res.string.search_mode_text)
                        },
                        isSelected = selectedFilter == filter,
                        onClick = { onFilterChange(filter) }
                    )
                }
            }
        }

}

@Composable
private fun FilterButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF0E639C) else Color.Transparent,
        animationSpec = tween(200),
        label = "backgroundColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFFCCCCCC),
        animationSpec = tween(200),
        label = "textColor"
    )

    Text(
        text = text,
        modifier = modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor)
            .clickable(indication = null, interactionSource = MutableInteractionSource()) { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .defaultMinSize(minWidth = 45.dp),
        color = textColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        fontFamily = FontFamily.Monospace
    )
}


@androidx.compose.desktop.ui.tooling.preview.Preview
@Composable
fun HomeViewPreview() {
    PreviewContainer {
        HomeView()
    }
}