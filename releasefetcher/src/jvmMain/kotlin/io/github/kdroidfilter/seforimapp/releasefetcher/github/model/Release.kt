package io.github.kdroidfilter.seforimapp.releasefetcher.github.model

import kotlinx.serialization.Serializable

@Serializable
data class Release(
    val url: String,
    val assets_url: String,
    val upload_url: String,
    val html_url: String,
    val id: Int,
    val author: Author,
    val node_id: String,
    val tag_name: String,
    val target_commitish: String,
    val name: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val created_at: String,
    val published_at: String,
    val assets: List<Asset>,
    val tarball_url: String,
    val zipball_url: String,
    val body: String? = null,
    val mentions_count: Int? = null,
)
