package io.github.kdroidfilter.seforimapp.features.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.platformtools.releasefetcher.github.GitHubReleaseFetcher
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class OnBoardingViewModel(
    private val settings: AppSettings,
    private val gitHubReleaseFetcher: GitHubReleaseFetcher
) : ViewModel() {

    private fun isDatabaseAvailable(): Boolean {
        val path = settings.getDatabasePath()
        return path != null && File(path).exists()
    }

    init {
        if (!isDatabaseAvailable()) {
            viewModelScope.launch {
                val latestRelease = gitHubReleaseFetcher.getLatestRelease()
                if (latestRelease != null) {
                    val databaseUrl = latestRelease.assets.firstOrNull { it.name.endsWith(".zst") }?.url

                }
            }
        }
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

    // Combined onboarding state
    val state: StateFlow<OnBoardingState> = combine(
        isDatabaseLoaded,
        downloadingInProgress,
        downloadProgress,
        extractingInProgress,
        extractProgress,
        _errorMessage
    ) { values: Array<Any?> ->
        val isDbLoaded = values[0] as Boolean
        val isDownloading = values[1] as Boolean
        val dlProgress = values[2] as Float
        val isExtracting = values[3] as Boolean
        val exProgress = values[4] as Float
        val error = values[5] as String?
        OnBoardingState(
            isDatabaseLoaded = isDbLoaded,
            downloadingInProgress = isDownloading,
            downloadProgress = dlProgress,
            errorMessage = error,
            extractingInProgress = isExtracting,
            extractProgress = exProgress
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
            extractProgress = _extractProgress.value
        )
    )

    fun onEvent(event: OnBoardingEvents) {
        when (event) {
            OnBoardingEvents.onFinish -> {
                // No-op for now
            }
        }
    }
}