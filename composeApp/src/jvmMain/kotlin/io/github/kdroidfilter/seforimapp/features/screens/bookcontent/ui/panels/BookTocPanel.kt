package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.BookContentUiState
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.TocView
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.Text
import seforimapp.composeapp.generated.resources.Res
import seforimapp.composeapp.generated.resources.select_book_for_toc
import seforimapp.composeapp.generated.resources.table_of_contents

@Composable
fun BookTocPanel(
    uiState: BookContentUiState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp).fillMaxHeight()
    ) {
        Text(
            text = stringResource(Res.string.table_of_contents),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        when {
            uiState.navigation.selectedBook == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(Res.string.select_book_for_toc))
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxHeight()) {
                    TocView(
                        uiState = uiState,
                        onEvent = onEvent,
                        modifier = Modifier.fillMaxHeight()
                    )
                }
            }
        }
    }
}

