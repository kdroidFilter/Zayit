package io.github.kdroidfilter.seforimapp.core.presentation.cache

/** Simple in-memory cache for category titles keyed by categoryId. */
object CategoryTitleCache {
    private val titles: MutableMap<Long, String> = mutableMapOf()

    fun get(categoryId: Long): String? = titles[categoryId]

    fun put(categoryId: Long, title: String) {
        titles[categoryId] = title
    }
}

