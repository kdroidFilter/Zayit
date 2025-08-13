// navigation/Navigator.kt
package io.github.kdroidfilter.seforimapp.core.presentation.navigation

import androidx.navigation.NavOptionsBuilder
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

interface Navigator {
    val startDestination: TabsDestination
    val navigationActions: Flow<NavigationAction>
    val navigationRequests: Flow<TabsDestination> // Nouveau

    suspend fun navigate(
        destination: TabsDestination,
        navOptions: NavOptionsBuilder.() -> Unit = {}
    )

    suspend fun navigateUp()

    val canGoBack: StateFlow<Boolean>

    fun setCanGoBack(value: Boolean)
}

class DefaultNavigator(
    override val startDestination: TabsDestination
): Navigator {
    private val _navigationActions =
        MutableSharedFlow<NavigationAction>(extraBufferCapacity = 1)
    override val navigationActions = _navigationActions.asSharedFlow()

    private val _navigationRequests =
        MutableSharedFlow<TabsDestination>(extraBufferCapacity = 1)
    override val navigationRequests = _navigationRequests.asSharedFlow()

    override suspend fun navigate(
        destination: TabsDestination,
        navOptions: NavOptionsBuilder.() -> Unit
    ) {
        // Émettre la destination pour que TabsViewModel puisse créer un onglet
        _navigationRequests.emit(destination)
        // Émettre l'action de navigation normale
        _navigationActions.emit(
            NavigationAction.Navigate(destination, navOptions)
        )
    }

    override suspend fun navigateUp() =
        _navigationActions.emit(NavigationAction.NavigateUp)

    private val _canGoBack = MutableStateFlow(false)
    override val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    override fun setCanGoBack(value: Boolean) {
        _canGoBack.value = value
    }
}