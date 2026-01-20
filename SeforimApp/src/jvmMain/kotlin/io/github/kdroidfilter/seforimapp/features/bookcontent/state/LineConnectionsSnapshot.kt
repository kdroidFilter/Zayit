package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import androidx.compose.runtime.Immutable

@Immutable
data class LineConnectionsSnapshot(
    val commentatorGroups: List<CommentatorGroup> = emptyList(),
    val targumSources: Map<String, Long> = emptyMap(),
    val sources: Map<String, Long> = emptyMap(),
)
