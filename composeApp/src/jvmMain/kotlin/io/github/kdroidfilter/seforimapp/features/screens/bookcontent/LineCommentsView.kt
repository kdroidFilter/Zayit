package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

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
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.SimpleTabContent
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import seforimapp.composeapp.generated.resources.Res
import seforimapp.composeapp.generated.resources.commentaries

@Composable
fun LineCommentsView(
    selectedLine: Line?,
    commentaries: List<CommentaryWithText>,
    onCommentClick: (CommentaryWithText) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header
        Text(
            text = stringResource(Res.string.commentaries),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (selectedLine == null) {
            // No line selected
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Select a line to view its commentaries")
            }
        } else {
            // Filter commentaries for the selected line
            val lineCommentaries = commentaries.filter { it.link.sourceLineId == selectedLine.id }

            if (lineCommentaries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No commentaries available for this line")
                }
            } else {
                Text(
                    text = "פירושים (${lineCommentaries.size}):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // State for selected tab
                var selectedTabIndex by remember { mutableStateOf(0) }

                // Get all connection types present in the commentaries
                val connectionTypes = lineCommentaries
                    .map { it.link.connectionType }
                    .distinct()
                    .ifEmpty { listOf(ConnectionType.OTHER) } // Fallback if no types found

                // Add "ALL" as the first tab with Hebrew titles
                val connectionTypeNames = connectionTypes.map { 
                    when (it) {
                        ConnectionType.COMMENTARY -> "פירוש"
                        ConnectionType.TARGUM -> "תרגום"
                        ConnectionType.REFERENCE -> "הפניה"
                        ConnectionType.OTHER -> "אחר"
                    }
                }
                val tabTitles = listOf("הכל") + connectionTypeNames

                // Tab row
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
                            onClick = { selectedTabIndex = index }
                        )
                    }
                }

                TabStrip(
                    tabs = tabs,
                    style = JewelTheme.defaultTabStyle,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Filter commentaries by selected connection type
                val filteredCommentaries = if (selectedTabIndex == 0) {
                    // "ALL" tab selected - show all commentaries
                    lineCommentaries
                } else {
                    // Filter by selected connection type
                    // Check if the index is within bounds
                    if (selectedTabIndex - 1 < connectionTypes.size) {
                        val selectedType = connectionTypes[selectedTabIndex - 1]
                        lineCommentaries.filter { it.link.connectionType == selectedType }
                    } else {
                        // Fallback to showing all commentaries if the index is out of bounds
                        lineCommentaries
                    }
                }

                // Group commentaries by book
                val commentariesByBook = filteredCommentaries.groupBy { it.targetBookTitle }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    commentariesByBook.forEach { (bookTitle, bookCommentaries) ->
                        item {
                            // Book header
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

                        items(bookCommentaries) { commentary ->
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
                            Divider(Orientation.Horizontal, thickness = 0.5.dp, color = JewelTheme.globalColors.borders.normal)
                        }
                    }
                }
            }
        }
    }
}
