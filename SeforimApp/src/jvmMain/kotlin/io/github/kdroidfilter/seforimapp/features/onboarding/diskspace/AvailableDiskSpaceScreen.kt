package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatBytes
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import io.github.koalaplot.core.pie.DefaultSlice
import io.github.koalaplot.core.pie.PieChart
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.util.generateHueColorPalette
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.DefaultErrorBanner
import org.jetbrains.jewel.ui.component.DefaultSuccessBanner
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.defaultBannerStyle
import seforimapp.seforimapp.generated.resources.*

@Composable
fun AvailableDiskSpaceScreen(
    navController: NavController,
    progressBarState: ProgressBarState = ProgressBarState
) {
    val viewModel: AvailableDiskSpaceViewModel = LocalAppGraph.current.availableDiskSpaceViewModel
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { progressBarState.setProgress(0.2f) }

    AvailableDiskSpaceView(
        state = state,
        onEvent = viewModel::onEvent,
        onNext = { navController.navigate(OnBoardingDestination.TypeOfInstallationScreen) }
    )
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
fun AvailableDiskSpaceView(
    state: AvailableDiskSpaceState,
    onEvent: (AvailableDiskSpaceEvents) -> Unit,
    onNext: () -> Unit = {}
) {
    val requiredBytes = 15L * 1024 * 1024 * 1024
    OnBoardingScaffold(title = stringResource(Res.string.onboarding_disk_title), bottomAction = {
        DefaultButton(onClick = onNext, enabled = state.hasEnoughSpace) {
            Text(stringResource(Res.string.next_button))
        }
    }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Pie chart: Used, Required (15GB), Free After Installation
            val total = state.totalDiskSpace
            val used = (total - state.availableDiskSpace).coerceAtLeast(0)
            val freeAfter = (state.availableDiskSpace - requiredBytes).coerceAtLeast(0)
            val slices = listOf(
                used.toFloat(),
                requiredBytes.toFloat(),
                freeAfter.toFloat()
            )
            val colors = generateHueColorPalette(slices.size)

            Row(Modifier.weight(0.9f)) {
                PieChart(
                    values = slices,
                    modifier = Modifier.size(240.dp).weight(1f),
                    slice = { i: Int ->
                        val labelText = when (i) {
                            0 -> stringResource(Res.string.disk_pie_used_with_value, formatBytes(used))
                            1 -> stringResource(Res.string.disk_pie_required, formatBytes(requiredBytes))
                            else -> stringResource(Res.string.disk_pie_free_after_with_value, formatBytes(freeAfter))
                        }
                        DefaultSlice(
                            color = colors[i],
                            hoverExpandFactor = 1.05f,
                            hoverElement = { Text(labelText) }
                        )
                    },
                    labelConnector = {}
                )

                // Simple legend with sizes
                val usedLabel = stringResource(Res.string.disk_pie_used_with_value, formatBytes(used))
                val reqLabel = stringResource(Res.string.disk_pie_required, formatBytes(requiredBytes))
                val freeAfterLabel = stringResource(Res.string.disk_pie_free_after_with_value, formatBytes(freeAfter))
                Column(verticalArrangement = Arrangement.Center, modifier = Modifier.weight(1f).fillMaxSize()) {
                    LegendItem(color = colors[0], text = usedLabel)
                    LegendItem(color = colors[1], text = reqLabel)
                    LegendItem(color = colors[2], text = freeAfterLabel)
                }
            }

            Row(modifier = Modifier.weight(0.2f)) {
                val recheckLabel = stringResource(Res.string.recheck_button)

                if (state.hasEnoughSpace) {
                    DefaultSuccessBanner(
                        text = stringResource(Res.string.onboarding_disk_success_continue),
                        style = JewelTheme.defaultBannerStyle.success
                    )
                } else {
                    DefaultErrorBanner(
                        text = stringResource(Res.string.onboarding_disk_error_free_up),
                        style = JewelTheme.defaultBannerStyle.error,
                        linkActions = {
                            action(recheckLabel, onClick = { onEvent(AvailableDiskSpaceEvents.Refresh) })
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).background(color))
        Text(text)
    }
}

@Composable
@Preview
private fun AvailableDiskSpaceScreenEnoughSpacePreview() {
    PreviewContainer {
        AvailableDiskSpaceView(
            AvailableDiskSpaceState(
                hasEnoughSpace = true,
                availableDiskSpace = 20L * 1024 * 1024 * 1024,
                remainingDiskSpaceAfter15Gb = 5L * 1024 * 1024 * 1024
            ),
            {}
        )
    }
}

@Composable
@Preview
private fun AvailableDiskSpaceScreenNoEnoughSpacePreview() {
    PreviewContainer {
        AvailableDiskSpaceView(
            AvailableDiskSpaceState(
                hasEnoughSpace = false,
                availableDiskSpace = 10L * 1024 * 1024 * 1024,
                remainingDiskSpaceAfter15Gb = -5L * 1024 * 1024 * 1024
            ),
            {}
        )
    }
}
