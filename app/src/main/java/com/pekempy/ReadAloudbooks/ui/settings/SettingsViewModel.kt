package com.pekempy.ReadAloudbooks.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pekempy.ReadAloudbooks.data.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: UserPreferencesRepository) : ViewModel() {
    
    var themeMode by mutableStateOf(0)
    var useDynamicColors by mutableStateOf(true)
    var sleepTimerMinutes by mutableStateOf(0)
    var themeSource by mutableStateOf(0)
    var sleepTimerFinishChapter by mutableStateOf(false)
    var serverUrl by mutableStateOf("")
    var localServerUrl by mutableStateOf("")
    var useLocalOnWifi by mutableStateOf(false)
    var wifiSsid by mutableStateOf("")
    
    var readerFontSize by mutableStateOf(18f)
    var readerTheme by mutableStateOf(0)
    var readerFontFamily by mutableStateOf("serif")
    var playbackSpeed by mutableStateOf(1.0f)
    var isLocalOnly by mutableStateOf(false)
    var localLibraryPath by mutableStateOf("")

    init {
        viewModelScope.launch {
            repository.userSettings.collect { settings ->
                themeMode = settings.themeMode
                useDynamicColors = settings.useDynamicColors
                sleepTimerMinutes = settings.sleepTimerMinutes
                themeSource = settings.themeSource
                readerFontSize = settings.readerFontSize
                readerTheme = settings.readerTheme
                readerFontFamily = settings.readerFontFamily
                playbackSpeed = settings.playbackSpeed
                sleepTimerFinishChapter = settings.sleepTimerFinishChapter
            }
        }
        viewModelScope.launch {
            repository.userCredentials.collect { credentials ->
                serverUrl = credentials?.url ?: ""
                localServerUrl = credentials?.localUrl ?: ""
                useLocalOnWifi = credentials?.useLocalOnWifi ?: false
                wifiSsid = credentials?.wifiSsid ?: ""
                isLocalOnly = credentials?.isLocalOnly ?: false
                localLibraryPath = credentials?.localLibraryPath ?: ""
            }
        }
    }

    fun updateConnectionSettings(url: String, localUrl: String, useLocal: Boolean, ssid: String) {
        viewModelScope.launch {
            repository.updateConnectionSettings(url, localUrl, useLocal, ssid)
        }
    }

    fun setTheme(mode: Int) {
        themeMode = mode
        viewModelScope.launch { repository.updateThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        useDynamicColors = enabled
        viewModelScope.launch { repository.updateDynamicColor(enabled) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerMinutes = minutes
        viewModelScope.launch { repository.updateSleepTimer(minutes) }
    }

    fun updateSleepTimerFinishChapter(enabled: Boolean) {
        sleepTimerFinishChapter = enabled
        viewModelScope.launch { repository.updateSleepTimerFinishChapter(enabled) }
    }

    fun updateThemeSource(source: Int) {
        themeSource = source
        viewModelScope.launch { repository.updateThemeSource(source) }
    }

    fun updateReaderFontSize(size: Float) {
        readerFontSize = size
        viewModelScope.launch { repository.updateReaderFontSize(size) }
    }

    fun updateReaderTheme(theme: Int) {
        readerTheme = theme
        viewModelScope.launch { repository.updateReaderTheme(theme) }
    }

    fun updateReaderFontFamily(family: String) {
        readerFontFamily = family
        viewModelScope.launch { repository.updateReaderFontFamily(family) }
    }

    fun updatePlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        viewModelScope.launch { repository.updatePlaybackSpeed(speed) }
    }

    fun updateLocalLibraryPath(path: String) {
        localLibraryPath = path
        viewModelScope.launch { repository.updateLocalLibraryPath(path) }
    }
}
