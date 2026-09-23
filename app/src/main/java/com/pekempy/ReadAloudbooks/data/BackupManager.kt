package com.pekempy.ReadAloudbooks.data

import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    suspend fun exportSettings(repository: UserPreferencesRepository): String {
        val settings = repository.userSettings.first()
        
        val json = JSONObject()
        json.put("version", 1)
        json.put("exportedAt", System.currentTimeMillis())
        json.put("description", "ReadAloudBooks Settings Backup")
        
        val settingsObj = JSONObject()
        settingsObj.put("themeMode", settings.themeMode)
        settingsObj.put("useDynamicColors", settings.useDynamicColors)
        settingsObj.put("sleepTimerMinutes", settings.sleepTimerMinutes)
        settingsObj.put("themeSource", settings.themeSource)
        settingsObj.put("readerFontSize", settings.readerFontSize)
        settingsObj.put("readerTheme", settings.readerTheme)
        settingsObj.put("readerFontFamily", settings.readerFontFamily)
        settingsObj.put("playbackSpeed", settings.playbackSpeed)
        settingsObj.put("sleepTimerFinishChapter", settings.sleepTimerFinishChapter)
        settingsObj.put("showBooksTab", settings.showBooksTab)
        settingsObj.put("showAuthorsTab", settings.showAuthorsTab)
        settingsObj.put("showSeriesTab", settings.showSeriesTab)
        settingsObj.put("showCollectionsTab", settings.showCollectionsTab)
        settingsObj.put("syncFrequency", settings.syncFrequency)
        settingsObj.put("syncFrequencyBackground", settings.syncFrequencyBackground)
        settingsObj.put("lastSyncTime", settings.lastSyncTime)
        settingsObj.put("readerHidePlayerWithControls", settings.readerHidePlayerWithControls)
        settingsObj.put("ignoredSeries", settings.ignoredSeries.joinToString(","))
        
        json.put("settings", settingsObj)
        json.put("tabOrder", org.json.JSONArray(listOf("books", "authors", "series", "collections")))
        
        return json.toString(2)
    }

    suspend fun importSettings(json: String, repository: UserPreferencesRepository): Result<Unit> {
        return try {
            val jsonObj = JSONObject(json)
            val settingsObj = jsonObj.getJSONObject("settings")
            
            // Apply each setting to the repository
            if (settingsObj.has("themeMode")) {
                repository.updateThemeMode(settingsObj.getInt("themeMode"))
            }
            if (settingsObj.has("useDynamicColors")) {
                repository.updateDynamicColor(settingsObj.getBoolean("useDynamicColors"))
            }
            if (settingsObj.has("sleepTimerMinutes")) {
                repository.updateSleepTimer(settingsObj.getInt("sleepTimerMinutes"))
            }
            if (settingsObj.has("themeSource")) {
                repository.updateThemeSource(settingsObj.getInt("themeSource"))
            }
            if (settingsObj.has("readerFontSize")) {
                repository.updateReaderFontSize(settingsObj.getDouble("readerFontSize").toFloat())
            }
            if (settingsObj.has("readerTheme")) {
                repository.updateReaderTheme(settingsObj.getInt("readerTheme"))
            }
            if (settingsObj.has("readerFontFamily")) {
                repository.updateReaderFontFamily(settingsObj.getString("readerFontFamily"))
            }
            if (settingsObj.has("playbackSpeed")) {
                repository.updatePlaybackSpeed(settingsObj.getDouble("playbackSpeed").toFloat())
            }
            if (settingsObj.has("sleepTimerFinishChapter")) {
                repository.updateSleepTimerFinishChapter(settingsObj.getBoolean("sleepTimerFinishChapter"))
            }
            if (settingsObj.has("showBooksTab")) {
                repository.updateShowBooksTab(settingsObj.getBoolean("showBooksTab"))
            }
            if (settingsObj.has("showAuthorsTab")) {
                repository.updateShowAuthorsTab(settingsObj.getBoolean("showAuthorsTab"))
            }
            if (settingsObj.has("showSeriesTab")) {
                repository.updateShowSeriesTab(settingsObj.getBoolean("showSeriesTab"))
            }
            if (settingsObj.has("showCollectionsTab")) {
                repository.updateShowCollectionsTab(settingsObj.getBoolean("showCollectionsTab"))
            }
            if (settingsObj.has("syncFrequency")) {
                repository.updateSyncFrequency(settingsObj.getInt("syncFrequency"))
            }
            if (settingsObj.has("syncFrequencyBackground")) {
                repository.updateSyncFrequencyBackground(settingsObj.getInt("syncFrequencyBackground"))
            }
            if (settingsObj.has("readerHidePlayerWithControls")) {
                repository.updateReaderHidePlayerWithControls(settingsObj.getBoolean("readerHidePlayerWithControls"))
            }
            if (settingsObj.has("ignoredSeries")) {
                val seriesStr = settingsObj.getString("ignoredSeries")
                if (seriesStr.isNotEmpty()) {
                    repository.updateIgnoredSeries(seriesStr.split(",").toSet())
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
