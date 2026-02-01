package com.pekempy.ReadAloudbooks.ui.reader

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pekempy.ReadAloudbooks.data.UserPreferencesRepository
import com.pekempy.ReadAloudbooks.data.InnerScreenSettings
import com.pekempy.ReadAloudbooks.data.api.AppContainer
import com.pekempy.ReadAloudbooks.util.ReaderScreenMode
import com.pekempy.ReadAloudbooks.data.UnifiedProgress
import com.pekempy.ReadAloudbooks.service.DownloadService
import nl.siegmann.epublib.epub.EpubReader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileInputStream
import android.webkit.WebResourceResponse
import com.pekempy.ReadAloudbooks.data.Book
import com.pekempy.ReadAloudbooks.data.api.Position
import com.pekempy.ReadAloudbooks.data.api.Locator
import com.pekempy.ReadAloudbooks.data.api.Locations
import com.pekempy.ReadAloudbooks.util.DownloadUtils
import com.pekempy.ReadAloudbooks.data.repository.HighlightRepository
import com.pekempy.ReadAloudbooks.data.repository.BookmarkRepository
import com.pekempy.ReadAloudbooks.data.repository.ReadingStatisticsRepository
import com.pekempy.ReadAloudbooks.data.local.entities.Highlight
import com.pekempy.ReadAloudbooks.data.local.entities.Bookmark
import com.pekempy.ReadAloudbooks.data.local.entities.ReadingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow

