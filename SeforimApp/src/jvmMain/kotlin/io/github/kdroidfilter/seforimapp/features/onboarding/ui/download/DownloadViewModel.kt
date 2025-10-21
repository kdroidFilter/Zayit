package io.github.kdroidfilter.seforimapp.features.onboarding.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforimapp.features.onboarding.business.DownloadUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.business.OnboardingProcessRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadViewModel(
    private val useCase: DownloadUseCase,
    private val processRepository: OnboardingProcessRepository,
) : ViewModel() {

    private val _inProgress = MutableStateFlow(false)
    private val _progress = MutableStateFlow(0f)
    private val _downloaded = MutableStateFlow(0L)
    private val _total = MutableStateFlow<Long?>(null)
    private val _speed = MutableStateFlow(0L)
    private val _error = MutableStateFlow<String?>(null)
    private val _completed = MutableStateFlow(false)

    val state: StateFlow<DownloadState> = combine(
        _inProgress, _progress, _downloaded, _total, _speed, _error, _completed
    ) { values ->
        DownloadState(
            inProgress = values[0] as Boolean,
            progress = values[1] as Float,
            downloadedBytes = values[2] as Long,
            totalBytes = values[3] as Long?,
            speedBytesPerSec = values[4] as Long,
            errorMessage = values[5] as String?,
            completed = values[6] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DownloadState(
            inProgress = false,
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = null,
            speedBytesPerSec = 0L,
            errorMessage = null,
            completed = false
        )
    )

    fun onEvent(event: DownloadEvents) {
        when (event) {
            DownloadEvents.Start -> startIfNeeded()
        }
    }

    private fun startIfNeeded() {
        if (_inProgress.value || _completed.value) return
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                _error.value = null
                _completed.value = false
                _inProgress.value = true
                _progress.value = 0f
                _downloaded.value = 0L
                _total.value = null
                _speed.value = 0L

                val zstPath = useCase.downloadLatestDatabase { read, total, progress, speed ->
                    _downloaded.value = read
                    _total.value = total
                    _progress.value = progress
                    _speed.value = speed
                }

                _inProgress.value = false
                _speed.value = 0L
                _progress.value = 1f
                _completed.value = true
                // Make the result available to the extraction step
                processRepository.setPendingZstPath(zstPath)
            }.onFailure {
                _inProgress.value = false
                _speed.value = 0L
                _error.value = it.message ?: it.toString()
            }
        }
    }
}

