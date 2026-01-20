package io.github.kdroidfilter.seforim.tabs

sealed class TabsEvents {
    data class onClose(
        val index: Int,
    ) : TabsEvents()

    data class onSelected(
        val index: Int,
    ) : TabsEvents()

    data object onAdd : TabsEvents()

    data class OnReorder(
        val fromIndex: Int,
        val toIndex: Int,
    ) : TabsEvents()

    // Bulk/advanced close operations
    data object CloseAll : TabsEvents()

    data class CloseOthers(
        val index: Int,
    ) : TabsEvents()

    data class CloseLeft(
        val index: Int,
    ) : TabsEvents()

    data class CloseRight(
        val index: Int,
    ) : TabsEvents()
}
