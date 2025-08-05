package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.components.HorizontalDivider
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.LocalCheckboxStyle
import seforimapp.composeapp.generated.resources.*

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun LineCommentsView(
    selectedLine: Line?,
    commentaries: List<CommentaryWithText>,
    commentariesScrollIndex: Int = 0,
    commentariesScrollOffset: Int = 0,
    onCommentClick: (CommentaryWithText) -> Unit = {},
    onScroll: (Int, Int) -> Unit = { _, _ -> },
    splitPaneState: SplitPaneState = rememberSplitPaneState(0.3f) // Default to 30% for left pane
) {
    // Collect text size from settings
    val rawTextSize by AppSettings.textSizeFlow.collectAsState()
    
    // Animate text size changes for smoother transitions
    val textSize by animateFloatAsState(
        targetValue = rawTextSize,
        animationSpec = tween(durationMillis = 300),
        label = "commentTextSizeAnimation"
    )
    
    // Apply scaling factor to ensure comments are always smaller than main text
    val commentTextSize = textSize * 0.875f
    
    // Collect line height from settings
    val rawLineHeight by AppSettings.lineHeightFlow.collectAsState()
    
    // Animate line height changes for smoother transitions
    val lineHeight by animateFloatAsState(
        targetValue = rawLineHeight,
        animationSpec = tween(durationMillis = 300),
        label = "commentLineHeightAnimation"
    )
    
    // State to track if user tried to select more than 4 commentators
    var showMaxCommentatorsWarning by remember { mutableStateOf(false) }
    
    // Auto-hide the warning after 5 seconds
    LaunchedEffect(showMaxCommentatorsWarning) {
        if (showMaxCommentatorsWarning) {
            delay(5000)
            showMaxCommentatorsWarning = false
        }
    }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header row with title and warning banner
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Title
            Text(
                text = stringResource(Res.string.commentaries),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(end = 16.dp)
            )
            
            // Warning banner (only shown when needed)
            if (showMaxCommentatorsWarning) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = Color(0xFFFFF4E5), // Light orange background
                            shape = RoundedCornerShape(4.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFFFFB74D), // Orange border
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Warning icon
                        Text(
                            text = "⚠️",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        
                        // Warning message
                        Text(
                            text = stringResource(Res.string.max_commentators_limit),
                            color = Color(0xFF7A4F01), // Dark orange text
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Close button (small cross)
                        Text(
                            text = "✕",
                            fontSize = 14.sp,
                            color = Color(0xFF7A4F01), // Dark orange text to match the message
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        showMaxCommentatorsWarning = false
                                    }
                                }
                        )
                    }
                }
            }
        }
        
        when {
            selectedLine == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.select_line_for_commentaries)
                    )
                }
            }
            else -> {
                // Filter commentaries for the selected line
                val lineCommentaries = remember(commentaries, selectedLine) {
                    commentaries.filter { 
                        it.link.sourceLineId == selectedLine.id && 
                        it.link.connectionType == ConnectionType.COMMENTARY 
                    }
                }
                
                // Extract available commentators for the current line
                val availableCommentators = remember(lineCommentaries) {
                    lineCommentaries.map { it.targetBookTitle }.distinct().toSet()
                }
                
                // State to track the selected commentators (up to 4)
                // Reset selected commentators if they're no longer available when changing lines or books
                var selectedCommentators by remember(availableCommentators) { 
                    // Filter out any previously selected commentators that are no longer available
                    val currentState = mutableStateOf<Set<String>>(emptySet<String>())
                    val currentValue = currentState.value
                    
                    // If we have any selected commentators, filter them to keep only those still available
                    if (currentValue.isNotEmpty()) {
                        val filteredSelection = currentValue.filter { it in availableCommentators }.toSet()
                        currentState.value = filteredSelection
                    }
                    
                    currentState
                }
                
                if (lineCommentaries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.no_commentaries_for_line)
                        )
                    }
                } else {
                    EnhancedHorizontalSplitPane(
                        splitPaneState = splitPaneState,
                        modifier = Modifier.fillMaxSize(),
                        firstMinSize = 150f, // Minimum width for commentators list (30% of screen)
                        firstContent = {
                            // Left side: Commentators list with checkboxes
                            CommentatorsListView(
                                commentaries = lineCommentaries,
                                selectedCommentators = selectedCommentators,
                                onCommentatorSelected = { commentator, isSelected ->
                                    selectedCommentators = if (isSelected) {
                                        // If trying to select more than 4, don't add
                                        if (selectedCommentators.size < 4) {
                                            selectedCommentators + commentator
                                        } else {
                                            // Show warning when trying to select more than 4
                                            showMaxCommentatorsWarning = true
                                            selectedCommentators
                                        }
                                    } else {
                                        // Remove from selection
                                        selectedCommentators - commentator
                                    }
                                },
                                onShowWarning = { show ->
                                    showMaxCommentatorsWarning = show
                                }
                            )
                        },
                        secondContent = {
                            // Right side: Filtered commentaries for selected commentators
                            CommentariesContent(
                                selectedLine = selectedLine,
                                commentaries = lineCommentaries,
                                selectedCommentators = selectedCommentators,
                                commentariesScrollIndex = commentariesScrollIndex,
                                commentariesScrollOffset = commentariesScrollOffset,
                                onCommentClick = onCommentClick,
                                onScroll = onScroll,
                                commentTextSize = commentTextSize,
                                lineHeight = lineHeight
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Helper composable to display a centered message
 */
@Composable
private fun CenteredMessage(
    message: String,
    fontSize: Float = 14f
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            fontSize = fontSize.sp
        )
    }
}

/**
 * Helper composable to display a commentator header (name + divider)
 */
@Composable
private fun CommentatorHeader(
    commentator: String,
    commentTextSize: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Commentator name at the top, centered
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = commentator,
                fontWeight = FontWeight.Bold,
                fontSize = (commentTextSize * 1.1f).sp,
                textAlign = TextAlign.Center
            )
        }
    }
}


