package io.github.kdroidfilter.seforimapp.framework.database

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog
import io.github.kdroidfilter.seforimlibrary.dao.CatalogLoader
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import java.io.File

private const val DEFAULT_DB_NAME = "seforim.db"

/**
 * Gets the database path, preferring an environment variable if present,
 * falling back to AppSettings, and finally checking the default location.
 */
fun getDatabasePath(): String {
    // 1) Prefer an explicit environment variable override if provided
    val envDbPath = System.getenv("SEFORIMAPP_DATABASE_PATH")?.takeIf { it.isNotBlank() }

    // 2) Try AppSettings (but fix if it points to lexical.db which is wrong)
    val rawSettingsPath = AppSettings.getDatabasePath()
    val settingsPath = if (rawSettingsPath?.endsWith("lexical.db", ignoreCase = true) == true) {
        // Fix incorrect path by clearing it
        AppSettings.setDatabasePath(null)
        null
    } else {
        rawSettingsPath
    }

    // 3) Fallback to default location
    val defaultDbPath = File(FileKit.databasesDir.path, DEFAULT_DB_NAME).absolutePath

    val dbPath = envDbPath ?: settingsPath ?: defaultDbPath

    println("[DatabaseUtils] env=$envDbPath, rawSettings=$rawSettingsPath, settings=$settingsPath, default=$defaultDbPath, final=$dbPath")
    println("[DatabaseUtils] File exists: ${File(dbPath).exists()}")

    // Check if the database file exists
    val dbFile = File(dbPath)
    if (!dbFile.exists()) {
        throw IllegalStateException("Database file not found at $dbPath")
    }

    return dbPath
}


/**
 * Singleton holder for the precomputed catalog.
 * The catalog is loaded once at application startup and cached for the entire session.
 */
object CatalogCache {
    private var _catalog: PrecomputedCatalog? = null

    /**
     * Gets the cached catalog, loading it if necessary.
     * Returns null if the catalog file doesn't exist or can't be loaded.
     */
    fun getCatalog(): PrecomputedCatalog? {
        if (_catalog == null) {
            _catalog = loadCatalog()
        }
        return _catalog
    }

    /**
     * Loads the precomputed catalog from the catalog.pb file next to the database.
     */
    private fun loadCatalog(): PrecomputedCatalog? {
        return try {
            val dbPath = getDatabasePath()
            val catalog = CatalogLoader.loadCatalog(dbPath)

            if (catalog != null) {
                println("✓ Precomputed catalog loaded: ${catalog.totalCategories} categories, ${catalog.totalBooks} books")
            } else {
                println("⚠ Precomputed catalog not found, will load from database instead")
            }

            catalog
        } catch (e: Exception) {
            println("✗ Failed to load precomputed catalog: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Forces a reload of the catalog (useful after regeneration).
     */
    fun reloadCatalog() {
        _catalog = loadCatalog()
    }

    /**
     * Checks if the catalog is available.
     */
    fun isCatalogAvailable(): Boolean = getCatalog() != null
}
