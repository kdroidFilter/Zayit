package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import androidx.compose.runtime.Immutable

@Immutable
data class CommentatorItem(
    val name: String,
    val bookId: Long,
)

@Immutable
data class CommentatorGroup(
    val label: String,
    val commentators: List<CommentatorItem>,
)
