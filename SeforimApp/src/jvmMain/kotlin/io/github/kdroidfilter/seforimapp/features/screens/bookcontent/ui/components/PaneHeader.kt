package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.components.HorizontalDivider
import io.github.kdroidfilter.seforimapp.core.presentation.components.WarningBanner
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.commentaries
import seforimapp.seforimapp.generated.resources.max_commentators_limit

@Composable
fun PaneHeader(
    label: String,
    warningMsg: String? = null,
    showWarning: Boolean? = null,
    onCloseWarning: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    onHide: () -> Unit
) {
    val headerHoverSource = interactionSource ?: remember { MutableInteractionSource() }
    val isHovered by headerHoverSource.collectIsHoveredAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(headerHoverSource),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                if (warningMsg != null && showWarning != null && onCloseWarning != null && showWarning) {
                    WarningBanner(
                        message = warningMsg,
                        onClose = onCloseWarning,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            AnimatedVisibility(
                visible = isHovered,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                IconActionButton(
                    key = AllIconsKeys.Windows.Minimize,
                    onClick = onHide,
                    contentDescription = "Hide panel"
                )
            }
        }

        HorizontalDivider(
            color = JewelTheme.globalColors.borders.normal
        )
    }
}


@Preview
@Composable
fun PaneHeaderPreview() {
    PreviewContainer {
        PaneHeader(
            label = stringResource(Res.string.commentaries),
            warningMsg = stringResource(Res.string.max_commentators_limit),
            showWarning = false,
            onHide = {}
        )
    }
}

@Preview
@Composable
fun PaneHeaderWithWarningPreview() {
    PreviewContainer {
        PaneHeader(
            label = stringResource(Res.string.commentaries),
            warningMsg = stringResource(Res.string.max_commentators_limit),
            showWarning = true,
            onCloseWarning = {},
            onHide = {}
        )
    }
}