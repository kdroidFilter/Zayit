package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider


enum class VerticalLateralBarPosition {
    Start, End
}

@Composable
fun VerticalLateralBar(
    topContent: @Composable () -> Unit,
    bottomContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    position: VerticalLateralBarPosition
) {
    val boxModifier = Modifier
        .fillMaxWidth()
        .padding(4.dp)
    val columnVerticalArrangement = Arrangement.spacedBy(4.dp)

    Row(modifier = modifier.width(56.dp).fillMaxHeight()) {
        if (position == VerticalLateralBarPosition.End) {
            Column {
                VerticalDivider()
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = boxModifier.fillMaxHeight(0.5f),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(verticalArrangement = columnVerticalArrangement) {
                    topContent()
                }
            }
            Box(
                modifier = boxModifier.fillMaxHeight(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    verticalArrangement = columnVerticalArrangement,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Divider(
                        orientation = Orientation.Horizontal,
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .width(1.dp)
                            .padding(bottom = 4.dp),
                        color = JewelTheme.globalColors.borders.disabled
                    )
                    bottomContent()
                }
            }
        }
        if (position == VerticalLateralBarPosition.Start) {
            Column {
                VerticalDivider()
            }
        }
    }
}