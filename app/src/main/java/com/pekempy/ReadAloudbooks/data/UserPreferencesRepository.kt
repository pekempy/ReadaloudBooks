package com.pekempy.ReadAloudbooks.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

data class UserCredentials(
    val url: String,
    val localUrl: String,
    val username: String,
    val token: String? = null,
    val useLocalOnWifi: Boolean = false,
    val wifiSsid: String = ""
)

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val URL = stringPreferencesKey("instance_url")
        val LOCAL_URL = stringPreferencesKey("local_instance_url")
        val USE_LOCAL_ON_WIFI = booleanPreferencesKey("use_local_on_wifi")
        val WIFI_SSID = stringPreferencesKey("wifi_ssid")
        
        val USERNAME = stringPreferencesKey("username")
        val TOKEN = stringPreferencesKey("auth_token")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        
        val THEME_MODE = intPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SLEEP_TIMER = intPreferencesKey("sleep_timer")
        val THEME_SOURCE = intPreferencesKey("theme_source")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val SLEEP_TIMER_FINISH_CHAPTER = booleanPreferencesKey("sleep_timer_finish_chapter")

        val READER_FONT_SIZE = floatPreferencesKey("reader_font_size")
        val READER_THEME = intPreferencesKey("reader_theme")
        val READER_FONT_FAMILY = stringPreferencesKey("reader_font_family")

        val LAST_ACTIVE_BOOK_ID = stringPreferencesKey("last_active_book_id")
        val LAST_ACTIVE_BOOK_TYPE = stringPreferencesKey("last_active_book_type")

        // New reader preferences
        val READER_BRIGHTNESS = floatPreferencesKey("reader_brightness")
        val READER_LINE_SPACING = floatPreferencesKey("reader_line_spacing")
        val READER_MARGIN_SIZE = intPreferencesKey("reader_margin_size")
        val READER_FULLSCREEN_MODE = booleanPreferencesKey("reader_fullscreen_mode")
        val READER_TEXT_ALIGNMENT = stringPreferencesKey("reader_text_alignment")

        // Advanced playback settings
        val SKIP_BACK_SECONDS = intPreferencesKey("skip_back_seconds")
        val SKIP_FORWARD_SECONDS = intPreferencesKey("skip_forward_seconds")
        val ENABLE_VOLUME_BOOST = booleanPreferencesKey("enable_volume_boost")
        val VOLUME_BOOST_LEVEL = floatPreferencesKey("volume_boost_level")

        // Library view preferences
        val LIBRARY_VIEW_MODE = stringPreferencesKey("library_view_mode")
        val LIBRARY_GRID_COLUMNS = intPreferencesKey("library_grid_columns")

        // Inner screen (unfolded foldable) reader preferences - separate from outer screen
        val INNER_READER_FONT_SIZE = floatPreferencesKey("inner_reader_font_size")
        val INNER_READER_THEME = intPreferencesKey("inner_reader_theme")
        val INNER_READER_FONT_FAMILY = stringPreferencesKey("inner_reader_font_family")
        val INNER_READER_LINE_SPACING = floatPreferencesKey("inner_reader_line_spacing")
        val INNER_READER_MARGIN_SIZE = intPreferencesKey("inner_reader_margin_size")
        val INNER_READER_TEXT_ALIGNMENT = stringPreferencesKey("inner_reader_text_alignment")
        val INNER_READER_BRIGHTNESS = floatPreferencesKey("inner_reader_brightness")
        val INNER_PAGE_GAP = intPreferencesKey("inner_page_gap")
        val INNER_SHOW_PAGE_DIVIDER = booleanPreferencesKey("inner_show_page_divider")
    }

    val userCredentials: Flow<UserCredentials?> = context.dataStore.data.map { preferences ->
        val url = preferences[URL]
        val localUrl = preferences[LOCAL_URL]
        
        val username = preferences[USERNAME]
        if ((url != null || localUrl != null) && username != null) {
            UserCredentials(
                url = url ?: "",
                localUrl = localUrl ?: "",
                username = username,
                token = preferences[TOKEN],
                useLocalOnWifi = preferences[USE_LOCAL_ON_WIFI] ?: false,
                wifiSsid = preferences[WIFI_SSID] ?: ""
            )
        } else {
            null
        }
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_LOGGED_IN] ?: false
    }

    val userSettings: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            themeMode = preferences[THEME_MODE] ?: 0,
            useDynamicColors = preferences[DYNAMIC_COLOR] ?: true,
            sleepTimerMinutes = preferences[SLEEP_TIMER] ?: 0,
            themeSource = preferences[THEME_SOURCE] ?: 0,
            readerFontSize = preferences[READER_FONT_SIZE] ?: 18f,
            readerTheme = preferences[READER_THEME] ?: 0,
            readerFontFamily = preferences[READER_FONT_FAMILY] ?: "serif",
            playbackSpeed = preferences[PLAYBACK_SPEED] ?: 1.0f,
            sleepTimerFinishChapter = preferences[SLEEP_TIMER_FINISH_CHAPTER] ?: false,
            readerBrightness = preferences[READER_BRIGHTNESS] ?: 1.0f,
            readerLineSpacing = preferences[READER_LINE_SPACING] ?: 1.6f,
            readerMarginSize = preferences[READER_MARGIN_SIZE] ?: 1,
            readerFullscreenMode = preferences[READER_FULLSCREEN_MODE] ?: false,
            readerTextAlignment = preferences[READER_TEXT_ALIGNMENT] ?: "justify",
            skipBackSeconds = preferences[SKIP_BACK_SECONDS] ?: 10,
            skipForwardSeconds = preferences[SKIP_FORWARD_SECONDS] ?: 30,
            enableVolumeBoost = preferences[ENABLE_VOLUME_BOOST] ?: false,
            volumeBoostLevel = preferences[VOLUME_BOOST_LEVEL] ?: 1.0f,
            libraryViewMode = preferences[LIBRARY_VIEW_MODE] ?: "grid",
            libraryGridColumns = preferences[LIBRARY_GRID_COLUMNS] ?: 2
        )
    }

    suspend fun saveCredentials(
        url: String, 
        localUrl: String,
        username: String, 
        token: String?,
        useLocalOnWifi: Boolean,
        wifiSsid: String
    ) {
        context.dataStore.edit { preferences ->
            if (url.isNotEmpty()) preferences[URL] = url else preferences.remove(URL)
            if (localUrl.isNotEmpty()) preferences[LOCAL_URL] = localUrl else preferences.remove(LOCAL_URL)
            
            preferences[USERNAME] = username
            if (token != null) preferences[TOKEN] = token
            
            preferences[USE_LOCAL_ON_WIFI] = useLocalOnWifi
            if (wifiSsid.isNotEmpty()) preferences[WIFI_SSID] = wifiSsid else preferences.remove(WIFI_SSID)
            
            preferences[IS_LOGGED_IN] = true
        }
    }

    suspend fun updateConnectionSettings(
        url: String,
        localUrl: String,
        useLocalOnWifi: Boolean,
        wifiSsid: String
    ) {
        context.dataStore.edit { preferences ->
            if (url.isNotEmpty()) preferences[URL] = url else preferences.remove(URL)
            if (localUrl.isNotEmpty()) preferences[LOCAL_URL] = localUrl else preferences.remove(LOCAL_URL)
            preferences[USE_LOCAL_ON_WIFI] = useLocalOnWifi
            if (wifiSsid.isNotEmpty()) preferences[WIFI_SSID] = wifiSsid else preferences.remove(WIFI_SSID)
        }
    }

    suspend fun updateThemeMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun updateDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun updateSleepTimer(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[SLEEP_TIMER] = minutes
        }
    }

    suspend fun updateThemeSource(source: Int) {
        context.dataStore.edit { preferences ->
            preferences[THEME_SOURCE] = source
        }
    }

    suspend fun updatePlaybackSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[PLAYBACK_SPEED] = speed
        }
    }
    
    suspend fun updateSleepTimerFinishChapter(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SLEEP_TIMER_FINISH_CHAPTER] = enabled
        }
    }

    suspend fun updateReaderFontSize(size: Float) {
        context.dataStore.edit { preferences ->
            preferences[READER_FONT_SIZE] = size
        }
    }

    suspend fun updateReaderTheme(theme: Int) {
        context.dataStore.edit { preferences ->
            preferences[READER_THEME] = theme
        }
    }

    suspend fun updateReaderFontFamily(family: String) {
        context.dataStore.edit { preferences ->
            preferences[READER_FONT_FAMILY] = family
        }
    }

    suspend fun saveBookProgress(bookId: String, progress: String) {
        context.dataStore.edit { preferences ->
            preferences[stringPreferencesKey("progress_$bookId")] = progress
        }
    }

    suspend fun deleteBookProgress(bookId: String) {
        context.dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey("progress_$bookId"))
        }
    }

    fun getBookProgress(bookId: String): Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[stringPreferencesKey("progress_$bookId")]
    }

    val allBookProgress: Flow<Map<String, String>> = context.dataStore.data.map { preferences ->
        preferences.asMap().entries
            .filter { it.key.name.startsWith("progress_") }
            .associate { it.key.name.removePrefix("progress_") to (it.value as String) }
    }

    suspend fun saveBookReaderSettings(bookId: String, fontSize: Float?, theme: Int?, fontFamily: String?) {
        context.dataStore.edit { preferences ->
            fontSize?.let { preferences[floatPreferencesKey("reader_font_size_$bookId")] = it }
            theme?.let { preferences[intPreferencesKey("reader_theme_$bookId")] = it }
            fontFamily?.let { preferences[stringPreferencesKey("reader_font_family_$bookId")] = it }
        }
    }

    fun getBookReaderSettings(bookId: String): Flow<Triple<Float?, Int?, String?>> = context.dataStore.data.map { preferences ->
        Triple(
            preferences[floatPreferencesKey("reader_font_size_$bookId")],
            preferences[intPreferencesKey("reader_theme_$bookId")],
            preferences[stringPreferencesKey("reader_font_family_$bookId")]
        )
    }

    suspend fun clearCredentials() {
        context.dataStore.edit { preferences ->
            preferences.remove(URL)
            preferences.remove(USERNAME)
            preferences.remove(TOKEN)
            preferences.remove(IS_LOGGED_IN)
        }
    }

    suspend fun saveBookPlaybackSpeed(bookId: String, speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[floatPreferencesKey("playback_speed_$bookId")] = speed
        }
    }

    fun getBookPlaybackSpeed(bookId: String): Flow<Float?> = context.dataStore.data.map { preferences ->
        preferences[floatPreferencesKey("playback_speed_$bookId")]
    }

    suspend fun saveLastActiveBook(bookId: String, type: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_ACTIVE_BOOK_ID] = bookId
            preferences[LAST_ACTIVE_BOOK_TYPE] = type
        }
    }

    val lastActiveBook: Flow<Pair<String?, String?>> = context.dataStore.data.map { preferences ->
        preferences[LAST_ACTIVE_BOOK_ID] to preferences[LAST_ACTIVE_BOOK_TYPE]
    }

    // New reader preference methods
    suspend fun updateReaderBrightness(brightness: Float) {
        context.dataStore.edit { preferences ->
            preferences[READER_BRIGHTNESS] = brightness
        }
    }

    suspend fun updateReaderLineSpacing(lineSpacing: Float) {
        context.dataStore.edit { preferences ->
            preferences[READER_LINE_SPACING] = lineSpacing
        }
    }

    suspend fun updateReaderMarginSize(marginSize: Int) {
        context.dataStore.edit { preferences ->
            preferences[READER_MARGIN_SIZE] = marginSize
        }
    }

    suspend fun updateReaderFullscreenMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[READER_FULLSCREEN_MODE] = enabled
        }
    }

    suspend fun updateReaderTextAlignment(alignment: String) {
        context.dataStore.edit { preferences ->
            preferences[READER_TEXT_ALIGNMENT] = alignment
        }
    }

    suspend fun updateSkipBackSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[SKIP_BACK_SECONDS] = seconds
        }
    }

    suspend fun updateSkipForwardSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[SKIP_FORWARD_SECONDS] = seconds
        }
    }

    suspend fun updateVolumeBoost(enabled: Boolean, level: Float) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_VOLUME_BOOST] = enabled
            preferences[VOLUME_BOOST_LEVEL] = level
        }
    }

    suspend fun updateLibraryViewMode(viewMode: String) {
        context.dataStore.edit { preferences ->
            preferences[LIBRARY_VIEW_MODE] = viewMode
        }
    }

    suspend fun updateLibraryGridColumns(columns: Int) {
        context.dataStore.edit { preferences ->
            preferences[LIBRARY_GRID_COLUMNS] = columns
        }
    }

    // Inner screen (foldable) settings
    val innerScreenSettings: Flow<InnerScreenSettings> = context.dataStore.data.map { preferences ->
        InnerScreenSettings(
            readerFontSize = preferences[INNER_READER_FONT_SIZE] ?: 16f,
            readerTheme = preferences[INNER_READER_THEME] ?: 0,
            readerFontFamily = preferences[INNER_READER_FONT_FAMILY] ?: "serif",
            readerLineSpacing = preferences[INNER_READER_LINE_SPACING] ?: 1.5f,
            readerMarginSize = preferences[INNER_READER_MARGIN_SIZE] ?: 0,
            readerTextAlignment = preferences[INNER_READER_TEXT_ALIGNMENT] ?: "justify",
            readerBrightness = preferences[INNER_READER_BRIGHTNESS] ?: 1.0f,
            pageGap = preferences[INNER_PAGE_GAP] ?: 16,
            showPageDivider = preferences[INNER_SHOW_PAGE_DIVIDER] ?: true
        )
    }

    suspend fun updateInnerReaderFontSize(size: Float) {
        context.dataStore.edit { preferences ->
            preferences[INNER_READER_FONT_SIZE] = size
        }
    }

    suspend fun updateInnerReaderTheme(theme: Int) {
        context.dataStore.edit { preferences ->
            preferences[INNER_READER_THEME] = theme
        }
    }

    suspend fun updateInnerReaderFontFamily(family: String) {
        context.dataStore.edit { preferences ->
            preferences[INNER_READER_FONT_FAMILY] = family
        }
    }

    suspend fun updateInnerReaderLineSpacing(lineSpacing: Float) {
        context.dataStore.edit { preferences ->
            preferences[INNER_READER_LINE_SPACING] = lineSpacing
        }
    }

    suspend fun updateInnerReaderMarginSize(marginSize: Int) {
        context.dataStore.edit { preferences ->
            preferences[INNER_READER_MARGIN_SIZE] = marginSize
        }
    }

    suspend fun updateInnerReaderTextAlignment(alignment: String) {
        context.dataStore.edit { preferences ->
            preferences[INNER_READER_TEXT_ALIGNMENT] = alignment
        }
    }

    suspend fun updateInnerReaderBrightness(brightness: Float) {
        context.dataStore.edit { preferences ->
            preferences[INNER_READER_BRIGHTNESS] = brightness
        }
    }

    suspend fun updateInnerPageGap(gap: Int) {
        context.dataStore.edit { preferences ->
            preferences[INNER_PAGE_GAP] = gap
        }
    }

    suspend fun updateInnerShowPageDivider(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[INNER_SHOW_PAGE_DIVIDER] = show
        }
    }
}

