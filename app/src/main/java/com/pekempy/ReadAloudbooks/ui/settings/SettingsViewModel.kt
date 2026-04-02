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
    var readerHidePlayerWithControls by mutableStateOf(true)
    
    var showBooksTab by mutableStateOf(true)
    var showAuthorsTab by mutableStateOf(true)
    var showSeriesTab by mutableStateOf(true)
    var showCollectionsTab by mutableStateOf(true)
    
    var syncFrequency by mutableStateOf(0)
    var syncFrequencyBackground by mutableStateOf(0)
    var lastSyncTime by mutableStateOf(0L)
    var isSyncing by mutableStateOf(false)

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
                showBooksTab = settings.showBooksTab
                showAuthorsTab = settings.showAuthorsTab
                showSeriesTab = settings.showSeriesTab
                showCollectionsTab = settings.showCollectionsTab
                syncFrequency = settings.syncFrequency
                syncFrequencyBackground = settings.syncFrequencyBackground
                lastSyncTime = settings.lastSyncTime
                readerHidePlayerWithControls = settings.readerHidePlayerWithControls
            }
        }
        viewModelScope.launch {
            repository.userCredentials.collect { credentials ->
                serverUrl = credentials?.url ?: ""
                localServerUrl = credentials?.localUrl ?: ""
                useLocalOnWifi = credentials?.useLocalOnWifi ?: false
                wifiSsid = credentials?.wifiSsid ?: ""
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

    fun updateShowBooksTab(enabled: Boolean) {
        showBooksTab = enabled
        viewModelScope.launch { repository.updateShowBooksTab(enabled) }
    }

    fun updateShowAuthorsTab(enabled: Boolean) {
        showAuthorsTab = enabled
        viewModelScope.launch { repository.updateShowAuthorsTab(enabled) }
    }

    fun updateShowSeriesTab(enabled: Boolean) {
        showSeriesTab = enabled
        viewModelScope.launch { repository.updateShowSeriesTab(enabled) }
    }

    fun updateShowCollectionsTab(enabled: Boolean) {
        showCollectionsTab = enabled
        viewModelScope.launch { repository.updateShowCollectionsTab(enabled) }
    }

    fun updateSyncFrequency(minutes: Int) {
        syncFrequency = minutes
        viewModelScope.launch { repository.updateSyncFrequency(minutes) }
    }

    fun updateSyncFrequencyBackground(minutes: Int) {
        syncFrequencyBackground = minutes
        viewModelScope.launch { 
            repository.updateSyncFrequencyBackground(minutes)
            com.pekempy.ReadAloudbooks.data.SyncWorker.schedule(com.pekempy.ReadAloudbooks.data.api.AppContainer.context, minutes)
        }
    }

    fun updateReaderHidePlayerWithControls(enabled: Boolean) {
        readerHidePlayerWithControls = enabled
        viewModelScope.launch { repository.updateReaderHidePlayerWithControls(enabled) }
    }

    suspend fun forceSync(): Boolean {
        isSyncing = true
        return try {
            val bookRepo = com.pekempy.ReadAloudbooks.data.db.BookRepository(com.pekempy.ReadAloudbooks.data.api.AppContainer.context, repository)
            val success = bookRepo.syncWithServer(force = true)
            if (success) {
                lastSyncTime = System.currentTimeMillis()
            }
            success
        } finally {
            isSyncing = false
        }
    }
}
