package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

sealed class BookContentEvents {
    data class OnUpdateParagraphScrollPosition(val position: Int): BookContentEvents()

    data class OnUpdateChapterScrollPosition(val position: Int): BookContentEvents()
    data object SaveAllStates : BookContentEvents()
    data class OnSearchTextChange(val text: String): BookContentEvents()
    data class OnChapterSelected(val index: Int): BookContentEvents()


}