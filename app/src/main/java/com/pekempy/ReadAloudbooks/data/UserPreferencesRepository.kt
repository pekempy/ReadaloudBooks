package com.pekempy.ReadAloudbooks.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

data class UserCredentials(
    val url: String,
    val localUrl: String,
    val username: String,
    val password: String? = null,
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
        val PASSWORD = stringPreferencesKey("password")
        val TOKEN = stringPreferencesKey("auth_token")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        
        val THEME_MODE = intPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SLEEP_TIMER = intPreferencesKey("sleep_timer")
        val THEME_SOURCE = intPreferencesKey("theme_source")
        val BOOK_THEME_COLOR = intPreferencesKey("book_theme_color")
        val USE_BOOK_COLORS = booleanPreferencesKey("use_book_colors")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val SLEEP_TIMER_FINISH_CHAPTER = booleanPreferencesKey("sleep_timer_finish_chapter")

        val READER_FONT_SIZE = floatPreferencesKey("reader_font_size")
        val READER_THEME = intPreferencesKey("reader_theme")
        val READER_FONT_FAMILY = stringPreferencesKey("reader_font_family")

        val LAST_ACTIVE_BOOK_ID = stringPreferencesKey("last_active_book_id")
        val LAST_ACTIVE_BOOK_TYPE = stringPreferencesKey("last_active_book_type")

        val SHOW_BOOKS_TAB = booleanPreferencesKey("show_books_tab")
        val SHOW_AUTHORS_TAB = booleanPreferencesKey("show_authors_tab")
        val SHOW_SERIES_TAB = booleanPreferencesKey("show_series_tab")
        val SHOW_COLLECTIONS_TAB = booleanPreferencesKey("show_collections_tab")
        val TAB_ORDER = stringPreferencesKey("tab_order")
        val SYNC_FREQUENCY = intPreferencesKey("sync_frequency") // in minutes, 0 = manual
        val SYNC_FREQUENCY_BACKGROUND = intPreferencesKey("sync_frequency_background") // in minutes, 0 = manual
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        val IGNORED_SERIES = stringSetPreferencesKey("ignored_series")
        
        val READER_HIDE_PLAYER_WITH_CONTROLS = booleanPreferencesKey("reader_hide_player_with_controls")
        // Sync Settings
        val SYNC_WIFI_ONLY = booleanPreferencesKey("sync_wifi_only")
        val AUTO_SYNC_PROGRESS = booleanPreferencesKey("auto_sync_progress")
        val BACKGROUND_SYNC_ENABLED = booleanPreferencesKey("background_sync_enabled")
        
        // Download Settings
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality") // "high", "medium", "low"
        val AUTO_DOWNLOAD_NEW_SERIES = booleanPreferencesKey("auto_download_new_series")
        val CACHE_LIMIT = intPreferencesKey("cache_limit_mb") // in MB
        
        // Playback Settings
        val AUTO_PLAY_NEXT_CHAPTER = booleanPreferencesKey("auto_play_next_chapter")
        val REMEMBER_POSITION_THRESHOLD = intPreferencesKey("remember_position_threshold") // in seconds
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        
        // Reader Settings
        val AUTO_SCROLL_SPEED = intPreferencesKey("auto_scroll_speed") // in pixels per second
        val PAGE_TURN_ANIMATION = booleanPreferencesKey("page_turn_animation")
        val BRIGHTNESS_OVERRIDE = booleanPreferencesKey("brightness_override")
        val BRIGHTNESS_LEVEL = floatPreferencesKey("brightness_level") // 0.0 to 1.0
        
        // Advanced Options
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val EXPORT_LOGS_ENABLED = booleanPreferencesKey("export_logs_enabled")
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
                password = preferences[PASSWORD],
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
            bookThemeColor = preferences[BOOK_THEME_COLOR] ?: 0,
            useBookColors = preferences[USE_BOOK_COLORS] ?: false,
            readerFontSize = preferences[READER_FONT_SIZE] ?: 18f,
            readerTheme = preferences[READER_THEME] ?: 0,
            readerFontFamily = preferences[READER_FONT_FAMILY] ?: "serif",
            playbackSpeed = preferences[PLAYBACK_SPEED] ?: 1.0f,
            sleepTimerFinishChapter = preferences[SLEEP_TIMER_FINISH_CHAPTER] ?: false,
            showBooksTab = preferences[SHOW_BOOKS_TAB] ?: true,
            showAuthorsTab = preferences[SHOW_AUTHORS_TAB] ?: true,
            showSeriesTab = preferences[SHOW_SERIES_TAB] ?: true,
            showCollectionsTab = preferences[SHOW_COLLECTIONS_TAB] ?: true,
            syncFrequency = preferences[SYNC_FREQUENCY] ?: 0,
            syncFrequencyBackground = preferences[SYNC_FREQUENCY_BACKGROUND] ?: 0,
            lastSyncTime = preferences[LAST_SYNC_TIME] ?: 0L,
            readerHidePlayerWithControls = preferences[READER_HIDE_PLAYER_WITH_CONTROLS] ?: false,
            ignoredSeries = preferences[IGNORED_SERIES] ?: emptySet(),
            // Sync Settings
            syncWifiOnly = preferences[SYNC_WIFI_ONLY] ?: false,
            autoSyncProgress = preferences[AUTO_SYNC_PROGRESS] ?: true,
            backgroundSyncEnabled = preferences[BACKGROUND_SYNC_ENABLED] ?: true,
            // Download Settings
            downloadQuality = preferences[DOWNLOAD_QUALITY] ?: "high",
            autoDownloadNewSeries = preferences[AUTO_DOWNLOAD_NEW_SERIES] ?: false,
            cacheLimitMb = preferences[CACHE_LIMIT] ?: 1000,
            // Playback Settings
            autoPlayNextChapter = preferences[AUTO_PLAY_NEXT_CHAPTER] ?: false,
            rememberPositionThreshold = preferences[REMEMBER_POSITION_THRESHOLD] ?: 30,
            skipSilence = preferences[SKIP_SILENCE] ?: false,
            // Reader Settings
            autoScrollSpeed = preferences[AUTO_SCROLL_SPEED] ?: 50,
            pageTurnAnimation = preferences[PAGE_TURN_ANIMATION] ?: true,
            brightnessOverride = preferences[BRIGHTNESS_OVERRIDE] ?: false,
            brightnessLevel = preferences[BRIGHTNESS_LEVEL] ?: 1.0f,
            // Advanced Options
            developerMode = preferences[DEVELOPER_MODE] ?: false,
            exportLogsEnabled = preferences[EXPORT_LOGS_ENABLED] ?: false
        )
    }
    suspend fun saveCredentials(
        url: String, 
        localUrl: String,
        username: String,
        password: String? = null,
        token: String?,
        useLocalOnWifi: Boolean,
        wifiSsid: String
    ) {
        context.dataStore.edit { preferences ->
            if (url.isNotEmpty()) preferences[URL] = url else preferences.remove(URL)
            if (localUrl.isNotEmpty()) preferences[LOCAL_URL] = localUrl else preferences.remove(LOCAL_URL)
            
            preferences[USERNAME] = username
            if (password != null) preferences[PASSWORD] = password
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

    suspend fun updateBookThemeColor(color: Int) {
        context.dataStore.edit { preferences ->
            preferences[BOOK_THEME_COLOR] = color
        }
    }

    suspend fun setUseBookColors(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_BOOK_COLORS] = enabled
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
    suspend fun updateShowBooksTab(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[SHOW_BOOKS_TAB] = enabled }
    }

    suspend fun updateShowAuthorsTab(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[SHOW_AUTHORS_TAB] = enabled }
    }

    suspend fun updateShowSeriesTab(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[SHOW_SERIES_TAB] = enabled }
    }

    suspend fun updateShowCollectionsTab(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[SHOW_COLLECTIONS_TAB] = enabled }
    }
    suspend fun updateTabOrder(order: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[TAB_ORDER] = order.joinToString(",")
        }
    }

    fun getTabOrder(): Flow<List<String>> = context.dataStore.data.map { preferences ->
        val order = preferences[TAB_ORDER] ?: ""
        if (order.isEmpty()) {
            listOf("shelf", "books", "authors", "series", "collections")
        } else {
            order.split(",")
        }
    }

    suspend fun updateSyncFrequency(minutes: Int) {
        context.dataStore.edit { preferences -> preferences[SYNC_FREQUENCY] = minutes }
    }

    suspend fun updateSyncFrequencyBackground(minutes: Int) {
        context.dataStore.edit { preferences -> preferences[SYNC_FREQUENCY_BACKGROUND] = minutes }
    }

    suspend fun updateLastSyncTime(time: Long) {
        context.dataStore.edit { preferences -> preferences[LAST_SYNC_TIME] = time }
    }

    suspend fun updateReaderHidePlayerWithControls(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[READER_HIDE_PLAYER_WITH_CONTROLS] = enabled
        }
    }

    suspend fun ignoreSeries(seriesName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[IGNORED_SERIES] ?: emptySet()
            preferences[IGNORED_SERIES] = current + seriesName
        }
    }

    suspend fun unignoreSeries(seriesName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[IGNORED_SERIES] ?: emptySet()
            preferences[IGNORED_SERIES] = current - seriesName
        }
    }
    suspend fun updateIgnoredSeries(series: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[IGNORED_SERIES] = series
        }
    }

    // Sync Settings
    suspend fun updateSyncWifiOnly(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[SYNC_WIFI_ONLY] = enabled }
    }

    suspend fun updateAutoSyncProgress(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[AUTO_SYNC_PROGRESS] = enabled }
    }

    suspend fun updateBackgroundSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[BACKGROUND_SYNC_ENABLED] = enabled }
    }

    // Download Settings
    suspend fun updateDownloadQuality(quality: String) {
        context.dataStore.edit { preferences -> preferences[DOWNLOAD_QUALITY] = quality }
    }

    suspend fun updateAutoDownloadNewSeries(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[AUTO_DOWNLOAD_NEW_SERIES] = enabled }
    }

    suspend fun updateCacheLimit(limitMb: Int) {
        context.dataStore.edit { preferences -> preferences[CACHE_LIMIT] = limitMb }
    }

    // Playback Settings
    suspend fun updateAutoPlayNextChapter(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[AUTO_PLAY_NEXT_CHAPTER] = enabled }
    }

    suspend fun updateRememberPositionThreshold(seconds: Int) {
        context.dataStore.edit { preferences -> preferences[REMEMBER_POSITION_THRESHOLD] = seconds }
    }

    suspend fun updateSkipSilence(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[SKIP_SILENCE] = enabled }
    }

    // Reader Settings
    suspend fun updateAutoScrollSpeed(speed: Int) {
        context.dataStore.edit { preferences -> preferences[AUTO_SCROLL_SPEED] = speed }
    }

    suspend fun updatePageTurnAnimation(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PAGE_TURN_ANIMATION] = enabled }
    }

    suspend fun updateBrightnessOverride(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[BRIGHTNESS_OVERRIDE] = enabled }
    }

    suspend fun updateBrightnessLevel(level: Float) {
        context.dataStore.edit { preferences -> preferences[BRIGHTNESS_LEVEL] = level }
    }

    // Advanced Options
    suspend fun updateDeveloperMode(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[DEVELOPER_MODE] = enabled }
    }

    suspend fun updateExportLogsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[EXPORT_LOGS_ENABLED] = enabled }
    }

    // Action methods
    suspend fun clearAllCaches() {
        // This would call into a CacheManager or similar
        // For now, this is a placeholder that logs the action
        android.util.Log.i("UserPreferencesRepository", "Clearing all caches")
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

data class UserSettings(
    val themeMode: Int,
    val useDynamicColors: Boolean,
    val sleepTimerMinutes: Int,
    val themeSource: Int,
    val bookThemeColor: Int,
    val useBookColors: Boolean,
    val readerFontSize: Float,
    val readerTheme: Int,
    val readerFontFamily: String,
    val playbackSpeed: Float,
    val sleepTimerFinishChapter: Boolean,
    val showBooksTab: Boolean,
    val showAuthorsTab: Boolean,
    val showSeriesTab: Boolean,
    val showCollectionsTab: Boolean,
    val syncFrequency: Int,
    val syncFrequencyBackground: Int,
    val lastSyncTime: Long,
    val readerHidePlayerWithControls: Boolean,
    val ignoredSeries: Set<String> = emptySet(),
    // Sync Settings
    val syncWifiOnly: Boolean = false,
    val autoSyncProgress: Boolean = true,
    val backgroundSyncEnabled: Boolean = true,
    // Download Settings
    val downloadQuality: String = "high",
    val autoDownloadNewSeries: Boolean = false,
    val cacheLimitMb: Int = 1000,
    // Playback Settings
    val autoPlayNextChapter: Boolean = false,
    val rememberPositionThreshold: Int = 30,
    val skipSilence: Boolean = false,
    // Reader Settings
    val autoScrollSpeed: Int = 50,
    val pageTurnAnimation: Boolean = true,
    val brightnessOverride: Boolean = false,
    val brightnessLevel: Float = 1.0f,
    // Advanced Options
    val developerMode: Boolean = false,
    val exportLogsEnabled: Boolean = false
)
