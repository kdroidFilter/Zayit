package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import androidx.compose.runtime.*
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.MainBookContentLayout
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BookContentScreen() {
    val viewModel: BookContentViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    MainBookContentLayout(
        uiState = uiState,
        linesPagingData = viewModel.linesPagingData, // Pass the lines paging data flow
        commentsPagingData = viewModel.commentsPagingData, // Pass the comments paging data flow
        buildCommentariesPagerFor = viewModel::buildCommentariesPagerFor,
        getAvailableCommentatorsForLine = viewModel::getAvailableCommentatorsForLine,
        onEvent = viewModel::onEvent
    )
}