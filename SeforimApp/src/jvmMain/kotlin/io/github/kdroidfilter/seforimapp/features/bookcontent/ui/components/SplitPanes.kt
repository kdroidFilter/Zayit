package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.presentation.utils.cursorForHorizontalResize
import io.github.kdroidfilter.seforimapp.core.presentation.utils.cursorForVerticalResize
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.SplitPaneState
import org.jetbrains.compose.splitpane.VerticalSplitPane
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider

@Stable
@JvmInline
value class StableSplitPaneState
    @OptIn(ExperimentalSplitPaneApi::class)
    constructor(
        val value: SplitPaneState,
    )

@OptIn(ExperimentalSplitPaneApi::class)
fun SplitPaneState.asStable(): StableSplitPaneState = StableSplitPaneState(this)

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun EnhancedHorizontalSplitPane(
    splitPaneState: StableSplitPaneState,
    firstContent: @Composable BoxScope.() -> Unit,
    secondContent: (@Composable BoxScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    firstMinSize: Float = 200f,
    secondMinSize: Float = 200f,
    showSplitter: Boolean = true,
    dividerVisibleInIslands: Boolean = false,
) {
    val isIslands = ThemeUtils.isIslandsStyle() && !dividerVisibleInIslands
    val state = splitPaneState.value
    val effectiveSecondMin = if (secondContent == null) 0f else secondMinSize
    val splitterVisible = showSplitter && secondContent != null

    // When the second pane is hidden, expand the first to 100% to avoid blank space
    LaunchedEffect(secondContent == null) {
        if (secondContent == null) {
            state.positionPercentage = 1f
        }
    }

    // When the first pane is hidden (minSize = 0), collapse it to 0% to avoid blank space
    LaunchedEffect(firstMinSize == 0f) {
        if (firstMinSize == 0f) {
            state.positionPercentage = 0f
        }
    }

    // Disable drag when splitter is hidden so the library's default handle
    // (8dp invisible drag zone) is never placed.
    LaunchedEffect(splitterVisible) {
        state.moveEnabled = splitterVisible
    }

    HorizontalSplitPane(
        splitPaneState = state,
        modifier = modifier,
    ) {
        first(firstMinSize.dp) {
            Box(
                modifier = Modifier.fillMaxSize(),
                content = firstContent,
            )
        }
        second(effectiveSecondMin.dp) {
            if (secondContent != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    content = secondContent,
                )
            } else {
                // Keep the pane structure stable even when hidden
                Box(modifier = Modifier.fillMaxSize())
            }
        }
        if (splitterVisible) {
            splitter {
                visiblePart {
                    if (!isIslands) {
                        Divider(
                            Orientation.Vertical,
                            Modifier.fillMaxHeight().width(1.dp),
                            color = JewelTheme.globalColors.borders.disabled,
                        )
                    }
                }
                handle {
                    Box(
                        Modifier
                            .width(5.dp)
                            .fillMaxHeight()
                            .markAsHandle()
                            .cursorForHorizontalResize(),
                        contentAlignment = Alignment.Center,
                    ) {}
                }
            }
        }
    }
}

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun EnhancedVerticalSplitPane(
    splitPaneState: StableSplitPaneState,
    firstContent: @Composable BoxScope.() -> Unit,
    secondContent: (@Composable BoxScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    firstMinSize: Float = 200f,
    secondMinSize: Float = 200f,
    showSplitter: Boolean = true,
    dividerVisibleInIslands: Boolean = false,
) {
    val isIslands = ThemeUtils.isIslandsStyle() && !dividerVisibleInIslands
    val state = splitPaneState.value
    val effectiveSecondMin = if (secondContent == null) 0f else secondMinSize
    val splitterVisible = showSplitter && secondContent != null

    // When the second pane is hidden, expand the first to 100% to avoid blank space
    LaunchedEffect(secondContent == null) {
        if (secondContent == null) {
            state.positionPercentage = 1f
        }
    }

    // When the first pane is hidden (minSize = 0), collapse it to 0% to avoid blank space
    LaunchedEffect(firstMinSize == 0f) {
        if (firstMinSize == 0f) {
            state.positionPercentage = 0f
        }
    }

    // Disable drag when splitter is hidden so the library's default handle
    // (8dp invisible drag zone) is never placed.
    LaunchedEffect(splitterVisible) {
        state.moveEnabled = splitterVisible
    }

    VerticalSplitPane(
        splitPaneState = state,
        modifier = modifier,
    ) {
        first(firstMinSize.dp) {
            Box(
                modifier = Modifier.fillMaxSize(),
                content = firstContent,
            )
        }
        second(effectiveSecondMin.dp) {
            if (secondContent != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    content = secondContent,
                )
            } else {
                // Keep the pane structure stable even when hidden
                Box(modifier = Modifier.fillMaxSize())
            }
        }
        if (splitterVisible) {
            splitter {
                visiblePart {
                    if (!isIslands) {
                        Divider(
                            Orientation.Horizontal,
                            Modifier.fillMaxWidth().height(1.dp),
                            color = JewelTheme.globalColors.borders.disabled,
                        )
                    }
                }
                handle {
                    Box(
                        Modifier
                            .height(5.dp)
                            .fillMaxWidth()
                            .markAsHandle()
                            .cursorForVerticalResize(),
                        contentAlignment = Alignment.Center,
                    ) {}
                }
            }
        }
    }
}
