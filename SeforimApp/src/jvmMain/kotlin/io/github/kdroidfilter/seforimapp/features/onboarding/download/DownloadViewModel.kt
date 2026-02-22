package io.github.kdroidfilter.seforimapp.features.onboarding.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.features.onboarding.data.OnboardingProcessRepository
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadUseCase
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@ContributesIntoMap(AppScope::class)
@ViewModelKey(DownloadViewModel::class)
@Inject
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

    private data class DownloadProgressSnapshot(
        val inProgress: Boolean,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long?,
        val speedBytesPerSec: Long,
    )

    private val progressSnapshot =
        combine(
            _inProgress,
            _progress,
            _downloaded,
            _total,
            _speed,
        ) { inProgress, progress, downloaded, total, speed ->
            DownloadProgressSnapshot(inProgress, progress, downloaded, total, speed)
        }

    val state: StateFlow<DownloadState> =
        combine(
            progressSnapshot,
            _error,
            _completed,
        ) { snapshot, error, completed ->
            DownloadState(
                inProgress = snapshot.inProgress,
                progress = snapshot.progress,
                downloadedBytes = snapshot.downloadedBytes,
                totalBytes = snapshot.totalBytes,
                speedBytesPerSec = snapshot.speedBytesPerSec,
                errorMessage = error,
                completed = completed,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue =
                DownloadState(
                    inProgress = false,
                    progress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = null,
                    speedBytesPerSec = 0L,
                    errorMessage = null,
                    completed = false,
                ),
        )

    fun onEvent(event: DownloadEvents) {
        when (event) {
            DownloadEvents.Start -> startIfNeeded()
        }
    }

    private fun startIfNeeded() {
        if (_inProgress.value || _completed.value) return
        viewModelScope.launch(Dispatchers.Default) {
            runSuspendCatching {
                _error.value = null
                _completed.value = false
                _inProgress.value = true
                _progress.value = 0f
                _downloaded.value = 0L
                _total.value = null
                _speed.value = 0L

                val path =
                    useCase.downloadLatestBundle { read, total, progress, speed ->
                        _downloaded.value = read
                        _total.value = total
                        _progress.value = progress
                        _speed.value = speed
                    }

                // Make the result available to the extraction step before marking as completed
                processRepository.setPendingZstPath(path)

                _inProgress.value = false
                _speed.value = 0L
                _progress.value = 1f
                _completed.value = true
            }.onFailure {
                _inProgress.value = false
                _speed.value = 0L
                _error.value = it.message ?: it.toString()
            }
        }
    }
}
