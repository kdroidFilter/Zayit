package io.github.kdroidfilter.seforim.tabs

sealed class TabsEvents {
    data class onClose(val index: Int): TabsEvents()
    data class onSelected(val index: Int): TabsEvents()
    data object onAdd: TabsEvents()
}