package io.github.kdroidfilter.seforimapp.features.onboarding.typeofinstall

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.features.onboarding.data.OnboardingProcessRepository
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@ContributesIntoMap(AppScope::class)
@ViewModelKey(TypeOfInstallationViewModel::class)
class TypeOfInstallationViewModel @Inject constructor(
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