/**
 * Helper composable to display a single commentator column with its commentaries
 */
@Composable
private fun CommentatorColumn(
    commentator: String,
    commentaries: List<CommentaryWithText>,
    scrollIndex: Int,
    scrollOffset: Int,
    onCommentClick: (CommentaryWithText) -> Unit,
    onScroll: (Int, Int) -> Unit,
    commentTextSize: Float,
    lineHeight: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Display commentator header
        CommentatorHeader(
            commentator = commentator,
            commentTextSize = commentTextSize
        )
        
        // Commentaries for this commentator
        CommentariesList(
            commentaries = commentaries,
            scrollIndex = scrollIndex,
            scrollOffset = scrollOffset,
            onCommentClick = onCommentClick,
            onScroll = onScroll,
            commentTextSize = commentTextSize,
            lineHeight = lineHeight
        )
    }
}

/**
 * Helper composable to display a row of commentators with dividers
 */
@Composable
private fun CommentatorsRow(
    commentators: List<String>,
    commentariesByCommentator: Map<String, List<CommentaryWithText>>,
    scrollIndex: Int,
    scrollOffset: Int,
    onCommentClick: (CommentaryWithText) -> Unit,
    onScroll: (Int, Int) -> Unit,
    commentTextSize: Float,
    lineHeight: Float,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        commentators.forEachIndexed { index, commentator ->
            CommentatorColumn(
                commentator = commentator,
                commentaries = commentariesByCommentator[commentator] ?: emptyList(),
                scrollIndex = scrollIndex,
                scrollOffset = scrollOffset,
                onCommentClick = onCommentClick,
                onScroll = onScroll,
                commentTextSize = commentTextSize,
                lineHeight = lineHeight,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp)
            )

        }
    }
}

/**
 * Helper function to create a row of commentators with appropriate layout
 */
@Composable
private fun CommentatorsRowLayout(
    commentators: List<String>,
    commentariesByCommentator: Map<String, List<CommentaryWithText>>,
    scrollIndex: Int,
    scrollOffset: Int,
    onCommentClick: (CommentaryWithText) -> Unit,
    onScroll: (Int, Int) -> Unit,
    commentTextSize: Float,
    lineHeight: Float,
    modifier: Modifier = Modifier
) {
    CommentatorsRow(
        commentators = commentators,
        commentariesByCommentator = commentariesByCommentator,
        scrollIndex = scrollIndex,
        scrollOffset = scrollOffset,
        onCommentClick = onCommentClick,
        onScroll = onScroll,
        commentTextSize = commentTextSize,
        lineHeight = lineHeight,
        modifier = modifier
    )
}

