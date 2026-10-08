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
        /** Where a fresh tab runs the search (a search reopened from history runs where it first ran). */
        val scope: SearchScope = SearchScope.Global,
        /** Searches all books rather than only the base ones. */
        val globalExtended: Boolean = false,
    ) : TabsDestination

    @Serializable
    @Immutable
    data class BookContent(
        val bookId: Long,
        override val tabId: String,
        val lineId: Long? = null,
        /** Opens the notes pane on [lineId]: the book is opened to read a note there. */
        val openNotes: Boolean = false,
        /** Marks the lines from [lineId] to this one: a passage to read, such as the day's limud. */
        val endLineId: Long? = null,
        /** Opens it in shnayim mikra: each verse twice, then its targum. */
        val shnayimMikra: Boolean = false,
    ) : TabsDestination

    /** Full visit-history page (the chrome://history equivalent). */
    @Serializable
    @Immutable
    data class History(
        override val tabId: String,
    ) : TabsDestination

    /** Favorites page (the chrome://bookmarks equivalent). */
    @Serializable
    @Immutable
    data class Favorites(
        override val tabId: String,
    ) : TabsDestination

    /** The user's notes, of every book. */
    @Serializable
    @Immutable
    data class Notes(
        override val tabId: String,
    ) : TabsDestination

    /**
     * The smart siddur, opened on [part] (a SiddurPart name) of the Jewish day [epochDay], or on the tefila of the
     * hour; scrolled to the first [heading] that has these words.
     */
    @Serializable
    @Immutable
    data class Siddur(
        override val tabId: String,
        val part: String? = null,
        val epochDay: Long? = null,
        val heading: String? = null,
    ) : TabsDestination
}
