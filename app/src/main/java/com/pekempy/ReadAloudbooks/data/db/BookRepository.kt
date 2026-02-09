package com.pekempy.ReadAloudbooks.data.db

import android.content.Context
import com.pekempy.ReadAloudbooks.data.Book
import com.pekempy.ReadAloudbooks.data.UserPreferencesRepository
import com.pekempy.ReadAloudbooks.data.api.AppContainer
import com.pekempy.ReadAloudbooks.data.api.BookResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.*
import com.google.gson.Gson

class BookRepository(private val context: Context, private val userPrefsRepo: UserPreferencesRepository) {
    
    private val dbHelper = LocalDatabaseHelper(context)
    
    @Volatile
    var isOfflineMode: Boolean = false
        private set
    
    /**
     * Sync with server: fetch all books, merge with local data
     * Returns true if sync was successful
     */
    suspend fun syncWithServer(force: Boolean = false): Boolean {
        return try {
            val credentials = userPrefsRepo.userCredentials.first() ?: return false
            
            val currentSsid = com.pekempy.ReadAloudbooks.util.NetworkUtils.getCurrentSsid(context)
            val shouldUseLocal = credentials.useLocalOnWifi && 
                                credentials.wifiSsid.isNotEmpty() &&
                                currentSsid == credentials.wifiSsid
            
            val targetUrl = when {
                shouldUseLocal && credentials.localUrl.isNotEmpty() -> credentials.localUrl
                credentials.url.isNotEmpty() -> credentials.url
                else -> credentials.localUrl 
            }
            
            if (targetUrl.isEmpty()) {
                android.util.Log.w("BookRepository", "No target URL available for sync")
                return false
            }

            val apiManager = AppContainer.apiClientManager
            apiManager.updateConfig(targetUrl, credentials.token)
            
            // 0. Vital Health Check
            android.util.Log.d("BookRepository", "Debugging connection: Pinging server at $targetUrl")
            try {
                val health = apiManager.getApi().healthCheck()
                android.util.Log.d("BookRepository", "Health check response from $targetUrl: $health")
                
                if (health["Hello"] != "World") {
                    android.util.Log.e("BookRepository", "Health check failed validation. Expected Hello=World, got: $health")
                    throw Exception("Unexpected health check response: $health")
                }
                android.util.Log.d("BookRepository", "Server health check passed: $targetUrl")
            } catch (e: Exception) {
                android.util.Log.e("BookRepository", "Health check failed for $targetUrl", e)
                isOfflineMode = true
                return false
            }

            isOfflineMode = false

            // Check if we should proceed with full sync
            if (!force && !shouldSync() && dbHelper.getBookCount() > 0) {
                 android.util.Log.d("BookRepository", "Sync skipped. Frequency not met and local data exists.")
                 return true
            }

            android.util.Log.d("BookRepository", "Starting full sync...")

            
            val response = apiManager.getApi().listBooks()
            val serverUuids = response.map { it.uuid }.toSet()
            
            // 1. Prune local books that no longer exist on server
            val localBooksBefore = dbHelper.getAllBooks()
            localBooksBefore.forEach { localBook ->
                if (!serverUuids.contains(localBook.id)) {
                    android.util.Log.d("BookRepository", "Book ${localBook.id} not found on server. Pruning.")
                    dbHelper.deleteBook(localBook.id)
                }
            }
            
            // Get all local progress data
            val progressMap = userPrefsRepo.allBookProgress.first()
            
            val gson = Gson()
            val syncedBooks = mutableListOf<Book>()
            
            // Process each book from server
            val processedUuids = mutableSetOf<String>()
            response.forEach { apiBook ->
                val bookId = apiBook.uuid
                processedUuids.add(bookId)
                val existingBook = dbHelper.getBook(bookId)
                
                // Build book from API response
                val book = buildBookFromApiResponse(apiBook, apiManager)
                
                // Get local progress
                val localProgressStr = progressMap[bookId]
                val localProgress = localProgressStr?.let { com.pekempy.ReadAloudbooks.data.UnifiedProgress.fromString(it) }
                
                // Get server progress
                val serverProgress = try {
                    apiBook.position?.let { posObj ->
                        val json = gson.toJson(posObj)
                        com.pekempy.ReadAloudbooks.data.UnifiedProgress.fromString(json)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("BookRepository", "Failed to parse server position for $bookId: ${e.message}")
                    null
                }
                
                var finalProgress = localProgress
                
                if (localProgress != null && serverProgress != null) {
                    val timeDiff = kotlin.math.abs(serverProgress.lastUpdated - localProgress.lastUpdated)
                    val serverOverall = serverProgress.getOverallProgress()
                    val localOverall = localProgress.getOverallProgress()
                    
                    when {
                        // 1. SMART MERGE: If timestamps are very close (under 5s), avoid "Ghost Rewinds"
                        // If one is significantly further (> 1%), prefer it regardless of small time diff
                        timeDiff < 5000 && kotlin.math.abs(serverOverall - localOverall) > 0.01f -> {
                            if (serverOverall > localOverall) {
                                finalProgress = serverProgress
                                android.util.Log.d("BookRepository", "Simultaneous sync for $bookId: Server is further (${(serverOverall*100).toInt()}% vs ${(localOverall*100).toInt()}%). Using server.")
                            } else {
                                finalProgress = localProgress
                                android.util.Log.d("BookRepository", "Simultaneous sync for $bookId: Local is further (${(localOverall*100).toInt()}% vs ${(serverOverall*100).toInt()}%). Keep local.")
                                // Sync local winner back to server
                                try { apiManager.getApi().updatePosition(bookId, localProgress.toPosition()) } catch (e: Exception) {}
                            }
                        }
                        
                        // 2. Standard timestamp merge
                        serverProgress.lastUpdated > localProgress.lastUpdated -> {
                            finalProgress = serverProgress
                            android.util.Log.d("BookRepository", "Server progress is newer for $bookId. Updating local.")
                        }
                        
                        localProgress.lastUpdated > serverProgress.lastUpdated -> {
                            android.util.Log.d("BookRepository", "Local progress is newer for $bookId. Syncing to server.")
                            try {
                                apiManager.getApi().updatePosition(bookId, localProgress.toPosition())
                            } catch (e: Exception) {
                                android.util.Log.w("BookRepository", "Failed to upload newer local progress for $bookId: ${e.message}")
                            }
                        }
                    }
                } else if (serverProgress != null) {
                    finalProgress = serverProgress
                    android.util.Log.d("BookRepository", "New server progress found for $bookId. Initializing local.")
                } else if (localProgress != null) {
                    android.util.Log.d("BookRepository", "Local progress only for $bookId. Syncing to server.")
                    try {
                        apiManager.getApi().updatePosition(bookId, localProgress.toPosition())
                    } catch (e: Exception) {
                        android.util.Log.w("BookRepository", "Failed to upload local-only progress for $bookId: ${e.message}")
                    }
                }
                
                val finalBook = book.copy(progress = finalProgress?.getOverallProgress())
                dbHelper.insertOrUpdateBook(finalBook)
                syncedBooks.add(finalBook)
                
                if (finalProgress != null) {
                    dbHelper.updateBookProgress(bookId, finalProgress.getOverallProgress(), finalProgress.lastUpdated)
                    userPrefsRepo.saveBookProgress(bookId, finalProgress.toString())
                }
            }
            
            // 3. Handle progress for local books not in the server's immediate listing 
            // (e.g. newly added books or different listing slices)
            progressMap.forEach { (bookId, localStr) ->
                if (!processedUuids.contains(bookId)) {
                    val localProgress = com.pekempy.ReadAloudbooks.data.UnifiedProgress.fromString(localStr)
                    if (localProgress != null && localProgress.getOverallProgress() > 0) {
                        android.util.Log.d("BookRepository", "Local-only book progress for $bookId. Attempting to push to server.")
                        try {
                            apiManager.getApi().updatePosition(bookId, localProgress.toPosition())
                        } catch (e: Exception) {
                            // Book might not exist on server yet or been deleted, which is fine
                        }
                    }
                }
            }
            
            // 4. Pre-fetch covers for all books in the library
            prefetchCovers(syncedBooks)
            
            // Update last sync time
            userPrefsRepo.updateLastSyncTime(System.currentTimeMillis())
            
            isOfflineMode = false
            android.util.Log.d("BookRepository", "Sync completed. Total books: ${response.size}, Pruned: ${localBooksBefore.size - syncedBooks.size}")
            true
        } catch (e: Exception) {
            isOfflineMode = true
            android.util.Log.e("BookRepository", "Sync failed: ${e.message}")
            false
        }
    }
    
    private fun prefetchCovers(books: List<Book>) {
        // Run pre-fetching in a background scope to avoid blocking the sync result or UI
        AppContainer.applicationScope.launch(Dispatchers.IO) {
            try {
                val imageLoader = coil.Coil.imageLoader(context)
                books.forEach { book ->
                    val urls = listOfNotNull(book.coverUrl, book.ebookCoverUrl, book.audiobookCoverUrl).distinct()
                    urls.forEach { url ->
                        val request = coil.request.ImageRequest.Builder(context)
                            .data(url)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .memoryCachePolicy(coil.request.CachePolicy.DISABLED) // Don't bloat memory during prefetch
                            .build()
                        imageLoader.enqueue(request)
                    }
                    // Small delay to prevent network/disk saturation
                    kotlinx.coroutines.delay(10)
                }
            } catch (e: Exception) {
                android.util.Log.w("BookRepository", "Failed to pre-fetch some covers: ${e.message}")
            }
        }
    }

    fun updateProcessingStatus(bookId: String, ra: com.pekempy.ReadAloudbooks.data.api.ReadAloudResponse) {
        dbHelper.updateProcessingStatus(bookId, ra)
    }

    /**
     * Get all books from local database
     */
    fun getAllBooksFromLocal(): List<Book> {
        val books = dbHelper.getAllBooks()
        
        // Enrich with download status
        return books.map { book ->
            book.copy(
                isDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isBookDownloaded(context.filesDir, book),
                isAudiobookDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isAudiobookDownloaded(context.filesDir, book),
                isEbookDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isEbookDownloaded(context.filesDir, book),
                isReadAloudDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isReadAloudDownloaded(context.filesDir, book)
            )
        }
    }
    
    /**
     * Update local book progress
     */
    fun updateLocalProgress(bookId: String, progress: Float) {
        dbHelper.updateBookProgress(bookId, progress, System.currentTimeMillis())
    }
    
    /**
     * Get a single book by ID
     */
    fun getBook(bookId: String): Book? {
        return dbHelper.getBook(bookId)?.let { book ->
            book.copy(
                isDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isBookDownloaded(context.filesDir, book),
                isAudiobookDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isAudiobookDownloaded(context.filesDir, book),
                isEbookDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isEbookDownloaded(context.filesDir, book),
                isReadAloudDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isReadAloudDownloaded(context.filesDir, book)
            )
        }
    }
    
    /**
     * Check if sync is needed based on frequency setting
     */
    suspend fun shouldSync(): Boolean {
        val settings = userPrefsRepo.userSettings.first()
        if (settings.syncFrequency == 0) return false // Manual only
        
        val lastSync = settings.lastSyncTime
        val now = System.currentTimeMillis()
        val frequencyMs = settings.syncFrequency * 60 * 1000L
        
        return (now - lastSync) >= frequencyMs
    }
    
    private fun buildBookFromApiResponse(apiBook: BookResponse, apiManager: com.pekempy.ReadAloudbooks.data.api.ApiClientManager): Book {
        val apiSeries = apiBook.series?.firstOrNull()
        val apiCollection = apiBook.collections?.firstOrNull()
        
        // Use the most specific/stable timestamp for the cover to avoid cache-busting on every progress sync
        val ebookTimestamp = apiBook.ebook?.updatedAt
        val audiobookTimestamp = apiBook.audiobook?.updatedAt
        val stableTimestamp = ebookTimestamp ?: audiobookTimestamp ?: apiBook.updatedAt
        
        val seriesName = com.pekempy.ReadAloudbooks.util.StringUtils.decodeHtml(apiSeries?.name ?: apiCollection?.name)

        return Book(
            id = apiBook.uuid,
            title = com.pekempy.ReadAloudbooks.util.StringUtils.decodeHtml(apiBook.title),
            author = apiBook.authors.joinToString(", ") { com.pekempy.ReadAloudbooks.util.StringUtils.decodeHtml(it.name) },
            narrator = apiBook.narrators?.joinToString(", ") { com.pekempy.ReadAloudbooks.util.StringUtils.decodeHtml(it.name) },
            coverUrl = if (apiBook.ebook != null) apiManager.getEbookCoverUrl(apiBook.uuid, ebookTimestamp) 
                        else if (apiBook.audiobook != null) apiManager.getAudiobookCoverUrl(apiBook.uuid, audiobookTimestamp)
                        else apiManager.getCoverUrl(apiBook.uuid, stableTimestamp),
            description = com.pekempy.ReadAloudbooks.util.StringUtils.decodeHtml(apiBook.description),
            hasReadAloud = apiBook.readaloud != null && !apiBook.readaloud.filepath.isNullOrBlank(),
            hasEbook = apiBook.ebook != null,
            hasAudiobook = apiBook.audiobook != null,
            syncedUrl = apiManager.getSyncDownloadUrl(apiBook.uuid),
            audiobookUrl = apiManager.getAudiobookDownloadUrl(apiBook.uuid),
            ebookUrl = apiManager.getEbookDownloadUrl(apiBook.uuid),
            series = seriesName,
            collection = com.pekempy.ReadAloudbooks.util.StringUtils.decodeHtml(apiCollection?.name),
            seriesIndex = apiBook.series?.firstNotNullOfOrNull { it.seriesIndex }
                ?: apiBook.collections?.firstNotNullOfOrNull { it.seriesIndex },
            addedDate = parseServerTimestamp(apiBook.createdAt) ?: System.currentTimeMillis(),
            ebookCoverUrl = if (apiBook.ebook != null) apiManager.getEbookCoverUrl(apiBook.uuid, ebookTimestamp) else null,
            audiobookCoverUrl = if (apiBook.audiobook != null) apiManager.getAudiobookCoverUrl(apiBook.uuid, audiobookTimestamp) else null,
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
        } catch (e: Exception) {
            null
        }
    }
}
