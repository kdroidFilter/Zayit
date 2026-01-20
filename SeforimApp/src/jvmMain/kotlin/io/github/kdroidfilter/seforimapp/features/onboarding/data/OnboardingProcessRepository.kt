package io.github.kdroidfilter.seforimapp.features.onboarding.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds transient onboarding process data shared across screens (e.g., the latest .zst path).
 * This avoids coupling screens to a single ViewModel while keeping progress reactive.
 */
class OnboardingProcessRepository {
    private val _pendingZstPath = MutableStateFlow<String?>(null)
    val pendingZstPath: StateFlow<String?> = _pendingZstPath.asStateFlow()

    fun setPendingZstPath(path: String?) {
        _pendingZstPath.value = path
    }
}
