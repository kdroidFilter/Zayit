package io.github.kdroidfilter.seforimapp.core.settings

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.presentation.theme.AccentColor
import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages application settings and preferences that persist across app restarts.
 * Uses Multiplatform Settings library for cross-platform storage.
 * App-scoped in the Metro graph; read it from there (constructor or LocalAppGraph).
 */
@Inject
@SingleIn(AppScope::class)
class AppSettings(
    private val settings: Settings,
) {
    // StateFlow to observe text size changes
    private val _textSizeFlow = MutableStateFlow(getTextSize())
    val textSizeFlow: StateFlow<Float> = _textSizeFlow.asStateFlow()

    // StateFlow to observe line height changes
    private val _lineHeightFlow = MutableStateFlow(getLineHeight())
    val lineHeightFlow: StateFlow<Float> = _lineHeightFlow.asStateFlow()

    // StateFlow to observe the max-commentators-per-page setting
    private val _maxCommentatorsPerPageFlow = MutableStateFlow(getMaxCommentatorsPerPage())
    val maxCommentatorsPerPageFlow: StateFlow<Int> = _maxCommentatorsPerPageFlow.asStateFlow()

    // StateFlow for auto-close book tree setting
    private val _closeTreeOnNewBookFlow = MutableStateFlow(getCloseBookTreeOnNewBookSelected())
    val closeBookTreeOnNewBookSelectedFlow: StateFlow<Boolean> = _closeTreeOnNewBookFlow.asStateFlow()

    // StateFlow for database path (nullable)
    private val _databasePathFlow = MutableStateFlow(getDatabasePath())
    val databasePathFlow: StateFlow<String?> = _databasePathFlow.asStateFlow()

    // StateFlow for session persistence setting
    private val _persistSessionFlow = MutableStateFlow(isPersistSessionEnabled())
    val persistSessionFlow: StateFlow<Boolean> = _persistSessionFlow.asStateFlow()

    // StateFlow for keep-screen-awake-while-reading setting
    private val _keepScreenAwakeOnBookFlow = MutableStateFlow(isKeepScreenAwakeOnBookEnabled())
    val keepScreenAwakeOnBookFlow: StateFlow<Boolean> = _keepScreenAwakeOnBookFlow.asStateFlow()

    private val _homeWidgetsLayoutFlow = MutableStateFlow(settings.getStringOrNull(KEY_HOME_WIDGETS_LAYOUT))
    val homeWidgetsLayoutFlow: StateFlow<String?> = _homeWidgetsLayoutFlow.asStateFlow()

    // Each Home widget's own options, set from its menu, by widget id; their format is the widget's own
    private val _homeWidgetOptionsFlow =
        MutableStateFlow(
            settings.keys
                .filter { it.startsWith(KEY_HOME_WIDGET_OPTIONS_PREFIX) }
                .associate { it.removePrefix(KEY_HOME_WIDGET_OPTIONS_PREFIX) to settings.getString(it, "") },
        )
    val homeWidgetOptionsFlow: StateFlow<Map<String, String>> = _homeWidgetOptionsFlow.asStateFlow()

    // StateFlow for homepage wallpaper visibility
    private val _showHomeWallpaperFlow = MutableStateFlow(isShowHomeWallpaperEnabled())
    val showHomeWallpaperFlow: StateFlow<Boolean> = _showHomeWallpaperFlow.asStateFlow()

    // StateFlow for compact mode
    private val _searchBookDetailsFlow = MutableStateFlow(isSearchBookDetailsVisible())

    // The search's book-details pane, shown or hidden as the book's panes
    val searchBookDetailsFlow: StateFlow<Boolean> = _searchBookDetailsFlow.asStateFlow()
    private val _compactModeFlow = MutableStateFlow(isCompactModeEnabled())
    val compactModeFlow: StateFlow<Boolean> = _compactModeFlow.asStateFlow()

    // Font preference flows
    private val _bookFontCodeFlow = MutableStateFlow(getBookFontCode())
    val bookFontCodeFlow: StateFlow<String> = _bookFontCodeFlow.asStateFlow()

    private val _commentaryFontCodeFlow = MutableStateFlow(getCommentaryFontCode())
    val commentaryFontCodeFlow: StateFlow<String> = _commentaryFontCodeFlow.asStateFlow()

    private val _targumFontCodeFlow = MutableStateFlow(getTargumFontCode())
    val targumFontCodeFlow: StateFlow<String> = _targumFontCodeFlow.asStateFlow()

    private val _sourceFontCodeFlow = MutableStateFlow(getSourceFontCode())
    val sourceFontCodeFlow: StateFlow<String> = _sourceFontCodeFlow.asStateFlow()

    // Find-in-page state (scoped per tab, not persisted)
    private val findQueryFlowByTab = mutableMapOf<String, MutableStateFlow<String>>()
    private val findBarOpenFlowByTab = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val findSmartModeByTab = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val findFocusRequestByTab = mutableMapOf<String, MutableStateFlow<Int>>()
    private val findStepRequestsByTab = mutableMapOf<String, MutableSharedFlow<Boolean>>()

    private fun focusRequestFlowFor(tabId: String): MutableStateFlow<Int> = findFocusRequestByTab.getOrPut(tabId) { MutableStateFlow(0) }

    private fun stepRequestsFor(tabId: String): MutableSharedFlow<Boolean> =
        findStepRequestsByTab.getOrPut(tabId) {
            MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        }

    private fun queryFlowFor(tabId: String): MutableStateFlow<String> = findQueryFlowByTab.getOrPut(tabId) { MutableStateFlow("") }

    private fun findOpenFlowFor(tabId: String): MutableStateFlow<Boolean> = findBarOpenFlowByTab.getOrPut(tabId) { MutableStateFlow(false) }

    private fun smartModeFlowFor(tabId: String): MutableStateFlow<Boolean> = findSmartModeByTab.getOrPut(tabId) { MutableStateFlow(false) }

    fun findQueryFlow(tabId: String): StateFlow<String> = queryFlowFor(tabId).asStateFlow()

    fun setFindQuery(
        tabId: String,
        q: String,
    ) {
        queryFlowFor(tabId).value = q
    }

    fun findBarOpenFlow(tabId: String): StateFlow<Boolean> = findOpenFlowFor(tabId).asStateFlow()

    fun openFindBar(tabId: String) {
        findOpenFlowFor(tabId).value = true
    }

    fun closeFindBar(tabId: String) {
        findOpenFlowFor(tabId).value = false
    }

    /** Bumped each time the find field must take the focus with its text selected. */
    fun findFocusRequestFlow(tabId: String): StateFlow<Int> = focusRequestFlowFor(tabId).asStateFlow()

    /** Find next (true) / previous (false) requested from outside the find field. */
    fun findStepRequests(tabId: String): SharedFlow<Boolean> = stepRequestsFor(tabId).asSharedFlow()

    /**
     * Ctrl/Cmd+F, as in Chromium: shows the find bar, or keeps it shown, and focuses its field
     * with the text selected. A new session starts from [selection] when it's short enough.
     */
    fun showFindBar(
        tabId: String,
        selection: String = "",
    ) {
        val open = findOpenFlowFor(tabId)
        // Spaces collapsed as the context menu's "find" does, so both give the same query
        val prefill = selection.replace(WHITESPACE, " ").trim()
        if (!open.value && prefill.length in 2..MAX_FIND_PREFILL_LENGTH) queryFlowFor(tabId).value = prefill
        open.value = true
        focusRequestFlowFor(tabId).value++
    }

    /** Ctrl/Cmd+G and F3, Shift going backwards, as in Chromium: shows the find bar and steps to the next match. */
    fun findNext(
        tabId: String,
        forward: Boolean,
    ) {
        showFindBar(tabId)
        stepRequestsFor(tabId).tryEmit(forward)
    }

    fun findSmartModeFlow(tabId: String): StateFlow<Boolean> = smartModeFlowFor(tabId).asStateFlow()

    fun setFindSmartMode(
        tabId: String,
        enabled: Boolean,
    ) {
        smartModeFlowFor(tabId).value = enabled
    }

    fun toggleFindSmartMode(tabId: String) {
        val flow = smartModeFlowFor(tabId)
        flow.value = !flow.value
    }

    fun getTextSize(): Float = settings[KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE]

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

    // Max commentators per commentaries page (0 = automatic). Stored value is clamped to the
    // supported range so a stale or out-of-range persisted value can never break the grid.
    fun getMaxCommentatorsPerPage(): Int =
        settings[KEY_MAX_COMMENTATORS_PER_PAGE, DEFAULT_MAX_COMMENTATORS_PER_PAGE]
            .coerceIn(MAX_COMMENTATORS_PER_PAGE_AUTO, MAX_COMMENTATORS_PER_PAGE_LIMIT)

    fun setMaxCommentatorsPerPage(value: Int) {
        val clamped = value.coerceIn(MAX_COMMENTATORS_PER_PAGE_AUTO, MAX_COMMENTATORS_PER_PAGE_LIMIT)
        settings[KEY_MAX_COMMENTATORS_PER_PAGE] = clamped
        _maxCommentatorsPerPageFlow.value = clamped
    }

    fun getLineHeight(): Float = settings[KEY_LINE_HEIGHT, DEFAULT_LINE_HEIGHT]

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

    // Font settings (persist codes for cross-platform stability)
    fun getBookFontCode(): String = settings[KEY_FONT_BOOK, DEFAULT_BOOK_FONT]

    fun setBookFontCode(code: String) {
        settings[KEY_FONT_BOOK] = code
        _bookFontCodeFlow.value = code
    }

    fun getCommentaryFontCode(): String = settings[KEY_FONT_COMMENTARY, DEFAULT_COMMENTARY_FONT]

    fun setCommentaryFontCode(code: String) {
        settings[KEY_FONT_COMMENTARY] = code
        _commentaryFontCodeFlow.value = code
    }

    fun getTargumFontCode(): String = settings[KEY_FONT_TARGUM, DEFAULT_TARGUM_FONT]

    fun setTargumFontCode(code: String) {
        settings[KEY_FONT_TARGUM] = code
        _targumFontCodeFlow.value = code
    }

    fun getSourceFontCode(): String = settings[KEY_FONT_SOURCE, DEFAULT_SOURCE_FONT]

    fun setSourceFontCode(code: String) {
        settings[KEY_FONT_SOURCE] = code
        _sourceFontCodeFlow.value = code
    }

    fun getCloseBookTreeOnNewBookSelected(): Boolean = settings[KEY_CLOSE_TREE_ON_NEW_BOOK, false]

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
    fun isPersistSessionEnabled(): Boolean = settings[KEY_PERSIST_SESSION, true]

    fun setPersistSessionEnabled(enabled: Boolean) {
        settings[KEY_PERSIST_SESSION] = enabled
        _persistSessionFlow.value = enabled
        if (!enabled) {
            // Clear any previously saved session when disabling persistence
            setSavedSessionJson(null)
        }
    }

    // Keep the screen awake while a book is open and the window is focused (enabled by default)
    fun isKeepScreenAwakeOnBookEnabled(): Boolean = settings[KEY_KEEP_SCREEN_AWAKE_ON_BOOK, true]

    fun setKeepScreenAwakeOnBookEnabled(enabled: Boolean) {
        settings[KEY_KEEP_SCREEN_AWAKE_ON_BOOK] = enabled
        _keepScreenAwakeOnBookFlow.value = enabled
    }

    /** Null goes back to the default layout. */
    fun setHomeWidgetsLayout(layout: String?) {
        if (layout == null) settings.remove(KEY_HOME_WIDGETS_LAYOUT) else settings[KEY_HOME_WIDGETS_LAYOUT] = layout
        _homeWidgetsLayoutFlow.value = layout
    }

    /** Null goes back to the widget's defaults. */
    fun setHomeWidgetOptions(
        widgetId: String,
        options: String?,
    ) {
        val key = KEY_HOME_WIDGET_OPTIONS_PREFIX + widgetId
        if (options == null) settings.remove(key) else settings[key] = options
        _homeWidgetOptionsFlow.value =
            if (options == null) _homeWidgetOptionsFlow.value - widgetId else _homeWidgetOptionsFlow.value + (widgetId to options)
    }

    // Homepage wallpaper visibility
    fun isShowHomeWallpaperEnabled(): Boolean = settings[KEY_SHOW_HOME_WALLPAPER, true]

    fun setShowHomeWallpaperEnabled(enabled: Boolean) {
        settings[KEY_SHOW_HOME_WALLPAPER] = enabled
        _showHomeWallpaperFlow.value = enabled
    }

    fun isSearchBookDetailsVisible(): Boolean = settings[KEY_SEARCH_BOOK_DETAILS, true]

    fun setSearchBookDetailsVisible(visible: Boolean) {
        settings[KEY_SEARCH_BOOK_DETAILS] = visible
        _searchBookDetailsFlow.value = visible
    }

    // Compact mode for vertical bars
    fun isCompactModeEnabled(): Boolean = settings[KEY_COMPACT_MODE, false]

    fun setCompactModeEnabled(enabled: Boolean) {
        settings[KEY_COMPACT_MODE] = enabled
        _compactModeFlow.value = enabled
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
    fun isOnboardingFinished(): Boolean = settings[KEY_ONBOARDING_FINISHED, false]

    fun setOnboardingFinished(finished: Boolean) {
        settings[KEY_ONBOARDING_FINISHED] = finished
    }

    // User profile accessors
    // Reactive flows to observe user identity changes across the app
    private val _userFirstNameFlow = MutableStateFlow(getUserFirstName() ?: "")
    val userFirstNameFlow: StateFlow<String> = _userFirstNameFlow.asStateFlow()

    private val _userLastNameFlow = MutableStateFlow(getUserLastName() ?: "")
    val userLastNameFlow: StateFlow<String> = _userLastNameFlow.asStateFlow()

    private val _userCommunityCodeFlow = MutableStateFlow(getUserCommunityCode())
    val userCommunityCodeFlow: StateFlow<String?> = _userCommunityCodeFlow.asStateFlow()

    fun getUserFirstName(): String? {
        val value: String = settings[KEY_USER_FIRST_NAME, ""]
        return value.ifBlank { null }
    }

    fun setUserFirstName(value: String?) {
        settings[KEY_USER_FIRST_NAME] = value?.takeIf { it.isNotBlank() } ?: ""
        _userFirstNameFlow.value = getUserFirstName() ?: ""
    }

    fun getUserLastName(): String? {
        val value: String = settings[KEY_USER_LAST_NAME, ""]
        return value.ifBlank { null }
    }

    fun setUserLastName(value: String?) {
        settings[KEY_USER_LAST_NAME] = value?.takeIf { it.isNotBlank() } ?: ""
        _userLastNameFlow.value = getUserLastName() ?: ""
    }

    // Community is stored as a stable code (enum name), not a localized label
    fun getUserCommunityCode(): String? {
        val value: String = settings[KEY_USER_COMMUNITY, ""]
        return value.ifBlank { null }
    }

    fun setUserCommunityCode(value: String?) {
        settings[KEY_USER_COMMUNITY] = value?.takeIf { it.isNotBlank() } ?: ""
        _userCommunityCodeFlow.value = getUserCommunityCode()
    }

    // Siddur: the nusach (null follows the community), and whether the user davens with a minyan
    private val _siddurNusachFlow = MutableStateFlow(getSiddurNusachCode())
    val siddurNusachFlow: StateFlow<String?> = _siddurNusachFlow.asStateFlow()

    fun getSiddurNusachCode(): String? = settings[KEY_SIDDUR_NUSACH, ""].ifBlank { null }

    fun setSiddurNusachCode(value: String?) {
        settings[KEY_SIDDUR_NUSACH] = value.orEmpty()
        _siddurNusachFlow.value = getSiddurNusachCode()
    }

    private val _siddurMinyanFlow = MutableStateFlow(isSiddurMinyan())
    val siddurMinyanFlow: StateFlow<Boolean> = _siddurMinyanFlow.asStateFlow()

    fun isSiddurMinyan(): Boolean = settings[KEY_SIDDUR_MINYAN, true]

    fun setSiddurMinyan(value: Boolean) {
        settings[KEY_SIDDUR_MINYAN] = value
        _siddurMinyanFlow.value = value
    }

    private val _siddurBeitAvelFlow = MutableStateFlow(isSiddurBeitAvel())
    val siddurBeitAvelFlow: StateFlow<Boolean> = _siddurBeitAvelFlow.asStateFlow()

    fun isSiddurBeitAvel(): Boolean = settings[KEY_SIDDUR_BEIT_AVEL, false]

    fun setSiddurBeitAvel(value: Boolean) {
        settings[KEY_SIDDUR_BEIT_AVEL] = value
        _siddurBeitAvelFlow.value = value
    }

    // Theme mode (Light/Dark/System) setting
    fun getThemeMode(): IntUiThemes {
        val storedValue: String = settings[KEY_THEME_MODE, IntUiThemes.System.name]
        return try {
            IntUiThemes.valueOf(storedValue)
        } catch (_: IllegalArgumentException) {
            IntUiThemes.System
        }
    }

    fun setThemeMode(theme: IntUiThemes) {
        settings[KEY_THEME_MODE] = theme.name
    }

    // Accent color preset
    fun getAccentColor(): AccentColor {
        val storedValue: String = settings[KEY_ACCENT_COLOR, AccentColor.Gold.name]
        return try {
            AccentColor.valueOf(storedValue)
        } catch (_: IllegalArgumentException) {
            AccentColor.Gold
        }
    }

    fun setAccentColor(accent: AccentColor) {
        settings[KEY_ACCENT_COLOR] = accent.name
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
        _maxCommentatorsPerPageFlow.value = DEFAULT_MAX_COMMENTATORS_PER_PAGE
        _closeTreeOnNewBookFlow.value = false
        _databasePathFlow.value = null
        _persistSessionFlow.value = true
        _homeWidgetsLayoutFlow.value = null
        _homeWidgetOptionsFlow.value = emptyMap()
        _showHomeWallpaperFlow.value = true
        _compactModeFlow.value = false
        _bookFontCodeFlow.value = DEFAULT_BOOK_FONT
        _commentaryFontCodeFlow.value = DEFAULT_COMMENTARY_FONT
        _targumFontCodeFlow.value = DEFAULT_TARGUM_FONT
        _sourceFontCodeFlow.value = DEFAULT_SOURCE_FONT
    }

    /** The preferences that follow the user to another machine (see [PORTABLE_KEYS]). */
    fun exportPortablePreferences(): Map<String, String> = exportPortablePreferences(settings)

    companion object {
        // Preferences worth carrying to another machine. The database location, the session and
        // the onboarding state belong to this machine only.
        private val PORTABLE_KEYS =
            setOf(
                KEY_TEXT_SIZE,
                KEY_LINE_HEIGHT,
                KEY_MAX_COMMENTATORS_PER_PAGE,
                KEY_CLOSE_TREE_ON_NEW_BOOK,
                KEY_PERSIST_SESSION,
                KEY_KEEP_SCREEN_AWAKE_ON_BOOK,
                KEY_FONT_BOOK,
                KEY_FONT_COMMENTARY,
                KEY_FONT_TARGUM,
                KEY_FONT_SOURCE,
                KEY_REGION_COUNTRY,
                KEY_REGION_CITY,
                KEY_USER_FIRST_NAME,
                KEY_USER_LAST_NAME,
                KEY_SIDDUR_NUSACH,
                KEY_SIDDUR_MINYAN,
                KEY_SIDDUR_BEIT_AVEL,
                KEY_USER_COMMUNITY,
                KEY_THEME_MODE,
                KEY_ACCENT_COLOR,
                KEY_HOME_WIDGETS_LAYOUT,
                KEY_SHOW_HOME_WALLPAPER,
                KEY_COMPACT_MODE,
            )

        private fun isPortableKey(key: String): Boolean = key in PORTABLE_KEYS || key.startsWith(KEY_HOME_WIDGET_OPTIONS_PREFIX)

        /**
         * The portable preferences of [settings] as their stored strings: Java Preferences keeps
         * every value as a string, so they round-trip whatever their type.
         */
        fun exportPortablePreferences(settings: Settings): Map<String, String> =
            settings.keys.filter(::isPortableKey).associateWith { settings.getString(it, "") }

        /** Replaces the portable preferences of [settings] with [preferences], leaving the others alone. */
        fun importPortablePreferences(
            settings: Settings,
            preferences: Map<String, String>,
        ) {
            settings.keys.filter(::isPortableKey).forEach(settings::remove)
            preferences.filterKeys(::isPortableKey).forEach { (key, value) -> settings.putString(key, value) }
        }

        // Text size constants
        const val DEFAULT_TEXT_SIZE = 16f
        const val MIN_TEXT_SIZE = 14f
        const val MAX_TEXT_SIZE = 50f
        const val TEXT_SIZE_INCREMENT = 2f

        // Line height constants
        const val DEFAULT_LINE_HEIGHT = 1.5f
        const val MIN_LINE_HEIGHT = 1.0f
        const val MAX_LINE_HEIGHT = 2.5f
        const val LINE_HEIGHT_INCREMENT = 0.1f

        // Max commentators displayed per commentaries page.
        // 0 = automatic (fit as many as the available space allows). A positive value acts as a
        // ceiling: the grid never shows more than this per page, but still shows fewer when the
        // pane only has room for fewer.
        const val MAX_COMMENTATORS_PER_PAGE_AUTO = 0
        const val MAX_COMMENTATORS_PER_PAGE_LIMIT = 6
        const val DEFAULT_MAX_COMMENTATORS_PER_PAGE = MAX_COMMENTATORS_PER_PAGE_AUTO

        // Default font codes
        const val DEFAULT_BOOK_FONT = "notoserifhebrew"
        const val DEFAULT_COMMENTARY_FONT = "frankruhllibre"
        const val DEFAULT_TARGUM_FONT = "taameyashkenaz"
        const val DEFAULT_SOURCE_FONT = "tinos"

        // Tab display constants
        const val MAX_TAB_TITLE_LENGTH = 20

        // Preferred max width for tabs in dp units (UI caps to this, shrinks below as needed)
        const val TAB_FIXED_WIDTH_DP = 180

        // Settings keys
        // Longest selection pre-filling a new find session, as Chromium's FindBarController
        private const val MAX_FIND_PREFILL_LENGTH = 250
        private val WHITESPACE = Regex("\\s+")

        private const val KEY_TEXT_SIZE = "text_size"
        private const val KEY_LINE_HEIGHT = "line_height"
        private const val KEY_MAX_COMMENTATORS_PER_PAGE = "max_commentators_per_page"
        private const val KEY_CLOSE_TREE_ON_NEW_BOOK = "close_tree_on_new_book"
        private const val KEY_DATABASE_PATH = "database_path"
        private const val KEY_PERSIST_SESSION = "persist_session"
        private const val KEY_KEEP_SCREEN_AWAKE_ON_BOOK = "keep_screen_awake_on_book"
        private const val KEY_FONT_BOOK = "font_book"
        private const val KEY_FONT_COMMENTARY = "font_commentary"
        private const val KEY_FONT_TARGUM = "font_targum"
        private const val KEY_FONT_SOURCE = "font_source"
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
        private const val KEY_SIDDUR_NUSACH = "siddur_nusach"
        private const val KEY_SIDDUR_MINYAN = "siddur_minyan"
        private const val KEY_SIDDUR_BEIT_AVEL = "siddur_beit_avel"
        private const val KEY_USER_COMMUNITY = "user_community" // stores a stable code (e.g., "SEPHARADE")

        // Theme configuration
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ACCENT_COLOR = "accent_color"

        // Zmanim widgets visibility

        // Home widgets the user placed, in order ("id:SIZE,…"); unset means the default layout
        private const val KEY_HOME_WIDGETS_LAYOUT = "home_widgets_layout"

        // A Home widget's own options: this, then its id
        private const val KEY_HOME_WIDGET_OPTIONS_PREFIX = "home_widget_options_"

        // Homepage wallpaper visibility
        private const val KEY_SHOW_HOME_WALLPAPER = "show_home_wallpaper"

        // Compact mode for vertical bars
        private const val KEY_COMPACT_MODE = "compact_mode"
        private const val KEY_SEARCH_BOOK_DETAILS = "search_book_details"
    }
}