data class UserSettings(
    val themeMode: Int,
    val useDynamicColors: Boolean,
    val sleepTimerMinutes: Int,
    val themeSource: Int,
    val readerFontSize: Float,
    val readerTheme: Int,
    val readerFontFamily: String,
    val playbackSpeed: Float,
    val sleepTimerFinishChapter: Boolean,
    // New reader preferences
    val readerBrightness: Float = 1.0f, // 0.0 to 1.0
    val readerLineSpacing: Float = 1.6f, // 1.0 to 2.5
    val readerMarginSize: Int = 1, // 0=compact, 1=normal, 2=wide
    val readerFullscreenMode: Boolean = false,
    val readerTextAlignment: String = "justify", // left, justify, center
    // Advanced playback settings
    val skipBackSeconds: Int = 10,
    val skipForwardSeconds: Int = 30,
    val enableVolumeBoost: Boolean = false,
    val volumeBoostLevel: Float = 1.0f,
    // Library view preferences
    val libraryViewMode: String = "grid", // grid, list, compact, table
    val libraryGridColumns: Int = 2
)

/**
 * Settings specific to the inner (unfolded) screen on foldable devices.
 * These are separate from the outer screen settings to allow different
 * configurations for two-page vs single-page reading modes.
 */
data class InnerScreenSettings(
    val readerFontSize: Float = 16f,  // Slightly smaller default for two-page
    val readerTheme: Int = 0,
    val readerFontFamily: String = "serif",
    val readerLineSpacing: Float = 1.5f,
    val readerMarginSize: Int = 0,  // Compact margins for two-page
    val readerTextAlignment: String = "justify",
    val readerBrightness: Float = 1.0f,
    val pageGap: Int = 16,  // Gap between two pages in dp
    val showPageDivider: Boolean = true
)
