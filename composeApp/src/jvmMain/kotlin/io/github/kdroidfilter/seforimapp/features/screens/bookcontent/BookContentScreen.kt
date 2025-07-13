package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import androidx.compose.runtime.Composable
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.components.MainBookContentLayout
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main screen composable for the book content feature.
 * This is the entry point for the book content screen.
 */
@Composable
fun BookContentScreen() {
    val viewModel: BookContentViewModel = koinViewModel()
    val state = rememberBookContentState(viewModel)
    MainBookContentLayout(state, viewModel::onEvent)
}
