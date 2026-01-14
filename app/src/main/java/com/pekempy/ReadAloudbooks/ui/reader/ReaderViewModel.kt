package com.pekempy.ReadAloudbooks.ui.reader

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pekempy.ReadAloudbooks.data.UserPreferencesRepository
import com.pekempy.ReadAloudbooks.data.api.AppContainer
import com.pekempy.ReadAloudbooks.data.UnifiedProgress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileInputStream
import android.webkit.WebResourceResponse
import com.pekempy.ReadAloudbooks.data.Book
import com.pekempy.ReadAloudbooks.data.api.Position
import com.pekempy.ReadAloudbooks.data.api.Locator as ApiLocator
import com.pekempy.ReadAloudbooks.data.api.Locations
import com.pekempy.ReadAloudbooks.util.DownloadUtils

import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.publication.services.search.SearchService
import org.readium.r2.shared.publication.services.search.SearchIterator
import org.readium.r2.shared.publication.services.search.SearchError
import org.readium.r2.shared.publication.LocatorCollection
import org.readium.r2.shared.publication.services.search.search

class ReaderViewModel(
    private val repository: UserPreferencesRepository
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

    var publication by mutableStateOf<Publication?>(null)
    internal var lazyBook: LazyBook? = null
    
    var currentLocator by mutableStateOf<Locator?>(null)
    
    var epubTitle by mutableStateOf("")
    var currentChapterIndex by mutableIntStateOf(0)
    var totalChapters by mutableIntStateOf(0)
    var lastScrollPercent by mutableFloatStateOf(0f)
    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    var settings by mutableStateOf<com.pekempy.ReadAloudbooks.data.UserSettings?>(null)
    private var currentBookId: String? = null
    
    var showTopBar by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var isReadAloudMode by mutableStateOf(false)

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
    
    fun loadEpub(bookId: String, isReadAloud: Boolean) {
        if (currentBookId == bookId && publication != null && isReadAloudMode == isReadAloud) {
            // Already loaded, just check for progress sync
            viewModelScope.launch {
                val progressStr = repository.getBookProgress(bookId).first()
                val progress = UnifiedProgress.fromString(progressStr)
                if (progress != null) {
                    val savedChapter = progress.chapterIndex.coerceIn(0, totalChapters - 1)
                    val savedAudioMs = progress.audioTimestampMs
                    
                    if (isReadAloud && savedAudioMs > 0) {
                        // Current sync logic stays for now
                        val serverPercent = (progress.getOverallProgress() * 100).coerceIn(0f, 100f)
                        val localPercent = (((currentChapterIndex + lastScrollPercent) / totalChapters.coerceAtLeast(1)) * 100).coerceIn(0f, 100f)
                        
                        if (kotlin.math.abs(serverPercent - localPercent) > 5f) {
                            val resolvedElementId = progress.elementId ?: if (savedAudioMs > 0) getElementIdAtTime(savedAudioMs)?.second else null
                            syncConfirmation = SyncConfirmation(
                                newChapterIndex = savedChapter,
                                newScrollPercent = progress.scrollPercent,
                                newAudioMs = savedAudioMs,
                                newElementId = resolvedElementId,
                                progressPercent = serverPercent,
                                localProgressPercent = localPercent,
                                source = "another device"
                            )
                        } else {
                            currentChapterIndex = savedChapter
                            lastScrollPercent = progress.scrollPercent
                            currentAudioPos = savedAudioMs
                            currentHighlightId = progress.elementId ?: if (savedAudioMs > 0) getElementIdAtTime(savedAudioMs)?.second else null
                        }
                    }
                }
            }
            return
        }

        currentBookId = bookId
        isReadAloudMode = isReadAloud
        isLoading = true
        readerInitialized = false
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Load settings
                val globalSettings = repository.userSettings.first()
                val bookSettings = repository.getBookReaderSettings(bookId).first()
                settings = globalSettings.copy(
                    readerFontSize = bookSettings.first ?: globalSettings.readerFontSize,
                    readerTheme = bookSettings.second ?: globalSettings.readerTheme,
                    readerFontFamily = bookSettings.third ?: globalSettings.readerFontFamily
                )
                
                // Get book details
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

                // Open with Readium
                val result = AppContainer.readiumEngine.open(file)
                val pub = result.getOrElse { throw it }
                
                withContext(Dispatchers.Main) {
                    publication = pub
                    epubTitle = pub.metadata.title ?: "Unknown Title"
                    
                    val spineHrefs = pub.readingOrder.map { it.href.toString() }
                    val resources = mutableMapOf<String, String>()
                    val mediaTypes = mutableMapOf<String, String>()
                    val spineTitles = mutableMapOf<String, String>()
                    
                    pub.readingOrder.forEach { link ->
                        val hrefStr = link.href.toString()
                        resources[hrefStr] = hrefStr
                        link.mediaType?.toString()?.let { mediaTypes[hrefStr] = it }
                        link.title?.let { spineTitles[hrefStr] = it }
                    }
                    
                    lazyBook = LazyBook(epubTitle, spineHrefs, resources, mediaTypes, spineTitles)
                    totalChapters = spineHrefs.size
                    
                    // Restore progress
                    val progressStr = repository.getBookProgress(bookId).first()
                    val progress = UnifiedProgress.fromString(progressStr)
                    if (progress != null) {
                        currentChapterIndex = progress.chapterIndex.coerceIn(0, totalChapters - 1)
                        lastScrollPercent = progress.scrollPercent
                        currentHighlightId = progress.elementId
                        if (progress.audioTimestampMs > 0) {
                            currentAudioPos = progress.audioTimestampMs
                        }
                        
                        // Create initial locator
                        val href = spineHrefs[currentChapterIndex]
                        currentLocator = Locator(
                            href = Url(href)!!,
                            mediaType = MediaType(mediaTypes[href] ?: "text/html") ?: MediaType.HTML,
                            locations = Locator.Locations(
                                progression = lastScrollPercent.toDouble()
                            )
                        )
                    } else if (spineHrefs.isNotEmpty()) {
                        val href = spineHrefs[0]
                        currentLocator = Locator(
                            href = Url(href)!!,
                            mediaType = MediaType(mediaTypes[href] ?: "text/html") ?: MediaType.HTML
                        )
                    }
                }

                // Media Overlays (SMIL) handling if in read-aloud mode
                if (pub.readingOrder.any { it.properties["media-overlay"] != null }) {
                    // Extract SMIL data for sync
                    val segmentsMap = mutableMapOf<String, List<SyncSegment>>()
                    pub.readingOrder.forEach { link ->
                        if (link.properties["media-overlay"] != null) {
                            // Readium 3.x simplifies Media Overlay access
                        }
                    }
                    
                    // Re-populating syncData for now to avoid breaking AudioViewModel
                    // This is temporary until AudioViewModel is also refactored
                    loadSmilLegacy(pub)
                }

                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    error = e.message
                    isLoading = false
                }
            }
        }
    }

    private suspend fun loadSmilLegacy(pub: Publication) {
        val smilMap = mutableMapOf<String, List<SyncSegment>>()
        val offsetMap = mutableMapOf<String, Double>()
        var totalOffset = 0.0

        for (link in pub.readingOrder) {
            val href = link.href.toString()
            // Find SMIL resource for this link
            val smilHref = link.properties["media-overlay"] as? String
            
            if (smilHref != null) {
                val smilUrl = Url(smilHref)
                val smilLink = smilUrl?.let { pub.linkWithHref(it) }
                if (smilLink != null) {
                    try {
                            val resource = pub.get(smilLink)
                            val bytes = resource?.read()?.getOrElse { null }
                            val smilContent = bytes?.decodeToString() ?: ""
                            if (smilContent.isNotEmpty()) {
                            val segments = parseSmil(smilContent, href)
                            if (segments.isNotEmpty()) {
                                smilMap[href] = segments
                                offsetMap[href] = totalOffset
                                totalOffset += segments.sumOf { it.clipEnd - it.clipBegin }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ReaderViewModel", "Failed to parse SMIL for $href: ${e.message}")
                    }
                }
            }
        }
        
        withContext(Dispatchers.Main) {
            syncData = smilMap
            chapterOffsets = offsetMap
        }
    }

    fun saveProgress() {
        saveProgress(currentChapterIndex, lastScrollPercent, if (isReadAloudMode) currentAudioPos else 0L, currentHighlightId)
    }

    fun onLocatorChange(locator: Locator) {
        currentLocator = locator
        val href = locator.href.toString()
        val index = lazyBook?.spineHrefs?.indexOfFirst { it == href || it.endsWith("/$href") || href.endsWith("/$it") } ?: -1
        if (index >= 0) {
            currentChapterIndex = index
            lastScrollPercent = locator.locations.progression?.toFloat() ?: 0f
            saveProgress(index, lastScrollPercent)
        }
    }

    fun saveProgress(chapterIndex: Int, scrollPercent: Float, audioTimestampMs: Long? = null, elementId: String? = null) {
        if (isLoading || !readerInitialized) {
            android.util.Log.d("ReaderViewModel", "Ignoring saveProgress: loading=$isLoading, initialized=$readerInitialized")
            return
        }
        
        lastScrollPercent = scrollPercent
        if (elementId != null && !isReadAloudMode) {
            currentHighlightId = elementId
        }
        if (audioTimestampMs != null) {
            currentAudioPos = audioTimestampMs
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
            
            val mediaTypeStr = lazyBook?.mediaTypes?.get(href) ?: "application/xhtml+xml"
            val mediaType = MediaType(mediaTypeStr) ?: MediaType.HTML

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
                mediaType = mediaType.toString()
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
            android.util.Log.d("ReaderSync", "Saved JSON progress: $progress")
            
            try {
                val pos = progress.toPosition()
                AppContainer.apiClientManager.getApi().updatePosition(bookId, pos)
                android.util.Log.d("ReaderSync", "Synced position to server: href=$href")
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 409) {
                    android.util.Log.i("ReaderSync", "Server has newer or same position (409). skipping.")
                } else {
                    android.util.Log.w("ReaderSync", "Failed to sync to server: ${e.message}")
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

    fun changeChapter(index: Int, audioPosMs: Long? = null) {
        val href = lazyBook?.spineHrefs?.getOrNull(index) ?: return
        val mediaTypeStr = lazyBook?.mediaTypes?.get(href) ?: "text/html"
        
        currentChapterIndex = index
        currentHighlightId = null
        currentLocator = Locator(
            href = Url(href)!!,
            mediaType = MediaType(mediaTypeStr) ?: MediaType.HTML
        )
        saveProgress(index, 0f, audioPosMs)
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

    fun getResourceResponse(href: String): WebResourceResponse? {
        val pub = publication ?: return null
        
        val cleanHref = href.substringAfter("https://epub-internal/")
            .substringBefore("?")
            .substringBefore("#")

        val url = Url(cleanHref) ?: return null
        val link = pub.linkWithHref(url) ?: return null
        val type = link.mediaType?.toString() ?: "application/octet-stream"
        
        return try {
            val resource = pub.get(link)
            val bytes = runBlocking { resource?.read()?.getOrNull() }
            val inputStream = bytes?.inputStream() ?: return null
            WebResourceResponse(type, null, inputStream)
        } catch (e: Exception) {
            null
        }
    }

    fun getCurrentChapterHtml(): String? {
        val pub = publication ?: return null
        if (currentChapterIndex !in pub.readingOrder.indices) return null
        
        val link = pub.readingOrder[currentChapterIndex]
        return try {
            val resource = pub.get(link)
            val bytes = runBlocking { resource?.read()?.getOrNull() }
            val raw = bytes?.decodeToString() ?: return null
            raw.replace(Regex("<\\?xml[^>]*\\?>", RegexOption.IGNORE_CASE), "").trim()
        } catch (e: Exception) {
            null
        }
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
        val matchIndex: Int,
        val locator: Locator
    )
    
    var searchResults by mutableStateOf<List<SearchResult>>(emptyList())
    var isSearching by mutableStateOf(false)
    var searchJob: Job? = null
    var activeSearchHighlight by mutableStateOf<String?>(null)
    var activeSearchMatchIndex by mutableIntStateOf(0)
    
    fun search(query: String) {
        searchJob?.cancel()
        if (query.length < 2) {
            searchResults = emptyList()
            return
        }
        
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            isSearching = true
            val results = mutableListOf<SearchResult>()
            val pub = publication ?: return@launch
            
            android.util.Log.d("ReaderViewModel", "Search: Starting search for '$query'")
            
            val searchTry = pub.search(query)
            android.util.Log.d("ReaderViewModel", "Search: searchTry = $searchTry, isSuccess = ${searchTry is Try.Success<*, *>}")
            
            val iterator = if (searchTry is Try.Success<*, *>) {
                searchTry.value as SearchIterator
            } else {
                android.util.Log.e("ReaderViewModel", "Search: Failed to get iterator - $searchTry")
                isSearching = false
                return@launch 
            }
            
            try {
                var matchCount = 0
                while (isActive && matchCount < 50) {
                    val nextTry = iterator.next()
                    android.util.Log.d("ReaderViewModel", "Search: nextTry = $nextTry")
                    
                    val collection = if (nextTry is Try.Success<*, *>) {
                        nextTry.value as? LocatorCollection ?: break
                    } else {
                        android.util.Log.d("ReaderViewModel", "Search: nextTry was not success, breaking")
                        break
                    }
                    
                    android.util.Log.d("ReaderViewModel", "Search: collection has ${collection.locators.size} locators")
                    if (collection.locators.isEmpty()) break
                    
                    for (locator in collection.locators) {
                        if (!isActive) break
                        val chapterIndex = pub.readingOrder.indexOfFirst { it.href == locator.href }
                        val chapterTitle = locator.title ?: pub.readingOrder.getOrNull(chapterIndex)?.title ?: "Chapter ${chapterIndex + 1}"
                        
                        val before = locator.text.before ?: ""
                        val highlight = locator.text.highlight ?: ""
                        val after = locator.text.after ?: ""
                        val snippet = if (after.isNotEmpty()) {
                            "...$before$highlight$after..."
                        } else {
                            highlight.ifEmpty { "Match found" }
                        }
                            
                        results.add(SearchResult(
                            chapterIndex = if (chapterIndex >= 0) chapterIndex else 0,
                            title = chapterTitle,
                            textSnippet = snippet,
                            matchIndex = matchCount,
                            locator = locator
                        ))
                        matchCount++
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ReaderViewModel", "Search: Exception", e)
                e.printStackTrace()
            } finally {
                iterator.close()
            }
            
            android.util.Log.d("ReaderViewModel", "Search: Found ${results.size} results")
            
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
        currentChapterIndex = result.chapterIndex
        currentLocator = result.locator
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
    }

    override fun onCleared() {
        super.onCleared()
        try {
            publication?.close()
        } catch (e: Exception) {}
        publication = null
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
                    
                 com.pekempy.ReadAloudbooks.data.DownloadManager.download(book, filesDir, type)
                 
             } catch (e: Exception) {
                 e.printStackTrace()
             }
        }
    }
}
