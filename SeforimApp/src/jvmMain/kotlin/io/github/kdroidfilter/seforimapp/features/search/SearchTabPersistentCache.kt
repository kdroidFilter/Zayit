package io.github.kdroidfilter.seforimapp.features.search

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists SearchTabCache snapshots to disk so they can be restored on cold boot
 * without re-running the search.
 */
object SearchTabPersistentCache {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun baseDir(): File {
        // Use a subdirectory next to the DB location to keep related data together
        val dirV = FileKit.databasesDir
        val dir = File(dirV.path, "search-cache").apply { mkdirs() }
        return dir
    }

    private fun sanitize(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun fileFor(tabId: String): File = File(baseDir(), "tab_${sanitize(tabId)}.json")

    fun save(tabId: String, snapshot: SearchTabCache.Snapshot) {
        runCatching {
            val file = fileFor(tabId)
            val text = json.encodeToString(SearchTabCache.Snapshot.serializer(), snapshot)
            file.writeText(text)
        }
    }

    fun load(tabId: String): SearchTabCache.Snapshot? {
        return runCatching {
            val file = fileFor(tabId)
            if (!file.exists()) return@runCatching null
            val text = file.readText()
            json.decodeFromString(SearchTabCache.Snapshot.serializer(), text)
        }.getOrNull()
    }

    fun clear(tabId: String) {
        runCatching { fileFor(tabId).delete() }
    }
}

