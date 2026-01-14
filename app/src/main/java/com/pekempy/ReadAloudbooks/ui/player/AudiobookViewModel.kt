package com.pekempy.ReadAloudbooks.ui.player

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pekempy.ReadAloudbooks.data.Book
import com.pekempy.ReadAloudbooks.data.UserPreferencesRepository
import com.pekempy.ReadAloudbooks.data.api.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.yield
import java.io.File
import com.pekempy.ReadAloudbooks.data.UnifiedProgress
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.navigator.media.audio.AudioNavigatorFactory
import org.readium.navigator.media.common.MediaNavigator
import org.readium.adapter.exoplayer.audio.ExoPlayerEngineProvider
import kotlin.time.Duration.Companion.milliseconds
import android.media.MediaMetadataRetriever
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import org.readium.navigator.media.audio.AudioNavigator


class AudiobookViewModel(private val repository: UserPreferencesRepository) : ViewModel() {

    // Readium Navigator
    private var navigator: Any? = null

    // State
    var currentBook by mutableStateOf<Book?>(null)
    var isPlaying by mutableStateOf(false)
    var currentPosition by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var playbackSpeed by mutableFloatStateOf(1.0f)
    var isLoading by mutableStateOf(false)
    var chapters by mutableStateOf<List<Chapter>>(emptyList())
    var currentChapterIndex by mutableIntStateOf(-1)
    
    // Sleep Timer
    var sleepTimerRemaining by mutableLongStateOf(0L)
    var sleepTimerFinishChapter by mutableStateOf(false)
    var isWaitingForChapterEnd by mutableStateOf(false)
    private var sleepTimerJob: Job? = null

    // Sync
    data class SyncConfirmation(
        val newPositionMs: Long,
        val progressPercent: Float,
        val localProgressPercent: Float,
        val source: String
    )
    var syncConfirmation by mutableStateOf<SyncConfirmation?>(null)

    // Other internals
    var error by mutableStateOf<String?>(null)
    var disableAutoSave by mutableStateOf(false)
    private var progressJob: Job? = null
    private var lastSaveTime = 0L
    private var app: android.app.Application? = null

    fun initializePlayer(context: android.content.Context) {
        this.app = context.applicationContext as android.app.Application
    }

    fun loadBook(bookId: String, isRetry: Boolean = false, autoPlay: Boolean = true) {
        isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Fetch Book Details
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

                withContext(Dispatchers.Main) {
                    currentBook = book
                }
                repository.saveLastActiveBook(bookId, "audiobook")

                // Prepare Media Source
                val filesDir = app?.filesDir
                val localFile = filesDir?.let { fDir ->
                    val bookDir = com.pekempy.ReadAloudbooks.util.DownloadUtils.getBookDir(fDir, book)
                    val baseName = com.pekempy.ReadAloudbooks.util.DownloadUtils.getBaseFileName(book)
                    val file = File(bookDir, "$baseName.m4b")
                    
                    android.util.Log.d("AudiobookViewModel", "Looking for audiobook file at: ${file.absolutePath}")
                    android.util.Log.d("AudiobookViewModel", "File exists: ${file.exists()}")
                    if (bookDir.exists()) {
                        android.util.Log.d("AudiobookViewModel", "Book directory contents: ${bookDir.listFiles()?.joinToString { it.name }}")
                    } else {
                        android.util.Log.d("AudiobookViewModel", "Book directory does not exist: ${bookDir.absolutePath}")
                    }
                    
                    if (file.exists()) file else null
                }
                
                // Get audio source (local file or streaming URL)
                val audioUrl = if (localFile != null) {
                    android.util.Log.d("AudiobookViewModel", "Using local file: ${localFile.absolutePath}")
                    AbsoluteUrl(localFile.toURI().toString())!!
                } else if (book.audiobookUrl != null) {
                    android.util.Log.d("AudiobookViewModel", "Streaming from: ${book.audiobookUrl}")
                    AbsoluteUrl(book.audiobookUrl)!!
                } else {
                    withContext(Dispatchers.Main) {
                        error = "No audiobook source available"
                        isLoading = false
                    }
                    return@launch
                }
                
                // Extract M4B metadata if local file exists (for our UI duration)
                val metadata = if (localFile != null) {
                    extractM4BMetadata(localFile)
                } else {
                    M4BMetadata(duration = 0L)
                }
                
                
                // Open the audiobook using Readium's proper flow
                withContext(Dispatchers.Main) {
                    val context = app ?: return@withContext
                    
                    // Allow spinner to animate
                    yield()
                    
                    // Create AssetRetriever and PublicationOpener (like Readium test app)
                    val httpClient = DefaultHttpClient()
                    val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
                    
                    yield()
                    
                    val publicationOpener = PublicationOpener(
                        publicationParser = DefaultPublicationParser(
                            context = context,
                            assetRetriever = assetRetriever,
                            httpClient = httpClient,
                            pdfFactory = null
                        ),
                        contentProtections = emptyList()
                    )
                    
                    yield()
                    
                    // Retrieve the asset
                    val assetResult = assetRetriever.retrieve(audioUrl, MediaType.MP4)
                    
                    yield()
                    
                    assetResult.onSuccess { asset ->
                        // Open the publication
                        val publicationResult = publicationOpener.open(
                            asset = asset,
                            allowUserInteraction = false
                        )
                        
                        publicationResult.onSuccess { publication ->
                            android.util.Log.d("AudiobookViewModel", "Publication opened successfully")
                            
                            // Create AudioNavigatorFactory
                            val factory = AudioNavigatorFactory(
                                publication,
                                ExoPlayerEngineProvider(app!!)
                            )
                            
                            if (factory == null) {
                                error = "Cannot create audio navigator factory"
                                isLoading = false
                                return@onSuccess
                            }
                            
                            // Create navigator
                            val navigatorResult = factory.createNavigator(
                                initialLocator = null,
                                initialPreferences = null
                            )
                            
                            navigatorResult.onSuccess { nav ->
                                navigator = nav
                                duration = metadata.duration * 1000L
                                error = null // Clear any previous errors
                                
                                // Start observing state
                                observePlayback(nav)
                                
                                // Load and apply saved playback speed
                                viewModelScope.launch {
                                    val savedSpeed = repository.getBookPlaybackSpeed(bookId).first()
                                    if (savedSpeed != null && savedSpeed > 0f) {
                                        setSpeed(savedSpeed)
                                    }
                                }
                                
                                // Restore progress in separate coroutine
                                viewModelScope.launch {
                                    loadProgress(bookId)
                                }
                                
                                // Auto play if requested
                                if (autoPlay) {
                                    play()
                                }
                                
                                isLoading = false
                            }.onFailure { err ->
                                error = "Failed to create navigator: $err"
                                android.util.Log.e("AudiobookViewModel", "Navigator error: $err")
                                isLoading = false
                            }
                        }.onFailure { err ->
                            error = "Failed to open publication: $err"
                            android.util.Log.e("AudiobookViewModel", "Publication error: $err")
                            isLoading = false
                        }
                    }.onFailure { err ->
                        error = "Failed to retrieve asset: $err"
                        android.util.Log.e("AudiobookViewModel", "Asset error: $err")
                        isLoading = false
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("AudiobookViewModel", "Exception in loadBook", e)
                withContext(Dispatchers.Main) {
                    error = "Failed to load audiobook: ${e.message}"
                    e.printStackTrace()
                    isLoading = false
                }
            }
        }
    }
    
