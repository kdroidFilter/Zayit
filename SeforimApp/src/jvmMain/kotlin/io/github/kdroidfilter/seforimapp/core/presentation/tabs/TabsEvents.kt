package io.github.kdroidfilter.seforimapp.core.presentation.tabs

sealed class TabsEvents {
    data class onClose(val index: Int): TabsEvents()
    data class onSelected(val index: Int): TabsEvents()
    data object onAdd: TabsEvents()
}