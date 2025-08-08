package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HorizontalLateralBar(content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        content()
        HorizontalDivider()
    }
}