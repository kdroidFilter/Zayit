package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import seforimapp.composeapp.generated.resources.Res
import seforimapp.composeapp.generated.resources.commentaries

private val connectionTypeNames = mapOf(
    ConnectionType.COMMENTARY to "פירוש",
    ConnectionType.TARGUM to "תרגום",
    ConnectionType.REFERENCE to "הפניה",
    ConnectionType.OTHER to "אחר"
)

@Composable
fun LineCommentsView(
    selectedLine: Line?,
    commentaries: List<CommentaryWithText>,
    onCommentClick: (CommentaryWithText) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(Res.string.commentaries),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
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
                CommentariesContent(
                    selectedLine = selectedLine,
                    commentaries = commentaries,
                    onCommentClick = onCommentClick
                )
            }
        }
    }
}

@Composable
private fun CommentariesContent(
    selectedLine: Line,
    commentaries: List<CommentaryWithText>,
    onCommentClick: (CommentaryWithText) -> Unit
) {
    val lineCommentaries = remember(commentaries, selectedLine) {
        commentaries.filter { it.link.sourceLineId == selectedLine.id }
    }
    
    if (lineCommentaries.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No commentaries available for this line")
        }
    } else {
        var selectedTabIndex by remember { mutableStateOf(0) }
        
        val connectionTypes = remember(lineCommentaries) {
            lineCommentaries
                .map { it.link.connectionType }
                .distinct()
                .ifEmpty { listOf(ConnectionType.OTHER) }
        }
        
        val tabTitles = remember(connectionTypes) {
            listOf("הכל") + connectionTypes.map { connectionTypeNames[it] ?: "אחר" }
        }
        
        Text(
            text = "פירושים (${lineCommentaries.size}):",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        CommentariesTabs(
            tabTitles = tabTitles,
            selectedTabIndex = selectedTabIndex,
            onTabSelected = { selectedTabIndex = it }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        val filteredCommentaries = remember(lineCommentaries, selectedTabIndex, connectionTypes) {
            if (selectedTabIndex == 0) {
                lineCommentaries
            } else if (selectedTabIndex - 1 < connectionTypes.size) {
                val selectedType = connectionTypes[selectedTabIndex - 1]
                lineCommentaries.filter { it.link.connectionType == selectedType }
            } else {
                lineCommentaries
            }
        }
        
        CommentariesList(
            commentaries = filteredCommentaries,
            onCommentClick = onCommentClick
        )
    }
}

@Composable
private fun CommentariesTabs(
    tabTitles: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = remember(tabTitles, selectedTabIndex) {
        tabTitles.mapIndexed { index, title ->
            TabData.Default(
                selected = index == selectedTabIndex,
                content = { tabState ->
                    SimpleTabContent(
                        label = title,
                        state = tabState
                    )
                },
                onClick = { onTabSelected(index) }
            )
        }
    }
    
    TabStrip(
        tabs = tabs,
        style = JewelTheme.defaultTabStyle,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CommentariesList(
    commentaries: List<CommentaryWithText>,
    onCommentClick: (CommentaryWithText) -> Unit
) {
    val commentariesByBook = remember(commentaries) {
        commentaries.groupBy { it.targetBookTitle }
    }
    
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        commentariesByBook.forEach { (bookTitle, bookCommentaries) ->
            item(key = bookTitle) {
                Text(
                    text = bookTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.LightGray.copy(alpha = 0.2f))
                        .padding(8.dp)
                )
            }
            
            items(
                items = bookCommentaries,
                key = { it.link.id }
            ) { commentary ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCommentClick(commentary) }
                        .padding(vertical = 8.dp, horizontal = 16.dp)
                ) {
                    Text(
                        text = commentary.targetText,
                        fontSize = 14.sp
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