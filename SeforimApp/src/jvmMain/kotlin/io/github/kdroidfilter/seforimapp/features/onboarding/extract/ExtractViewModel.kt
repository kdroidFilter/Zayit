package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforimapp.features.onboarding.data.OnboardingProcessRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ExtractViewModel(
    private val useCase: ExtractUseCase,
    private val processRepository: OnboardingProcessRepository,
) : ViewModel() {

    private val _inProgress = MutableStateFlow(false)
    private val _progress = MutableStateFlow(0f)
    private val _error = MutableStateFlow<String?>(null)
    private val _completed = MutableStateFlow(false)

    private var job: Job? = null

    val state: StateFlow<ExtractState> = combine(
        _inProgress, _progress, _error, _completed
    ) { inProgress: Boolean, progress: Float, error: String?, completed: Boolean ->
        ExtractState(
            inProgress = inProgress,
            progress = progress,
            errorMessage = error,
            completed = completed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ExtractState(
            inProgress = false,
            progress = 0f,
            errorMessage = null,
            completed = false
        )
    )

    fun onEvent(event: ExtractEvents) {
        when (event) {
            ExtractEvents.StartIfPending -> startIfPending()
        }
    }

    private fun startIfPending() {
        if (_inProgress.value || _completed.value) return
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.Default) {
            val path = processRepository.pendingZstPath.first()
            if (path.isNullOrBlank()) return@launch
            runCatching {
                _error.value = null
                _inProgress.value = true
                _progress.value = 0f
                useCase.extractToDatabase(path) { p -> _progress.value = p }
                _inProgress.value = false
                _progress.value = 1f
                _completed.value = true
                // Clear pending once used
                processRepository.setPendingZstPath(null)
            }.onFailure {
                _inProgress.value = false
                _error.value = it.message ?: it.toString()
            }
        }
    }
}

