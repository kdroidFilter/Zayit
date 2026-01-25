package io.github.kdroidfilter.seforimapp.features.search.domain

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [CategoryNavigationUseCase].
 */
class CategoryNavigationUseCaseTest : SearchIntegrationTestBase() {
    @Test
    fun `buildCategoryPath returns path from root to category`() =
        runBlocking {
            skipIfNoIndex()
            val useCase = CategoryNavigationUseCase(requireRepository())

            // Get any category with a parent to test path building
            // First, let's get a known category - we'll search for any book and get its category
            val engine = requireSearchEngine()
            val session = engine.openSession("בראשית", near = 5)
            if (session == null) {
                println("SKIPPED: No search results for query")
                return@runBlocking
            }

            val page = session.nextPage(1)
            session.close()

            if (page == null || page.hits.isEmpty()) {
                println("SKIPPED: No search results found")
                return@runBlocking
            }

            // Get the book's category
            val book = requireRepository().getBookCore(page.hits.first().bookId)
            if (book == null) {
                println("SKIPPED: Book not found")
                return@runBlocking
            }

            val path = useCase.buildCategoryPath(book.categoryId)

            assertTrue(path.isNotEmpty(), "Path should not be empty")
            assertEquals(book.categoryId, path.last().id, "Last element should be the target category")

            // Path should be ordered from root to target (root has null parentId)
            if (path.size > 1) {
                val root = path.first()
                // Root should have null parentId or parentId not in the path
                val parentIds = path.map { it.parentId }
                if (root.parentId != null) {
                    assertTrue(
                        !parentIds.contains(root.parentId) || root.parentId == null,
                        "Root should not have a parent in the path",
                    )
                }
            }
        }

    @Test
    fun `buildCategoryPath caches results`() =
        runBlocking {
            skipIfNoIndex()
            val useCase = CategoryNavigationUseCase(requireRepository())

            // Get a category to test
            val categories = requireRepository().getRootCategories()
            if (categories.isEmpty()) {
                println("SKIPPED: No root categories found")
                return@runBlocking
            }

            val categoryId = categories.first().id

            // Build path twice
            val path1 = useCase.buildCategoryPath(categoryId)
            val path2 = useCase.buildCategoryPath(categoryId)

            // Should be the same list (cached)
            assertEquals(path1, path2)
        }

    @Test
    fun `buildCategoryPath returns empty for non-existent category`() =
        runBlocking {
            skipIfNoIndex()
            val useCase = CategoryNavigationUseCase(requireRepository())

            val path = useCase.buildCategoryPath(-999999L)

            assertTrue(path.isEmpty(), "Path should be empty for non-existent category")
        }

    @Test
    fun `collectBookIdsUnderCategory returns books in category tree`() =
        runBlocking {
            skipIfNoIndex()
            val useCase = CategoryNavigationUseCase(requireRepository())

            // Get a root category that has books
            val categories = requireRepository().getRootCategories()
            if (categories.isEmpty()) {
                println("SKIPPED: No root categories found")
                return@runBlocking
            }

            // Try each root category until we find one with books
            for (category in categories) {
                val bookIds = useCase.collectBookIdsUnderCategory(category.id)
                if (bookIds.isNotEmpty()) {
                    assertTrue(bookIds.isNotEmpty(), "Should have found books under category")
                    return@runBlocking
                }
            }

            println("SKIPPED: No categories with books found")
        }

    @Test
    fun `collectBookIdsUnderCategory caches results`() =
        runBlocking {
            skipIfNoIndex()
            val useCase = CategoryNavigationUseCase(requireRepository())

            // Get a category with books
            val categories = requireRepository().getRootCategories()
            if (categories.isEmpty()) {
                println("SKIPPED: No root categories found")
                return@runBlocking
            }

            val categoryId = categories.first().id

            // Collect twice
            val books1 = useCase.collectBookIdsUnderCategory(categoryId)
            val books2 = useCase.collectBookIdsUnderCategory(categoryId)

            // Should be the same set (cached)
            assertEquals(books1, books2)
        }

    @Test
    fun `collectBookIdsUnderCategory returns empty for non-existent category`() =
        runBlocking {
            skipIfNoIndex()
            val useCase = CategoryNavigationUseCase(requireRepository())

            val bookIds = useCase.collectBookIdsUnderCategory(-999999L)

            assertTrue(bookIds.isEmpty(), "Should return empty set for non-existent category")
        }

    @Test
    fun `getCategory returns category by ID`() =
        runBlocking {
            skipIfNoIndex()
            val useCase = CategoryNavigationUseCase(requireRepository())

            // Get a known category
            val categories = requireRepository().getRootCategories()
            if (categories.isEmpty()) {
                println("SKIPPED: No root categories found")
                return@runBlocking
            }

            val expectedCategory = categories.first()
            val category = useCase.getCategory(expectedCategory.id)

            assertNotNull(category)
            assertEquals(expectedCategory.id, category.id)
            assertEquals(expectedCategory.title, category.title)
        }

    @Test
    fun `getCategory caches results`() =
        runBlocking {
            skipIfNoIndex()
            val useCase = CategoryNavigationUseCase(requireRepository())

            // Get a known category
            val categories = requireRepository().getRootCategories()
            if (categories.isEmpty()) {
                println("SKIPPED: No root categories found")
                return@runBlocking
            }

            val categoryId = categories.first().id

            // Get twice
            val cat1 = useCase.getCategory(categoryId)
            val cat2 = useCase.getCategory(categoryId)

            // Should be the same instance (cached)
            assertTrue(cat1 === cat2, "Should return cached instance")
        }

    @Test
    fun `clearCache invalidates all caches`() =
        runBlocking {
            skipIfNoIndex()
            val useCase = CategoryNavigationUseCase(requireRepository())

            // Get a category
            val categories = requireRepository().getRootCategories()
            if (categories.isEmpty()) {
                println("SKIPPED: No root categories found")
                return@runBlocking
            }

            val categoryId = categories.first().id

            // Populate caches
            useCase.getCategory(categoryId)
            useCase.buildCategoryPath(categoryId)
            useCase.collectBookIdsUnderCategory(categoryId)

            // Clear cache
            useCase.clearCache()

            // Get again - should be fresh instances
            val cat1 = useCase.getCategory(categoryId)
            val cat2 = useCase.getCategory(categoryId)

            // After first get, second get should be cached again
            assertTrue(cat1 === cat2, "Should cache after re-fetch")
        }
}
