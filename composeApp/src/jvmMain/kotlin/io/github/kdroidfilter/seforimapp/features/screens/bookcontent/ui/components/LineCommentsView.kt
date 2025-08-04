package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import seforimapp.composeapp.generated.resources.Res
import seforimapp.composeapp.generated.resources.commentaries
import seforimapp.composeapp.generated.resources.notorashihebrew
import seforimapp.composeapp.generated.resources.notoserifhebrew

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
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(Res.string.commentaries),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp ,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        when {
            selectedLine == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Select a line to view its commentaries")
                }
            }
            else -> {
                // State to track the selected commentator
                var selectedCommentator by remember { mutableStateOf<String?>(null) }
                
                // Filter commentaries for the selected line
                val lineCommentaries = remember(commentaries, selectedLine) {
                    commentaries.filter { 
                        it.link.sourceLineId == selectedLine.id && 
                        it.link.connectionType == ConnectionType.COMMENTARY 
                    }
                }
                
                if (lineCommentaries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No commentaries available for this line")
                    }
                } else {
                    EnhancedHorizontalSplitPane(
                        splitPaneState = splitPaneState,
                        modifier = Modifier.fillMaxSize(),
                        firstMinSize = 150f, // Minimum width for commentators list (30% of screen)
                        firstContent = {
                            // Left side: Commentators list
                            CommentatorsListView(
                                commentaries = lineCommentaries,
                                selectedCommentator = selectedCommentator,
                                onCommentatorSelected = { commentator ->
                                    selectedCommentator = if (selectedCommentator == commentator) null else commentator
                                }
                            )
                        },
                        secondContent = {
                            // Right side: Filtered commentaries
                            CommentariesContent(
                                selectedLine = selectedLine,
                                commentaries = lineCommentaries,
                                selectedCommentator = selectedCommentator,
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

@Composable
private fun CommentariesContent(
    selectedLine: Line,
    commentaries: List<CommentaryWithText>,
    selectedCommentator: String? = null,
    commentariesScrollIndex: Int = 0,
    commentariesScrollOffset: Int = 0,
    onCommentClick: (CommentaryWithText) -> Unit,
    onScroll: (Int, Int) -> Unit = { _, _ -> },
    commentTextSize: Float = 14f, // Default to 14sp if not provided
    lineHeight: Float = 1.5f // Default to 1.5 if not provided
) {
    // Filter commentaries by line and connection type
    val lineCommentaries = remember(commentaries, selectedLine) {
        commentaries.filter { 
            it.link.sourceLineId == selectedLine.id && 
            it.link.connectionType == ConnectionType.COMMENTARY 
        }
    }
    
    // Check if a commentator is selected
    if (selectedCommentator == null) {
        // If no commentator is selected, show a message prompting the user to select one
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Please select a commentator from the list to view their commentaries",
                fontSize = commentTextSize.sp
            )
        }
    } else {
        // Filter commentaries by the selected commentator
        val filteredCommentaries = remember(lineCommentaries, selectedCommentator) {
            lineCommentaries.filter { it.targetBookTitle == selectedCommentator }
        }
        
        if (filteredCommentaries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No commentaries available from $selectedCommentator for this line"
                )
            }
        } else {
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
}

@Composable
private fun CommentatorsListView(
    commentaries: List<CommentaryWithText>,
    selectedCommentator: String?,
    onCommentatorSelected: (String) -> Unit
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
                Text("No commentators available")
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
                            val isSelected = commentator == selectedCommentator
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(Unit) { detectTapGestures(onTap = {onCommentatorSelected(commentator)}) }
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .padding(vertical = 8.dp, horizontal = 12.dp)
                            ) {
                                Text(
                                    text = commentator,
                                    fontFamily = FontFamily(Font(resource = Res.font.notoserifhebrew, weight = if (isSelected) FontWeight.Bold else FontWeight.Normal) ),
                                    fontSize = 14.sp,
                                )
                            }
                            
                            Divider(
                                Orientation.Horizontal,
                                thickness = 0.5.dp,
                                color = JewelTheme.globalColors.borders.normal
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
    
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
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
                Divider(
                    Orientation.Horizontal,
                    thickness = 0.5.dp,
                    color = JewelTheme.globalColors.borders.normal
                )
            }
        }
    }
}