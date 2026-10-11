package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `<bundle>.manifest.json` published by SeforimLibrary's packaging next to the bundle:
 * the files to download in concatenation order, with their size and sha256.
 */
@Serializable
data class BundleManifest(
    val formatVersion: Int,
    val file: String,
    val size: Long,
    val sha256: String,
    val parts: List<Part>,
) {
    @Serializable
    data class Part(
        val file: String,
        val size: Long,
        val sha256: String,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): BundleManifest =
            json.decodeFromString<BundleManifest>(text).also { manifest ->
                require(manifest.parts.isNotEmpty()) { "Bundle manifest lists no part" }
            }
    }
}
