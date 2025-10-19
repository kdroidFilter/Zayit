package io.github.kdroidfilter.seforimapp.features.onboarding

import androidx.lifecycle.ViewModel

class OnBoardingViewModel : ViewModel() {

    val state : OnBoardingState = OnBoardingState(
        isDatabaseLoaded = false,
        downloadingInProgress = false,
        downloadProgress = 0f,
        errorMessage = null,
        extractingInProgress = false,
        extractProgress = 0f
    )

    fun onEvent(event: OnBoardingEvents) {
        when(event) {
            OnBoardingEvents.onFinish -> {

            }
        }
    }
}