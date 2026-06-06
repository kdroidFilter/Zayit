package io.github.kdroidfilter.seforimapp.features.onboarding.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.features.database.update.DatabasePreparationUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.data.OnboardingProcessRepository
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
class DownloadViewModel(
    private val useCase: DownloadUseCase,
    private val processRepository: OnboardingProcessRepository,
    private val preparationUseCase: DatabasePreparationUseCase,
) : ViewModel() {
    private val _inProgress = MutableStateFlow(false)
    private val _progress = MutableStateFlow(0f)
    private val _downloaded = MutableStateFlow(0L)
    private val _total = MutableStateFlow<Long?>(null)
    private val _speed = MutableStateFlow(0L)
    private val _error = MutableStateFlow<String?>(null)
    private val _errorKind = MutableStateFlow<DownloadErrorKind?>(null)
    private val _completed = MutableStateFlow(false)

    // Set while the pre-download gate runs, so a stray Start event can't run it twice.
    @Volatile
    private var preparing = false

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
            _errorKind,
            _completed,
        ) { snapshot, error, errorKind, completed ->
            DownloadState(
                inProgress = snapshot.inProgress,
                progress = snapshot.progress,
                downloadedBytes = snapshot.downloadedBytes,
                totalBytes = snapshot.totalBytes,
                speedBytesPerSec = snapshot.speedBytesPerSec,
                errorMessage = error,
                errorKind = errorKind,
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
        if (_inProgress.value || _completed.value || preparing) return
        preparing = true
        viewModelScope.launch(Dispatchers.Default) {
            runSuspendCatching {
                _error.value = null
                _errorKind.value = null
                _completed.value = false
                _progress.value = 0f
                _downloaded.value = 0L
                _total.value = null
                _speed.value = 0L

                // Gate: remove the old database and verify free space BEFORE transferring
                // anything. Refusing here is what prevents a multi-GB download from filling
                // the disk and freezing when an old database was left in place.
                when (preparationUseCase.prepareForInstall()) {
                    DatabasePreparationUseCase.Result.Ready -> Unit
                    is DatabasePreparationUseCase.Result.CleanupFailed -> {
                        _errorKind.value = DownloadErrorKind.CLEANUP_FAILED
                        return@runSuspendCatching
                    }
                    is DatabasePreparationUseCase.Result.InsufficientSpace -> {
                        _errorKind.value = DownloadErrorKind.INSUFFICIENT_SPACE
                        return@runSuspendCatching
                    }
                }

                _inProgress.value = true

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
            preparing = false
        }
    }
}
