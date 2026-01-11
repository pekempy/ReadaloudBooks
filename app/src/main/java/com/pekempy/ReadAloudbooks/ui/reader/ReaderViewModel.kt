package com.pekempy.ReadAloudbooks.ui.reader

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pekempy.ReadAloudbooks.data.UserPreferencesRepository
import com.pekempy.ReadAloudbooks.data.api.AppContainer
import com.pekempy.ReadAloudbooks.data.UnifiedProgress
import nl.siegmann.epublib.epub.EpubReader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileInputStream
import android.webkit.WebResourceResponse
import com.pekempy.ReadAloudbooks.data.Book
import com.pekempy.ReadAloudbooks.data.api.Position
import com.pekempy.ReadAloudbooks.util.DownloadUtils

class ReaderViewModel(
    private val repository: UserPreferencesRepository
) : ViewModel() {

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
        val source: String
    )
    var syncConfirmation by mutableStateOf<SyncConfirmation?>(null)
    
    var pendingAnchorId = mutableStateOf<String?>(null)
    
    fun loadEpub(bookId: String, isReadAloud: Boolean) {
        if (currentBookId == bookId && lazyBook != null && isReadAloudMode == isReadAloud) {
            viewModelScope.launch {
                val progressStr = repository.getBookProgress(bookId).first()
                val progress = UnifiedProgress.fromString(progressStr)
                if (progress != null) {
                    val savedChapter = progress.chapterIndex.coerceIn(0, totalChapters - 1)
                    val savedAudioMs = progress.audioTimestampMs
                    
                    if (isReadAloud && savedAudioMs > 0) {
                        val diff = savedAudioMs - currentAudioPos
                        val fiveMinutesMs = 5 * 60 * 1000L
                        
                        val isAtStart = currentAudioPos < 5000L
                        
                        if ((diff < 0 && diff >= -fiveMinutesMs) || (isAtStart && diff > 0)) {
                            android.util.Log.d("ReaderViewModel", "Auto-syncing position jump: $diff ms (isAtStart=$isAtStart)")
                            currentChapterIndex = savedChapter
                            lastScrollPercent = progress.scrollPercent
                            currentAudioPos = savedAudioMs
                            currentHighlightId = progress.elementId ?: if (savedAudioMs > 0) getElementIdAtTime(savedAudioMs)?.second else null
                        } else if (kotlin.math.abs(diff) > 5000) {
                            android.util.Log.d("ReaderViewModel", "Showing sync confirmation for jump: $diff ms")
                            val resolvedElementId = progress.elementId ?: if (savedAudioMs > 0) getElementIdAtTime(savedAudioMs)?.second else null
                            syncConfirmation = SyncConfirmation(
                                newChapterIndex = savedChapter,
                                newScrollPercent = progress.scrollPercent,
                                newAudioMs = savedAudioMs,
                                newElementId = resolvedElementId,
                                progressPercent = (savedAudioMs.toFloat() / (if (totalChapters > 0) totalChapters else 1)) * 100,
                                source = "another device"
                            )
                        }
                    }
                }
            }
            return
        }
        currentBookId = bookId
        isReadAloudMode = isReadAloud
        
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
                val progress = UnifiedProgress.fromString(progressStr)
                var savedAudioMs: Long? = null
                if (progress != null) {
                    currentChapterIndex = progress.chapterIndex.coerceIn(0, totalChapters - 1)
                    lastScrollPercent = progress.scrollPercent
                    currentHighlightId = progress.elementId
                    savedAudioMs = if (progress.audioTimestampMs > 0) progress.audioTimestampMs else null
                }

                if (resolvedOverlayMap.isNotEmpty()) {
                    val segmentsMap = java.util.concurrent.ConcurrentHashMap<String, List<SyncSegment>>()
                    
                    viewModelScope.launch(Dispatchers.IO) {
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

                        val sortedOffsets = mutableMapOf<String, Double>()
                        var currentOffset = 0.0
                        spineHrefs.forEach { chapterHref ->
                            val smilHref = resolvedOverlayMap[chapterHref] ?: return@forEach
                            sortedOffsets[chapterHref] = currentOffset
                            val segments = segmentsMap[chapterHref]
                            val dur = segments?.sumOf { it.clipEnd - it.clipBegin } ?: 0.0
                            currentOffset += dur
                        }
                        
                        syncData = segmentsMap.toMap()
                        if (chapterOffsets.isEmpty()) {
                            chapterOffsets = sortedOffsets
                        }
                        
                        if (savedAudioMs != null && savedAudioMs > 0) {
                            val result = getElementIdAtTime(savedAudioMs)
                            if (result != null) {
                                currentChapterIndex = result.first
                                currentHighlightId = result.second
                                android.util.Log.d("ReaderSync", "Progress restored via priority Audio MS: ${result.first} / ${result.second} (${savedAudioMs}ms)")
                            }
                        } else if (currentHighlightId != null) {
                            android.util.Log.d("ReaderSync", "Progress restored via Element ID: $currentHighlightId")
                        } else {
                            android.util.Log.d("ReaderSync", "Progress remaining on Chapter Index: $currentChapterIndex")
                        }
                        
                        try {
                            val serverPos = AppContainer.apiClientManager.getApi().getPosition(bookId)
                            if (serverPos != null) {
                                val serverProgress = UnifiedProgress.fromString(serverPos.location)
                                val localLastUpdated = progress?.lastUpdated ?: 0L
                                
                                if (serverProgress != null && serverProgress.lastUpdated > localLastUpdated) {
                                    android.util.Log.d("ReaderSync", "Found newer server progress: ${serverProgress.lastUpdated} > $localLastUpdated")
                                    
                                    val savedChapter = serverProgress.chapterIndex
                                    val savedAudioMs = serverProgress.audioTimestampMs
                                    val resolvedElementId = serverProgress.elementId ?: if (savedAudioMs > 0) getElementIdAtTime(savedAudioMs)?.second else null
                                    
                                    withContext(Dispatchers.Main) {
                                        syncConfirmation = SyncConfirmation(
                                            newChapterIndex = savedChapter,
                                            newScrollPercent = serverProgress.scrollPercent,
                                            newAudioMs = savedAudioMs,
                                            newElementId = resolvedElementId,
                                            progressPercent = serverPos.percentage.toFloat() * 100,
                                            source = "Storyteller Server"
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("ReaderSync", "Failed to fetch server progress: ${e.message}")
                        }
                        
                        android.util.Log.d("ReaderSync", "SMIL data background-loaded for ${segmentsMap.size} chapters.")
                    }
                }

                isLoading = false
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
        lastScrollPercent = scrollPercent
        if (elementId != null && !isReadAloudMode) {
            currentHighlightId = elementId
        }
        if (audioPosMs != null) {
            currentAudioPos = audioPosMs
        }
        
        viewModelScope.launch {
            val bookId = currentBookId ?: return@launch
            
            val actualAudioPos = if (isReadAloudMode) {
                currentAudioPos
            } else {
                audioPosMs ?: 0L
            }

            val finalElementId = elementId ?: if (actualAudioPos > 0) {
                currentHighlightId ?: getElementIdAtTime(actualAudioPos)?.second
            } else {
                currentHighlightId
            }
            
            val progress = UnifiedProgress(
                chapterIndex = chapterIndex,
                elementId = finalElementId,
                audioTimestampMs = actualAudioPos,
                scrollPercent = scrollPercent,
                lastUpdated = System.currentTimeMillis(),
                totalChapters = totalChapters
            )
            
            
            repository.saveBookProgress(bookId, progress.toString())
            android.util.Log.d("ReaderSync", "Saved unified progress: ch=$chapterIndex, elem=$elementId, audio=${actualAudioPos}ms, scroll=$scrollPercent")
            

            try {
                val percentage = ((chapterIndex + scrollPercent) / totalChapters.coerceAtLeast(1)).toDouble().coerceIn(0.0, 1.0)
                val position = Position(
                    percentage = percentage,
                    timestamp = System.currentTimeMillis(),
                    location = progress.toString()
                )
                AppContainer.apiClientManager.getApi().updatePosition(bookId, position)
                android.util.Log.d("ReaderSync", "Synced position to server: ${(percentage * 100).toInt()}%")
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
        currentChapterIndex = index
        currentHighlightId = null
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
        val relativePos = segments.find { it.id == elementId }?.clipBegin ?: return null
        val offset = chapterOffsets[href] ?: 0.0
        return offset + relativePos
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
    }

    override fun onCleared() {
        super.onCleared()
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
                     hasReadAloud = apiBook.ReadAloud != null,
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
