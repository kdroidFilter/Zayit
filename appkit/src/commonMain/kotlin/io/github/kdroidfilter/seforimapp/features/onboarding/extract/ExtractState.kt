package io.github.kdroidfilter.seforimapp.features.onboarding.extract

data class ExtractState(
    val inProgress: Boolean,
    val progress: Float,
    val errorMessage: String? = null,
    val completed: Boolean = false,
)
