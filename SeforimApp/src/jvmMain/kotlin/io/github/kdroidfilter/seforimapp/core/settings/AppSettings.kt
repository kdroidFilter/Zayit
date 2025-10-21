package io.github.kdroidfilter.seforimapp.core.settings

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages application settings and preferences that persist across app restarts.
 * Uses Multiplatform Settings library for cross-platform storage.
 * Single, global settings instance (no interface, no delegation).
 */
object AppSettings {
    // Text size constants
    const val DEFAULT_TEXT_SIZE = 16f
    const val MIN_TEXT_SIZE = 8f
    const val MAX_TEXT_SIZE = 32f
    const val TEXT_SIZE_INCREMENT = 2f

    // Line height constants
    const val DEFAULT_LINE_HEIGHT = 1.5f
    const val MIN_LINE_HEIGHT = 1.0f
    const val MAX_LINE_HEIGHT = 2.5f
    const val LINE_HEIGHT_INCREMENT = 0.1f

    // Tab display constants
    const val MAX_TAB_TITLE_LENGTH = 20

    // Settings keys
    private const val KEY_TEXT_SIZE = "text_size"
    private const val KEY_LINE_HEIGHT = "line_height"
    private const val KEY_CLOSE_TREE_ON_NEW_BOOK = "close_tree_on_new_book"
    private const val KEY_DATABASE_PATH = "database_path"
    private const val KEY_PERSIST_SESSION = "persist_session"
    private const val KEY_SAVED_SESSION = "saved_session_json"
    private const val KEY_SAVED_SESSION_PARTS_COUNT = "saved_session_parts_count"
    private const val KEY_SAVED_SESSION_PART_PREFIX = "saved_session_part_"
    private const val SESSION_CHUNK_SIZE = 4000

    // Onboarding state
    private const val KEY_ONBOARDING_FINISHED = "onboarding_finished"

    // Region configuration keys
    private const val KEY_REGION_COUNTRY = "region_country"
    private const val KEY_REGION_CITY = "region_city"

    // User profile keys
    private const val KEY_USER_FIRST_NAME = "user_first_name"
    private const val KEY_USER_LAST_NAME = "user_last_name"
    private const val KEY_USER_COMMUNITY = "user_community" // stores a stable code (e.g., "SEPHARADE")

    // Backing Settings storage (can be replaced at startup if needed)
    @Volatile
    private var settings: Settings = Settings()

    // Allow optional initialization with an externally provided Settings instance
    fun initialize(settings: Settings) {
        this.settings = settings
        // Refresh flows with current values from provided settings
        _textSizeFlow.value = getTextSize()
        _lineHeightFlow.value = getLineHeight()
        _closeTreeOnNewBookFlow.value = getCloseBookTreeOnNewBookSelected()
        _databasePathFlow.value = getDatabasePath()
        _persistSessionFlow.value = isPersistSessionEnabled()
    }

    // StateFlow to observe text size changes
    private val _textSizeFlow = MutableStateFlow(getTextSize())
    val textSizeFlow: StateFlow<Float> = _textSizeFlow.asStateFlow()

    // StateFlow to observe line height changes
    private val _lineHeightFlow = MutableStateFlow(getLineHeight())
    val lineHeightFlow: StateFlow<Float> = _lineHeightFlow.asStateFlow()

    // StateFlow for auto-close book tree setting
    private val _closeTreeOnNewBookFlow = MutableStateFlow(getCloseBookTreeOnNewBookSelected())
    val closeBookTreeOnNewBookSelectedFlow: StateFlow<Boolean> = _closeTreeOnNewBookFlow.asStateFlow()

    // StateFlow for database path (nullable)
    private val _databasePathFlow = MutableStateFlow(getDatabasePath())
    val databasePathFlow: StateFlow<String?> = _databasePathFlow.asStateFlow()

    // StateFlow for session persistence setting
    private val _persistSessionFlow = MutableStateFlow(isPersistSessionEnabled())
    val persistSessionFlow: StateFlow<Boolean> = _persistSessionFlow.asStateFlow()

    fun getTextSize(): Float {
        return settings[KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE]
    }

    fun setTextSize(size: Float) {
        settings[KEY_TEXT_SIZE] = size
        _textSizeFlow.value = size
    }

    fun increaseTextSize(increment: Float = TEXT_SIZE_INCREMENT) {
        val currentSize = getTextSize()
        val newSize = (currentSize + increment).coerceAtMost(MAX_TEXT_SIZE)
        setTextSize(newSize)
    }

    fun decreaseTextSize(decrement: Float = TEXT_SIZE_INCREMENT) {
        val currentSize = getTextSize()
        val newSize = (currentSize - decrement).coerceAtLeast(MIN_TEXT_SIZE)
        setTextSize(newSize)
    }

    fun getLineHeight(): Float {
        return settings[KEY_LINE_HEIGHT, DEFAULT_LINE_HEIGHT]
    }

    fun setLineHeight(height: Float) {
        settings[KEY_LINE_HEIGHT] = height
        _lineHeightFlow.value = height
    }

    fun increaseLineHeight(increment: Float = LINE_HEIGHT_INCREMENT) {
        val currentHeight = getLineHeight()
        val newHeight = (currentHeight + increment).coerceAtMost(MAX_LINE_HEIGHT)
        setLineHeight(newHeight)
    }

    fun decreaseLineHeight(decrement: Float = LINE_HEIGHT_INCREMENT) {
        val currentHeight = getLineHeight()
        val newHeight = (currentHeight - decrement).coerceAtLeast(MIN_LINE_HEIGHT)
        setLineHeight(newHeight)
    }

