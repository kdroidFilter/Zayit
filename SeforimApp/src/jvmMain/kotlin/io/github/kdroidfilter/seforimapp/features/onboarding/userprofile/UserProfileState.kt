package io.github.kdroidfilter.seforimapp.features.onboarding.userprofile

import androidx.compose.runtime.Immutable

@Immutable
data class UserProfileState(
    val firstName: String = "",
    val lastName: String = "",
    val communities: List<Community> = emptyList(),
    val selectedCommunityIndex: Int = -1,
)
