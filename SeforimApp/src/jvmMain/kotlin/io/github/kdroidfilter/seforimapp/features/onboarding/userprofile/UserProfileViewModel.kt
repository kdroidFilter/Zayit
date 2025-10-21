package io.github.kdroidfilter.seforimapp.features.onboarding.userprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class UserProfileViewModel(
    private val useCase: UserProfileUseCase
) : ViewModel() {

    private val communities = useCase.getCommunities()

    private val firstName = MutableStateFlow(AppSettings.getUserFirstName() ?: "")
    private val lastName = MutableStateFlow(AppSettings.getUserLastName() ?: "")
    private val selectedCommunityIndex = MutableStateFlow(
        AppSettings.getUserCommunityCode()?.let { code ->
            communities.indexOfFirst { it.name.equals(code, ignoreCase = true) }
        } ?: -1
    )

    val state = combine(
        firstName,
        lastName,
        selectedCommunityIndex,
    ) { f, l, cIdx ->
        UserProfileState(
            firstName = f,
            lastName = l,
            communities = communities,
            selectedCommunityIndex = if (cIdx in communities.indices) cIdx else -1
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserProfileState(communities = communities)
    )

    fun onEvent(event: UserProfileEvents) {
        when (event) {
            is UserProfileEvents.FirstNameChanged -> firstName.value = event.value
            is UserProfileEvents.LastNameChanged -> lastName.value = event.value
            is UserProfileEvents.SelectCommunity -> selectedCommunityIndex.value = event.index
        }
    }
}

