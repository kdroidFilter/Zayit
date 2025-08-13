package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import kotlinx.serialization.Serializable

sealed interface TabsDestination {
    val tabId: String

    @Serializable
    data class Home(
        override val tabId: String
    ) : TabsDestination

    @Serializable
    data class Search(
        val searchQuery: String,
        override val tabId: String
    ) : TabsDestination

    @Serializable
    data class BookContent(
        val bookId: Long,
        override val tabId: String,
        val lineId: Long? = null
    ) : TabsDestination
}
