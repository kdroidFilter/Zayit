package io.github.kdroidfilter.seforimapp.framework.database

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.logger.errorln
import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog
import io.github.kdroidfilter.seforimlibrary.core.models.extractAllBooks
import io.github.kdroidfilter.seforimlibrary.core.models.extractCategoryChildren
import io.github.kdroidfilter.seforimlibrary.core.models.extractRootCategories
import io.github.kdroidfilter.seforimlibrary.dao.CatalogLoader
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App-scoped holder for the precomputed catalog and its extracted data.
 * The catalog is loaded lazily on first access and cached for the entire session.
 * Extracted data (categories, books) is also cached to avoid re-traversing the tree on each tab open.
 */
@Inject
@SingleIn(AppScope::class)
class CatalogCache(
    private val databasePathProvider: DatabasePathProvider,
) {
    private var _catalog: PrecomputedCatalog? = null

    // Cached extracted data - computed once from the catalog
    private var _rootCategories: List<Category>? = null
    private var _categoryChildren: Map<Long, List<Category>>? = null
    private var _categoriesById: Map<Long, Category>? = null
    private var _allBooks: Set<Book>? = null

    @Volatile
    private var _allBooksWithAltFlags: Set<Book>? = null

    /**
     * Gets the cached catalog, loading it if necessary.
     * Returns null if the catalog file doesn't exist or can't be loaded.
     */
    @Synchronized
    fun getCatalog(): PrecomputedCatalog? {
        if (_catalog == null) {
            _catalog = loadCatalog()
        }
        return _catalog
    }

    /**
     * Gets the cached root categories, extracting them from the catalog if necessary.
     * Returns null if the catalog is not available.
     */
    @Synchronized
    fun getRootCategories(): List<Category>? {
        if (_rootCategories == null) {
            _rootCategories = getCatalog()?.extractRootCategories()
        }
        return _rootCategories
    }

    /**
     * Gets the cached category children map, extracting it from the catalog if necessary.
     * Returns null if the catalog is not available.
     */
    @Synchronized
    fun getCategoryChildren(): Map<Long, List<Category>>? {
        if (_categoryChildren == null) {
            _categoryChildren = getCatalog()?.extractCategoryChildren()
        }
        return _categoryChildren
    }

    /**
     * Flat id → Category index built once from roots + children. Used by features that need
     * O(1) parent walks (e.g. resolving the root category of a book) without hitting the DB.
     */
    @Synchronized
    fun getCategoriesById(): Map<Long, Category>? {
        if (_categoriesById == null) {
            val roots = getRootCategories() ?: return null
            val children = getCategoryChildren() ?: return null
            val map = HashMap<Long, Category>(roots.size + children.values.sumOf { it.size })
            roots.forEach { map[it.id] = it }
            children.values.forEach { list -> list.forEach { map[it.id] = it } }
            _categoriesById = map
        }
        return _categoriesById
    }

    /**
     * Walks up parentId from the book's immediate category to the root. Returns null if the
     * catalog is unavailable or the chain is broken. Logs a warning if the safety counter is
     * hit — that signals either a corrupted catalog (cycle) or a tree deeper than expected,
     * and the returned category would no longer be a true root.
     */
    fun getRootForBook(book: Book): Category? {
        val byId = getCategoriesById() ?: return null
        var current: Category? = byId[book.categoryId] ?: return null
        val maxDepth = 32
        var safety = maxDepth
        while (current?.parentId != null && safety-- > 0) {
            current = byId[current.parentId]
        }
        if (current?.parentId != null) {
            warnln {
                "[CatalogCache] getRootForBook for book id=${book.id} title='${book.title}' " +
                    "stopped at depth=$maxDepth without reaching a root (last category id=${current.id}, " +
                    "parentId=${current.parentId}); catalog may contain a cycle"
            }
            return null
        }
        return current
    }

    /**
     * Gets all books from the catalog, cached after first extraction.
     * Returns null if the catalog is not available.
     */
    @Synchronized
    fun getAllBooks(): Set<Book>? {
        if (_allBooks == null) {
            _allBooks = getCatalog()?.extractAllBooks()
        }
        return _allBooks
    }

    /**
     * Gets all books with alt-structure flags merged from the database.
     * Computed once and cached — avoids a DB query + N copy() calls per tab.
     */
    suspend fun getAllBooksWithAltFlags(repository: SeforimRepository): Set<Book>? {
        _allBooksWithAltFlags?.let { return it }
        // Every book of the catalog: off the caller's thread, the UI's when a book tab opens
        return withContext(Dispatchers.Default) {
            val books = getAllBooks() ?: return@withContext null
            val altFlags = repository.getAllBookAltFlags()
            val merged =
                books
                    .map { book ->
                        altFlags[book.id]?.let { book.copy(hasAltStructures = it) } ?: book
                    }.toSet()
            _allBooksWithAltFlags = merged
            merged
        }
    }

    /**
     * Loads the precomputed catalog from the catalog.pb file next to the database.
     */
    private fun loadCatalog(): PrecomputedCatalog? =
        try {
            val dbPath = databasePathProvider.get()
            val catalog = CatalogLoader.loadCatalog(dbPath)

            if (catalog != null) {
                infoln { "[CatalogCache] Precomputed catalog loaded: ${catalog.totalCategories} categories, ${catalog.totalBooks} books" }
            } else {
                warnln { "[CatalogCache] Precomputed catalog not found, will load from database instead" }
            }

            catalog
        } catch (e: Exception) {
            errorln { "[CatalogCache] Failed to load precomputed catalog: ${e.message}" }
            null
        }
}
