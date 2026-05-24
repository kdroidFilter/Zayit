package io.github.kdroidfilter.seforimapp.core.catalog

import io.github.kdroidfilter.seforimapp.catalog.BookRef
import io.github.kdroidfilter.seforimapp.catalog.CatalogPresets
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog

/**
 * Runtime access to per-category titles and book lists, sourced from the lib's
 * [PrecomputedCatalog] (catalog.pb). Applies the display transformations that
 * `cataloggen` used to bake into PrecomputedCatalog.kt:
 *  - "תלמוד" prefix on Bavli/Yerushalmi category titles
 *  - "מפרשים" filter inside Mishneh Torah and its direct children
 *  - "הקדמה" / "פרי מגדים" filter inside Shulchan Aruch and its direct children
 *  - ancestor-label prefix stripping on book display titles
 */
class CatalogAccess(
    private val catalogProvider: () -> PrecomputedCatalog?,
) {
    private data class Indices(
        val source: PrecomputedCatalog,
        val categoryTitles: Map<Long, String>,
        val bookTitles: Map<Long, String>,
        val booksByCategory: Map<Long, List<BookRef>>,
    )

    @Volatile
    private var indices: Indices? = null

    private fun resolve(): Indices? {
        val catalog = catalogProvider() ?: return null
        val current = indices
        if (current != null && current.source === catalog) return current
        val built = build(catalog)
        indices = built
        return built
    }

    fun categoryTitle(id: Long): String? = resolve()?.categoryTitles?.get(id)

    fun bookTitle(id: Long): String? = resolve()?.bookTitles?.get(id)

    fun booksFor(categoryId: Long): List<BookRef> = resolve()?.booksByCategory?.get(categoryId).orEmpty()

    private fun build(catalog: PrecomputedCatalog): Indices {
        val rawCategoryTitles = LinkedHashMap<Long, String>()
        val parentIdByCategory = HashMap<Long, Long?>()
        val bookTitles = HashMap<Long, String>()

        fun walk(cat: CatalogCategory) {
            rawCategoryTitles[cat.id] = cat.title
            parentIdByCategory[cat.id] = cat.parentId
            cat.books.forEach { bookTitles[it.id] = it.title }
            cat.subcategories.forEach { walk(it) }
        }
        catalog.rootCategories.forEach { walk(it) }

        val categoryTitles = LinkedHashMap<Long, String>(rawCategoryTitles.size)
        rawCategoryTitles.forEach { (id, title) ->
            categoryTitles[id] = displayCategoryTitle(id, title, rawCategoryTitles, parentIdByCategory)
        }

        val booksByCategory = HashMap<Long, List<BookRef>>()
        val ancestorTitlesCache = HashMap<Long, List<String>>()

        fun walkBooks(cat: CatalogCategory) {
            val excludedPrefixes = excludedBookPrefixesFor(cat.id, parentIdByCategory)
            val ancestorLabels =
                ancestorTitlesCache.getOrPut(cat.id) {
                    collectAncestorTitles(cat.id, rawCategoryTitles, parentIdByCategory)
                }
            // Strip ancestor labels first, then exclude books whose display title starts with
            // a forbidden prefix (e.g. שולחן ערוך, הקדמה → הקדמה → filtered).
            val refs =
                cat.books.mapNotNull { book ->
                    val display = stripAnyLabelPrefix(ancestorLabels, book.title)
                    val trimmed = display.trimStart()
                    if (excludedPrefixes.any { trimmed.startsWith(it) }) {
                        null
                    } else {
                        BookRef(book.id, display)
                    }
                }
            booksByCategory[cat.id] = refs
            cat.subcategories.forEach { walkBooks(it) }
        }
        catalog.rootCategories.forEach { walkBooks(it) }

        return Indices(catalog, categoryTitles, bookTitles, booksByCategory)
    }

    /**
     * Returns the book-title prefixes to exclude in the given category context, or empty if none.
     * Mirrors the legacy display rules previously baked into the codegen:
     *  - Mishneh Torah (root or direct child): drop "מפרשים".
     *  - Shulchan Aruch (root or direct child): drop "הקדמה" and "פרי מגדים".
     */
    private fun excludedBookPrefixesFor(
        categoryId: Long,
        parents: Map<Long, Long?>,
    ): List<String> {
        val parentId = parents[categoryId]
        val mishnehTorahId = CatalogPresets.Ids.Categories.MISHNE_TORAH
        val shulchanAruchId = CatalogPresets.Ids.Categories.SHULCHAN_ARUCH
        return when {
            categoryId == mishnehTorahId || parentId == mishnehTorahId -> listOf(MEFARSHIM_PREFIX)
            categoryId == shulchanAruchId || parentId == shulchanAruchId -> SHULCHAN_ARUCH_EXCLUDED_PREFIXES
            else -> emptyList()
        }
    }

    private fun displayCategoryTitle(
        id: Long,
        rawTitle: String,
        rawTitles: Map<Long, String>,
        parents: Map<Long, Long?>,
    ): String {
        val needsTalmudPrefix = id == CatalogPresets.Ids.Categories.BAVLI || id == CatalogPresets.Ids.Categories.YERUSHALMI
        if (!needsTalmudPrefix) return rawTitle
        val parentTitle = parents[id]?.let { rawTitles[it] }?.takeIf { it.isNotBlank() } ?: TALMUD_FALLBACK
        return "$parentTitle $rawTitle"
    }

    private fun collectAncestorTitles(
        categoryId: Long,
        titles: Map<Long, String>,
        parents: Map<Long, Long?>,
    ): List<String> {
        val labels = mutableListOf<String>()
        var current: Long? = categoryId
        var guard = 0
        while (current != null && guard++ < MAX_ANCESTOR_WALK) {
            titles[current]?.takeIf { it.isNotBlank() }?.let { labels += it }
            current = parents[current]
        }
        return labels.distinct()
    }

    private fun stripAnyLabelPrefix(
        labels: List<String>,
        title: String,
    ): String {
        var result = title
        for (label in labels) result = stripLabelPrefix(label, result)
        return result
    }

    private fun stripLabelPrefix(
        label: String,
        title: String,
    ): String {
        if (label.isBlank()) return title
        val prefix = Regex.escape(label)
        val patterns =
            listOf(
                Regex("^$prefix\\s*,\\s*"),
                Regex("^$prefix,\\s*"),
                Regex("^$prefix\\s*[:–—-]\\s*"),
                Regex("^$prefix\\s*\\+\\s*"),
                Regex("^$prefix\\s+"),
            )
        for (p in patterns) {
            val replaced = title.replaceFirst(p, "")
            if (replaced !== title) return replaced.trimStart()
        }
        return title
    }

    private companion object {
        const val MEFARSHIM_PREFIX = "מפרשים"
        const val TALMUD_FALLBACK = "תלמוד"
        const val MAX_ANCESTOR_WALK = 50
        val SHULCHAN_ARUCH_EXCLUDED_PREFIXES = listOf("הקדמה", "פרי מגדים")
    }
}
