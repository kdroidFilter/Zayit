package io.github.kdroidfilter.seforimapp.features.onboarding.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforimapp.features.onboarding.data.OnboardingProcessRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
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

    val state: StateFlow<DownloadState> = combine(_inProgress, _progress) { inProgress, progress ->
        DlAggregate(
            inProgress = inProgress,
            progress = progress,
            downloaded = _downloaded.value,
            total = _total.value,
            speed = _speed.value,
            error = _error.value,
            completed = _completed.value
        )
    }
        .combine(_downloaded) { agg, downloaded -> agg.copy(downloaded = downloaded) }
        .combine(_total) { agg, total -> agg.copy(total = total) }
        .combine(_speed) { agg, speed -> agg.copy(speed = speed) }
        .combine(_error) { agg, error -> agg.copy(error = error) }
        .combine(_completed) { agg, completed -> agg.copy(completed = completed) }
        .map { agg ->
            DownloadState(
                inProgress = agg.inProgress,
                progress = agg.progress,
                downloadedBytes = agg.downloaded,
                totalBytes = agg.total,
                speedBytesPerSec = agg.speed,
                errorMessage = agg.error,
                completed = agg.completed
            )
        }
        .stateIn(
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

    private data class DlAggregate(
        val inProgress: Boolean,
        val progress: Float,
        val downloaded: Long,
        val total: Long?,
        val speed: Long,
        val error: String?,
        val completed: Boolean,
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

                val path = useCase.downloadLatestBundle { read, total, progress, speed ->
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
                processRepository.setPendingZstPath(path)
            }.onFailure {
                _inProgress.value = false
                _speed.value = 0L
                _error.value = it.message ?: it.toString()
            }
        }
    }
}
