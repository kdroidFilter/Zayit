package io.github.kdroidfilter.seforimapp.bookcontent.presentation.state

import androidx.compose.runtime.Immutable

@Immutable
data class CommentariesCategoryExpansionState(
    val expandedByCategoryId: Map<Long, Boolean> = emptyMap()
) {
    fun isExpanded(categoryId: Long): Boolean = expandedByCategoryId[categoryId] == true

    fun setExpanded(categoryId: Long, expanded: Boolean): CommentariesCategoryExpansionState {
        val updated = if (expanded) {
            expandedByCategoryId + (categoryId to true)
        } else {
            expandedByCategoryId - categoryId
        }
        return copy(expandedByCategoryId = updated)
    }

    fun toggle(categoryId: Long): CommentariesCategoryExpansionState {
        return setExpanded(categoryId, !isExpanded(categoryId))
    }
}
