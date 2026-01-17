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
    val wifiSsid: String = "",
    val isLocalOnly: Boolean = false,
    val localLibraryPath: String? = null
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
        val IS_LOCAL_ONLY = booleanPreferencesKey("is_local_only")
        val LOCAL_LIBRARY_PATH = stringPreferencesKey("local_library_path")
        
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
                wifiSsid = preferences[WIFI_SSID] ?: "",
                isLocalOnly = preferences[IS_LOCAL_ONLY] ?: false,
                localLibraryPath = preferences[LOCAL_LIBRARY_PATH]
            )
        } else if (preferences[IS_LOCAL_ONLY] == true) {
            UserCredentials(
                url = "",
                localUrl = "",
                username = "Local User",
                token = null,
                useLocalOnWifi = false,
                wifiSsid = "",
                isLocalOnly = true,
                localLibraryPath = preferences[LOCAL_LIBRARY_PATH]
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
            sleepTimerFinishChapter = preferences[SLEEP_TIMER_FINISH_CHAPTER] ?: false
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
            preferences[IS_LOCAL_ONLY] = false
        }
    }

    suspend fun setLocalOnly(path: String? = null) {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = true
            preferences[IS_LOCAL_ONLY] = true
            preferences[USERNAME] = "Local User"
            path?.let { preferences[LOCAL_LIBRARY_PATH] = it }
        }
    }

    suspend fun updateLocalLibraryPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[LOCAL_LIBRARY_PATH] = path
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
    val sleepTimerFinishChapter: Boolean
)
