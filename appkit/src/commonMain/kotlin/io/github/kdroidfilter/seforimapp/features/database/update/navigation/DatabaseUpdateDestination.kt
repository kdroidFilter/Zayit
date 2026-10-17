package io.github.kdroidfilter.seforimapp.features.database.update.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface DatabaseUpdateDestination {
    @Serializable
    data object VersionCheckScreen : DatabaseUpdateDestination

    @Serializable
    data object UpdateOptionsScreen : DatabaseUpdateDestination

    @Serializable
    data object OnlineUpdateScreen : DatabaseUpdateDestination

    @Serializable
    data object OfflineUpdateScreen : DatabaseUpdateDestination

    @Serializable
    data object CompletionScreen : DatabaseUpdateDestination
}
