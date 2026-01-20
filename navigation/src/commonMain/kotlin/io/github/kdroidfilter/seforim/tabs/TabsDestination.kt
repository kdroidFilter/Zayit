package io.github.kdroidfilter.seforim.tabs

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
sealed interface TabsDestination {
    val tabId: String

    @Serializable
    @Immutable
    data class Home(
        override val tabId: String,
        val version: Long = 0L,
    ) : TabsDestination

    @Serializable
    @Immutable
    data class Search(
        val searchQuery: String,
        override val tabId: String,
    ) : TabsDestination

    @Serializable
    @Immutable
    data class BookContent(
        val bookId: Long,
        override val tabId: String,
        val lineId: Long? = null,
    ) : TabsDestination
}
