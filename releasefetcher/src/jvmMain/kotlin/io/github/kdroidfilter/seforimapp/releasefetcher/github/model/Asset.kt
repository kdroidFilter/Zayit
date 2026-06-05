package io.github.kdroidfilter.seforimapp.releasefetcher.github.model

import kotlinx.serialization.Serializable

@Serializable
data class Asset(
    val url: String,
    val id: Int,
    val node_id: String,
    val name: String,
    val label: String? = null,
    val uploader: Uploader,
    val content_type: String,
    val state: String,
    val size: Int,
    val download_count: Int,
    val created_at: String,
    val updated_at: String,
    val browser_download_url: String,
)
