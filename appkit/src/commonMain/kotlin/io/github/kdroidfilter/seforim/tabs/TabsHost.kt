package io.github.kdroidfilter.seforim.tabs

/**
 * The tabs of one window as the platform holds them: their order, the selection and the pins (the desktop's
 * `TabWorkspace`, driven by its strip). [TabsViewModel] mirrors it through `snapshotFlow`, so every read must come
 * from snapshot state; every call happens on the UI thread.
 */
interface TabsHost {
    /** The window's tab ids, in order (empty once the window is gone). */
    fun ids(): List<String>

    fun selectedId(): String?

    fun item(tabId: String): TabItem?

    /** Whether the window has declared [tabId] (a tab added is only declared on its next frame). */
    fun isDeclared(tabId: String): Boolean

    fun isPinned(tabId: String): Boolean

    fun setPinned(
        tabId: String,
        pinned: Boolean,
    )

    fun select(tabId: String)

    fun close(tabId: String)

    fun reorder(
        tabId: String,
        toIndex: Int,
    )

    /** Adds [destination] at [index] of the window, selected; in place of [replacing] when given. */
    fun add(
        destination: TabsDestination,
        index: Int,
        replacing: String? = null,
        pinned: Boolean = false,
    )

    fun updateDestination(
        tabId: String,
        destination: TabsDestination,
    )
}
