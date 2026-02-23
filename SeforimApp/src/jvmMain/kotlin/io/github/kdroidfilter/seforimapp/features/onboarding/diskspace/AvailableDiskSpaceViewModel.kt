package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@ContributesIntoMap(AppScope::class)
@ViewModelKey(AvailableDiskSpaceViewModel::class)
@Inject
class AvailableDiskSpaceViewModel(
    private val useCase: AvailableDiskSpaceUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(AvailableDiskSpaceState())
    val state = _state.asStateFlow()

    init {
        loadDiskSpace()
    }

    private fun loadDiskSpace() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val info = useCase.getDiskSpaceInfo()
            _state.update {
                AvailableDiskSpaceState(
                    isLoading = false,
                    hasEnoughSpace = info.hasEnoughSpace,
                    availableDiskSpace = info.availableBytes,
                    totalDiskSpace = info.totalBytes,
                    remainingDiskSpaceAfterInstall = info.remainingAfterInstall,
                )
            }
        }
    }

    fun onEvent(event: AvailableDiskSpaceEvents) {
        when (event) {
            AvailableDiskSpaceEvents.Refresh -> loadDiskSpace()
        }
    }
}