class ReaderViewModel(
    private val repository: UserPreferencesRepository,
    private val highlightRepository: HighlightRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val readingStatisticsRepository: ReadingStatisticsRepository
) : ViewModel() {
    private var lastSyncedProgress: com.pekempy.ReadAloudbooks.data.UnifiedProgress? = null
    private var readerInitialized = false
    
    fun markReady() {
        readerInitialized = true
    }

    data class LazyBook(
        val title: String,
        val spineHrefs: List<String>,
        val resources: Map<String, String>,
        val mediaTypes: Map<String, String>,
        val spineTitles: Map<String, String> = emptyMap()
    )

    private var currentZipFile: java.util.zip.ZipFile? = null
    internal var lazyBook: LazyBook? = null
    
    var epubTitle by mutableStateOf("")
    var currentChapterIndex by mutableIntStateOf(0)
    var totalChapters by mutableIntStateOf(0)
    var lastScrollPercent by mutableFloatStateOf(0f)
    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    var settings by mutableStateOf<com.pekempy.ReadAloudbooks.data.UserSettings?>(null)
    private var currentBookId: String? = null
    
    var showControls by mutableStateOf(false)
    var isReadAloudMode by mutableStateOf(false)

    // Foldable device support
    var screenMode by mutableStateOf(ReaderScreenMode.SINGLE_PAGE)
    var innerScreenSettings by mutableStateOf<InnerScreenSettings?>(null)
    var foldBoundsPx by mutableIntStateOf(0)
    private var innerSettingsCollectionJob: Job? = null

    // Returns appropriate settings based on screen mode (inner or outer)
    val activeSettings: com.pekempy.ReadAloudbooks.data.UserSettings?
        get() = when (screenMode) {
            ReaderScreenMode.SINGLE_PAGE -> settings
            ReaderScreenMode.TWO_PAGE -> innerScreenSettings?.let { inner ->
                settings?.copy(
                    readerFontSize = inner.readerFontSize,
                    readerTheme = inner.readerTheme,
                    readerFontFamily = inner.readerFontFamily,
                    readerLineSpacing = inner.readerLineSpacing,
                    readerMarginSize = inner.readerMarginSize,
                    readerTextAlignment = inner.readerTextAlignment,
                    readerBrightness = inner.readerBrightness
                )
            } ?: settings
        }

    data class SyncSegment(
        val id: String,
        val audioSrc: String,
        val clipBegin: Double,
        val clipEnd: Double
    )
    var syncData by mutableStateOf<Map<String, List<SyncSegment>>>(emptyMap())
    var chapterOffsets by mutableStateOf<Map<String, Double>>(emptyMap())
    var currentHighlightId by mutableStateOf<String?>(null)
    var syncTrigger by mutableIntStateOf(0)
    var currentAudioPos by mutableLongStateOf(0L)
    var jumpToElementRequest = mutableStateOf<String?>(null)

    // Track the currently visible element from page navigation (for syncing audio when not playing)
    var visibleElementId by mutableStateOf<String?>(null)

    // Smart "Return to Reading Position" button
    data class SavedReadingPosition(
        val chapterIndex: Int,
        val elementId: String?,
        val audioPositionMs: Long,
        val scrollPercent: Float,
        val chapterTitle: String
    )

    var savedReadingPosition by mutableStateOf<SavedReadingPosition?>(null)
    var showReturnButton by mutableStateOf(false)

    fun saveCurrentPositionForReturn(audioPositionMs: Long) {
        val chapterTitle = lazyBook?.spineTitles?.get(
            lazyBook?.spineHrefs?.getOrNull(currentChapterIndex) ?: ""
        ) ?: "Chapter ${currentChapterIndex + 1}"

        savedReadingPosition = SavedReadingPosition(
            chapterIndex = currentChapterIndex,
            elementId = visibleElementId ?: currentHighlightId,
            audioPositionMs = audioPositionMs,
            scrollPercent = lastScrollPercent,
            chapterTitle = chapterTitle
        )
        showReturnButton = true
    }

    fun returnToSavedPosition(): SavedReadingPosition? {
        val position = savedReadingPosition
        showReturnButton = false
        savedReadingPosition = null
        return position
    }

    fun dismissReturnButton() {
        showReturnButton = false
    }

    data class SyncConfirmation(
        val newChapterIndex: Int,
        val newScrollPercent: Float,
        val newAudioMs: Long?,
        val newElementId: String?,
        val progressPercent: Float,
        val localProgressPercent: Float,
        val source: String
    )
    var syncConfirmation by mutableStateOf<SyncConfirmation?>(null)

    var pendingAnchorId = mutableStateOf<String?>(null)

    // Reading/Playback History
    enum class HistoryEventType {
        PLAY, PAUSE, NAVIGATE, HIGHLIGHT_CLICK
    }

    data class HistoryEvent(
        val id: Long = System.currentTimeMillis(),
        val type: HistoryEventType,
        val timestamp: Long = System.currentTimeMillis(),
        val chapterIndex: Int,
        val chapterTitle: String,
        val elementId: String?,
        val audioPositionMs: Long,
        val scrollPercent: Float,
        val description: String // e.g., "Paused at Chapter 3", "Navigated to highlight"
    )

    private val _historyEvents = mutableStateListOf<HistoryEvent>()
    val historyEvents: List<HistoryEvent> get() = _historyEvents.toList()

    fun addHistoryEvent(type: HistoryEventType, description: String, audioPositionMs: Long = currentAudioPos) {
        val chapterTitle = lazyBook?.spineTitles?.get(
            lazyBook?.spineHrefs?.getOrNull(currentChapterIndex) ?: ""
        ) ?: "Chapter ${currentChapterIndex + 1}"

        val event = HistoryEvent(
            type = type,
            chapterIndex = currentChapterIndex,
            chapterTitle = chapterTitle,
            elementId = visibleElementId ?: currentHighlightId,
            audioPositionMs = audioPositionMs,
            scrollPercent = lastScrollPercent,
            description = description
        )
        _historyEvents.add(0, event) // Add to beginning (most recent first)

        // Keep only last 50 events
        if (_historyEvents.size > 50) {
            _historyEvents.removeAt(_historyEvents.size - 1)
        }
    }

    fun navigateToHistoryEvent(event: HistoryEvent) {
        // Add current position to history before navigating
        addHistoryEvent(HistoryEventType.NAVIGATE, "Before returning to history")

        // Navigate to the history event's position
        if (event.chapterIndex != currentChapterIndex) {
            changeChapter(event.chapterIndex, audioPosMs = event.audioPositionMs)
        }
        if (event.elementId != null) {
            currentHighlightId = event.elementId
            forceScrollUpdate()
        } else {
            lastScrollPercent = event.scrollPercent
            forceScrollUpdate()
        }
    }
    
    fun loadEpub(bookId: String, isReadAloud: Boolean) {
        if (currentBookId == bookId && isReadAloudMode == isReadAloud) {
            if (lazyBook != null && !isReadAloud) {
                viewModelScope.launch {
                    val progressStr = repository.getBookProgress(bookId).first()
                    val progress = UnifiedProgress.fromString(progressStr)
                    if (progress != null) {
                        try {
                            val serverPos = AppContainer.apiClientManager.getApi().getPosition(bookId)
                            if (serverPos != null) {
                                val serverProgress = UnifiedProgress.fromPosition(serverPos, totalChapters)
                                val localLastUpdated = progress.lastUpdated
                                val serverTimestampMs = serverPos.timestamp.let { if (it < 1000000000000L) it * 1000 else it }
                                if (serverTimestampMs > localLastUpdated + 10000) {
                                    val serverPercent = (serverProgress.getOverallProgress() * 100).coerceIn(0f, 100f)
                                    val localUnified = UnifiedProgress(
                                        chapterIndex = currentChapterIndex,
                                        elementId = currentHighlightId,
                                        audioTimestampMs = currentAudioPos,
                                        scrollPercent = lastScrollPercent,
                                        lastUpdated = progress.lastUpdated,
                                        totalChapters = totalChapters.coerceAtLeast(1),
                                        totalDurationMs = (chapterOffsets.values.maxOrNull() ?: 0.0).let { (it * 1000).toLong() }
                                    )
                                    val localPercent = (localUnified.getOverallProgress() * 100).coerceIn(0f, 100f)
                                    
                                    if (kotlin.math.abs(serverPercent - localPercent) > 5f) {
                                        syncConfirmation = SyncConfirmation(
                                            newChapterIndex = serverProgress.chapterIndex,
                                            newScrollPercent = serverProgress.scrollPercent,
                                            newAudioMs = serverProgress.audioTimestampMs,
                                            newElementId = serverProgress.elementId,
                                            progressPercent = serverPercent,
                                            localProgressPercent = localPercent,
                                            source = "Storyteller Server"
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {}
                    }
                }
            }
            return
        }
        currentBookId = bookId
        isReadAloudMode = isReadAloud
        currentChapterIndex = 0
        lastScrollPercent = 0f
        currentAudioPos = 0L
        currentHighlightId = null
        isLoading = true
        readerInitialized = false
        syncData = emptyMap()
        chapterOffsets = emptyMap()
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val globalSettings = repository.userSettings.first()
                val bookSettings = repository.getBookReaderSettings(bookId).first()
                
                settings = globalSettings.copy(
                    readerFontSize = bookSettings.first ?: globalSettings.readerFontSize,
                    readerTheme = bookSettings.second ?: globalSettings.readerTheme,
                    readerFontFamily = bookSettings.third ?: globalSettings.readerFontFamily
                )
                
                val apiManager = AppContainer.apiClientManager
                val apiBook = apiManager.getApi().getBookDetails(bookId)
                
                val apiSeries = apiBook.series?.firstOrNull()
                val apiCollection = apiBook.collections?.firstOrNull()
                val seriesName = apiSeries?.name ?: apiCollection?.name
                val seriesIdx = apiSeries?.seriesIndex ?: apiCollection?.seriesIndex

                val book = Book(
                    id = apiBook.uuid,
                    title = apiBook.title,
                    author = apiBook.authors.joinToString(", ") { it.name },
                    series = seriesName,
                    seriesIndex = seriesIdx,
                    coverUrl = apiManager.getCoverUrl(apiBook.uuid),
                    audiobookCoverUrl = apiManager.getAudiobookCoverUrl(apiBook.uuid),
                    ebookCoverUrl = apiManager.getEbookCoverUrl(apiBook.uuid)
                )
                
                val bookDir = DownloadUtils.getBookDir(AppContainer.context.filesDir, book)
                val baseFileName = DownloadUtils.getBaseFileName(book)
                val fileName = if (isReadAloud) "$baseFileName (readaloud).epub" else "$baseFileName.epub"
                val file = File(bookDir, fileName)
                
                if (!file.exists()) {
                    throw Exception("Ebook file not found on disk. Please download it first.")
                }

                android.util.Log.d("ReaderViewModel", "Lazily opening: ${file.absolutePath}")
                
                val zip = java.util.zip.ZipFile(file)
                currentZipFile = zip
                
                val containerEntry = zip.getEntry("META-INF/container.xml") 
                    ?: throw Exception("Invalid EPUB: Missing container.xml")
                val containerHtml = zip.getInputStream(containerEntry).bufferedReader().readText()
                val opfPath = containerHtml.substringAfter("full-path=\"").substringBefore("\"")
                
                val opfEntry = zip.getEntry(opfPath) ?: throw Exception("Invalid EPUB: Missing $opfPath")
                val opfContent = zip.getInputStream(opfEntry).bufferedReader().readText()
                
                val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
                
                epubTitle = opfContent.substringAfter("<dc:title>").substringBefore("</dc:title>")
                
                val manifestMap = mutableMapOf<String, String>()
                val mediaTypeMap = mutableMapOf<String, String>()
                val resourcesMap = mutableMapOf<String, String>()
                val mediaOverlayMap = mutableMapOf<String, String>()
                
                val manifestLines = opfContent.substringAfter("<manifest>").substringBefore("</manifest>")
                manifestLines.split("<item ").drop(1).forEach { line ->
                    val id = line.substringAfter("id=\"").substringBefore("\"")
                    val href = line.substringAfter("href=\"").substringBefore("\"")
                    val type = line.substringAfter("media-type=\"").substringBefore("\"")
                    val overlay = if (line.contains("media-overlay=\"")) line.substringAfter("media-overlay=\"").substringBefore("\"") else null
                    val durationStr = if (line.contains("duration=\"")) line.substringAfter("duration=\"").substringBefore("\"") else null
                    
                    manifestMap[id] = href
                    val fullPath = (opfDir + href).replace("./", "").replace("//", "/")
                    mediaTypeMap[fullPath] = type
                    resourcesMap[fullPath] = fullPath
                    
                    if (overlay != null) {
                        mediaOverlayMap[fullPath] = overlay
                    }
                }

                val resolvedOverlayMap = mutableMapOf<String, String>()
                mediaOverlayMap.forEach { (itemHref, overlayId) ->
                    manifestMap[overlayId]?.let { overlayHref ->
                        val fullPath = (opfDir + overlayHref).replace("./", "").replace("//", "/")
                        resolvedOverlayMap[itemHref] = fullPath
                    }
                }
                
                val spineHrefs = mutableListOf<String>()
                val spineLines = opfContent.substringAfter("<spine").substringAfter(">").substringBefore("</spine>")
                spineLines.split("<itemref ").drop(1).forEach { line ->
                    val idref = line.substringAfter("idref=\"").substringBefore("\"")
                    manifestMap[idref]?.let { href ->
                        val fullPath = (opfDir + href).replace("./", "").replace("//", "/")
                        spineHrefs.add(fullPath)
                    }
                }

                val spineTitles = mutableMapOf<String, String>()
                try {
                    val ncxId = opfContent.substringAfter("toc=\"", "").substringBefore("\"").ifEmpty {
                        manifestLines.split("<item ").find { it.contains("application/x-dtbncx+xml") }
                            ?.substringAfter("id=\"")?.substringBefore("\"")
                    }
                    
                    if (!ncxId.isNullOrEmpty()) {
                        val ncxHref = manifestMap[ncxId]
                        if (ncxHref != null) {
                            val fullNcxPath = (opfDir + ncxHref).replace("./", "").replace("//", "/")
                            val ncxEntry = zip.getEntry(fullNcxPath)
                            if (ncxEntry != null) {
                                val ncxContent = zip.getInputStream(ncxEntry).bufferedReader().readText()
                                val navPoints = ncxContent.split("<navPoint")
                                navPoints.drop(1).forEach { point ->
                                    val label = point.substringAfter("<text>").substringBefore("</text>")
                                    var src = point.substringAfter("src=\"").substringBefore("\"")
                                    val ncxDir = if (fullNcxPath.contains("/")) fullNcxPath.substringBeforeLast("/") + "/" else ""
                                    src = src.substringBefore("#") 
                                    val absPath = (ncxDir + src).replace("./", "").replace("//", "/")
                                    
                                    if (label.isNotBlank()) {
                                        spineTitles[absPath] = label
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ReaderViewModel", "Failed to parse NCX titles: ${e.message}")
                }

                lazyBook = LazyBook(epubTitle, spineHrefs, resourcesMap, mediaTypeMap, spineTitles)
                totalChapters = spineHrefs.size

                val progressStr = repository.getBookProgress(bookId).first()
                android.util.Log.d("ReaderViewModel", "Loading progress for $bookId: $progressStr")
                val progress = UnifiedProgress.fromString(progressStr)
                var savedAudioMs: Long? = null
                if (progress != null) {
                    android.util.Log.d("ReaderViewModel", "Parsed progress: chapter=${progress.chapterIndex}, scroll=${progress.scrollPercent}, audio=${progress.audioTimestampMs}")
                    currentChapterIndex = progress.chapterIndex.coerceIn(0, totalChapters - 1)
                    lastScrollPercent = progress.scrollPercent
                    currentHighlightId = progress.elementId
                    savedAudioMs = if (progress.audioTimestampMs > 0) progress.audioTimestampMs else null
                    android.util.Log.d("ReaderViewModel", "Applied progress: currentChapterIndex=$currentChapterIndex, lastScrollPercent=$lastScrollPercent")
                } else {
                    android.util.Log.w("ReaderViewModel", "No progress found for $bookId, starting from beginning")
                }

                if (resolvedOverlayMap.isNotEmpty()) {
                    val segmentsMap = java.util.concurrent.ConcurrentHashMap<String, List<SyncSegment>>()
                    
                    coroutineScope {
                        val jobs = spineHrefs.map { chapterHref ->
                            val smilHref = resolvedOverlayMap[chapterHref] ?: return@map null
                            async {
                                try {
                                    val smilEntry = zip.getEntry(smilHref)
                                    if (smilEntry != null) {
                                        val smilContent = zip.getInputStream(smilEntry).bufferedReader().readText()
                                        segmentsMap[chapterHref] = parseSmil(smilContent, chapterHref)
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }.filterNotNull()
                        jobs.awaitAll()
                    }

                    val sortedOffsets = mutableMapOf<String, Double>()
                    var currentOffset = 0.0
                    spineHrefs.forEach { chapterHref ->
                        sortedOffsets[chapterHref] = currentOffset
                        val segments = segmentsMap[chapterHref]
                        val dur = segments?.sumOf { it.clipEnd - it.clipBegin } ?: 0.0
                        currentOffset += dur
                    }
                    
                    withContext(Dispatchers.Main) {
                        syncData = segmentsMap.toMap()
                        chapterOffsets = sortedOffsets
                        
                         if (savedAudioMs != null && savedAudioMs > 0) {
                            getElementIdAtTime(savedAudioMs)?.let { result ->
                                currentChapterIndex = result.first
                                currentHighlightId = result.second
                                currentAudioPos = savedAudioMs
                                android.util.Log.d("ReaderSync", "Progress restored via priority Audio MS: ${result.first} / ${result.second} (${savedAudioMs}ms)")
                            }
                        } else if (currentHighlightId != null) {
                            android.util.Log.d("ReaderSync", "Progress restored via Element ID: $currentHighlightId")
                        } else {
                            android.util.Log.d("ReaderSync", "Progress remaining on Chapter Index: $currentChapterIndex")
                        }
                    }
                    
                    if (!isReadAloud) {
                        try {
                            val serverPos = AppContainer.apiClientManager.getApi().getPosition(bookId)
                            if (serverPos != null) {
                                val serverHref = serverPos.locator.href
                                val chapterIdxFromHref = lazyBook?.spineHrefs?.indexOfFirst { it == serverHref || it.endsWith("/$serverHref") || serverHref.endsWith("/$it") }
                                
                                val serverProgress = UnifiedProgress.fromPosition(serverPos, totalChapters).let {
                                    val resolvedIdx = serverPos.locator.locations.chapterIndex ?: chapterIdxFromHref
                                    if (resolvedIdx != null && resolvedIdx >= 0) {
                                        it.copy(chapterIndex = resolvedIdx)
                                    } else it
                                }
                        
                                val localLastUpdated = progress?.lastUpdated ?: 0L
                                val serverTimestampMs = serverPos.timestamp.let { if (it < 1000000000000L) it * 1000 else it }
                                
                                val serverPercent = (serverProgress.getOverallProgress() * 100).coerceIn(0f, 100f)
                                
                                val localUnified = UnifiedProgress(
                                    chapterIndex = currentChapterIndex,
                                    elementId = currentHighlightId,
                                    audioTimestampMs = currentAudioPos,
                                    scrollPercent = lastScrollPercent,
                                    lastUpdated = progress?.lastUpdated ?: 0L,
                                    totalChapters = totalChapters.coerceAtLeast(1),
                                    totalDurationMs = ((sortedOffsets.values.maxOrNull() ?: 0.0) + (segmentsMap[spineHrefs.lastOrNull()]?.sumOf { it.clipEnd - it.clipBegin } ?: 0.0)).let { (it * 1000).toLong() }
                                )
                                val localPercent = (localUnified.getOverallProgress() * 100).coerceIn(0f, 100f)
                                
                                val isServerZeroButLocalStarted = serverPercent < 0.1f && localPercent > 1.0f
                                val isSignificantlyNewer = serverTimestampMs > localLastUpdated + 300000
                                val isRecentEnough = serverTimestampMs > localLastUpdated + 10000
                                
                                if (isRecentEnough && (!isServerZeroButLocalStarted || isSignificantlyNewer)) {
                                    if (kotlin.math.abs(serverPercent - localPercent) > 5f) {
                                        android.util.Log.d("ReaderSync", "Significant progress diff ($serverPercent% vs $localPercent%). Prompting.")
                                        
                                        val savedChapter = serverProgress.chapterIndex
                                        val savedAudioMs = serverProgress.audioTimestampMs
                                        val resolvedElementId = if (savedAudioMs > 0) getElementIdAtTime(savedAudioMs)?.second else null
                                        
                                        withContext(Dispatchers.Main) {
                                            syncConfirmation = SyncConfirmation(
                                                newChapterIndex = savedChapter,
                                                newScrollPercent = serverProgress.scrollPercent,
                                                newAudioMs = savedAudioMs,
                                                newElementId = resolvedElementId,
                                                progressPercent = serverPercent,
                                                localProgressPercent = localPercent,
                                                source = "Storyteller Server"
                                            )
                                        }
                                    } else {
                                        android.util.Log.d("ReaderSync", "Progress diff is minor ($serverPercent% vs $localPercent%). Auto-syncing to Storyteller.")
                                        withContext(Dispatchers.Main) {
                                            currentChapterIndex = serverProgress.chapterIndex
                                            lastScrollPercent = serverProgress.scrollPercent
                                            if (serverProgress.audioTimestampMs > 0) {
                                                currentAudioPos = serverProgress.audioTimestampMs
                                            }
                                            currentHighlightId = serverProgress.elementId ?: if (serverProgress.audioTimestampMs > 0) getElementIdAtTime(serverProgress.audioTimestampMs)?.second else null
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("ReaderSync", "Failed to fetch server progress: ${e.message}")
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    isLoading = false
                    // Initialize highlights and bookmarks
                    loadHighlightsForChapter(currentChapterIndex)
                    loadBookmarks()
                    // Start reading session
                    startReadingSession()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                error = e.message
                isLoading = false
            }
        }
    }

    fun saveProgress() {
        saveProgress(currentChapterIndex, lastScrollPercent, if (isReadAloudMode) currentAudioPos else 0L, currentHighlightId)
    }

    fun saveProgress(chapterIndex: Int, scrollPercent: Float, audioPosMs: Long? = null, elementId: String? = null) {
        if (isLoading || !readerInitialized) {
            android.util.Log.d("ReaderViewModel", "Ignoring saveProgress: loading=$isLoading, initialized=$readerInitialized")
            return
        }

        lastScrollPercent = scrollPercent
        if (elementId != null && !isReadAloudMode) {
            currentHighlightId = elementId
        }
        if (audioPosMs != null) {
            currentAudioPos = audioPosMs
        }

        // Track the visible element from page navigation (used for audio sync when not playing)
        if (elementId != null) {
            visibleElementId = elementId
        }
        
        viewModelScope.launch {
            val bookId = currentBookId ?: return@launch
            
            val href = lazyBook?.spineHrefs?.getOrNull(chapterIndex) ?: ""
            
            val actualAudioPos = if (isReadAloudMode) {
                currentAudioPos
            } else {
                val elementMs = elementId?.let { (getTimeAtElement(chapterIndex, it) ?: 0.0) * 1000 }?.toLong()
                
                if (elementMs != null && elementMs > 0) {
                    elementMs
                } else {
                    val offset = chapterOffsets[href] ?: 0.0
                    val chapterDur = syncData[href]?.sumOf { it.clipEnd - it.clipBegin } ?: 0.0
                    ((offset + (chapterDur * scrollPercent)) * 1000).toLong()
                }
            }

            val finalElementId = if (isReadAloudMode) {
                currentHighlightId ?: elementId
            } else {
                elementId ?: currentHighlightId ?: if (actualAudioPos > 0) {
                    getElementIdAtTime(actualAudioPos)?.second
                } else null
            }
            
            val mediaType = lazyBook?.mediaTypes?.get(href) ?: "application/xhtml+xml"

            val lastChapterHref = chapterOffsets.entries.maxByOrNull { it.value }?.key
            val lastChapterDur = syncData[lastChapterHref]?.sumOf { it.clipEnd - it.clipBegin } ?: 0.0
            val totalAudioDurMs = ((chapterOffsets.values.maxOrNull() ?: 0.0) + lastChapterDur).let { (it * 1000).toLong() }

            val progress = UnifiedProgress(
                chapterIndex = chapterIndex,
                elementId = finalElementId,
                audioTimestampMs = actualAudioPos,
                scrollPercent = scrollPercent,
                lastUpdated = System.currentTimeMillis(),
                totalChapters = totalChapters,
                totalDurationMs = totalAudioDurMs,
                href = href,
                mediaType = mediaType
            )

            if (actualAudioPos == 0L && finalElementId == null && lastSyncedProgress != null) {
                val old = lastSyncedProgress!!
                if (old.chapterIndex == chapterIndex && old.audioTimestampMs > 5000) {
                    android.util.Log.d("ReaderSync", "Blocking reset to 0 for chapter $chapterIndex")
                    return@launch
                }
            }
            
            if (lastSyncedProgress != null) {
                val old = lastSyncedProgress!!
                val timeDiff = kotlin.math.abs(progress.audioTimestampMs - old.audioTimestampMs)
                if (progress.chapterIndex == old.chapterIndex && 
                    progress.elementId == old.elementId && 
                    timeDiff < 1000 &&
                    kotlin.math.abs(progress.scrollPercent - old.scrollPercent) < 0.01) {
                    return@launch
                }
            }
            lastSyncedProgress = progress

            repository.saveBookProgress(bookId, progress.toString()) 
            android.util.Log.d("ReaderSync", "Saved local progress for $bookId")
            
            try {
                val pos = progress.toPosition()
                android.util.Log.d("ReaderSync", "Uploading reader progress: $pos")
                AppContainer.apiClientManager.getApi().updatePosition(bookId, pos)
                android.util.Log.d("ReaderSync", "Successfully synced position to server: href=$href")
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 409) {
                    android.util.Log.i("ReaderSync", "Server has newer or same position (409). skipping.")
                } else {
                    android.util.Log.w("ReaderSync", "Failed to sync to server (HTTP ${e.code()}): ${e.message}")
                }
            } catch (e: Exception) {
                android.util.Log.w("ReaderSync", "Failed to sync to server: ${e.message}")
            }
        }
    }

    fun navigateToHref(href: String) {
        val parts = href.split("#")
        val path = parts[0]
        val anchor = if (parts.size > 1) parts[1] else null
        
        var targetIndex = -1
        
        targetIndex = lazyBook?.spineHrefs?.indexOfFirst { it == path || it.endsWith("/$path") || path.endsWith("/$it") } ?: -1
        
        if (targetIndex != -1) {
            changeChapter(targetIndex)
            if (anchor != null) {
                pendingAnchorId.value = anchor
            }
        }
    }

    fun changeChapter(index: Int, audioPosMs: Long? = null, scrollToEnd: Boolean = false) {
        currentChapterIndex = index
        currentHighlightId = null
        val scrollPercent = if (scrollToEnd) 1.0f else 0f
        saveProgress(index, scrollPercent, audioPosMs)
        // Load highlights for new chapter
        loadHighlightsForChapter(index)
    }

    fun updateFontSize(newSize: Float) {
        viewModelScope.launch {
            val bookId = currentBookId ?: return@launch
            repository.saveBookReaderSettings(bookId, fontSize = newSize, theme = null, fontFamily = null)
            settings = settings?.copy(readerFontSize = newSize)
        }
    }

    fun updateTheme(theme: Int) {
        viewModelScope.launch {
            val bookId = currentBookId ?: return@launch
            repository.saveBookReaderSettings(bookId, fontSize = null, theme = theme, fontFamily = null)
            settings = settings?.copy(readerTheme = theme)
        }
    }

    fun updateFontFamily(family: String) {
        viewModelScope.launch {
            val bookId = currentBookId ?: return@launch
            repository.saveBookReaderSettings(bookId, fontSize = null, theme = null, fontFamily = family)
            settings = settings?.copy(readerFontFamily = family)
        }
    }

    fun updateBrightness(brightness: Float) {
        viewModelScope.launch {
            repository.updateReaderBrightness(brightness)
            settings = settings?.copy(readerBrightness = brightness)
        }
    }

    fun updateLineSpacing(lineSpacing: Float) {
        viewModelScope.launch {
            repository.updateReaderLineSpacing(lineSpacing)
            settings = settings?.copy(readerLineSpacing = lineSpacing)
        }
    }

    fun updateMarginSize(marginSize: Int) {
        viewModelScope.launch {
            repository.updateReaderMarginSize(marginSize)
            settings = settings?.copy(readerMarginSize = marginSize)
        }
    }

    fun updateFullscreenMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateReaderFullscreenMode(enabled)
            settings = settings?.copy(readerFullscreenMode = enabled)
        }
    }

    fun updateTextAlignment(alignment: String) {
        viewModelScope.launch {
            repository.updateReaderTextAlignment(alignment)
            settings = settings?.copy(readerTextAlignment = alignment)
        }
    }

    // === FOLDABLE DEVICE SUPPORT ===

    fun updateScreenMode(mode: ReaderScreenMode, foldBounds: Int = 0) {
        val previousMode = screenMode
        screenMode = mode
        foldBoundsPx = foldBounds

        if (mode == ReaderScreenMode.TWO_PAGE && previousMode != ReaderScreenMode.TWO_PAGE) {
            loadInnerScreenSettings()
        }
    }

    private fun loadInnerScreenSettings() {
        innerSettingsCollectionJob?.cancel()
        innerSettingsCollectionJob = viewModelScope.launch {
            repository.innerScreenSettings.collect { settings ->
                innerScreenSettings = settings
            }
        }
    }

    // Inner screen setting update methods
    fun updateInnerFontSize(size: Float) {
        viewModelScope.launch {
            repository.updateInnerReaderFontSize(size)
            innerScreenSettings = innerScreenSettings?.copy(readerFontSize = size)
        }
    }

    fun updateInnerTheme(theme: Int) {
        viewModelScope.launch {
            repository.updateInnerReaderTheme(theme)
            innerScreenSettings = innerScreenSettings?.copy(readerTheme = theme)
        }
    }

    fun updateInnerFontFamily(family: String) {
        viewModelScope.launch {
            repository.updateInnerReaderFontFamily(family)
            innerScreenSettings = innerScreenSettings?.copy(readerFontFamily = family)
        }
    }

    fun updateInnerLineSpacing(lineSpacing: Float) {
        viewModelScope.launch {
            repository.updateInnerReaderLineSpacing(lineSpacing)
            innerScreenSettings = innerScreenSettings?.copy(readerLineSpacing = lineSpacing)
        }
    }

    fun updateInnerMarginSize(marginSize: Int) {
        viewModelScope.launch {
            repository.updateInnerReaderMarginSize(marginSize)
            innerScreenSettings = innerScreenSettings?.copy(readerMarginSize = marginSize)
        }
    }

    fun updateInnerTextAlignment(alignment: String) {
        viewModelScope.launch {
            repository.updateInnerReaderTextAlignment(alignment)
            innerScreenSettings = innerScreenSettings?.copy(readerTextAlignment = alignment)
        }
    }

    fun updateInnerBrightness(brightness: Float) {
        viewModelScope.launch {
            repository.updateInnerReaderBrightness(brightness)
            innerScreenSettings = innerScreenSettings?.copy(readerBrightness = brightness)
        }
    }

    fun updateInnerPageGap(gap: Int) {
        viewModelScope.launch {
            repository.updateInnerPageGap(gap)
            innerScreenSettings = innerScreenSettings?.copy(pageGap = gap)
        }
    }

    fun updateInnerShowPageDivider(show: Boolean) {
        viewModelScope.launch {
            repository.updateInnerShowPageDivider(show)
            innerScreenSettings = innerScreenSettings?.copy(showPageDivider = show)
        }
    }

    fun getResourceResponse(href: String): WebResourceResponse? {
        val zip = currentZipFile ?: return null
        val book = lazyBook ?: return null
        
        val cleanHref = href.substringAfter("https://epub-internal/")
            .substringBefore("?")
            .substringBefore("#")

        val zipEntryName = book.resources[cleanHref] ?: return null
        val entry = zip.getEntry(zipEntryName) ?: return null
        val mimeType = book.mediaTypes[cleanHref] ?: "application/octet-stream"
        
        return try {
            WebResourceResponse(mimeType, null, zip.getInputStream(entry))
        } catch (e: Exception) {
            null
        }
    }

    fun getCurrentChapterHtml(): String? {
        val zip = currentZipFile ?: return null
        val book = lazyBook ?: return null
        if (currentChapterIndex !in book.spineHrefs.indices) return null
        
        val href = book.spineHrefs[currentChapterIndex]
        val entryName = book.resources[href] ?: return null
        val entry = zip.getEntry(entryName) ?: return null
        val raw = zip.getInputStream(entry).bufferedReader().readText()
        
        return raw.replace(Regex("<\\?xml[^>]*\\?>", RegexOption.IGNORE_CASE), "").trim()
    }

    fun getCurrentChapterPath(): String {
        return lazyBook?.spineHrefs?.getOrNull(currentChapterIndex) ?: ""
    }

    private fun parseSmil(content: String, targetHref: String): List<SyncSegment> {
        val segments = mutableListOf<SyncSegment>()
        val targetFilename = targetHref.substringAfterLast("/")
        
        PAR_REGEX.findAll(content).forEach { match ->
            val inner = match.groupValues[1]
            val textMatch = TEXT_REGEX.find(inner)
            val audioMatch = AUDIO_REGEX.find(inner)

            if (textMatch != null && audioMatch != null) {
                val fullTextSrc = textMatch.groupValues[1]
                val textFilename = fullTextSrc.substringBefore("#").substringAfterLast("/")
                
                if (textFilename.equals(targetFilename, ignoreCase = true) || targetFilename.isBlank()) {
                    val elementId = fullTextSrc.substringAfter("#")
                    val audioSrc = audioMatch.groupValues[1]
                    val begin = parseClock(audioMatch.groupValues[2])
                    val end = parseClock(audioMatch.groupValues[3])
                    segments.add(SyncSegment(elementId, audioSrc, begin, end))
                }
            }
        }
        return segments
    }

    companion object {
        private val PAR_REGEX = Regex("<par[^>]*>(.*?)</par>", RegexOption.DOT_MATCHES_ALL)
        private val TEXT_REGEX = Regex("<text[^>]*src=\"([^\"]+)\"")
        private val AUDIO_REGEX = Regex("<audio[^>]*src=\"([^\"]+)\"[^>]*clipBegin=\"([^\"]+)\"[^>]*clipEnd=\"([^\"]+)\"")
    }

    private fun parseClock(time: String): Double {
        return try {
            if (time.endsWith("s")) {
                time.dropLast(1).toDouble()
            } else if (time.contains(":")) {
                val parts = time.split(":")
                if (parts.size == 3) {
                    parts[0].toDouble() * 3600 + parts[1].toDouble() * 60 + parts[2].toDouble()
                } else if (parts.size == 2) {
                    parts[0].toDouble() * 60 + parts[1].toDouble()
                } else {
                    time.toDouble()
                }
            } else {
                time.toDouble()
            }
        } catch (e: Exception) {
            0.0
        }
    }

    fun getElementIdAtTime(timeMs: Long): Pair<Int, String>? {
        val timeSec = timeMs / 1000.0
        
        val sortedChapters = chapterOffsets.entries.sortedBy { it.value }
        val chapterEntry = sortedChapters.findLast { timeSec >= it.value } ?: sortedChapters.firstOrNull() ?: return null
        
        val href = chapterEntry.key
        val chapterOffset = chapterEntry.value
        val relativeSec = timeSec - chapterOffset
        
        val chapterIndex = lazyBook?.spineHrefs?.indexOf(href) ?: -1
        if (chapterIndex == -1) return null
        
        val segments = syncData[href] ?: return chapterIndex to ""
        
        var cumulativeChapterSec = 0.0
        for (seg in segments) {
            val dur = Math.max(0.0, seg.clipEnd - seg.clipBegin)
            if (relativeSec >= cumulativeChapterSec && relativeSec < cumulativeChapterSec + dur) {
                return chapterIndex to seg.id
            }
            cumulativeChapterSec += dur
        }
        
        return if (segments.isNotEmpty()) chapterIndex to segments.last().id else null
    }

    fun getTimeAtElement(chapterIndex: Int, elementId: String): Double? {
        val href = lazyBook?.spineHrefs?.getOrNull(chapterIndex) ?: return null
        val segments = syncData[href] ?: return null
        val offset = chapterOffsets[href] ?: 0.0
        
        var cumulative = 0.0
        for (seg in segments) {
            if (seg.id == elementId) return offset + cumulative
            cumulative += Math.max(0.0, seg.clipEnd - seg.clipBegin)
        }
        return null
    }

    fun overwriteChapterOffsets(startTimesMs: List<Long>) {
        val spine = lazyBook?.spineHrefs ?: return
        val currentMap = chapterOffsets.toMutableMap()
        
        startTimesMs.forEachIndexed { index, timeMs ->
            if (index < spine.size) {
                currentMap[spine[index]] = timeMs / 1000.0
            }
        }
        chapterOffsets = currentMap
        android.util.Log.d("ReaderSync", "Overwrote ${startTimesMs.size} chapter offsets from audiobook metadata")
    }

    fun forceScrollUpdate() {
        syncTrigger++
    }

    data class SearchResult(
        val chapterIndex: Int,
        val title: String,
        val textSnippet: String,
        val matchIndex: Int
    )
    
    var searchResults by mutableStateOf<List<SearchResult>>(emptyList())
    var isSearching by mutableStateOf(false)
    var searchJob: Job? = null
    var activeSearchHighlight by mutableStateOf<String?>(null)
    var activeSearchMatchIndex by mutableIntStateOf(0)

    // Highlight management
    var showHighlightMenu by mutableStateOf(false)
    var selectedHighlightColor by mutableStateOf("#FFEB3B") // Yellow default
    var pendingHighlight by mutableStateOf<PendingHighlight?>(null)
    var clickedHighlight by mutableStateOf<Highlight?>(null)
    val highlightsForCurrentChapter = mutableStateOf<List<Highlight>>(emptyList())
    var longPressedElementId by mutableStateOf<String?>(null)
    private var highlightCollectionJob: Job? = null
    var clearSelectionTrigger by mutableIntStateOf(0)  // Trigger to clear text selection

    // Event flow for highlight UI events (bypasses Compose state observation issues)
    sealed class HighlightEvent {
        data class ShowLongPressMenu(val elementId: String, val pendingHighlight: PendingHighlight?) : HighlightEvent()
        data class ShowColorPicker(val pendingHighlight: PendingHighlight) : HighlightEvent()
    }
    private val _highlightEvents = kotlinx.coroutines.flow.MutableSharedFlow<HighlightEvent>(replay = 0, extraBufferCapacity = 1)
    val highlightEvents = _highlightEvents.asSharedFlow()

    fun emitLongPressEvent(elementId: String) {
        android.util.Log.e("ReaderViewModel", "=== EMITTING LongPressMenu event for: $elementId ===")
        _highlightEvents.tryEmit(HighlightEvent.ShowLongPressMenu(elementId, pendingHighlight))
    }

    fun emitColorPickerEvent(pending: PendingHighlight) {
        android.util.Log.e("ReaderViewModel", "=== EMITTING ColorPicker event for: ${pending.text.take(20)} ===")
        _highlightEvents.tryEmit(HighlightEvent.ShowColorPicker(pending))
    }

    // Bookmark management
    var bookmarks = mutableStateOf<List<Bookmark>>(emptyList())
    var showBookmarkDialog by mutableStateOf(false)

    // Reading session tracking
    private var currentSessionStartTime: Long? = null
    private var currentSessionId: Long? = null

    data class PendingHighlight(
        val chapterIndex: Int,
        val elementId: String,
        val text: String,
        val startOffset: Int = 0,
        val endOffset: Int = 0
    )
    
    fun search(query: String) {
        searchJob?.cancel()
        if (query.length < 2) {
            searchResults = emptyList()
            return
        }
        
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            isSearching = true
            val results = mutableListOf<SearchResult>()
            val book = lazyBook ?: return@launch
            val zip = currentZipFile ?: return@launch
            
            val htmlTagRegex = Regex("<[^>]*>")
            val spaceRegex = Regex("\\s+")
            
            book.spineHrefs.forEachIndexed { index, href ->
                if (!isActive) return@forEachIndexed
                try {
                    val entryName = book.resources[href] ?: return@forEachIndexed
                    val entry = zip.getEntry(entryName) ?: return@forEachIndexed
                    
                    val rawHtml = zip.getInputStream(entry).bufferedReader().readText()
                    
                    val bodyContent = Regex("<body[^>]*>([\\s\\S]*?)</body>", RegexOption.IGNORE_CASE)
                        .find(rawHtml)?.groupValues?.get(1) ?: rawHtml

                    val noScriptStyle = bodyContent.replace(Regex("<(script|style)[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE), " ")
                    
                    val breaksToSpaces = noScriptStyle.replace(Regex("</?(br|p|div|li|h\\d|tr|td)[^>]*>", RegexOption.IGNORE_CASE), " ")

                    val simpleText = breaksToSpaces.replace(htmlTagRegex, "")
                    
                    val plainText = simpleText.replace(spaceRegex, " ")
                    
                    var searchIndex = 0
                    var matchCount = 0
                    while (true) {
                        val foundIndex = plainText.indexOf(query, searchIndex, ignoreCase = true)
                        if (foundIndex == -1 || matchCount > 5) break 
                        
                        val start = (foundIndex - 30).coerceAtLeast(0)
                        val end = (foundIndex + query.length + 30).coerceAtMost(plainText.length)
                        val snippet = "..." + plainText.substring(start, end) + "..."
                        
                        val title = book.spineTitles[href] ?: "Section ${index + 1}"
                        
                        results.add(SearchResult(index, title, snippet, matchCount))
                        
                        searchIndex = foundIndex + 1
                        matchCount++
                        if (results.size > 50) break 
                    }
                    if (results.size > 50) return@forEachIndexed
                } catch (e: Exception) {}
            }
            
            withContext(Dispatchers.Main) {
                searchResults = results
                isSearching = false
            }
        }
    }
    
    fun clearSearch() {
        searchJob?.cancel()
        searchResults = emptyList()
        isSearching = false
        activeSearchHighlight = null
        activeSearchMatchIndex = 0
    }

    fun navigateToSearchResult(result: SearchResult, query: String) {
        changeChapter(result.chapterIndex)
        activeSearchHighlight = query
        activeSearchMatchIndex = result.matchIndex
    }

    fun confirmSync() {
        syncConfirmation?.let {
            currentChapterIndex = it.newChapterIndex
            lastScrollPercent = it.newScrollPercent
            currentAudioPos = it.newAudioMs ?: 0L
            currentHighlightId = it.newElementId
            if (it.newElementId == null && (it.newAudioMs ?: 0L) > 0) {
                val res = getElementIdAtTime(it.newAudioMs!!)
                if (res != null) currentHighlightId = res.second
            }
            forceScrollUpdate()
            syncConfirmation = null
        }
    }

    fun dismissSync() {
        syncConfirmation = null
        saveProgress()
    }

    // === HIGHLIGHT MANAGEMENT ===

    fun loadHighlightsForChapter(chapterIndex: Int) {
        val bookId = currentBookId ?: return
        highlightCollectionJob?.cancel()
        highlightCollectionJob = viewModelScope.launch {
            highlightRepository.getHighlightsForChapter(bookId, chapterIndex).collect { highlights ->
                highlightsForCurrentChapter.value = highlights
            }
        }
    }

    fun createHighlight(
        chapterIndex: Int,
        elementId: String,
        text: String,
        color: String = selectedHighlightColor,
        note: String? = null
    ) {
        val bookId = currentBookId ?: return
        viewModelScope.launch {
            val highlight = Highlight(
                bookId = bookId,
                chapterIndex = chapterIndex,
                elementId = elementId,
                text = text,
                color = color,
                note = note,
                timestamp = System.currentTimeMillis()
            )
            highlightRepository.addHighlight(highlight)
            loadHighlightsForChapter(chapterIndex)

            // Clear text selection after creating highlight
            delay(100)  // Small delay to ensure highlight is applied first
            clearSelectionTrigger++
        }
    }

    fun updateHighlightNote(highlightId: Long, note: String) {
        viewModelScope.launch {
            val highlight = highlightRepository.getHighlightById(highlightId)
            if (highlight != null) {
                highlightRepository.updateHighlight(highlight.copy(note = note))
            }
        }
    }

    fun updateHighlightColor(highlightId: Long, color: String) {
        // Remember this color for future highlights
        selectedHighlightColor = color
        viewModelScope.launch {
            val highlight = highlightRepository.getHighlightById(highlightId)
            if (highlight != null) {
                highlightRepository.updateHighlight(highlight.copy(color = color))
            }
        }
    }

    fun deleteHighlight(highlight: Highlight) {
        viewModelScope.launch {
            highlightRepository.deleteHighlight(highlight)
            loadHighlightsForChapter(currentChapterIndex)
        }
    }

    fun getHighlightsForBook(): Flow<List<Highlight>> {
        val bookId = currentBookId ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return highlightRepository.getHighlightsForBook(bookId)
    }

    suspend fun exportHighlightsToMarkdown(context: android.content.Context) {
        val bookId = currentBookId ?: return
        try {
            // Get book details
            val apiManager = AppContainer.apiClientManager
            val apiBook = apiManager.getApi().getBookDetails(bookId)

            val book = Book(
                id = apiBook.uuid,
                title = apiBook.title,
                author = apiBook.authors.joinToString(", ") { it.name },
                series = apiBook.series?.firstOrNull()?.name ?: apiBook.collections?.firstOrNull()?.name,
                seriesIndex = apiBook.series?.firstOrNull()?.seriesIndex ?: apiBook.collections?.firstOrNull()?.seriesIndex,
                coverUrl = apiManager.getCoverUrl(apiBook.uuid),
                audiobookCoverUrl = apiManager.getAudiobookCoverUrl(apiBook.uuid),
                ebookCoverUrl = apiManager.getEbookCoverUrl(apiBook.uuid)
            )

            // Get all highlights
            val allHighlights = highlightRepository.getHighlightsForBook(bookId).first()

            // Build chapter titles map
            val chapterTitles = lazyBook?.spineTitles?.mapKeys { entry ->
                lazyBook?.spineHrefs?.indexOf(entry.key) ?: -1
            }?.filterKeys { it >= 0 } ?: emptyMap()

            // Export
            val exporter = com.pekempy.ReadAloudbooks.util.HighlightExporter()
            exporter.saveAndShareMarkdown(context, book, allHighlights, chapterTitles)
        } catch (e: Exception) {
            android.util.Log.e("ReaderViewModel", "Failed to export highlights", e)
        }
    }

    suspend fun exportHighlightsToCsv(context: android.content.Context) {
        val bookId = currentBookId ?: return
        try {
            // Get book details
            val apiManager = AppContainer.apiClientManager
            val apiBook = apiManager.getApi().getBookDetails(bookId)

            val book = Book(
                id = apiBook.uuid,
                title = apiBook.title,
                author = apiBook.authors.joinToString(", ") { it.name },
                series = apiBook.series?.firstOrNull()?.name ?: apiBook.collections?.firstOrNull()?.name,
                seriesIndex = apiBook.series?.firstOrNull()?.seriesIndex ?: apiBook.collections?.firstOrNull()?.seriesIndex,
                coverUrl = apiManager.getCoverUrl(apiBook.uuid),
                audiobookCoverUrl = apiManager.getAudiobookCoverUrl(apiBook.uuid),
                ebookCoverUrl = apiManager.getEbookCoverUrl(apiBook.uuid)
            )

            // Get all highlights
            val allHighlights = highlightRepository.getHighlightsForBook(bookId).first()

            // Build chapter titles map
            val chapterTitles = lazyBook?.spineTitles?.mapKeys { entry ->
                lazyBook?.spineHrefs?.indexOf(entry.key) ?: -1
            }?.filterKeys { it >= 0 } ?: emptyMap()

            // Export
            val exporter = com.pekempy.ReadAloudbooks.util.HighlightExporter()
            exporter.saveAndShareCsv(context, book, allHighlights, chapterTitles)
        } catch (e: Exception) {
            android.util.Log.e("ReaderViewModel", "Failed to export highlights", e)
        }
    }

    // === BOOKMARK MANAGEMENT ===

    fun loadBookmarks() {
        val bookId = currentBookId ?: return
        viewModelScope.launch {
            bookmarkRepository.getBookmarksForBook(bookId).collect { bookmarkList ->
                bookmarks.value = bookmarkList
            }
        }
    }

    fun createBookmark(label: String? = null) {
        val bookId = currentBookId ?: return
        viewModelScope.launch {
            val bookmark = Bookmark(
                bookId = bookId,
                chapterIndex = currentChapterIndex,
                scrollPercent = lastScrollPercent,
                elementId = currentHighlightId,
                label = label,
                timestamp = System.currentTimeMillis()
            )
            bookmarkRepository.addBookmark(bookmark)
            loadBookmarks()
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(bookmark)
            loadBookmarks()
        }
    }

    fun navigateToBookmark(bookmark: Bookmark) {
        changeChapter(bookmark.chapterIndex)
        lastScrollPercent = bookmark.scrollPercent
        currentHighlightId = bookmark.elementId
        forceScrollUpdate()
    }

    // === READING SESSION TRACKING ===

    fun startReadingSession() {
        val bookId = currentBookId ?: return
        if (currentSessionStartTime != null) return // Already tracking

        currentSessionStartTime = System.currentTimeMillis()
        viewModelScope.launch {
            val session = ReadingSession(
                bookId = bookId,
                bookTitle = epubTitle,
                startTime = currentSessionStartTime!!,
                endTime = currentSessionStartTime!!,
                durationMillis = 0,
                pagesRead = 0,
                chapterIndex = currentChapterIndex
            )
            currentSessionId = readingStatisticsRepository.addSession(session)
        }
    }

    fun updateReadingSession() {
        val sessionId = currentSessionId ?: return
        val startTime = currentSessionStartTime ?: return
        val bookId = currentBookId ?: return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val duration = now - startTime

            // Only update if session is at least 10 seconds
            if (duration >= 10_000) {
                val session = ReadingSession(
                    id = sessionId,
                    bookId = bookId,
                    bookTitle = epubTitle,
                    startTime = startTime,
                    endTime = now,
                    durationMillis = duration,
                    pagesRead = 0, // Would need to calculate this
                    chapterIndex = currentChapterIndex
                )
                // Note: We'd need an update method in the repository
                // For now, this structure is in place
            }
        }
    }

    fun endReadingSession() {
        updateReadingSession()
        currentSessionStartTime = null
        currentSessionId = null
    }

    override fun onCleared() {
        super.onCleared()
        endReadingSession()
        try {
            currentZipFile?.close()
        } catch (e: Exception) {}
        currentZipFile = null
        lazyBook = null
    }

    fun redownloadBook(context: android.content.Context) {
        val bookId = currentBookId ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
             try {
                 
                 val apiManager = AppContainer.apiClientManager
                 val apiBook = apiManager.getApi().getBookDetails(bookId)
                 val apiSeries = apiBook.series?.firstOrNull()
                 val apiCollection = apiBook.collections?.firstOrNull()
                 
                 val book = Book(
                     id = apiBook.uuid,
                     title = apiBook.title,
                     author = apiBook.authors.joinToString(", ") { it.name },
                     series = apiSeries?.name ?: apiCollection?.name,
                     seriesIndex = apiSeries?.seriesIndex ?: apiCollection?.seriesIndex,
                     hasReadAloud = apiBook.readaloud != null,
                     hasEbook = apiBook.ebook != null,
                     hasAudiobook = apiBook.audiobook != null,
                     syncedUrl = apiManager.getSyncDownloadUrl(apiBook.uuid),
                     audiobookUrl = apiManager.getAudiobookDownloadUrl(apiBook.uuid),
                     ebookUrl = apiManager.getEbookDownloadUrl(apiBook.uuid),
                     coverUrl = apiManager.getCoverUrl(apiBook.uuid),
                     audiobookCoverUrl = apiManager.getAudiobookCoverUrl(apiBook.uuid),
                     ebookCoverUrl = apiManager.getEbookCoverUrl(apiBook.uuid)
                 )
                 
                 val filesDir = context.filesDir
                 val bookDir = DownloadUtils.getBookDir(filesDir, book)
                 val baseFileName = DownloadUtils.getBaseFileName(book)
                 val fileName = if (isReadAloudMode) "$baseFileName (readaloud).epub" else "$baseFileName.epub"
                 val file = File(bookDir, fileName)
                 if (file.exists()) file.delete()
                 
                 withContext(kotlinx.coroutines.Dispatchers.Main) {
                     error = null
                     isLoading = true
                 }
                 
                 val type = if (isReadAloudMode)
                    com.pekempy.ReadAloudbooks.data.DownloadManager.DownloadType.ReadAloud
                 else
                    com.pekempy.ReadAloudbooks.data.DownloadManager.DownloadType.Ebook

                 DownloadService.startDownload(context, book, type)
                 
             } catch (e: Exception) {
                 e.printStackTrace()
             }
        }
    }
}
