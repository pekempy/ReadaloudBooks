package com.pekempy.ReadAloudbooks.ui.player

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.pekempy.ReadAloudbooks.data.Book
import com.pekempy.ReadAloudbooks.data.UserPreferencesRepository
import com.pekempy.ReadAloudbooks.data.api.AppContainer
import com.pekempy.ReadAloudbooks.service.DownloadService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import com.pekempy.ReadAloudbooks.data.UnifiedProgress
import com.pekempy.ReadAloudbooks.data.repository.AudioBookmarkRepository
import com.pekempy.ReadAloudbooks.data.local.entities.AudioBookmark
import kotlinx.coroutines.flow.Flow

class AudiobookViewModel(
    private val repository: UserPreferencesRepository,
    private val audioBookmarkRepository: AudioBookmarkRepository
) : ViewModel() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var player: Player? = null
    
    var currentBook by mutableStateOf<Book?>(null)
    var isPlaying by mutableStateOf(false)
    var currentPosition by mutableStateOf(0L)
    var duration by mutableStateOf(0L)
    var playbackSpeed by mutableStateOf(1.0f)
    var isLoading by mutableStateOf(true)
    var chapters by mutableStateOf<List<Chapter>>(emptyList())
    var currentChapterIndex by mutableStateOf(-1)
    var sleepTimerRemaining by mutableLongStateOf(0L)
    var disableAutoSave by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var sleepTimerFinishChapter by mutableStateOf(false)
    private var pendingResumeMs: Long? = null

    // Audio bookmark state
    var audioBookmarks = mutableStateOf<List<AudioBookmark>>(emptyList())
    private var probedDurationMs: Long = 0L
    private var probedChapters: List<Chapter> = emptyList()
    private var nativeRetryCount = 0
    
    data class SyncConfirmation(
        val newPositionMs: Long,
        val progressPercent: Float,
        val localProgressPercent: Float,
        val source: String
    )
    var syncConfirmation by mutableStateOf<SyncConfirmation?>(null)
    
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var filesDir: File? = null
    private var appContext: android.content.Context? = null
    private var lastSaveTime = 0L

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
                        this@AudiobookViewModel.isPlaying = playing
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            nativeRetryCount = 0 
                            
                            val timeline = controller.currentTimeline
                            val calculatedDuration = if (!timeline.isEmpty && timeline.windowCount > 1) {
                                var totalDur = 0L
                                val window = androidx.media3.common.Timeline.Window()
                                for (i in 0 until timeline.windowCount) {
                                    timeline.getWindow(i, window)
                                    totalDur += window.durationMs
                                }
                                totalDur
                            } else {
                                controller.duration
                            }

                            val effectiveDur = if (probedDurationMs > 0) probedDurationMs else calculatedDuration
                            this@AudiobookViewModel.duration = effectiveDur
                            extractChapters()
                            updateCurrentChapterIndex()

                            pendingResumeMs?.let { resumeMs ->
                                android.util.Log.d("AudiobookVM", "Player READY, performing pending resume to: $resumeMs ms")
                                seekTo(resumeMs)
                                pendingResumeMs = null
                            }
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        val book = currentBook ?: return
                        if (nativeRetryCount < 2) {
                            nativeRetryCount++
                            android.util.Log.w("AudiobookVM", "Native playback failed (attempt $nativeRetryCount). Retrying...")
                            loadBook(book.id, isRetry = true)
                        } else {
                            android.util.Log.e("AudiobookVM", "Native playback failed twice. FFmpeg fallback should have triggered if applicable.")
                            this@AudiobookViewModel.error = "Playback failed after multiple attempts: ${error.message}"
                        }
                    }
                })
                startProgressUpdate()
            }, MoreExecutors.directExecutor())
        }
    }

    private fun extractChapters() {
        val p = this.player ?: return
        val currentChapters = mutableListOf<Chapter>()
        
        val timeline = p.currentTimeline
        if (!timeline.isEmpty) {
            for (i in 0 until timeline.windowCount) {
                val window = androidx.media3.common.Timeline.Window()
                timeline.getWindow(i, window)
                val title = window.mediaItem.mediaMetadata.title?.toString() ?: "Chapter ${i + 1}"
                
                if (timeline.windowCount > 1) {
                    currentChapters.add(Chapter(title, 0L, window.durationMs)) 
                }
            }
            
            if (currentChapters.isNotEmpty()) {
                var acc = 0L
                val fixedChapters = currentChapters.map { 
                    val chapterDur = if (it.duration > 0) it.duration else 0L
                    val c = it.copy(startOffset = acc, duration = chapterDur)
                    acc += chapterDur
                    c
                }
                currentChapters.clear()
                currentChapters.addAll(fixedChapters)
            }
        }
        
        if (currentChapters.isEmpty() && probedChapters.isNotEmpty()) {
            currentChapters.addAll(probedChapters)
        }
        
        this.chapters = currentChapters
        android.util.Log.d("AudiobookVM", "Extracted ${chapters.size} chapters")
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

    fun loadBook(bookId: String, isRetry: Boolean = false, autoPlay: Boolean = true) {
        if (!isRetry) {
            nativeRetryCount = 0
        }
        
        if (currentBook?.id == bookId && player != null && !isRetry) {
            android.util.Log.d("AudiobookVM", "Book $bookId already loaded. Checking for external progress updates...")
            viewModelScope.launch {
                val progressStr = repository.getBookProgress(bookId).first()
                val progress = com.pekempy.ReadAloudbooks.data.UnifiedProgress.fromString(progressStr)
                
                try {
                    val serverPos = AppContainer.apiClientManager.getApi().getPosition(bookId)
                    if (serverPos != null) {
                        val serverProgress = com.pekempy.ReadAloudbooks.data.UnifiedProgress.fromPosition(serverPos)
                        val localLastUpdated = progress?.lastUpdated ?: 0L
                        
                        if (serverProgress.lastUpdated > localLastUpdated) {
                           val totalDur = if (probedDurationMs > 0) probedDurationMs else duration
                           if (totalDur > 0) {
                               val serverMs = serverProgress.audioTimestampMs
                               val localMs = currentPosition
                               
                               val serverPercent = serverProgress.getOverallProgress() * 100
                               
                               val localUnified = UnifiedProgress(
                                   chapterIndex = currentChapterIndex,
                                   elementId = null,
                                   audioTimestampMs = currentPosition,
                                   scrollPercent = 0f, 
                                   lastUpdated = progress?.lastUpdated ?: 0L,
                                   totalChapters = chapters.size.coerceAtLeast(1),
                                   totalDurationMs = totalDur
                               )
                               val localPercent = localUnified.getOverallProgress() * 100
                               
                               if (kotlin.math.abs(serverPercent - localPercent) > 5f) {
                                   android.util.Log.d("AudiobookVM", "Server progress is newer and significantly different. Prompting.")
                                   withContext(Dispatchers.Main) {
                                       syncConfirmation = SyncConfirmation(
                                           newPositionMs = serverMs,
                                           progressPercent = serverPercent.coerceIn(0f, 100f),
                                           localProgressPercent = localPercent.coerceIn(0f, 100f),
                                           source = "Storyteller Server"
                                       )
                                   }
                               }
 else {
                                   android.util.Log.d("AudiobookVM", "Server progress is newer but minor. Auto-syncing.")
                                   withContext(Dispatchers.Main) {
                                       seekTo(serverMs)
                                   }
                               }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AudiobookVM", "Failed to check server progress: ${e.message}")
                }
            }
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                isLoading = true
            }
            try {
                val credentials = repository.userCredentials.first()
                if (credentials == null) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        isLoading = false
                    }
                    return@launch
                }
                
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
                    hasReadAloud = apiBook.readaloud != null,
                    hasEbook = apiBook.ebook != null,
                    hasAudiobook = apiBook.audiobook != null,
                    syncedUrl = apiManager.getSyncDownloadUrl(apiBook.uuid),
                    audiobookUrl = apiManager.getAudiobookDownloadUrl(apiBook.uuid),
                    ebookUrl = apiManager.getEbookDownloadUrl(apiBook.uuid),
                    series = apiSeries?.name ?: apiCollection?.name,
                    seriesIndex = apiBook.series?.firstNotNullOfOrNull { it.seriesIndex }
                        ?: apiBook.collections?.firstNotNullOfOrNull { it.seriesIndex }
                )
                
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    currentBook = book
                }
                repository.saveLastActiveBook(bookId, "audiobook")

                val localFile = filesDir?.let { fDir ->
                    val bookDir = com.pekempy.ReadAloudbooks.util.DownloadUtils.getBookDir(fDir, book)
                    val baseName = com.pekempy.ReadAloudbooks.util.DownloadUtils.getBaseFileName(book)
                    val file = File(bookDir, "$baseName.m4b")
                    if (file.exists()) file else null
                }

                val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(book.title)
                    .setArtist(book.author)
                    .setSubtitle(book.narrator)
                    .setDescription(book.description?.take(200))
                    .setArtworkUri(android.net.Uri.parse(book.audiobookCoverUrl ?: book.coverUrl))
                    .build()

                val progressStr = repository.getBookProgress(bookId).first()
                val progress = UnifiedProgress.fromString(progressStr)
                var resumeMs = 0L
                if (progress != null && progress.audioTimestampMs > 0) {
                    resumeMs = progress.audioTimestampMs
                }

                val mediaItem = if (localFile != null) {
                    android.util.Log.d("AudiobookVM", "Playing local file: ${localFile.absolutePath}")
                    
                    val codecConverter = com.pekempy.ReadAloudbooks.util.AudioCodecConverter(appContext ?: throw IllegalStateException("Context not initialized"))
                    val audioMetadata = codecConverter.getAudioMetadata(localFile.absolutePath)
                    
                    val enrichedMetadata = mediaMetadata.buildUpon()
                        .setExtras(android.os.Bundle().apply { 
                            putLong("duration_ms", audioMetadata.durationMs) 
                        })
                        .build()
                    
                    probedDurationMs = audioMetadata.durationMs
                    probedChapters = audioMetadata.chapters.map { Chapter(it.title, it.startMs, it.durationMs) }
                    val cachedPath = codecConverter.getCachedPathIfValid(localFile.absolutePath)
                    
                    if (cachedPath != null) {
                        android.util.Log.i("AudiobookVM", "Using existing converted file for playback")
                        MediaItem.Builder()
                            .setUri(android.net.Uri.fromFile(File(cachedPath)))
                            .setMediaMetadata(enrichedMetadata)
                            .build()
                    } else if (audioMetadata.codec == "eac3" || audioMetadata.codec == "ac3") {
                        android.util.Log.i("AudiobookVM", "File needs transcoding. Starting streaming proxy and background conversion.")
                        viewModelScope.launch {
                            codecConverter.checkAndConvertIfNeeded(localFile.absolutePath)
                        }
                        
                        val playbackUri = "ffmpeg://durationMs=${audioMetadata.durationMs}//${localFile.absolutePath}"
                        
                        MediaItem.Builder()
                            .setUri(playbackUri)
                            .setMediaMetadata(enrichedMetadata)
                            .build()
                    } else {
                        android.util.Log.d("AudiobookVM", "File supported natively, playing directly")
                        MediaItem.Builder()
                            .setUri(localFile.absolutePath)
                            .setMediaMetadata(enrichedMetadata)
                            .build()
                    }
                } else {
                    android.util.Log.d("AudiobookVM", "Streaming from: ${book.audiobookUrl}")
                    val url = book.audiobookUrl ?: ""
                    val lowerUrl = url.lowercase()
                    
                    val enrichedMetadataRemote = mediaMetadata.buildUpon()
                    
                    val isPotentiallyUnsupported = lowerUrl.contains("atmos") || lowerUrl.contains("eac3") || lowerUrl.contains(".m4b")
                    
                    val finalUrl = if (isPotentiallyUnsupported) {
                        android.util.Log.i("AudiobookVM", "Potential unsupported codec or container detected, probing remote metadata...")
                        val codecConverter = com.pekempy.ReadAloudbooks.util.AudioCodecConverter(appContext!!)
                        val remoteMetadata = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { codecConverter.getAudioMetadata(url) }
                        probedDurationMs = remoteMetadata.durationMs
                        probedChapters = remoteMetadata.chapters.map { Chapter(it.title, it.startMs, it.durationMs) }
                        
                        enrichedMetadataRemote.setExtras(android.os.Bundle().apply { 
                            putLong("duration_ms", remoteMetadata.durationMs) 
                        })
                        
                        val needsFfmpeg = (remoteMetadata.codec == "eac3" || remoteMetadata.codec == "ac3")
                        
                        if (needsFfmpeg && nativeRetryCount >= 2) {
                            android.util.Log.i("AudiobookVM", "Native playback failed 2 times. Using FFmpeg transcoding proxy.")
                            "ffmpeg://durationMs=${remoteMetadata.durationMs}//$url"
                        } else {
                            android.util.Log.d("AudiobookVM", "Trying native playback (attempt ${nativeRetryCount + 1})")
                            url
                        }
                    } else {
                        url
                    }
                    
                    MediaItem.Builder()
                        .setUri(finalUrl)
                        .setMediaMetadata(enrichedMetadataRemote.build())
                        .build()
                }
                
                var retryCount = 0
                while (player == null && retryCount < 50) {
                    android.util.Log.d("AudiobookVM", "Waiting for player initialization... ($retryCount)")
                    kotlinx.coroutines.delay(100)
                    retryCount++
                }

                val p = player ?: run {
                    android.util.Log.e("AudiobookVM", "Player initialization timed out")
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        isLoading = false
                    }
                    return@launch
                }
                
                android.util.Log.d("AudiobookVM", "Player initialized, setting media item for $bookId")
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    p.setMediaItem(mediaItem)
                    p.prepare()
                    if (autoPlay) {
                        p.play()
                    }
                }
                
                val localProgressStr = repository.getBookProgress(bookId).first()
                val localProgress = UnifiedProgress.fromString(localProgressStr)
                
                var finalProgressToUse: UnifiedProgress? = localProgress
                
                try {
                    val serverPos = AppContainer.apiClientManager.getApi().getPosition(bookId)
                    if (serverPos != null) {
                        val serverProgress = UnifiedProgress.fromPosition(serverPos)
                        val localLastUpdated = localProgress?.lastUpdated ?: 0L
                        
                        if (serverProgress.lastUpdated > localLastUpdated) {
                            val totalDur = if (probedDurationMs > 0) probedDurationMs else duration
                            if (totalDur > 0) {
                                val serverPercent = serverProgress.getOverallProgress() * 100
                                
                                val localUnified = UnifiedProgress(
                                    chapterIndex = localProgress?.chapterIndex ?: 0,
                                    elementId = localProgress?.elementId,
                                    audioTimestampMs = localProgress?.audioTimestampMs ?: 0L,
                                    scrollPercent = localProgress?.scrollPercent ?: 0f,
                                    lastUpdated = localProgress?.lastUpdated ?: 0L,
                                    totalChapters = (if (probedChapters.isNotEmpty()) probedChapters else chapters).size.coerceAtLeast(1),
                                    totalDurationMs = totalDur
                                )
                                val localPercent = localUnified.getOverallProgress() * 100
                                
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
                    android.util.Log.w("AudiobookVM", "Failed to fetch server progress early: ${e.message}")
                }

                finalProgressToUse?.let { progress ->
                    val savedMs = progress.audioTimestampMs
                    val chapterIndex = progress.chapterIndex
                    val scrollPercent = progress.scrollPercent
                    
                    val p = player
                    if (savedMs > 0) {
                        android.util.Log.d("AudiobookVM", "Trusting saved timestamp: $savedMs ms")
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            currentPosition = savedMs
                            pendingResumeMs = savedMs
                            if (p?.playbackState == Player.STATE_READY) {
                                seekTo(savedMs)
                                pendingResumeMs = null
                            }
                        }
                    } else {
                        val chaptersList = if (probedChapters.isNotEmpty()) probedChapters else chapters
                        
                        if (chaptersList.isNotEmpty() && chapterIndex in chaptersList.indices && chapterIndex >= 0) {
                            val chapter = chaptersList[chapterIndex]
                            val offset = (chapter.duration * scrollPercent).toLong()
                            val calculatedMs = chapter.startOffset + offset
                            
                            android.util.Log.d("AudiobookVM", "Resuming via Chapter Calculation: $calculatedMs ms")
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                pendingResumeMs = calculatedMs
                                if (p?.playbackState == Player.STATE_READY) {
                                    seekTo(calculatedMs)
                                    pendingResumeMs = null
                                }
                            }
                        } else if (duration > 0 || probedDurationMs > 0) {
                            val durationToUse = if (probedDurationMs > 0) probedDurationMs else duration
                            val overallProgress = progress.getOverallProgress()
                            val resMs = (overallProgress * durationToUse).toLong()
                            
                            android.util.Log.d("AudiobookVM", "Resuming via Global Progress Fallback: $resMs ms (prog=$overallProgress)")
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                pendingResumeMs = resMs
                                if (p?.playbackState == Player.STATE_READY) {
                                    seekTo(resMs)
                                    pendingResumeMs = null
                                }
                            }
                        }
                    }
                }

                val perBookSpd = repository.getBookPlaybackSpeed(bookId).first()
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (perBookSpd != null) {
                        setSpeed(perBookSpd)
                    } else {
                        val settings = repository.userSettings.first()
                        setSpeed(settings.playbackSpeed)
                    }

                    // Load audio bookmarks for the current book
                    loadAudioBookmarks()
                }

            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    e.printStackTrace()
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        error = e.message ?: "Unknown error occurred"
                    }
                }
            } finally {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    fun restoreBook(bookId: String) {
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
                    narrator = apiBook.narrators?.joinToString(", ") { it.name },
                    coverUrl = apiManager.getCoverUrl(apiBook.uuid),
                    audiobookCoverUrl = apiManager.getAudiobookCoverUrl(apiBook.uuid),
                    ebookCoverUrl = apiManager.getEbookCoverUrl(apiBook.uuid),
                    description = apiBook.description,
                    hasReadAloud = apiBook.readaloud != null,
                    hasEbook = apiBook.ebook != null,
                    hasAudiobook = apiBook.audiobook != null,
                    syncedUrl = apiManager.getSyncDownloadUrl(apiBook.uuid),
                    audiobookUrl = apiManager.getAudiobookDownloadUrl(apiBook.uuid),
                    ebookUrl = apiManager.getEbookDownloadUrl(apiBook.uuid),
                    series = apiSeries?.name ?: apiCollection?.name,
                    seriesIndex = apiBook.series?.firstNotNullOfOrNull { it.seriesIndex }
                        ?: apiBook.collections?.firstNotNullOfOrNull { it.seriesIndex }
                )
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    currentBook = book
                }
            } catch (e: Exception) {}
        }
    }

    fun redownloadBook(context: android.content.Context) {
        val book = currentBook ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                filesDir?.let { fDir ->
                    val bookDir = com.pekempy.ReadAloudbooks.util.DownloadUtils.getBookDir(fDir, book)
                    val baseName = com.pekempy.ReadAloudbooks.util.DownloadUtils.getBaseFileName(book)
                    val file = File(bookDir, "$baseName.m4b")
                    if (file.exists()) file.delete()
                }
                
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    error = null
                    isLoading = true
                }

                DownloadService.startDownload(
                    context,
                    book,
                    com.pekempy.ReadAloudbooks.data.DownloadManager.DownloadType.Audio
                )
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    fun seekTo(position: Long) {
        val p = player ?: return
        val timeline = p.currentTimeline
        if (!timeline.isEmpty && timeline.windowCount > 1) {
            var accumulated = 0L
            val window = androidx.media3.common.Timeline.Window()
            for (i in 0 until timeline.windowCount) {
                timeline.getWindow(i, window)
                val winDur = window.durationMs
                if (position < accumulated + winDur) {
                    p.seekTo(i, position - accumulated)
                    currentPosition = position
                    return
                }
                accumulated += winDur
            }
            p.seekTo(timeline.windowCount - 1, window.durationMs)
        } else {
            p.seekTo(position)
        }
        currentPosition = position
    }

    fun rewind10s() {
        val newPos = (currentPosition - 10000).coerceAtLeast(0)
        seekTo(newPos)
    }

    fun forward30s() {
        val newPos = (currentPosition + 30000).coerceAtMost(duration)
        seekTo(newPos)
    }

    fun cyclePlaybackSpeed() {
        playbackSpeed = when (playbackSpeed) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            2.0f -> 0.75f
            else -> 1.0f
        }
        player?.setPlaybackSpeed(playbackSpeed)
    }

    private fun updateCurrentChapterIndex() {
        if (chapters.isEmpty()) {
            currentChapterIndex = -1
            return
        }
        val pos = currentPosition
        val index = chapters.indexOfLast { pos >= it.startOffset }
        if (index != currentChapterIndex) {
            currentChapterIndex = index
        }
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                try {
                    player?.let { p ->
                        val timeline = p.currentTimeline
                        val index = p.currentMediaItemIndex
                        
                        if (!timeline.isEmpty && timeline.windowCount > 1 && index >= 0 && index < timeline.windowCount) {
                            var accumulated = 0L
                            val window = androidx.media3.common.Timeline.Window()
                            for (i in 0 until index) {
                                timeline.getWindow(i, window)
                                accumulated += window.durationMs
                            }
                            currentPosition = accumulated + p.currentPosition
                            
                            if (probedDurationMs > 0) {
                                duration = probedDurationMs
                            } else {
                                var totalDur = 0L
                                for (i in 0 until timeline.windowCount) {
                                    timeline.getWindow(i, window)
                                    totalDur += window.durationMs
                                }
                                duration = if (totalDur > 0) totalDur else duration
                            }
                        } else {
                            currentPosition = p.currentPosition
                            if (probedDurationMs > 0) {
                                duration = probedDurationMs
                            } else if (p.duration > 0) {
                                duration = p.duration
                            }
                        }
                        updateCurrentChapterIndex()
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
                    android.util.Log.e("AudiobookViewModel", "Error in progress update loop", e)
                }
                delay(1000)
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
                            android.util.Log.d("AudiobookVM", "Sleep timer expired, waiting for end of chapter...")
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

    internal fun saveBookProgress() {
        if (disableAutoSave) return
        val bookId = currentBook?.id ?: ""
        val pos = currentPosition
        val dur = duration
        if (bookId.isEmpty() || dur <= 0) return

        viewModelScope.launch {
            val chapterCount = if (chapters.isNotEmpty()) chapters.size else 1
            val currentChapter = currentChapterIndex.coerceAtLeast(0)
            val chapterProgress = if (chapters.isNotEmpty() && currentChapter in chapters.indices) {
                val chapter = chapters[currentChapter]
                if (chapter.duration > 0) {
                    (pos - chapter.startOffset).toFloat() / chapter.duration
                } else 0f
            } else {
                pos.toFloat() / dur
            }

            val progress = UnifiedProgress(
                chapterIndex = currentChapter,
                elementId = null,
                audioTimestampMs = pos,
                scrollPercent = chapterProgress,
                lastUpdated = System.currentTimeMillis(),
                totalChapters = chapterCount.coerceAtLeast(1),
                totalDurationMs = dur,
                href = chapters.getOrNull(currentChapter)?.title ?: "chapter_$currentChapter",
                mediaType = "audio/mpeg"
            )
            repository.saveBookProgress(bookId, progress.toString())
            
            try {
                val position = progress.toPosition()
                android.util.Log.d("AudiobookVM", "Uploading audiobook progress: $position")
                AppContainer.apiClientManager.getApi().updatePosition(bookId, position)
                android.util.Log.d("AudiobookVM", "Successfully synced audiobook progress to server")
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 409) {
                    android.util.Log.i("AudiobookVM", "Server has newer or same audiobook position (409). Skipping sync.")
                } else {
                    android.util.Log.w("AudiobookVM", "Failed to sync audiobook progress (HTTP ${e.code()}): ${e.message}")
                }
            } catch (e: Exception) {
                android.util.Log.w("AudiobookVM", "Failed to sync audiobook progress: ${e.message}")
            }
        }
    }

    fun dismissSync() {
        syncConfirmation = null
        saveBookProgress()
    }

    fun stopPlayback() {
        val bookId = currentBook?.id ?: ""
        val pos = currentPosition
        val dur = duration
        val chIdx = currentChapterIndex
        val chList = chapters

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
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        sleepTimerRemaining = 0
    }

    // === AUDIO BOOKMARK MANAGEMENT ===

    fun loadAudioBookmarks() {
        val bookId = currentBook?.id ?: return
        viewModelScope.launch {
            audioBookmarkRepository.getAudioBookmarksForBook(bookId).collect { bookmarkList ->
                audioBookmarks.value = bookmarkList
            }
        }
    }

    fun addAudioBookmark(label: String? = null, note: String? = null) {
        val bookId = currentBook?.id ?: return
        viewModelScope.launch {
            val bookmark = AudioBookmark(
                bookId = bookId,
                timestampMillis = currentPosition,
                chapterIndex = if (currentChapterIndex >= 0) currentChapterIndex else null,
                label = label,
                note = note,
                timestamp = System.currentTimeMillis()
            )
            audioBookmarkRepository.addAudioBookmark(bookmark)
            loadAudioBookmarks()
        }
    }

    fun deleteAudioBookmark(bookmark: AudioBookmark) {
        viewModelScope.launch {
            audioBookmarkRepository.deleteAudioBookmark(bookmark)
            loadAudioBookmarks()
        }
    }

    fun navigateToAudioBookmark(bookmark: AudioBookmark) {
        seekTo(bookmark.timestampMillis)
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        player = null
        sleepTimerJob?.cancel()
    }
}

data class Chapter(
    val title: String,
    val startOffset: Long,
    val duration: Long
)
