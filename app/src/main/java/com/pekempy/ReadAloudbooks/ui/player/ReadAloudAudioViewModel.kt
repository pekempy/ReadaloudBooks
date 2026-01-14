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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.net.Uri
import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.TimeUnit
import com.pekempy.ReadAloudbooks.data.UnifiedProgress
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.Url

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
    
    private var sleepTimerJob: Job? = null
    private var progressJob: Job? = null
    private var loadJob: Job? = null
    private var filesDir: File? = null
    private var appContext: android.content.Context? = null
    private var pendingSeekPosition: Long? = null
    private var loadedSpineHrefs: List<String> = emptyList()
    
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
                            val totalDur = controller.duration
                            if (totalDur > duration) {
                                duration = totalDur
                            }
                            
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
        publication: org.readium.r2.shared.publication.Publication,
        smilData: Map<String, List<com.pekempy.ReadAloudbooks.ui.reader.ReaderViewModel.SyncSegment>>,
        chapterOffsets: Map<String, Double>,
        autoPlay: Boolean = true
    ) {
        if (currentBook?.id == bookId && player != null && player?.playbackState != androidx.media3.common.Player.STATE_IDLE) {
            android.util.Log.d("ReadAloudAudioVM", "Book $bookId already loaded. Checking for external progress updates...")
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
                                val serverPercent = (serverProgress.audioTimestampMs.toFloat() / duration) * 100
                                val localPercent = (currentPosition.toFloat() / duration) * 100
                                
                                if (kotlin.math.abs(serverPercent - localPercent) > 5f) {
                                    android.util.Log.d("ReadAloudAudioVM", "Server progress is newer and significantly different. Prompting.")
                                    syncConfirmation = SyncConfirmation(
                                        newPositionMs = serverProgress.audioTimestampMs,
                                        progressPercent = serverPercent.coerceIn(0f, 100f),
                                        localProgressPercent = localPercent.coerceIn(0f, 100f),
                                        source = "Storyteller Server"
                                    )
                                } else {
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
                
                val book = Book(
                    id = apiBook.uuid,
                    title = apiBook.title,
                    author = apiBook.authors.joinToString(", ") { it.name },
                    narrator = apiBook.narrators?.joinToString(", ") { it.name },
                    coverUrl = apiManager.getCoverUrl(apiBook.uuid),
                    audiobookCoverUrl = apiManager.getAudiobookCoverUrl(apiBook.uuid),
                    ebookCoverUrl = apiManager.getEbookCoverUrl(apiBook.uuid),
                    description = apiBook.description,
                    series = apiBook.series?.firstOrNull()?.name ?: apiBook.collections?.firstOrNull()?.name,
                    seriesIndex = apiBook.series?.firstNotNullOfOrNull { it.seriesIndex }
                        ?: apiBook.collections?.firstNotNullOfOrNull { it.seriesIndex }
                )
                
                withContext(Dispatchers.Main) {
                    currentBook = book
                }
                repository.saveLastActiveBook(bookId, "readaloud")
                
                withContext(Dispatchers.Main) {
                    duration = 0
                    currentPosition = 0
                }
                
                val localExtractedFiles = mutableMapOf<String, File>()
                extractAudioFiles(publication, smilData, bookId, localExtractedFiles)
                
                val localClipSegments = mutableListOf<ClipSegment>()
                val localChapterOffsets = mutableMapOf<String, Long>()
                val spineHrefs = publication.readingOrder.map { it.href.toString() }
                val spineTitles = publication.readingOrder.associate { it.href.toString() to (it.title ?: "") }
                
                createClippedSegments(smilData, spineHrefs, localExtractedFiles, localClipSegments, localChapterOffsets)
                
                val calculatedDuration = localClipSegments.sumOf { it.clipEndMs - it.clipBeginMs }
                val localChaptersList = createChaptersList(localChapterOffsets, spineHrefs, spineTitles, calculatedDuration)
                
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
                
                val mediaItems = localClipSegments.map { clip ->
                    val chapter = localChaptersList.findLast { it.startOffset <= clip.cumulativeStartMs }
                    val extras = android.os.Bundle().apply {
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
                    audioChapterOffsets = chapterOffsets
                    chapters = localChaptersList
                    duration = calculatedDuration
                    
                    loadedSpineHrefs = spineHrefs
                    player?.setMediaItems(mediaItems)
                    player?.prepare()
                    if (autoPlay) player?.play()
                }
                
                loadProgress(bookId)
                
                withContext(Dispatchers.Main) {
                    if (player?.playbackState == Player.STATE_READY) {
                        pendingSeekPosition?.let { seekPos ->
                            seekTo(seekPos)
                            pendingSeekPosition = null
                        }
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
                         currentPosition = progress?.audioTimestampMs ?: 0L
                         duration = progress.totalDurationMs
                         currentChapterIndex = progress?.chapterIndex ?: 0
                         currentElementId = progress?.elementId
                     }
                     isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    private suspend fun extractAudioFiles(
        publication: Publication,
        smilData: Map<String, List<com.pekempy.ReadAloudbooks.ui.reader.ReaderViewModel.SyncSegment>>,
        bookId: String,
        outputMap: MutableMap<String, File>
    ) {
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
        
        uniqueAudioSources.forEach { audioSrc ->
            val safeName = audioSrc.replace("/", "_").replace("\\", "_").removePrefix(".._")
            val tempFile = File(tempDir, safeName)
            
            if (tempFile.exists() && tempFile.length() > 0) {
                outputMap[audioSrc] = tempFile
                return@forEach
            }

            val url = Url(audioSrc) ?: return@forEach
            val link = publication.linkWithHref(url) ?: return@forEach
            try {
                val resource = publication.get(link)
                val inputStream = resource?.read()?.getOrNull()?.inputStream() ?: return@forEach
                inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                outputMap[audioSrc] = tempFile
                android.util.Log.d("ReadAloudAudioVM", "Extracted via Readium: $audioSrc → ${tempFile.name}")
            } catch (e: Exception) {
                android.util.Log.e("ReadAloudAudioVM", "Failed to extract $audioSrc: ${e.message}")
            }
        }
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
    
    private suspend fun loadProgress(bookId: String) {
        val progressStr = repository.getBookProgress(bookId).first()
        val localProgress = UnifiedProgress.fromString(progressStr)
        var finalProgressToUse = localProgress
        
        try {
            val serverPos = AppContainer.apiClientManager.getApi().getPosition(bookId)
            if (serverPos != null) {
                val serverProgress = UnifiedProgress.fromPosition(serverPos, chapters.size)
                val localLastUpdated = localProgress?.lastUpdated ?: 0L
                
                if (serverProgress.lastUpdated > localLastUpdated) {
                    if (duration > 0) {
                        val serverPercent = (serverProgress.audioTimestampMs.toFloat() / duration) * 100
                        val localPercent = ((localProgress?.audioTimestampMs?.toFloat() ?: 0f) / duration) * 100
                        
                        if (kotlin.math.abs(serverPercent - localPercent) > 5f) {
                            withContext(Dispatchers.Main) {
                                syncConfirmation = SyncConfirmation(
                                    newPositionMs = serverProgress.audioTimestampMs,
                                    progressPercent = serverPercent.coerceIn(0f, 100f),
                                    localProgressPercent = localPercent.coerceIn(0f, 100f),
                                    source = "Storyteller Server"
                                )
                            }
                        } else {
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
        val newPos = (currentPosition - 10000).coerceAtLeast(0)
        seekTo(newPos)
    }

    fun forward30s() {
        val newPos = (currentPosition + 30000).coerceAtMost(duration)
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
                        
                        val currentSegments = clipSegments
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
                    
                    if (duration <= 0 && player?.duration ?: 0 > 0 && clipSegments.isNotEmpty()) {
                        duration = player?.duration ?: 0
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
                totalChapters = chList.size,
                totalDurationMs = dur,
                href = href,
                mediaType = "application/xhtml+xml"
            )
            repository.saveBookProgress(bookId, progress.toString())
            
            try {
                AppContainer.apiClientManager.getApi().updatePosition(bookId, progress.toPosition())
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
        
        clipSegments.clear()
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        player = null
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        
        sleepTimerJob?.cancel()
    }
}

