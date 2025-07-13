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
        onEvent = viewModel::onEvent
    )
}