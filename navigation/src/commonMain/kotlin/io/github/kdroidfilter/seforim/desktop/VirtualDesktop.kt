package io.github.kdroidfilter.seforim.desktop

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class VirtualDesktop(
    val id: String,
    val name: String,
)
