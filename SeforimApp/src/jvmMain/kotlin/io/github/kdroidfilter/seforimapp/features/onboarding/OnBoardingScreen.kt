package io.github.kdroidfilter.seforimapp.features.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun OnBoardingScreen() {
    // In a real screen we would collect state from a ViewModel and pass it to OnBoardingView
}

@Composable
private fun OnBoardingView(state: OnBoardingState, onEvent: (OnBoardingEvents) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {



    }

}

// Previews
@Preview(name = "Loaded", showBackground = true)
@Composable
private fun Preview_OnBoarding_Loaded() {
    OnBoardingView(state = OnBoardingState.previewLoaded) { }
}

@Preview(name = "Downloading", showBackground = true)
@Composable
private fun Preview_OnBoarding_Downloading() {
    OnBoardingView(state = OnBoardingState.previewDownloading) { }
}

@Preview(name = "Extracting", showBackground = true)
@Composable
private fun Preview_OnBoarding_Extracting() {
    OnBoardingView(state = OnBoardingState.previewExtracting) { }
}

@Preview(name = "Error", showBackground = true)
@Composable
private fun Preview_OnBoarding_Error() {
    OnBoardingView(state = OnBoardingState.previewError) { }
}