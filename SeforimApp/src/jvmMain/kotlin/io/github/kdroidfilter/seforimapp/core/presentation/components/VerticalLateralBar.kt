package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text

enum class VerticalLateralBarPosition {
    Start,
    End,
}

@Composable
fun VerticalLateralBar(
    topContent: @Composable () -> Unit,
    bottomContent: @Composable () -> Unit,
    position: VerticalLateralBarPosition,
    modifier: Modifier = Modifier,
    topContentLabel: String? = null,
    bottomContentLabel: String? = null,
) {
    val boxModifier =
        Modifier
            .fillMaxWidth()
            .padding(4.dp)
    val lazyColumnVerticalArrangement = Arrangement.spacedBy(4.dp)

    Row(modifier = modifier.width(64.dp).fillMaxHeight()) {
        if (position == VerticalLateralBarPosition.End) {
            Column {
                VerticalDivider()
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = boxModifier,
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    verticalArrangement = lazyColumnVerticalArrangement,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        if (topContentLabel != null) {
                            Text(
                                text = topContentLabel,
                                fontSize = 14.sp,
                                textDecoration = TextDecoration.Underline,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            topContent()
                        }
                        Divider(
                            orientation = Orientation.Horizontal,
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.5f)
                                    .width(1.dp)
                                    .padding(vertical = 4.dp),
                            color = JewelTheme.globalColors.borders.disabled,
                        )
                    }
                }
            }
            Box(
                modifier = boxModifier,
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        if (bottomContentLabel != null) {
                            Text(
                                text = bottomContentLabel,
                                fontSize = 14.sp,
                                textDecoration = TextDecoration.Underline,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            bottomContent()
                        }
                    }
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
