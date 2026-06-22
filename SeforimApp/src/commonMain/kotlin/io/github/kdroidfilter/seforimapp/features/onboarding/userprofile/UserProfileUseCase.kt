package io.github.kdroidfilter.seforimapp.features.onboarding.userprofile

class UserProfileUseCase {
    fun getCommunities(): List<Community> =
        listOf(
            Community.SEPHARADE,
            Community.ASHKENAZE,
            Community.SEFARD,
        )
}
