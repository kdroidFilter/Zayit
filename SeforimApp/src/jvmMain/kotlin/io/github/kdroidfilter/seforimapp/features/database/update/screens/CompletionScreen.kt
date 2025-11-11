package io.github.kdroidfilter.seforimapp.features.database.update.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.icons.CheckCircle
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import seforimapp.seforimapp.generated.resources.*

@Composable
fun CompletionScreen(onUpdateCompleted: () -> Unit) {
    OnBoardingScaffold(title = stringResource(Res.string.db_update_completion_title)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
        ) {
            // Success icon
            Icon(
                CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = JewelTheme.globalColors.text.normal
            )
            
            // Title
            Text(
                text = stringResource(Res.string.db_update_success_title),
                textAlign = TextAlign.Center
            )
            
            // Description
            Text(
                text = stringResource(Res.string.db_update_success_message),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            
            // Continue button
            DefaultButton(
                onClick = onUpdateCompleted
            ) {
                Text(stringResource(Res.string.db_update_continue))
            }
        }
    }
}