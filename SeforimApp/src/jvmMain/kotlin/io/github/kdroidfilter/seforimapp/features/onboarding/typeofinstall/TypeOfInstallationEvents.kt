package io.github.kdroidfilter.seforimapp.features.onboarding.typeofinstall

sealed interface TypeOfInstallationEvents {
    data class OfflineFileChosen(
        val path: String,
    ) : TypeOfInstallationEvents
}