    private data class M4BMetadata(
        val duration: Long, // in seconds
        val chapters: List<Chapter> = emptyList()
    )
    
    private fun extractM4BMetadata(file: File): M4BMetadata {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val durationSeconds = durationMs / 1000L
            
            return M4BMetadata(
                duration = durationSeconds,
                chapters = emptyList()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return M4BMetadata(duration = 0L)
        } finally {
            retriever.release()
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    private fun observePlayback(nav: Any) {
        viewModelScope.launch {
            try {
                android.util.Log.d("AudiobookViewModel", "Starting playback observation")
                val playbackFlow = (nav as MediaNavigator<*, *, *>).playback
                
                playbackFlow.collect { playback ->
                    // Check playWhenReady to determine if playing
                    var playWhenReady = false
                    try {
                        val playWhenReadyField = playback::class.java.getDeclaredField("playWhenReady")
                        playWhenReadyField.isAccessible = true
                        val value = playWhenReadyField.get(playback) as? Boolean
                        if (value != null) {
                            playWhenReady = value
                            isPlaying = value
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                    
                    // Only update position if actually playing
                    if (playWhenReady) {
                        try {
                            val playbackClass = playback::class.java
                            val offsetField = playbackClass.getDeclaredField("offset")
                            offsetField.isAccessible = true
                            val offset = offsetField.get(playback)
                            
                            // Offset appears to need division by 2,000,000 for correct speed
                            if (offset is Long) {
                                currentPosition = offset / 2_000_000L
                            }
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AudiobookViewModel", "Playback observation failed", e)
            }
        }
        
        startProgressUpdate()
    }
    
    private suspend fun loadProgress(bookId: String) {
        val progressStr = repository.getBookProgress(bookId).first()
        val progress = UnifiedProgress.fromString(progressStr)
        if (progress != null && progress.audioTimestampMs > 0) {
            withContext(Dispatchers.Main) {
                seekTo(progress.audioTimestampMs)
            }
        }
    }

    fun restoreBook(bookId: String) {
       loadBook(bookId, autoPlay = false)
    }

    fun redownloadBook(context: android.content.Context) {
        val book = currentBook ?: return
        viewModelScope.launch {
            loadBook(book.id)
        }
    }

    // Playback Controls
    fun togglePlayPause() {
        if (isPlaying) pause() else play()
    }

    @Suppress("UNCHECKED_CAST")
    fun play() {
        try {
            val nav = navigator
            if (nav != null) {
                val playMethod = nav::class.java.getMethod("play")
                playMethod.invoke(nav)
                android.util.Log.d("AudiobookViewModel", "Play called")
            }
        } catch (e: Exception) {
            android.util.Log.e("AudiobookViewModel", "Play failed", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun pause() {
        try {
            val nav = navigator
            if (nav != null) {
                val pauseMethod = nav::class.java.getMethod("pause")
                pauseMethod.invoke(nav)
                android.util.Log.d("AudiobookViewModel", "Pause called")
                saveBookProgress()
            }
        } catch (e: Exception) {
            android.util.Log.e("AudiobookViewModel", "Pause failed", e)
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    fun seekTo(positionMs: Long) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val nav = navigator
                if (nav != null) {
                    // Calculate the offset from current position
                    val offsetMs = positionMs - currentPosition
                    
                    // Use skip method - it has mangled name due to Kotlin inline classes
                    val skipMethods = nav.javaClass.methods.filter { it.name.contains("skip") && it.name.contains("-") }
                    val skipMethod = skipMethods.firstOrNull { it.parameterCount == 1 }
                    
                    if (skipMethod != null) {
                        // Skip takes long in nanoseconds
                        val offsetNanos = offsetMs * 1_000_000L
                        skipMethod.invoke(nav, offsetNanos)
                        currentPosition = positionMs
                        android.util.Log.d("AudiobookViewModel", "Seeked using skip to: $positionMs ms")
                    } else {
                        android.util.Log.e("AudiobookViewModel", "Skip method not found")
                        currentPosition = positionMs
                    }
                } else {
                    currentPosition = positionMs
                }
            } catch (e: Exception) {
                android.util.Log.e("AudiobookViewModel", "Seek failed", e)
                currentPosition = positionMs
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

    fun skipToChapter(index: Int) {
        if (index in chapters.indices) {
            seekTo(chapters[index].startOffset)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun setSpeed(speed: Float) {
        playbackSpeed = speed
        
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val nav = navigator
                if (nav != null) {
                    // Access the underlying Media3 player directly
                    val asMedia3Method = nav.javaClass.methods.find { it.name == "asMedia3Player" }
                    if (asMedia3Method != null) {
                        val media3Player = asMedia3Method.invoke(nav)
                        
                        // Set playback parameters on ExoPlayer
                        val setPlaybackParamsMethod = media3Player.javaClass.methods.find { 
                            it.name == "setPlaybackSpeed"
                        }
                        
                        if (setPlaybackParamsMethod != null) {
                            setPlaybackParamsMethod.invoke(media3Player, speed)
                            android.util.Log.d("AudiobookViewModel", "Speed set to: $speed via Media3")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AudiobookViewModel", "Set speed failed", e)
            }
            
            // Save preference
            val bookId = currentBook?.id ?: return@launch
            repository.saveBookPlaybackSpeed(bookId, speed)
        }
    }
    
    fun cyclePlaybackSpeed() {
        val newSpeed = when (playbackSpeed) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            2.0f -> 0.75f
            else -> 1.0f
        }
        setSpeed(newSpeed)
    }

    // Sleep Timer
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

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        isWaitingForChapterEnd = false
        if (minutes <= 0) {
            sleepTimerRemaining = 0
            return
        }
        sleepTimerRemaining = minutes * 60 * 1000L
        sleepTimerJob = viewModelScope.launch {
            while (sleepTimerRemaining > 0) {
                delay(1000)
                if (isPlaying) {
                    sleepTimerRemaining -= 1000
                    if (sleepTimerRemaining <= 0) {
                        withContext(Dispatchers.Main) { pause() }
                        sleepTimerRemaining = 0
                    }
                }
            }
        }
    }

    // Sync
    fun confirmSync() {
        syncConfirmation?.let {
            seekTo(it.newPositionMs)
            syncConfirmation = null
        }
    }

    fun dismissSync() {
        syncConfirmation = null
    }

    // Private
    internal fun saveBookProgress() {
        if (disableAutoSave) return
        val bookId = currentBook?.id ?: return
        viewModelScope.launch {
            val progress = UnifiedProgress(
                chapterIndex = currentChapterIndex,
                elementId = null,
                audioTimestampMs = currentPosition,
                scrollPercent = 0f,
                totalChapters = chapters.size,
                totalDurationMs = duration,
                lastUpdated = System.currentTimeMillis(),
                mediaType = "audio/mpeg"
            )
            repository.saveBookProgress(bookId, progress.toString())
            try {
                AppContainer.apiClientManager.getApi().updatePosition(bookId, progress.toPosition())
            } catch (e: Exception) {}
        }
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                if (isPlaying && now - lastSaveTime > 5000) {
                    saveBookProgress()
                    lastSaveTime = now
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun onCleared() {
        super.onCleared()
        try {
            (navigator as? MediaNavigator<*, *, *>)?.close()
        } catch (e: Exception) { }
        navigator = null
    }
    
    data class Chapter(
        val title: String,
        val startOffset: Long,
        val duration: Long
    )
}
