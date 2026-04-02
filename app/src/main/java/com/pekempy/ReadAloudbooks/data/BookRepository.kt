package com.pekempy.ReadAloudbooks.data

import android.content.Context
import android.util.Log
import com.pekempy.ReadAloudbooks.data.api.ApiClientManager
import com.pekempy.ReadAloudbooks.data.api.AppContainer
import com.pekempy.ReadAloudbooks.data.api.BookResponse
import com.pekempy.ReadAloudbooks.data.database.BookDao
import com.pekempy.ReadAloudbooks.data.database.ProgressDao
import com.pekempy.ReadAloudbooks.data.database.ProgressEntity
import com.pekempy.ReadAloudbooks.util.DownloadUtils
import com.pekempy.ReadAloudbooks.util.NetworkUtils
import com.pekempy.ReadAloudbooks.util.StringUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class BookRepository(
    private val bookDao: BookDao,
    private val progressDao: ProgressDao,
    private val apiClientManager: ApiClientManager,
    private val userPrefs: UserPreferencesRepository,
    private val context: Context
) {
    @Volatile
    var isOfflineMode: Boolean = false
        private set

    val allBooks: Flow<List<Book>> = bookDao.getAllBooksFlow().map { books ->
        books.map { book ->
            book.copy(
                isDownloaded = DownloadUtils.isBookDownloaded(context.filesDir, book),
                isAudiobookDownloaded = DownloadUtils.isAudiobookDownloaded(context.filesDir, book),
                isEbookDownloaded = DownloadUtils.isEbookDownloaded(context.filesDir, book),
                isReadAloudDownloaded = DownloadUtils.isReadAloudDownloaded(context.filesDir, book)
            )
        }
    }

    suspend fun getBook(id: String): Book? {
        return bookDao.getBookById(id)?.let { book ->
            book.copy(
                isDownloaded = DownloadUtils.isBookDownloaded(context.filesDir, book),
                isAudiobookDownloaded = DownloadUtils.isAudiobookDownloaded(context.filesDir, book),
                isEbookDownloaded = DownloadUtils.isEbookDownloaded(context.filesDir, book),
                isReadAloudDownloaded = DownloadUtils.isReadAloudDownloaded(context.filesDir, book)
            )
        }
    }

    suspend fun syncWithServer(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val credentials = userPrefs.userCredentials.first() ?: return@withContext false
            
            val currentSsid = NetworkUtils.getCurrentSsid(context)
            val shouldUseLocal = credentials.useLocalOnWifi && 
                                credentials.wifiSsid.isNotEmpty() &&
                                currentSsid == credentials.wifiSsid
            
            val targetUrl = when {
                shouldUseLocal && credentials.localUrl.isNotEmpty() -> credentials.localUrl
                credentials.url.isNotEmpty() -> credentials.url
                else -> credentials.localUrl 
            }
            
            if (targetUrl.isEmpty()) return@withContext false

            apiClientManager.updateConfig(targetUrl, credentials.token)
            
            // Health check
            try {
                val health = apiClientManager.getApi().healthCheck()
                if (health["Hello"] != "World") throw Exception("Health check failed")
            } catch (e: Exception) {
                isOfflineMode = true
                return@withContext false
            }

            isOfflineMode = false

            // Frequency check
            if (!force && !shouldSync()) {
                val count = bookDao.getAllBooks().size
                if (count > 0) return@withContext true
            }

            val response = apiClientManager.getApi().listBooks()
            val serverUuids = response.map { it.uuid }.toSet()
            
            // Prune local books that don't exist on server anymore
            val localBooksBefore = bookDao.getAllBooks()
            localBooksBefore.forEach { localBook ->
                if (!serverUuids.contains(localBook.id)) {
                    android.util.Log.i("BookRepository", "Pruning book deleted from server: ${localBook.title} (${localBook.id})")
                    bookDao.deleteBookById(localBook.id)
                }
            }

            val progressMap = userPrefs.allBookProgress.first()
            val syncedBooks = mutableListOf<Book>()
            
            response.forEach { apiBook ->
                val bookId = apiBook.uuid
                val book = buildBookFromApiResponse(apiBook, apiClientManager)
                
                // Progress merging logic
                val localProgressStr = progressMap[bookId] ?: progressDao.getProgressForBook(bookId)?.progressJson
                val localProgress = localProgressStr?.let { UnifiedProgress.fromString(it) }
                
                val serverProgress = try {
                    apiBook.position?.let { UnifiedProgress.fromPosition(it) }
                } catch (e: Exception) { null }
                
                var finalProgress = localProgress
                if (localProgress != null && serverProgress != null) {
                    val timeDiff = kotlin.math.abs(serverProgress.lastUpdated - localProgress.lastUpdated)
                    val serverOverall = serverProgress.getOverallProgress()
                    val localOverall = localProgress.getOverallProgress()
                    
                    if (timeDiff < 5000 && kotlin.math.abs(serverOverall - localOverall) > 0.01f) {
                        if (serverOverall > localOverall) {
                            finalProgress = serverProgress
                        } else {
                            finalProgress = localProgress
                            // Push local winner to server
                            try { apiClientManager.getApi().updatePosition(bookId, localProgress.toPosition()) } catch (e: Exception) {}
                        }
                    } else if (serverProgress.lastUpdated > localProgress.lastUpdated) {
                        finalProgress = serverProgress
                    } else if (localProgress.lastUpdated > serverProgress.lastUpdated) {
                        // Push local to server
                        try { apiClientManager.getApi().updatePosition(bookId, localProgress.toPosition()) } catch (e: Exception) {}
                    }
                } else if (serverProgress != null) {
                    finalProgress = serverProgress
                } else if (localProgress != null) {
                    // Push local-only to server
                    try { apiClientManager.getApi().updatePosition(bookId, localProgress.toPosition()) } catch (e: Exception) {}
                }
                
                val finalBook = book.copy(progress = finalProgress?.getOverallProgress())
                bookDao.insertBook(finalBook)
                syncedBooks.add(finalBook)
                
                if (finalProgress != null) {
                    saveProgressLocally(bookId, finalProgress)
                }
            }
            
            prefetchCovers(syncedBooks)
            userPrefs.updateLastSyncTime(System.currentTimeMillis())
            true
        } catch (e: Exception) {
            Log.e("BookRepository", "Sync failed", e)
            isOfflineMode = true
            false
        }
    }

    private fun prefetchCovers(books: List<Book>) {
        AppContainer.applicationScope.launch(Dispatchers.IO) {
            try {
                val imageLoader = coil.Coil.imageLoader(context)
                books.forEach { book ->
                    val urls = listOfNotNull(book.coverUrl, book.ebookCoverUrl, book.audiobookCoverUrl).distinct()
                    urls.forEach { url ->
                        val request = coil.request.ImageRequest.Builder(context)
                            .data(url)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                            .build()
                        imageLoader.enqueue(request)
                    }
                }
            } catch (e: Exception) {}
        }
    }

    suspend fun getServerProcessingBooks(): List<Book> = withContext(Dispatchers.IO) {
        try {
            val allApiBooks = apiClientManager.getApi().listBooks()
            return@withContext allApiBooks
                .filter { it.readaloud != null }
                .map { buildBookFromApiResponse(it, apiClientManager) }
                .filter { it.isReadAloudQueued || it.processingStatus == "ERROR" || it.processingStatus == "QUEUED" || it.processingStatus == "PROCESSING" }
                .sortedBy { it.queuePosition ?: Int.MAX_VALUE }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAllBooksFromLocal(): List<Book> = withContext(Dispatchers.IO) {
        bookDao.getAllBooks().map { book ->
            book.copy(
                isDownloaded = DownloadUtils.isBookDownloaded(context.filesDir, book),
                isAudiobookDownloaded = DownloadUtils.isAudiobookDownloaded(context.filesDir, book),
                isEbookDownloaded = DownloadUtils.isEbookDownloaded(context.filesDir, book),
                isReadAloudDownloaded = DownloadUtils.isReadAloudDownloaded(context.filesDir, book)
            )
        }
    }

    suspend fun saveProgress(bookId: String, progress: UnifiedProgress) = withContext(Dispatchers.IO) {
        saveProgressLocally(bookId, progress)
        try {
            apiClientManager.getApi().updatePosition(bookId, progress.toPosition())
        } catch (e: Exception) {
            Log.w("BookRepository", "Failed to sync progress for $bookId")
        }
    }

    private suspend fun saveProgressLocally(bookId: String, progress: UnifiedProgress) {
        progressDao.insertProgress(ProgressEntity(bookId, progress.toString(), progress.lastUpdated))
        bookDao.getBookById(bookId)?.let { book ->
            bookDao.updateBook(book.copy(progress = progress.getOverallProgress()))
        }
        userPrefs.saveBookProgress(bookId, progress.toString()) // Legacy sync for now
    }

    fun getProgress(bookId: String): Flow<UnifiedProgress?> = flow {
        val local = progressDao.getProgressForBook(bookId)
        if (local != null) {
            emit(UnifiedProgress.fromString(local.progressJson))
        } else {
            // Check legacy preference
            val legacy = userPrefs.getBookProgress(bookId).first()
            emit(UnifiedProgress.fromString(legacy))
        }
    }

    suspend fun updateBookDownloadStatus(bookId: String) = withContext(Dispatchers.IO) {
        val book = bookDao.getBookById(bookId) ?: return@withContext
        bookDao.updateBook(book.copy(
            isDownloaded = DownloadUtils.isBookDownloaded(context.filesDir, book),
            isAudiobookDownloaded = DownloadUtils.isAudiobookDownloaded(context.filesDir, book),
            isEbookDownloaded = DownloadUtils.isEbookDownloaded(context.filesDir, book),
            isReadAloudDownloaded = DownloadUtils.isReadAloudDownloaded(context.filesDir, book)
        ))
    }
    
    suspend fun updateLocalProgress(bookId: String, progress: Float) = withContext(Dispatchers.IO) {
         bookDao.getBookById(bookId)?.let { book ->
             bookDao.updateBook(book.copy(progress = progress))
         }
    }
    
    suspend fun updateProcessingStatus(bookId: String, ra: com.pekempy.ReadAloudbooks.data.api.ReadAloudResponse) = withContext(Dispatchers.IO) {
        bookDao.getBookById(bookId)?.let { book ->
            bookDao.updateBook(book.copy(
                isReadAloudQueued = ra.filepath.isNullOrBlank() && ra.status != "STOPPED",
                processingStatus = ra.status,
                currentProcessingStage = ra.currentStage,
                processingProgress = ra.stageProgress?.toFloat(),
                queuePosition = ra.queuePosition,
                hasReadAloud = !ra.filepath.isNullOrBlank() || book.hasReadAloud
            ))
        }
    }

    private suspend fun shouldSync(): Boolean {
        val settings = userPrefs.userSettings.first()
        if (settings.syncFrequency == 0) return false
        val lastSync = settings.lastSyncTime
        val now = System.currentTimeMillis()
        return (now - lastSync) >= (settings.syncFrequency * 60 * 1000L)
    }

    private fun buildBookFromApiResponse(apiBook: BookResponse, apiManager: ApiClientManager): Book {
        val apiSeries = apiBook.series?.firstOrNull()
        val apiCollection = apiBook.collections?.firstOrNull()
        val stableTimestamp = apiBook.ebook?.updatedAt ?: apiBook.audiobook?.updatedAt ?: apiBook.updatedAt
        
        return Book(
            id = apiBook.uuid,
            title = StringUtils.decodeHtml(apiBook.title),
            author = apiBook.authors.joinToString(", ") { StringUtils.decodeHtml(it.name) },
            narrator = apiBook.narrators?.joinToString(", ") { StringUtils.decodeHtml(it.name) },
            coverUrl = if (apiBook.ebook != null) apiManager.getEbookCoverUrl(apiBook.uuid, apiBook.ebook.updatedAt) 
                        else if (apiBook.audiobook != null) apiManager.getAudiobookCoverUrl(apiBook.uuid, apiBook.audiobook.updatedAt)
                        else apiManager.getCoverUrl(apiBook.uuid, stableTimestamp),
            description = StringUtils.decodeHtml(apiBook.description),
            hasReadAloud = apiBook.readaloud != null && !apiBook.readaloud.filepath.isNullOrBlank(),
            hasEbook = apiBook.ebook != null,
            hasAudiobook = apiBook.audiobook != null,
            syncedUrl = apiManager.getSyncDownloadUrl(apiBook.uuid),
            audiobookUrl = apiManager.getAudiobookDownloadUrl(apiBook.uuid),
            ebookUrl = apiManager.getEbookDownloadUrl(apiBook.uuid),
            series = StringUtils.decodeHtml(apiSeries?.name ?: apiCollection?.name),
            collection = StringUtils.decodeHtml(apiCollection?.name),
            seriesIndex = apiBook.series?.firstNotNullOfOrNull { it.seriesIndex }
                ?: apiBook.collections?.firstNotNullOfOrNull { it.seriesIndex },
            addedDate = parseServerTimestamp(apiBook.createdAt) ?: System.currentTimeMillis(),
            ebookCoverUrl = if (apiBook.ebook != null) apiManager.getEbookCoverUrl(apiBook.uuid, apiBook.ebook.updatedAt) else null,
            audiobookCoverUrl = if (apiBook.audiobook != null) apiManager.getAudiobookCoverUrl(apiBook.uuid, apiBook.audiobook.updatedAt) else null,
            updatedAt = apiBook.updatedAt,
            isReadAloudQueued = apiBook.readaloud != null && apiBook.readaloud.filepath.isNullOrBlank() && apiBook.readaloud.status != "STOPPED",
            processingStatus = apiBook.readaloud?.status,
            currentProcessingStage = apiBook.readaloud?.currentStage,
            processingProgress = apiBook.readaloud?.stageProgress?.toFloat(),
            queuePosition = apiBook.readaloud?.queuePosition
        )
    }

    private fun parseServerTimestamp(timestamp: String?): Long? {
        if (timestamp == null) return null
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(timestamp)?.time
        } catch (e: Exception) { null }
    }
}
