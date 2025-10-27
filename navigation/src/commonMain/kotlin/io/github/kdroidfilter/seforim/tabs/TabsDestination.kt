package io.github.kdroidfilter.seforim.tabs

import kotlinx.serialization.Serializable

@Serializable
sealed interface TabsDestination {
    val tabId: String

    @Serializable
    data class Home(
        override val tabId: String,
        val version: Long = 0L
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