/**
 * Helper function to create a multi-row layout of commentators
 */
@Composable
private fun MultiRowCommentatorsLayout(
    commentatorGroups: List<List<String>>,
    commentariesByCommentator: Map<String, List<CommentaryWithText>>,
    scrollIndex: Int,
    scrollOffset: Int,
    onCommentClick: (CommentaryWithText) -> Unit,
    onScroll: (Int, Int) -> Unit,
    commentTextSize: Float,
    lineHeight: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        commentatorGroups.forEachIndexed { index, commentators ->
            // Add a row of commentators
            CommentatorsRowLayout(
                commentators = commentators,
                commentariesByCommentator = commentariesByCommentator,
                scrollIndex = scrollIndex,
                scrollOffset = scrollOffset,
                onCommentClick = onCommentClick,
                onScroll = onScroll,
                commentTextSize = commentTextSize,
                lineHeight = lineHeight,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CommentariesContent(
    selectedLine: Line,
    commentaries: List<CommentaryWithText>, // Already filtered for the selected line
    selectedCommentators: Set<String> = emptySet(),
    commentariesScrollIndex: Int = 0,
    commentariesScrollOffset: Int = 0,
    onCommentClick: (CommentaryWithText) -> Unit,
    onScroll: (Int, Int) -> Unit = { _, _ -> },
    commentTextSize: Float = 14f, // Default to 14sp if not provided
    lineHeight: Float = 1.5f // Default to 1.5 if not provided
) {
    // Use the already filtered commentaries
    val lineCommentaries = commentaries
    
    // Check if any commentators are selected
    if (selectedCommentators.isEmpty()) {
        CenteredMessage(
            message = stringResource(Res.string.select_at_least_one_commentator),
            fontSize = commentTextSize
        )
    } else {
        // Filter commentaries by the selected commentators
        val commentariesByCommentator = remember(lineCommentaries, selectedCommentators) {
            selectedCommentators.associateWith { commentator ->
                lineCommentaries.filter { it.targetBookTitle == commentator }
            }
        }
        
        // Check if we have any commentaries for the selected commentators
        val hasCommentaries = commentariesByCommentator.any { it.value.isNotEmpty() }
        
        if (!hasCommentaries) {
            CenteredMessage(
                message = stringResource(Res.string.no_commentaries_from_selected),
                fontSize = commentTextSize
            )
        } else {
            // Display commentaries based on the number of selected commentators
            when (selectedCommentators.size) {
                1 -> {
                    // Single commentator - display commentator name centered at the top
                    val commentator = selectedCommentators.first()
                    val filteredCommentaries = commentariesByCommentator[commentator] ?: emptyList()
                    
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Display commentator header
                        CommentatorHeader(
                            commentator = commentator,
                            commentTextSize = commentTextSize
                        )
                        
                        // Commentaries list
                        CommentariesList(
                            commentaries = filteredCommentaries,
                            scrollIndex = commentariesScrollIndex,
                            scrollOffset = commentariesScrollOffset,
                            onCommentClick = onCommentClick,
                            onScroll = onScroll,
                            commentTextSize = commentTextSize,
                            lineHeight = lineHeight
                        )
                    }
                }
                2 -> {
                    // Two commentators - display in 2 columns
                    CommentatorsRowLayout(
                        commentators = selectedCommentators.toList(),
                        commentariesByCommentator = commentariesByCommentator,
                        scrollIndex = commentariesScrollIndex,
                        scrollOffset = commentariesScrollOffset,
                        onCommentClick = onCommentClick,
                        onScroll = onScroll,
                        commentTextSize = commentTextSize,
                        lineHeight = lineHeight,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                3 -> {
                    // Three commentators - 2 columns in first row, 1 column in second row
                    val firstRow = selectedCommentators.take(2).toList()
                    val secondRow = listOf(selectedCommentators.elementAt(2))
                    
                    MultiRowCommentatorsLayout(
                        commentatorGroups = listOf(firstRow, secondRow),
                        commentariesByCommentator = commentariesByCommentator,
                        scrollIndex = commentariesScrollIndex,
                        scrollOffset = commentariesScrollOffset,
                        onCommentClick = onCommentClick,
                        onScroll = onScroll,
                        commentTextSize = commentTextSize,
                        lineHeight = lineHeight,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                4 -> {
                    // Four commentators - 2 rows of 2 columns each
                    val firstRow = selectedCommentators.take(2).toList()
                    val secondRow = selectedCommentators.drop(2).take(2).toList()
                    
                    MultiRowCommentatorsLayout(
                        commentatorGroups = listOf(firstRow, secondRow),
                        commentariesByCommentator = commentariesByCommentator,
                        scrollIndex = commentariesScrollIndex,
                        scrollOffset = commentariesScrollOffset,
                        onCommentClick = onCommentClick,
                        onScroll = onScroll,
                        commentTextSize = commentTextSize,
                        lineHeight = lineHeight,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    // This shouldn't happen as we limit to 4 commentators, but handle it just in case
                    CenteredMessage(
                        message = stringResource(Res.string.select_between_1_and_4_commentators),
                        fontSize = commentTextSize
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentatorsListView(
    commentaries: List<CommentaryWithText>,
    selectedCommentators: Set<String>,
    onCommentatorSelected: (String, Boolean) -> Unit,
    onShowWarning: (Boolean) -> Unit
) {
    // Extract unique book titles (commentators) from the commentaries
    val commentators = remember(commentaries) {
        commentaries.map { it.targetBookTitle }.distinct().sorted()
    }
    
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        
        if (commentators.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(Res.string.no_commentators_available)
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                val listState = rememberLazyListState()
                
                Row(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        items(commentators) { commentator ->
                            val isSelected = commentator in selectedCommentators
                            
                            CheckboxRow(
                                text = commentator,
                                checked = isSelected,
                                onCheckedChange = { checked -> 
                                    // Show warning if trying to select more than 4 commentators
                                    if (checked && selectedCommentators.size >= 4) {
                                        onShowWarning(true)
                                    }
                                    onCommentatorSelected(commentator, checked) 
                                },
                                colors = LocalCheckboxStyle.current.colors,
                                metrics = LocalCheckboxStyle.current.metrics,
                                icons = LocalCheckboxStyle.current.icons,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            )
                        }
                    }
                    
                    VerticalScrollbar(
                        modifier = Modifier.fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(listState)
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentariesList(
    commentaries: List<CommentaryWithText>,
    scrollIndex: Int = 0,
    scrollOffset: Int = 0,
    onCommentClick: (CommentaryWithText) -> Unit,
    onScroll: (Int, Int) -> Unit = { _, _ -> },
    commentTextSize: Float = 14f, // Default to 14sp if not provided
    lineHeight: Float = 1.5f // Default to 1.5 if not provided
) {
    val commentariesByBook = remember(commentaries) {
        commentaries.groupBy { it.targetBookTitle }
    }
    
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = scrollIndex,
        initialFirstVisibleItemScrollOffset = scrollOffset
    )
    
    // Effect to save scroll position when it changes
    LaunchedEffect(listState) {
        snapshotFlow { 
            Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }.collect { (index, offset) ->
            onScroll(index, offset)
        }
    }
    
    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            commentariesByBook.forEach { (bookTitle, bookCommentaries) ->
                items(
                    items = bookCommentaries,
                    key = { it.link.id }
                ) { commentary ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) { detectTapGestures(onTap = { onCommentClick(commentary) }) }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    ) {
                        Text(
                            text = commentary.targetText,
                            textAlign = TextAlign.Justify,
                            fontFamily = FontFamily(Font(resource = Res.font.notorashihebrew)),
                            fontSize = commentTextSize.sp,
                            lineHeight = (commentTextSize * lineHeight).sp
                        )
                    }
                }
            }
        }
        
        VerticalScrollbar(
            modifier = Modifier.fillMaxHeight(),
            adapter = rememberScrollbarAdapter(listState)
        )
    }
}