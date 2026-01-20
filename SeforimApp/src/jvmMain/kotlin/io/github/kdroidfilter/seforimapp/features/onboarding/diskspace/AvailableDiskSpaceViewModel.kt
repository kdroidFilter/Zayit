package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@ContributesIntoMap(AppScope::class)
@ViewModelKey(AvailableDiskSpaceViewModel::class)
@Inject
class AvailableDiskSpaceViewModel(
    private val useCase: AvailableDiskSpaceUseCase,
) : ViewModel() {
    private var _hasEnoughSpace = MutableStateFlow(useCase.hasEnoughSpace())
    val hasEnoughSpace = _hasEnoughSpace.asStateFlow()

    private var _availableDiskSpace = MutableStateFlow(useCase.getAvailableDiskSpace())
    val availableDiskSpace = _availableDiskSpace.asStateFlow()

    private var _totalDiskSpace = MutableStateFlow(useCase.getTotalDiskSpace())
    val totalDiskSpace = _totalDiskSpace.asStateFlow()

    private val _remainingDiskSpaceAfterInstall = MutableStateFlow(useCase.getRemainingSpaceAfterInstall())
    var remainingDiskSpaceAfterInstall = _remainingDiskSpaceAfterInstall.asStateFlow()

    // Expose a single combined state with combine
    val state =
        combine(
            hasEnoughSpace,
            availableDiskSpace,
            remainingDiskSpaceAfterInstall,
            totalDiskSpace,
        ) { hasEnough, available, remainingAfter, total ->
            AvailableDiskSpaceState(
                hasEnoughSpace = hasEnough,
                availableDiskSpace = available,
                remainingDiskSpaceAfterInstall = remainingAfter,
                totalDiskSpace = total,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue =
                AvailableDiskSpaceState(
                    hasEnoughSpace = _hasEnoughSpace.value,
                    availableDiskSpace = _availableDiskSpace.value,
                    remainingDiskSpaceAfterInstall = _remainingDiskSpaceAfterInstall.value,
                    totalDiskSpace = _totalDiskSpace.value,
                ),
        )

    private fun recheck() {
        _hasEnoughSpace.value = useCase.hasEnoughSpace()
        _availableDiskSpace.value = useCase.getAvailableDiskSpace()
        _totalDiskSpace.value = useCase.getTotalDiskSpace()
        _remainingDiskSpaceAfterInstall.value = useCase.getRemainingSpaceAfterInstall()
    }

    fun onEvent(event: AvailableDiskSpaceEvents) {
        when (event) {
            AvailableDiskSpaceEvents.Refresh -> recheck()
        }
    }
}
