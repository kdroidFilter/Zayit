package io.github.kdroidfilter.seforimapp.core.presentation.cache

/** Simple in-memory cache for book titles keyed by bookId. */
object BookTitleCache {
    private val titles: MutableMap<Long, String> = mutableMapOf()

    fun get(bookId: Long): String? = titles[bookId]

    fun put(bookId: Long, title: String) {
        titles[bookId] = title
    }
}

