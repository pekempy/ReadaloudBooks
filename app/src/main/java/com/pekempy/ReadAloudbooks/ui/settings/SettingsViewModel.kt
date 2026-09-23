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
    var useBookColors by mutableStateOf(false)
    var bookThemeColor by mutableStateOf(0)
    var readerHidePlayerWithControls by mutableStateOf(true)
    
    var showBooksTab by mutableStateOf(true)
    var showAuthorsTab by mutableStateOf(true)
    var showSeriesTab by mutableStateOf(true)
    var showCollectionsTab by mutableStateOf(true)
    
    var syncFrequency by mutableStateOf(0)
    var syncFrequencyBackground by mutableStateOf(0)
    var lastSyncTime by mutableStateOf(0L)
    var isSyncing by mutableStateOf(false)
    var tabOrder by mutableStateOf(listOf("shelf", "books", "authors", "series", "collections"))
    
    // Advanced Settings
    var syncWifiOnly by mutableStateOf(false)
    var autoSyncProgress by mutableStateOf(true)
    var backgroundSyncEnabled by mutableStateOf(true)
    var downloadQuality by mutableStateOf("high")
    var autoDownloadNewSeries by mutableStateOf(false)
    var cacheLimitMb by mutableStateOf(1000)
    var autoPlayNextChapter by mutableStateOf(false)
    var rememberPositionThreshold by mutableStateOf(30)
    var skipSilence by mutableStateOf(false)
    var autoScrollSpeed by mutableStateOf(50)
    var pageTurnAnimation by mutableStateOf(true)
    var brightnessOverride by mutableStateOf(false)
    var brightnessLevel by mutableStateOf(1.0f)
    var developerMode by mutableStateOf(false)
    var exportLogsEnabled by mutableStateOf(false)

    init {
        viewModelScope.launch {
            repository.userSettings.collect { settings ->
                themeMode = settings.themeMode
                useDynamicColors = settings.useDynamicColors
                sleepTimerMinutes = settings.sleepTimerMinutes
                themeSource = settings.themeSource
                readerFontSize = settings.readerFontSize
                useBookColors = settings.useBookColors
                bookThemeColor = settings.bookThemeColor
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
                // Advanced Settings
                syncWifiOnly = settings.syncWifiOnly
                autoSyncProgress = settings.autoSyncProgress
                backgroundSyncEnabled = settings.backgroundSyncEnabled
                downloadQuality = settings.downloadQuality
                autoDownloadNewSeries = settings.autoDownloadNewSeries
                cacheLimitMb = settings.cacheLimitMb
                autoPlayNextChapter = settings.autoPlayNextChapter
                rememberPositionThreshold = settings.rememberPositionThreshold
                skipSilence = settings.skipSilence
                autoScrollSpeed = settings.autoScrollSpeed
                pageTurnAnimation = settings.pageTurnAnimation
                brightnessOverride = settings.brightnessOverride
                brightnessLevel = settings.brightnessLevel
                developerMode = settings.developerMode
                exportLogsEnabled = settings.exportLogsEnabled
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
        viewModelScope.launch {
            repository.getTabOrder().collect { order ->
                tabOrder = order
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

    fun updateTabOrder(order: List<String>) {
        tabOrder = order
        viewModelScope.launch { 
            repository.updateTabOrder(order)
        }
    }

    fun toggleTab(tabId: String) {
        when (tabId) {
            "books" -> updateShowBooksTab(!showBooksTab)
            "authors" -> updateShowAuthorsTab(!showAuthorsTab)
            "series" -> updateShowSeriesTab(!showSeriesTab)
            "collections" -> updateShowCollectionsTab(!showCollectionsTab)
        }
    }

    fun updateUseBookColors(enabled: Boolean) {
        useBookColors = enabled
        viewModelScope.launch { repository.setUseBookColors(enabled) }
    }

    // Advanced Settings Update Methods
    fun updateSyncWifiOnly(enabled: Boolean) {
        syncWifiOnly = enabled
        viewModelScope.launch { repository.updateSyncWifiOnly(enabled) }
    }

    fun updateAutoSyncProgress(enabled: Boolean) {
        autoSyncProgress = enabled
        viewModelScope.launch { repository.updateAutoSyncProgress(enabled) }
    }

    fun updateBackgroundSyncEnabled(enabled: Boolean) {
        backgroundSyncEnabled = enabled
        viewModelScope.launch { repository.updateBackgroundSyncEnabled(enabled) }
    }

    fun updateDownloadQuality(quality: String) {
        downloadQuality = quality
        viewModelScope.launch { repository.updateDownloadQuality(quality) }
    }

    fun updateAutoDownloadNewSeries(enabled: Boolean) {
        autoDownloadNewSeries = enabled
        viewModelScope.launch { repository.updateAutoDownloadNewSeries(enabled) }
    }

    fun updateCacheLimit(limitMb: Int) {
        cacheLimitMb = limitMb
        viewModelScope.launch { repository.updateCacheLimit(limitMb) }
    }

    fun updateAutoPlayNextChapter(enabled: Boolean) {
        autoPlayNextChapter = enabled
        viewModelScope.launch { repository.updateAutoPlayNextChapter(enabled) }
    }

    fun updateRememberPositionThreshold(seconds: Int) {
        rememberPositionThreshold = seconds
        viewModelScope.launch { repository.updateRememberPositionThreshold(seconds) }
    }

    fun updateSkipSilence(enabled: Boolean) {
        skipSilence = enabled
        viewModelScope.launch { repository.updateSkipSilence(enabled) }
    }

    fun updateAutoScrollSpeed(speed: Int) {
        autoScrollSpeed = speed
        viewModelScope.launch { repository.updateAutoScrollSpeed(speed) }
    }

    fun updatePageTurnAnimation(enabled: Boolean) {
        pageTurnAnimation = enabled
        viewModelScope.launch { repository.updatePageTurnAnimation(enabled) }
    }

    fun updateBrightnessOverride(enabled: Boolean) {
        brightnessOverride = enabled
        viewModelScope.launch { repository.updateBrightnessOverride(enabled) }
    }

    fun updateBrightnessLevel(level: Float) {
        brightnessLevel = level
        viewModelScope.launch { repository.updateBrightnessLevel(level) }
    }

    fun updateDeveloperMode(enabled: Boolean) {
        developerMode = enabled
        viewModelScope.launch { repository.updateDeveloperMode(enabled) }
    }

    fun updateExportLogsEnabled(enabled: Boolean) {
        exportLogsEnabled = enabled
        viewModelScope.launch { repository.updateExportLogsEnabled(enabled) }
    }

    fun clearAllCaches() {
        viewModelScope.launch { repository.clearAllCaches() }
    }

    fun resetToDefaults() {
        viewModelScope.launch { repository.resetToDefaults() }
    }
}
