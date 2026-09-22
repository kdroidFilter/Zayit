package io.github.kdroidfilter.seforimapp.features.onboarding.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Holds transient onboarding process data shared across screens (e.g., the latest .zst path).
 * This avoids coupling screens to a single ViewModel while keeping progress reactive.
 */
class OnboardingProcessRepository {
    /** Installation target, kept separate from the active database setting until extraction succeeds. */
    @Volatile
    var installDirectory: File? = null
        private set

    fun setInstallDirectory(directory: File) {
        installDirectory = directory.absoluteFile
    }

    private val _pendingZstPath = MutableStateFlow<String?>(null)
    val pendingZstPath: StateFlow<String?> = _pendingZstPath.asStateFlow()

    fun setPendingZstPath(path: String?) {
        _pendingZstPath.value = path
    }
}
