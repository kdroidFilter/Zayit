package io.github.kdroidfilter.seforimapp.features.search

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf

/**
 * Persists SearchTabCache snapshots to disk so they can be restored on cold boot
 * without re-running the search.
 *
 * Previously JSON was used; we now use ProtoBuf for faster, smaller IO.
 * JSON fallback is kept for a smooth one-time migration from existing caches.
 */
@OptIn(ExperimentalSerializationApi::class)
object SearchTabPersistentCache {
    // JSON kept only for backward-compat load of existing cache files
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    // ProtoBuf for new saves/loads
    private val proto = ProtoBuf

    private fun baseDir(): File {
        // Use a subdirectory next to the DB location to keep related data together
        val dirV = FileKit.databasesDir
        val dir = File(dirV.path, "search-cache").apply { mkdirs() }
        return dir
    }

    private fun sanitize(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun binFileFor(tabId: String): File = File(baseDir(), "tab_${sanitize(tabId)}.pb")
    private fun jsonFileFor(tabId: String): File = File(baseDir(), "tab_${sanitize(tabId)}.json")

    fun save(tabId: String, snapshot: SearchTabCache.Snapshot) {
        runCatching {
            val file = binFileFor(tabId)
            val bytes = proto.encodeToByteArray(SearchTabCache.Snapshot.serializer(), snapshot)
            file.writeBytes(bytes)
            // Best-effort cleanup of any legacy JSON file
            runCatching { jsonFileFor(tabId).delete() }
        }
    }

    fun load(tabId: String): SearchTabCache.Snapshot? {
        // Prefer ProtoBuf; fallback to legacy JSON once, then re-save in ProtoBuf
        binFileFor(tabId).let { bin ->
            if (bin.exists()) {
                return runCatching {
                    val bytes = bin.readBytes()
                    proto.decodeFromByteArray(SearchTabCache.Snapshot.serializer(), bytes)
                }.getOrNull()
            }
        }

        // Legacy JSON migration path
        jsonFileFor(tabId).let { js ->
            if (js.exists()) {
                val snap = runCatching {
                    val text = js.readText()
                    json.decodeFromString(SearchTabCache.Snapshot.serializer(), text)
                }.getOrNull()
                // If parsed, immediately rewrite as ProtoBuf for next time
                if (snap != null) save(tabId, snap)
                return snap
            }
        }
        return null
    }

    fun clear(tabId: String) {
        runCatching { binFileFor(tabId).delete() }
        runCatching { jsonFileFor(tabId).delete() }
    }
}
