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
        /** Version of the manifest format this app understands. */
        const val FORMAT_VERSION = 1

        private val json = Json { ignoreUnknownKeys = true }

        /** The manifest, or null if it is malformed or in a format this app does not know. */
        fun parseOrNull(text: String): BundleManifest? =
            runCatching { json.decodeFromString<BundleManifest>(text) }
                .getOrNull()
                ?.takeIf { it.formatVersion == FORMAT_VERSION && it.parts.isNotEmpty() }
    }
}
