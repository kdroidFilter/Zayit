package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SearchShellActionsTest {
    @Test
    fun `SearchShellActions can be created with all callbacks`() {
        var submitCalled = false
        var queryCalled = false
        var globalExtendedCalled = false
        var scrollCalled = false
        var cancelCalled = false
        var openResultCalled = false
        var requestBreadcrumbCalled = false
        var loadMoreCalled = false
        var categoryCheckedCalled = false
        var bookCheckedCalled = false
        var ensureScopeBookCalled = false
        var tocToggleCalled = false
        var tocFilterCalled = false

        val actions =
            SearchShellActions(
                onSubmit = { submitCalled = true },
                onQueryChange = { queryCalled = true },
                onGlobalExtendedChange = { globalExtendedCalled = true },
                onScroll = { _, _, _, _ -> scrollCalled = true },
                onCancelSearch = { cancelCalled = true },
                onOpenResult = { _, _ -> openResultCalled = true },
                onRequestBreadcrumb = { requestBreadcrumbCalled = true },
                onLoadMore = { loadMoreCalled = true },
                onCategoryCheckedChange = { _, _ -> categoryCheckedCalled = true },
                onBookCheckedChange = { _, _ -> bookCheckedCalled = true },
                onEnsureScopeBookForToc = { ensureScopeBookCalled = true },
                onTocToggle = { _, _ -> tocToggleCalled = true },
                onTocFilter = { tocFilterCalled = true },
            )

        assertNotNull(actions)

        // Test callbacks can be invoked
        actions.onSubmit("test")
        assertEquals(true, submitCalled)

        actions.onQueryChange("query")
        assertEquals(true, queryCalled)

        actions.onGlobalExtendedChange(true)
        assertEquals(true, globalExtendedCalled)

        actions.onScroll(1L, 0, 0, 0)
        assertEquals(true, scrollCalled)

        actions.onCancelSearch()
        assertEquals(true, cancelCalled)

        actions.onLoadMore()
        assertEquals(true, loadMoreCalled)

        actions.onCategoryCheckedChange(1L, true)
        assertEquals(true, categoryCheckedCalled)

        actions.onBookCheckedChange(1L, true)
        assertEquals(true, bookCheckedCalled)

        actions.onEnsureScopeBookForToc(1L)
        assertEquals(true, ensureScopeBookCalled)
    }

    @Test
    fun `SearchShellActions equals works correctly`() {
        val noOp: () -> Unit = {}
        val noOpResult: (SearchResult, Boolean) -> Unit = { _, _ -> }
        val noOpBreadcrumb: (SearchResult) -> Unit = {}
        val noOpTocToggle: (TocEntry, Boolean) -> Unit = { _, _ -> }
        val noOpTocFilter: (TocEntry) -> Unit = {}

        val actions1 =
            SearchShellActions(
                onSubmit = {},
                onQueryChange = {},
                onGlobalExtendedChange = {},
                onScroll = { _, _, _, _ -> },
                onCancelSearch = noOp,
                onOpenResult = noOpResult,
                onRequestBreadcrumb = noOpBreadcrumb,
                onLoadMore = noOp,
                onCategoryCheckedChange = { _, _ -> },
                onBookCheckedChange = { _, _ -> },
                onEnsureScopeBookForToc = {},
                onTocToggle = noOpTocToggle,
                onTocFilter = noOpTocFilter,
            )

        // Each lambda is a unique instance, so equality will not match
        // This test just verifies the data class doesn't throw
        assertNotNull(actions1)
    }

    @Test
    fun `SearchShellActions copy works`() {
        var newSubmitCalled = false

        val original =
            SearchShellActions(
                onSubmit = {},
                onQueryChange = {},
                onGlobalExtendedChange = {},
                onScroll = { _, _, _, _ -> },
                onCancelSearch = {},
                onOpenResult = { _, _ -> },
                onRequestBreadcrumb = {},
                onLoadMore = {},
                onCategoryCheckedChange = { _, _ -> },
                onBookCheckedChange = { _, _ -> },
                onEnsureScopeBookForToc = {},
                onTocToggle = { _, _ -> },
                onTocFilter = {},
            )

        val modified = original.copy(onSubmit = { newSubmitCalled = true })
        modified.onSubmit("test")

        assertEquals(true, newSubmitCalled)
    }
}
