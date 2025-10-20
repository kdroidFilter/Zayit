package io.github.kdroidfilter.seforimapp.features.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import io.github.kdroidfilter.seforimapp.core.MainAppState
import io.github.kdroidfilter.seforimapp.features.onboarding.business.OnBoardingUseCase

class OnBoardingViewModel(
    private val mainState : MainAppState,
    private val useCase: OnBoardingUseCase,
) : ViewModel() {


    private fun isDatabaseAvailable(): Boolean {
        val path = useCase.getDatabasePath()
        return path != null && File(path).exists()
    }

    private val _isDatabaseLoaded = MutableStateFlow(isDatabaseAvailable())
    val isDatabaseLoaded = _isDatabaseLoaded.asStateFlow()

    private val _downloadingInProgress = MutableStateFlow(false)
    val downloadingInProgress = _downloadingInProgress.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _extractingInProgress = MutableStateFlow(false)
    val extractingInProgress = _extractingInProgress.asStateFlow()

    private val _extractProgress = MutableStateFlow(0f)
    val extractProgress = _extractProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)

    // Byte-level download metrics
    private val _downloadedBytes = MutableStateFlow(0L)
    private val _downloadTotalBytes = MutableStateFlow<Long?>(null)
    private val _downloadSpeedBytesPerSec = MutableStateFlow(0L)

    // Combined onboarding state
    val state: StateFlow<OnBoardingState> = combine(
        isDatabaseLoaded,
        downloadingInProgress,
        downloadProgress,
        extractingInProgress,
        extractProgress,
        _errorMessage,
        _downloadedBytes,
        _downloadTotalBytes,
        _downloadSpeedBytesPerSec
    ) { values: Array<Any?> ->
        val isDbLoaded = values[0] as Boolean
        val isDownloading = values[1] as Boolean
        val dlProgress = values[2] as Float
        val isExtracting = values[3] as Boolean
        val exProgress = values[4] as Float
        val error = values[5] as String?
        val bytesRead = values[6] as Long
        val totalBytes = values[7] as Long?
        val speed = values[8] as Long
        OnBoardingState(
            isDatabaseLoaded = isDbLoaded,
            downloadingInProgress = isDownloading,
            downloadProgress = dlProgress,
            errorMessage = error,
            extractingInProgress = isExtracting,
            extractProgress = exProgress,
            downloadedBytes = bytesRead,
            downloadTotalBytes = totalBytes,
            downloadSpeedBytesPerSec = speed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = OnBoardingState(
            isDatabaseLoaded = _isDatabaseLoaded.value,
            downloadingInProgress = _downloadingInProgress.value,
            downloadProgress = _downloadProgress.value,
            errorMessage = _errorMessage.value,
            extractingInProgress = _extractingInProgress.value,
            extractProgress = _extractProgress.value,
            downloadedBytes = _downloadedBytes.value,
            downloadTotalBytes = _downloadTotalBytes.value,
            downloadSpeedBytesPerSec = _downloadSpeedBytesPerSec.value
        )
    )

    fun onEvent(event: OnBoardingEvents) {
        when (event) {
            OnBoardingEvents.onFinish -> {
                mainState.setShowOnBoarding(false)
            }
            OnBoardingEvents.StartDownload -> {
                if (_downloadingInProgress.value || _extractingInProgress.value || _isDatabaseLoaded.value) return
                viewModelScope.launch(Dispatchers.Default) {
                    runCatching { performSetup() }
                        .onFailure { e ->
                            _downloadingInProgress.value = false
                            _extractingInProgress.value = false
                            _downloadSpeedBytesPerSec.value = 0L
                            _errorMessage.value = e.message ?: e.toString()
                        }
                }
            }
            is OnBoardingEvents.ImportFromZst -> {
                if (_extractingInProgress.value || _isDatabaseLoaded.value) return
                val sourcePath = event.path
                viewModelScope.launch(Dispatchers.Default) {
                    runCatching {
                        _errorMessage.value = null
                        _extractingInProgress.value = true
                        _extractProgress.value = 0f

                        useCase.importFromZst(sourcePath) { p ->
                            _extractProgress.value = p.coerceIn(0f, 1f)
                        }
                        _extractingInProgress.value = false
                        _extractProgress.value = 1f
                        _isDatabaseLoaded.value = true
                    }.onFailure { e ->
                        _extractingInProgress.value = false
                        _errorMessage.value = e.message ?: e.toString()
                    }
                }
            }
        }
    }

    private suspend fun performSetup() {
        // Prepare UI state early so progress UI appears immediately
        _errorMessage.value = null
        _downloadingInProgress.value = true
        _downloadProgress.value = 0f
        _downloadedBytes.value = 0L
        _downloadTotalBytes.value = null
        _downloadSpeedBytesPerSec.value = 0L
        _extractingInProgress.value = false
        _extractProgress.value = 0f

        useCase.setupDatabase(
            onDownloadProgress = { read, total, progress, speed ->
                _downloadedBytes.value = read
                _downloadTotalBytes.value = total
                _downloadProgress.value = progress
                _downloadSpeedBytesPerSec.value = speed
                if (progress >= 1f) {
                    _downloadingInProgress.value = false
                    _downloadSpeedBytesPerSec.value = 0L
                }
            },
            onExtractProgress = { p ->
                if (!_extractingInProgress.value && p < 1f) {
                    _extractingInProgress.value = true
                }
                _extractProgress.value = p
                if (p >= 1f) {
                    _extractingInProgress.value = false
                    _isDatabaseLoaded.value = true
                }
            }
        )
    }
}
