package io.github.kdroidfilter.seforim.desktop

import kotlinx.serialization.Serializable

@Serializable
data class VirtualDesktop(
    val id: String,
    val name: String,
)
