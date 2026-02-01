package com.pekempy.ReadAloudbooks.ui.player

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.MediaMetadata
import com.pekempy.ReadAloudbooks.data.Book
import com.pekempy.ReadAloudbooks.data.UserPreferencesRepository
import com.pekempy.ReadAloudbooks.data.api.AppContainer
import com.pekempy.ReadAloudbooks.util.DownloadUtils
import com.pekempy.ReadAloudbooks.util.FormatUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import android.net.Uri
import android.content.ComponentName
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.TimeUnit
import com.pekempy.ReadAloudbooks.data.UnifiedProgress

class ReadAloudAudioViewModel(private val repository: UserPreferencesRepository) : ViewModel() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var player: Player? = null
    
    var currentBook by mutableStateOf<Book?>(null)
    var isPlaying by mutableStateOf(false)
    var currentPosition by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var playbackSpeed by mutableFloatStateOf(1.0f)
    var isLoading by mutableStateOf(true)
    var chapters by mutableStateOf<List<Chapter>>(emptyList())
    var currentChapterIndex by mutableIntStateOf(-1)
    var sleepTimerRemaining by mutableLongStateOf(0L)
    var error by mutableStateOf<String?>(null)
    var sleepTimerFinishChapter by mutableStateOf(false)
    var currentElementId by mutableStateOf<String?>(null)
        private set
    
    var audioChapterOffsets by mutableStateOf<Map<String, Double>>(emptyMap())
        private set
    
    data class SyncConfirmation(
        val newPositionMs: Long,
        val progressPercent: Float,
        val localProgressPercent: Float,
        val source: String
    )
    var syncConfirmation by mutableStateOf<SyncConfirmation?>(null)
    
    private var lastSyncCheckTime = 0L
    private var sleepTimerJob: Job? = null
    private var progressJob: Job? = null
    private var loadJob: Job? = null
    private var filesDir: File? = null
    private var appContext: android.content.Context? = null
    private var currentZipFile: ZipFile? = null
    private var pendingSeekPosition: Long? = null
    private var loadedSpineHrefs: List<String> = emptyList()

    // Callback for manual seek/skip actions (for "Return to Reading Position" feature)
    var onManualSeek: ((previousPosition: Long) -> Unit)? = null

    data class ClipSegment(
        val elementId: String, 
        val audioSrc: String,
        val audioFile: File,
        val clipBeginMs: Long,
        var clipEndMs: Long,
        val cumulativeStartMs: Long,
        val chapterIndex: Int,
        val subSegments: MutableList<SubSegment> = mutableListOf()
    )
    
    data class SubSegment(
        val elementId: String,
        val relativeStartMs: Long,
        val durationMs: Long
    )
    
    data class Chapter(
        val title: String,
        val startOffset: Long,
        val duration: Long
    )
    
    private val clipSegments = mutableListOf<ClipSegment>()
    private val extractedAudioFiles = mutableMapOf<String, File>() 

    fun initializePlayer(context: android.content.Context) {
        this.appContext = context.applicationContext
        this.filesDir = context.filesDir
        
        if (player == null) {
            val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture?.addListener({
                val controller = controllerFuture?.get() ?: return@addListener
                this.player = controller
                
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        this@ReadAloudAudioViewModel.isPlaying = playing
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val index = controller.currentMediaItemIndex
                        if (index in clipSegments.indices) {
                            val clip = clipSegments[index]
                            currentElementId = clip.elementId
                            currentChapterIndex = clip.chapterIndex
                            android.util.Log.d("ReadAloudAudioVM", "Transition to element: $currentElementId, Chapter: $currentChapterIndex")
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            pendingSeekPosition?.let { seekPos ->
                                val targetPos = seekPos.coerceIn(0, duration)
                                this@ReadAloudAudioViewModel.seekTo(targetPos)
                                pendingSeekPosition = null
                            }
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        android.util.Log.e("ReadAloudAudioVM", "Playback error: ${error.message}")
                        this@ReadAloudAudioViewModel.error = "Playback error: ${error.message}"
                    }
                })
                startProgressUpdate()
            }, MoreExecutors.directExecutor())
        }
    }

    fun loadBook(
        bookId: String,
        smilData: Map<String, List<com.pekempy.ReadAloudbooks.ui.reader.ReaderViewModel.SyncSegment>>,
        chapterOffsets: Map<String, Double>,
        spineHrefs: List<String>,
        spineTitles: Map<String, String> = emptyMap(),
        autoPlay: Boolean = true
    ) {
        if (currentBook?.id == bookId && player != null && player?.playbackState != androidx.media3.common.Player.STATE_IDLE) {
            // If autoPlay is false and player is currently playing, pause it
            if (!autoPlay && player?.isPlaying == true) {
                android.util.Log.d("ReadAloudAudioVM", "Book already loaded but autoPlay=false, pausing playback")
                player?.pause()
            }
            if (syncConfirmation != null || System.currentTimeMillis() - lastSyncCheckTime < 10000) {
                isLoading = false
                return
            }
            android.util.Log.d("ReadAloudAudioVM", "Book $bookId already loaded. Checking for external progress updates...")
            lastSyncCheckTime = System.currentTimeMillis()
            viewModelScope.launch(Dispatchers.Main) {
                val progressStr = repository.getBookProgress(bookId).first()
                val progress = com.pekempy.ReadAloudbooks.data.UnifiedProgress.fromString(progressStr)
                
                try {
                    val serverPos = AppContainer.apiClientManager.getApi().getPosition(bookId)
                    if (serverPos != null) {
                        val serverProgress = com.pekempy.ReadAloudbooks.data.UnifiedProgress.fromPosition(serverPos)
                        val localLastUpdated = progress?.lastUpdated ?: 0L
                        
                        if (serverProgress.lastUpdated > localLastUpdated) {
                            if (duration > 0) {
                            val serverPercent = (serverProgress.getOverallProgress() * 100).coerceIn(0f, 100f)
                            
                            val localUnified = UnifiedProgress(
                                chapterIndex = currentChapterIndex,
                                elementId = currentElementId,
                                audioTimestampMs = currentPosition,
                                scrollPercent = 0f,
                                lastUpdated = progress?.lastUpdated ?: 0L,
                                totalChapters = loadedSpineHrefs.size.coerceAtLeast(1),
                                totalDurationMs = duration
                            )
                            val localPercent = (localUnified.getOverallProgress() * 100).coerceIn(0f, 100f)
                            
                            if (kotlin.math.abs(serverPercent - localPercent) > 5f) {
                                android.util.Log.d("ReadAloudAudioVM", "Server progress is newer and significantly different. Prompting.")
                                syncConfirmation = SyncConfirmation(
                                    newPositionMs = serverProgress.audioTimestampMs,
                                    progressPercent = serverPercent.coerceIn(0f, 100f),
                                    localProgressPercent = localPercent.coerceIn(0f, 100f),
                                    source = "Storyteller Server"
                                )
                            }
 else {
                                    android.util.Log.d("ReadAloudAudioVM", "Server progress is newer but minor. Auto-syncing.")
                                    seekTo(serverProgress.audioTimestampMs)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ReadAloudAudioVM", "Failed to check server progress: ${e.message}")
                }
                isLoading = false
            }
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            error = null
            
            try {
                val apiManager = AppContainer.apiClientManager
                val apiBook = apiManager.getApi().getBookDetails(bookId)
                
                val apiSeries = apiBook.series?.firstOrNull()
                val apiCollection = apiBook.collections?.firstOrNull()
                
                val book = Book(
                    id = apiBook.uuid,
                    title = apiBook.title,
                    author = apiBook.authors.joinToString(", ") { it.name },
                    narrator = apiBook.narrators?.joinToString(", ") { it.name },
                    coverUrl = apiManager.getCoverUrl(apiBook.uuid),
                    audiobookCoverUrl = apiManager.getAudiobookCoverUrl(apiBook.uuid),
                    ebookCoverUrl = apiManager.getEbookCoverUrl(apiBook.uuid),
                    description = apiBook.description,
                    series = apiSeries?.name ?: apiCollection?.name,
                    seriesIndex = apiBook.series?.firstNotNullOfOrNull { it.seriesIndex }
                        ?: apiBook.collections?.firstNotNullOfOrNull { it.seriesIndex }
                )
                
                withContext(Dispatchers.Main) {
                    currentBook = book
                }
                repository.saveLastActiveBook(bookId, "readaloud")
                
                val bookDir = DownloadUtils.getBookDir(filesDir!!, book)
                val baseFileName = DownloadUtils.getBaseFileName(book)
                val epubFile = File(bookDir, "$baseFileName (readaloud).epub")
                
                if (!epubFile.exists()) {
                    throw Exception("Read-aloud EPUB not found on disk")
                }
                
                currentZipFile = ZipFile(epubFile)
                
                val localExtractedFiles = mutableMapOf<String, File>()
                extractAudioFiles(smilData, bookId, localExtractedFiles)
                
                val localClipSegments = mutableListOf<ClipSegment>()
                val localChapterOffsets = mutableMapOf<String, Long>()
                createClippedSegments(smilData, spineHrefs, localExtractedFiles, localClipSegments, localChapterOffsets)
                
                val calculatedDuration = localClipSegments.sumOf { it.clipEndMs - it.clipBeginMs }
                android.util.Log.i("ReadAloudAudioVM", "TOTAL BOOK DURATION: ${FormatUtils.formatTime(calculatedDuration)} ($calculatedDuration ms)")
                
                val chaptersFromXml = parseChaptersXml(currentZipFile!!)
                val localChaptersList = if (chaptersFromXml != null && chaptersFromXml.isNotEmpty()) {
                    android.util.Log.i("ReadAloudAudioVM", "Using ${chaptersFromXml.size} chapters from misc/chapters.xml")
                    chaptersFromXml
                } else {
                    createChaptersList(localChapterOffsets, spineHrefs, spineTitles, calculatedDuration)
                }
                
                val mediaMetadataBuilder = MediaMetadata.Builder()
                    .setTitle(book.title)
                    .setArtist(book.author)
                    .setSubtitle(book.narrator)
                
                val coverUrl = book.audiobookCoverUrl ?: book.coverUrl
                if (!coverUrl.isNullOrEmpty()) {
                    try {
                        mediaMetadataBuilder.setArtworkUri(Uri.parse(coverUrl))
                    } catch (e: Exception) {
                        android.util.Log.w("ReadAloudAudioVM", "Failed to parse cover URL: $coverUrl")
                    }
                }
                val mediaMetadata = mediaMetadataBuilder.build()
                
                android.util.Log.d("ReadAloudAudioVM", "Building media items from ${localClipSegments.size} clips")
                
                val mediaItems = localClipSegments.map { clip ->
                    val chapter = localChaptersList.findLast { it.startOffset <= clip.cumulativeStartMs }
                    val extras = android.os.Bundle().apply {
                        putString("bookId", bookId)
                        putLong("globalDurationMs", calculatedDuration)
                        putLong("cumulativeStartMs", clip.cumulativeStartMs)
                        putBoolean("isAudiobookMode", true)
                    }
                    val itemMetadata = mediaMetadataBuilder
                        .setTitle(book.title)
                        .setSubtitle(chapter?.title ?: book.author)
                        .setExtras(extras)
                        .build()

                    MediaItem.Builder()
                        .setUri(Uri.fromFile(clip.audioFile))
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(clip.clipBeginMs)
                                .setEndPositionMs(clip.clipEndMs)
                                .build()
                        )
                        .setMediaMetadata(itemMetadata)
                        .build()
                }
                
                android.util.Log.d("ReadAloudAudioVM", "Built ${mediaItems.size} media items, setting to player...")
                
                var connectionWaitTime = 0
                while (player == null && connectionWaitTime < 100) { 
                    delay(100)
                    connectionWaitTime++
                }
                
                if (player == null) {
                    throw Exception("Timed out waiting for audio service connection")
                }
                
                withContext(Dispatchers.Main) {
                    clipSegments.clear()
                    clipSegments.addAll(localClipSegments)
                    extractedAudioFiles.clear()
                    extractedAudioFiles.putAll(localExtractedFiles)
                    hrefToAudioOffset.clear()
                    hrefToAudioOffset.putAll(localChapterOffsets)
                    audioChapterOffsets = localChapterOffsets.mapValues { it.value / 1000.0 }
                    chapters = localChaptersList
                    duration = calculatedDuration
                    loadedSpineHrefs = spineHrefs
                    
                    val p = player
                    val isAlreadyPlayingUs = if (p != null && p.mediaItemCount > 0) {
                        val currentItem = p.getMediaItemAt(0)
                        val playingBookId = currentItem.mediaMetadata.extras?.getString("bookId")
                        playingBookId == bookId && p.playbackState != Player.STATE_IDLE
                    } else false

                    if (!isAlreadyPlayingUs) {
                        android.util.Log.d("ReadAloudAudioVM", "Player not playing this book ($bookId). Setting media items...")
                        player?.setMediaItems(mediaItems)

                        // Load progress FIRST to set pendingSeekPosition before player becomes READY
                        loadProgress(bookId)

                        player?.prepare()
                    } else {
                        android.util.Log.i("ReadAloudAudioVM", "RE-ADOPTING active session for book $bookId")
                        p?.let { player ->
                            val index = player.currentMediaItemIndex
                            val posInClip = player.currentPosition
                            if (index >= 0 && index < clipSegments.size) {
                                val clip = clipSegments[index]
                                currentPosition = clip.cumulativeStartMs + posInClip
                                currentChapterIndex = clip.chapterIndex
                                currentElementId = clip.elementId
                                android.util.Log.d("ReadAloudAudioVM", "Synced from player: chap=$currentChapterIndex, pos=$currentPosition, el=$currentElementId")
                            }
                        }
                        loadProgress(bookId, skipSeek = false)
                    }

                    if (autoPlay && player?.isPlaying == false) {
                        player?.play()
                        android.util.Log.d("ReadAloudAudioVM", "Auto-starting playback")
                    }
                }
                
                val perBookSpeed = repository.getBookPlaybackSpeed(bookId).first()
                if (perBookSpeed != null) {
                    withContext(Dispatchers.Main) {
                        setSpeed(perBookSpeed)
                    }
                } else {
                    val settings = repository.userSettings.first()
                    withContext(Dispatchers.Main) {
                        setSpeed(settings.playbackSpeed)
                    }
                }
                
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    e.printStackTrace()
                    error = "Error: ${e.message ?: e.javaClass.simpleName}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    fun restoreBook(bookId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apiManager = AppContainer.apiClientManager
                val apiBook = apiManager.getApi().getBookDetails(bookId)
                
                val apiSeries = apiBook.series?.firstOrNull()
                val apiCollection = apiBook.collections?.firstOrNull()
                
                val book = Book(
                    id = apiBook.uuid,
                    title = apiBook.title,
                    author = apiBook.authors.joinToString(", ") { it.name },
                    narrator = apiBook.narrators?.joinToString(", ") { it.name },
                    coverUrl = apiManager.getCoverUrl(apiBook.uuid),
                    audiobookCoverUrl = apiManager.getAudiobookCoverUrl(apiBook.uuid),
                    ebookCoverUrl = apiManager.getEbookCoverUrl(apiBook.uuid),
                    description = apiBook.description,
                    series = apiSeries?.name ?: apiCollection?.name,
                    seriesIndex = apiBook.series?.firstNotNullOfOrNull { it.seriesIndex }
                        ?: apiBook.collections?.firstNotNullOfOrNull { it.seriesIndex }
                )
                
                val progressStr = repository.getBookProgress(bookId).first()
                val progress = com.pekempy.ReadAloudbooks.data.UnifiedProgress.fromString(progressStr)
                
                withContext(Dispatchers.Main) {
                     currentBook = book
                     if (progress != null) {
                         currentPosition = progress.audioTimestampMs
                         duration = progress.totalDurationMs
                         currentChapterIndex = progress.chapterIndex
                         currentElementId = progress.elementId
                     }
                     isLoading = false
                }
            } catch (e: Exception) {}
        }
    }

    private suspend fun extractAudioFiles(
        smilData: Map<String, List<com.pekempy.ReadAloudbooks.ui.reader.ReaderViewModel.SyncSegment>>,
        bookId: String,
        outputMap: MutableMap<String, File>
    ) {
        val zip = currentZipFile ?: return
        
        val tempDir = File(appContext!!.cacheDir, "readaloud_audio/$bookId")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        
        val uniqueAudioSources = mutableSetOf<String>()
        smilData.values.forEach { segments ->
            segments.forEach { segment ->
                uniqueAudioSources.add(segment.audioSrc)
            }
        }
        
        android.util.Log.d("ReadAloudAudioVM", "Found ${uniqueAudioSources.size} unique audio files to extract")
        
        uniqueAudioSources.forEach { audioSrc ->
            val filename = audioSrc.substringAfterLast("/")
            
            android.util.Log.d("ReadAloudAudioVM", "Searching for audio: $audioSrc (filename: $filename)")
            
            val possiblePaths = listOf(
                audioSrc.removePrefix("../"),
                "OEBPS/${audioSrc.removePrefix("../")}",
                "Audio/$filename",
                "OEBPS/Audio/$filename",
                audioSrc
            )
            
            var entry: java.util.zip.ZipEntry? = null
            var foundPath: String? = null
            
            for (path in possiblePaths) {
                entry = zip.getEntry(path)
                if (entry != null) {
                    foundPath = path
                    break
                }
            }

            if (entry == null) {
                val allEntries = zip.entries()
                while (allEntries.hasMoreElements()) {
                    val next = allEntries.nextElement()
                    if (next.name.endsWith("/$filename") || next.name == filename) {
                        entry = next
                        foundPath = next.name
                        break
                    }
                }
            }
            
            if (entry == null) {
                android.util.Log.e("ReadAloudAudioVM", "Audio file NOT FOUND: $audioSrc")
                return@forEach
            }
            
            android.util.Log.d("ReadAloudAudioVM", "Found audio at: $foundPath")
            
            val safeName = audioSrc.replace("/", "_").replace("\\", "_").removePrefix(".._")
            val tempFile = File(tempDir, safeName)
            
            if (tempFile.exists() && tempFile.length() > 0) {
                android.util.Log.d("ReadAloudAudioVM", "Using existing file: $audioSrc")
                outputMap[audioSrc] = tempFile
                return@forEach
            }
            
            zip.getInputStream(entry).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            outputMap[audioSrc] = tempFile
            android.util.Log.d("ReadAloudAudioVM", "Extracted: $audioSrc → ${tempFile.name} (${tempFile.length()} bytes)")
        }
        
        android.util.Log.d("ReadAloudAudioVM", "Extracted ${outputMap.size} audio files")
    }
    
    private fun createClippedSegments(
        smilData: Map<String, List<com.pekempy.ReadAloudbooks.ui.reader.ReaderViewModel.SyncSegment>>,
        spineHrefs: List<String>,
        localExtractedFiles: Map<String, File>,
        outputSegments: MutableList<ClipSegment>,
        outputOffsets: MutableMap<String, Long>
    ) {
        var cumulativeOffset = 0L
        
        spineHrefs.forEach { href ->
            outputOffsets[href!!] = cumulativeOffset
            
            val segments = smilData[href] ?: return@forEach
            var currentClip: ClipSegment? = null
            
            segments.forEach { segment ->
                val audioFile = localExtractedFiles[segment.audioSrc] ?: return@forEach
                if (!audioFile.exists()) return@forEach
                
                val clipBeginMs = (segment.clipBegin * 1000).toLong()
                val clipEndMs = (segment.clipEnd * 1000).toLong()
                val durationMs = clipEndMs - clipBeginMs
                if (durationMs <= 0) return@forEach
                
                if (currentClip != null && currentClip!!.audioSrc == segment.audioSrc && 
                    Math.abs(currentClip!!.clipEndMs - clipBeginMs) < 100) {
                    
                    val relativeStart = currentClip!!.clipEndMs - currentClip!!.clipBeginMs
                    currentClip!!.subSegments.add(SubSegment(segment.id, relativeStart, durationMs))
                    currentClip!!.clipEndMs = clipEndMs
                    cumulativeOffset += durationMs
                } else {
                    currentClip = ClipSegment(
                        elementId = segment.id,
                        audioSrc = segment.audioSrc,
                        audioFile = audioFile,
                        clipBeginMs = clipBeginMs,
                        clipEndMs = clipEndMs,
                        cumulativeStartMs = cumulativeOffset,
                        chapterIndex = spineHrefs.indexOf(href)
                    ).apply {
                        subSegments.add(SubSegment(segment.id, 0, durationMs))
                    }
                    outputSegments.add(currentClip!!)
                    cumulativeOffset += durationMs
                }
            }
        }
    }
    
    private val hrefToAudioOffset = mutableMapOf<String, Long>()
    
    private fun createChaptersList(
        chapterOffsets: Map<String, Long>, 
        spineHrefs: List<String>, 
        spineTitles: Map<String, String>,
        totalDuration: Long
    ): List<Chapter> {
        val chapterList = mutableListOf<Chapter>()
        
        val sortedChapters = chapterOffsets.entries.sortedBy { it.value }
        sortedChapters.forEachIndexed { index, entry ->
            val startMs = entry.value
            val nextStartMs = if (index + 1 < sortedChapters.size) {
                sortedChapters[index + 1].value
            } else {
                totalDuration
            }
            val durationMs = nextStartMs - startMs
            
            val chapterTitle = spineTitles[entry.key] ?: entry.key.substringAfterLast("/").substringBeforeLast(".")
            
            chapterList.add(Chapter(chapterTitle, startMs, durationMs))
        }
        
        return chapterList
    }

    private fun parseChaptersXml(zipFile: ZipFile): List<Chapter>? {
        return try {
            var entry = zipFile.getEntry("misc/chapters.xml") 
                ?: zipFile.getEntry("OEBPS/misc/chapters.xml")
                
            if (entry == null) {
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val next = entries.nextElement()
                    if (next.name.endsWith("chapters.xml", ignoreCase = true)) {
                        entry = next
                        break
                    }
                }
            }
            
            if (entry == null) return null
                
            val inputStream = zipFile.getInputStream(entry)
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)

            val chaptersList = mutableListOf<Chapter>()
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name.equals("chapter", ignoreCase = true)) {
                    val title = parser.getAttributeValue(null, "title") ?: "Chapter"
                    val startMs = parser.getAttributeValue(null, "start_ms")?.toLongOrNull() ?: 0L
                    val endMs = parser.getAttributeValue(null, "end_ms")?.toLongOrNull() ?: 0L
                    val duration = endMs - startMs
                    if (duration > 0 || startMs == 0L) {
                        chaptersList.add(Chapter(title, startMs, Math.max(0, duration)))
                    }
                }
                eventType = parser.next()
            }
            inputStream.close()
            if (chaptersList.isEmpty()) return null
            
            chaptersList.sortBy { it.startOffset }
            
            for (i in 0 until chaptersList.size) {
                if (chaptersList[i].duration <= 0) {
                    val nextStart = if (i + 1 < chaptersList.size) chaptersList[i+1].startOffset else duration
                    val fixedChapter = chaptersList[i].copy(duration = Math.max(0, nextStart - chaptersList[i].startOffset))
                    chaptersList[i] = fixedChapter
                }
            }
            
            chaptersList
        } catch (e: Exception) {
            android.util.Log.w("ReadAloudAudioVM", "Failed to parse chapters.xml", e)
            null
        }
    }
    
    private suspend fun loadProgress(bookId: String, skipSeek: Boolean = false) {
        val progressStr = repository.getBookProgress(bookId).first()
        val localProgress = UnifiedProgress.fromString(progressStr)
        var finalProgressToUse = localProgress
        
        try {
            val serverPos = AppContainer.apiClientManager.getApi().getPosition(bookId)
            lastSyncCheckTime = System.currentTimeMillis()
            if (serverPos != null) {
                val serverProgress = UnifiedProgress.fromPosition(serverPos, chapters.size)
                val localLastUpdated = localProgress?.lastUpdated ?: 0L
                
                if (serverProgress.lastUpdated > localLastUpdated) {
                    if (duration > 0) {
                        val serverPercent = (serverProgress.getOverallProgress() * 100).coerceIn(0f, 100f)
                        
                        val localUnified = UnifiedProgress(
                            chapterIndex = localProgress?.chapterIndex ?: 0,
                            elementId = localProgress?.elementId,
                            audioTimestampMs = localProgress?.audioTimestampMs ?: 0L,
                            scrollPercent = localProgress?.scrollPercent ?: 0f,
                            lastUpdated = localProgress?.lastUpdated ?: 0L,
                            totalChapters = chapters.size.coerceAtLeast(1),
                            totalDurationMs = duration
                        )
                        val localPercent = (localUnified.getOverallProgress() * 100).coerceIn(0f, 100f)
                        
                        if (kotlin.math.abs(serverPercent - localPercent) > 5f) {
                            withContext(Dispatchers.Main) {
                                syncConfirmation = SyncConfirmation(
                                    newPositionMs = serverProgress.audioTimestampMs,
                                    progressPercent = serverPercent.coerceIn(0f, 100f),
                                    localProgressPercent = localPercent.coerceIn(0f, 100f),
                                    source = "Storyteller Server"
                                )
                            }
                        }
 else {
                            finalProgressToUse = serverProgress
                        }
                    } else {
                        finalProgressToUse = serverProgress
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ReadAloudAudioVM", "Failed to fetch server progress early: ${e.message}")
        }
        
        finalProgressToUse?.let {
            if (skipSeek) return@let
            val audioMs = it.audioTimestampMs
            
            if (audioMs > 0) {
                val validMs = audioMs.coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
                android.util.Log.d("ReadAloudAudioVM", "Will seek to progress: $validMs ms")
                withContext(Dispatchers.Main) {
                    pendingSeekPosition = validMs
                    currentPosition = validMs
                }
            } else {
                val chapterIndex = it.chapterIndex
                val scrollPercent = it.scrollPercent
                if (chapterIndex in chapters.indices) {
                    val chapter = chapters[chapterIndex]
                    val offset = (chapter.duration * scrollPercent).toLong()
                    val resMs = (chapter.startOffset + offset).coerceIn(0L, duration)
                    withContext(Dispatchers.Main) {
                        pendingSeekPosition = resMs
                        currentPosition = resMs
                    }
                }
            }
        }
    }

    fun setSpeed(speed: Float) {
        playbackSpeed = speed
        player?.setPlaybackSpeed(speed)
        viewModelScope.launch {
            val bookId = currentBook?.id ?: return@launch
            repository.saveBookPlaybackSpeed(bookId, speed)
        }
    }

    fun skipToChapter(index: Int) {
        if (index in chapters.indices) {
            seekTo(chapters[index].startOffset)
        }
    }

    fun togglePlayPause() {
        if (isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        player?.play()
    }

    fun pause() {
        player?.pause()
        saveBookProgress()
    }
    
    fun seekToElement(elementId: String) {
        var foundPosition: Long? = null
        var foundClipIndex: Int? = null
        var foundRelativeMs: Long? = null
        
        for ((index, clip) in clipSegments.withIndex()) {
            if (clip.elementId == elementId) {
                foundPosition = clip.cumulativeStartMs
                foundClipIndex = index
                foundRelativeMs = 0L
                break
            }
            
            val sub = clip.subSegments.find { it.elementId == elementId }
            if (sub != null) {
                foundPosition = clip.cumulativeStartMs + sub.relativeStartMs
                foundClipIndex = index
                foundRelativeMs = sub.relativeStartMs
                break
            }
        }
        
        if (foundPosition != null && foundClipIndex != null && foundRelativeMs != null) {
            android.util.Log.d("ReadAloudAudioVM", "Seeking to element $elementId at clip[$foundClipIndex] + ${foundRelativeMs}ms = $foundPosition ms total")
            
            player?.let { p ->
                if (foundClipIndex < p.mediaItemCount) {
                    p.seekTo(foundClipIndex, foundRelativeMs)
                    
                    currentPosition = foundPosition
                    currentElementId = elementId
                } else {
                    android.util.Log.w("ReadAloudAudioVM", "Clip index $foundClipIndex out of bounds (mediaItemCount=${p.mediaItemCount})")
                }
            }
        } else {
            android.util.Log.w("ReadAloudAudioVM", "Element $elementId not found in timeline")
        }
    }

    fun seekTo(positionMs: Long) {
        val player = player ?: return
        
        val validPosition = positionMs.coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        
        if (validPosition != positionMs) {
            android.util.Log.w("ReadAloudAudioVM", "Clamped seek position from $positionMs to $validPosition (duration: $duration)")
        }
        
        val clipIndex = clipSegments.indexOfFirst { clip ->
            val clipDur = clip.clipEndMs - clip.clipBeginMs
            validPosition >= clip.cumulativeStartMs && 
            validPosition < clip.cumulativeStartMs + clipDur
        }
        
        if (clipIndex >= 0) {
            val clip = clipSegments[clipIndex]
            val offsetInClip = validPosition - clip.cumulativeStartMs
            
            android.util.Log.d("ReadAloudAudioVM", "Seeking to book position $validPosition ms -> Clip #$clipIndex at $offsetInClip ms")
            
            player.seekTo(clipIndex, offsetInClip)
            currentPosition = validPosition
            currentChapterIndex = clip.chapterIndex
            val subMatch = clip.subSegments
                .filter { offsetInClip >= it.relativeStartMs && offsetInClip < it.relativeStartMs + it.durationMs }
                .maxByOrNull { it.relativeStartMs }
            currentElementId = subMatch?.elementId ?: clip.elementId
        } else if (validPosition <= 0) {
            player.seekTo(0, 0)
            currentPosition = 0
        } else if (validPosition >= duration) {
            if (clipSegments.isNotEmpty()) {
                val lastIdx = clipSegments.size - 1
                val lastClip = clipSegments[lastIdx]
                player.seekTo(lastIdx, lastClip.clipEndMs - lastClip.clipBeginMs - 1)
            }
        }
    }

    fun rewind10s() {
        val previousPos = currentPosition
        val newPos = (currentPosition - 10000).coerceAtLeast(0)
        onManualSeek?.invoke(previousPos)
        seekTo(newPos)
    }

    fun forward30s() {
        val previousPos = currentPosition
        val newPos = (currentPosition + 30000).coerceAtMost(duration)
        onManualSeek?.invoke(previousPos)
        seekTo(newPos)
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            var lastSaveTime = 0L
            while (isActive) {
                try {
                    player?.let { p ->
                        val index = p.currentMediaItemIndex
                        val posInClip = p.currentPosition
                        
                        val currentSegments = clipSegments.toList()
                        if (index >= 0 && index < currentSegments.size) {
                            val clip = currentSegments[index]
                            currentPosition = clip.cumulativeStartMs + posInClip
                            currentChapterIndex = clip.chapterIndex
                            
                            val subMatch = clip.subSegments
                                .filter { posInClip >= it.relativeStartMs && posInClip < it.relativeStartMs + it.durationMs }
                                .maxByOrNull { it.relativeStartMs }  
                            
                            if (currentElementId != (subMatch?.elementId ?: clip.elementId)) {
                                currentElementId = subMatch?.elementId ?: clip.elementId
                                android.util.Log.d("ReadAloudAudioVM", "Element changed: $currentElementId at $currentPosition ms")
                            }
                        }
                    }
                    

                    
                    if (isPlaying) {
                        val now = System.currentTimeMillis()
                        if (now - lastSaveTime > 5000) { 
                            saveBookProgress()
                            lastSaveTime = now
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.e("ReadAloudAudioVM", "Error in progress update loop", e)
                }
                delay(100)
            }
        }
    }

    fun applyDefaultSleepTimer() {
        viewModelScope.launch {
            val settings = repository.userSettings.first()
            sleepTimerFinishChapter = settings.sleepTimerFinishChapter
            if (settings.sleepTimerMinutes > 0) {
                setSleepTimer(settings.sleepTimerMinutes)
            }
        }
    }
    
    fun toggleSleepTimerFinishChapter() {
        sleepTimerFinishChapter = !sleepTimerFinishChapter
        viewModelScope.launch {
            repository.updateSleepTimerFinishChapter(sleepTimerFinishChapter)
        }
    }

    var isWaitingForChapterEnd by mutableStateOf(false)
    
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        isWaitingForChapterEnd = false
        if (minutes <= 0) {
            sleepTimerRemaining = 0L
            return
        }
        sleepTimerRemaining = minutes * 60 * 1000L
        
        sleepTimerJob = viewModelScope.launch {
            while (sleepTimerRemaining > 0) {
                delay(1000)
                if (isPlaying) {
                    sleepTimerRemaining -= 1000
                    if (sleepTimerRemaining <= 0) {
                        if (sleepTimerFinishChapter) {
                            android.util.Log.d("ReadAloudAudioVM", "Sleep timer expired, waiting for end of chapter...")
                            isWaitingForChapterEnd = true
                            val startingChapter = currentChapterIndex
                            while (isPlaying && currentChapterIndex == startingChapter && currentPosition < duration - 2000) {
                                delay(1000)
                            }
                        }
                        player?.pause()
                        sleepTimerRemaining = 0
                        isWaitingForChapterEnd = false
                    }
                }
            }
        }
    }

    fun confirmSync() {
        syncConfirmation?.let {
            seekTo(it.newPositionMs)
            syncConfirmation = null
        }
    }

    fun dismissSync() {
        syncConfirmation = null
        saveBookProgress()
    }

    internal fun saveBookProgress() {
        val bookId = currentBook?.id ?: ""
        val pos = currentPosition
        val dur = duration
        if (bookId.isEmpty() || dur <= 0) return

        viewModelScope.launch {
            val chIdx = currentChapterIndex
            val chList = chapters
            val elemId = currentElementId
            
            val currentCh = chList.getOrNull(chIdx)
            val chapterProgress = if (currentCh != null && currentCh.duration > 0) {
                (pos - currentCh.startOffset).toFloat() / currentCh.duration
            } else 0f
            
            val chIdxToUse = chIdx.coerceAtLeast(0)
            val href = loadedSpineHrefs.getOrNull(chIdxToUse) 
                ?: (if (chIdx >= 0 && chList.isNotEmpty()) "chapter_$chIdx" else null)
            
            if (href == null) {
                android.util.Log.w("ReadAloudAudioVM", "Cannot save progress: No valid HREF found for chapter $chIdx")
                return@launch
            }

            val progress = com.pekempy.ReadAloudbooks.data.UnifiedProgress(
                chapterIndex = chIdxToUse,
                elementId = elemId,
                audioTimestampMs = pos,
                scrollPercent = chapterProgress,
                lastUpdated = System.currentTimeMillis(),
                totalChapters = chList.size.coerceAtLeast(1),
                totalDurationMs = dur,
                href = href,
                mediaType = "application/xhtml+xml"
            )
            repository.saveBookProgress(bookId, progress.toString())
            
            try {
                val position = progress.toPosition()
                android.util.Log.d("ReadAloudAudioVM", "Uploading ReadAloud progress: $position")
                AppContainer.apiClientManager.getApi().updatePosition(bookId, position)
                android.util.Log.d("ReadAloudAudioVM", "Successfully synced ReadAloud progress to server")
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 409) {
                    android.util.Log.i("ReadAloudAudioVM", "Server has newer or same ReadAloud position (409). Skipping sync.")
                } else {
                    android.util.Log.w("ReadAloudAudioVM", "Failed to sync ReadAloud progress (HTTP ${e.code()}): ${e.message}")
                }
            } catch (e: Exception) {
                android.util.Log.w("ReadAloudAudioVM", "Failed to sync ReadAloud progress: ${e.message}")
            }
        }
    }

    fun stopPlayback() {
        val bookId = currentBook?.id ?: ""
        val pos = currentPosition
        val dur = duration
        val chIdx = currentChapterIndex
        val chList = chapters
        val elemId = currentElementId
        
        if (bookId.isNotEmpty() && dur > 0) {
            saveBookProgress()
            viewModelScope.launch {
                repository.saveLastActiveBook("", "")
            }
        }

        player?.let {
            it.pause()
            it.stop()
            it.clearMediaItems()
        }
        
        currentBook = null
        currentPosition = 0
        duration = 0
        chapters = emptyList()
        currentChapterIndex = -1
        currentElementId = null
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        sleepTimerRemaining = 0
        clipSegments.clear()
        
        try {
            currentZipFile?.close()
            currentZipFile = null
        } catch (e: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        player = null
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        
        try {
            currentZipFile?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