    fun getCloseBookTreeOnNewBookSelected(): Boolean {
        return settings[KEY_CLOSE_TREE_ON_NEW_BOOK, false]
    }

    fun setCloseBookTreeOnNewBookSelected(value: Boolean) {
        settings[KEY_CLOSE_TREE_ON_NEW_BOOK] = value
        _closeTreeOnNewBookFlow.value = value
    }

    // Database path settings
    // Returns null if not configured or if stored as an empty string
    fun getDatabasePath(): String? {
        val value: String = settings[KEY_DATABASE_PATH, ""]
        return value.ifBlank { null }
    }

    fun setDatabasePath(path: String?) {
        if (path == null || path.isBlank()) {
            // Clear by setting empty string
            settings[KEY_DATABASE_PATH] = ""
            _databasePathFlow.value = null
        } else {
            settings[KEY_DATABASE_PATH] = path
            _databasePathFlow.value = path
        }
    }

    // Session persistence preference
    fun isPersistSessionEnabled(): Boolean {
        return settings[KEY_PERSIST_SESSION, false]
    }

    fun setPersistSessionEnabled(enabled: Boolean) {
        settings[KEY_PERSIST_SESSION] = enabled
        _persistSessionFlow.value = enabled
        if (!enabled) {
            // Clear any previously saved session when disabling persistence
            setSavedSessionJson(null)
        }
    }

    // Saved session blob (JSON)
    fun getSavedSessionJson(): String? {
        // Prefer chunked storage if present
        val partsCount: Int = settings[KEY_SAVED_SESSION_PARTS_COUNT, 0]
        if (partsCount > 0) {
            val sb = StringBuilder(partsCount * SESSION_CHUNK_SIZE)
            for (i in 0 until partsCount) {
                val partKey = "$KEY_SAVED_SESSION_PART_PREFIX$i"
                sb.append(settings[partKey, ""])
            }
            val result = sb.toString()
            return result.ifBlank { null }
        }
        // Backward compatibility (single key)
        val legacy: String = settings[KEY_SAVED_SESSION, ""]
        return legacy.ifBlank { null }
    }

    // Region configuration accessors
    fun getRegionCountry(): String? {
        val value: String = settings[KEY_REGION_COUNTRY, ""]
        return value.ifBlank { null }
    }

    fun setRegionCountry(value: String?) {
        settings[KEY_REGION_COUNTRY] = value?.takeIf { it.isNotBlank() } ?: ""
    }

    fun getRegionCity(): String? {
        val value: String = settings[KEY_REGION_CITY, ""]
        return value.ifBlank { null }
    }

    fun setRegionCity(value: String?) {
        settings[KEY_REGION_CITY] = value?.takeIf { it.isNotBlank() } ?: ""
    }

    // Onboarding finished flag
    fun isOnboardingFinished(): Boolean {
        return settings[KEY_ONBOARDING_FINISHED, false]
    }

    fun setOnboardingFinished(finished: Boolean) {
        settings[KEY_ONBOARDING_FINISHED] = finished
    }

    // User profile accessors
    fun getUserFirstName(): String? {
        val value: String = settings[KEY_USER_FIRST_NAME, ""]
        return value.ifBlank { null }
    }

    fun setUserFirstName(value: String?) {
        settings[KEY_USER_FIRST_NAME] = value?.takeIf { it.isNotBlank() } ?: ""
    }

    fun getUserLastName(): String? {
        val value: String = settings[KEY_USER_LAST_NAME, ""]
        return value.ifBlank { null }
    }

    fun setUserLastName(value: String?) {
        settings[KEY_USER_LAST_NAME] = value?.takeIf { it.isNotBlank() } ?: ""
    }

    // Community is stored as a stable code (enum name), not a localized label
    fun getUserCommunityCode(): String? {
        val value: String = settings[KEY_USER_COMMUNITY, ""]
        return value.ifBlank { null }
    }

    fun setUserCommunityCode(value: String?) {
        settings[KEY_USER_COMMUNITY] = value?.takeIf { it.isNotBlank() } ?: ""
    }

    fun setSavedSessionJson(json: String?) {
        if (json.isNullOrBlank()) {
            // Clear legacy and chunked storage
            settings[KEY_SAVED_SESSION] = ""
            val oldCount: Int = settings[KEY_SAVED_SESSION_PARTS_COUNT, 0]
            if (oldCount > 0) {
                for (i in 0 until oldCount) {
                    val partKey = "$KEY_SAVED_SESSION_PART_PREFIX$i"
                    settings[partKey] = ""
                }
                settings[KEY_SAVED_SESSION_PARTS_COUNT] = 0
            }
            return
        }

        // Write chunked to avoid JVM Preferences value-length limits
        val totalLength = json.length
        val parts = (totalLength + SESSION_CHUNK_SIZE - 1) / SESSION_CHUNK_SIZE
        settings[KEY_SAVED_SESSION_PARTS_COUNT] = parts
        for (i in 0 until parts) {
            val start = i * SESSION_CHUNK_SIZE
            val end = minOf(start + SESSION_CHUNK_SIZE, totalLength)
            val partKey = "$KEY_SAVED_SESSION_PART_PREFIX$i"
            settings[partKey] = json.substring(start, end)
        }
        // Clear legacy single key to avoid oversized writes
        settings[KEY_SAVED_SESSION] = ""
    }

    // Clears all persisted settings and resets in-memory flows to defaults
    fun clearAll() {
        settings.clear()
        _textSizeFlow.value = DEFAULT_TEXT_SIZE
        _lineHeightFlow.value = DEFAULT_LINE_HEIGHT
        _closeTreeOnNewBookFlow.value = false
        _databasePathFlow.value = null
        _persistSessionFlow.value = false
    }
}
