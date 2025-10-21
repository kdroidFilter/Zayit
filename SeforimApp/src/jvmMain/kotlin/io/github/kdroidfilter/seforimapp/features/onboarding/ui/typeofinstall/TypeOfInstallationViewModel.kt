package io.github.kdroidfilter.seforimapp.features.onboarding.ui.typeofinstall

import androidx.lifecycle.ViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.business.OnboardingProcessRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TypeOfInstallationViewModel(
    private val processRepository: OnboardingProcessRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TypeOfInstallationState())
    val state: StateFlow<TypeOfInstallationState> = _state.asStateFlow()

    fun onEvent(event: TypeOfInstallationEvents) {
        when (event) {
            is TypeOfInstallationEvents.OfflineFileChosen -> {
                // Publish the chosen .zst path for the extraction step
                processRepository.setPendingZstPath(event.path)
            }
        }
    }
}

