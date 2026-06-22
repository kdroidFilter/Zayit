package io.github.kdroidfilter.seforimapp.features.onboarding.userprofile

sealed interface UserProfileEvents {
    data class FirstNameChanged(
        val value: String,
    ) : UserProfileEvents

    data class LastNameChanged(
        val value: String,
    ) : UserProfileEvents

    data class SelectCommunity(
        val index: Int,
    ) : UserProfileEvents
}
