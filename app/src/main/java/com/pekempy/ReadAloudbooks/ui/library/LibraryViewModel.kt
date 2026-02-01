package com.pekempy.ReadAloudbooks.ui.library

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pekempy.ReadAloudbooks.data.Book
import com.pekempy.ReadAloudbooks.data.UserCredentials
import com.pekempy.ReadAloudbooks.data.UserPreferencesRepository
import com.pekempy.ReadAloudbooks.data.api.AppContainer
import com.pekempy.ReadAloudbooks.service.DownloadService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive

class LibraryViewModel(private val repository: UserPreferencesRepository) : ViewModel() {
    private val PREFS_NAME = "download_prefs"
    private val KEY_PENDING_DOWNLOADS = "pending_downloads"

    private fun getPendingDownloads(): Set<String> {
        val prefs = AppContainer.context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_PENDING_DOWNLOADS, emptySet()) ?: emptySet()
    }

    private fun addPendingDownload(bookId: String) {
        val prefs = AppContainer.context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val current = HashSet(getPendingDownloads())
        current.add(bookId)
        prefs.edit().putStringSet(KEY_PENDING_DOWNLOADS, current).commit()
    }

    private fun removePendingDownload(bookId: String) {
        val prefs = AppContainer.context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val current = HashSet(getPendingDownloads())
        current.remove(bookId)
        prefs.edit().putStringSet(KEY_PENDING_DOWNLOADS, current).commit()
    }
    
    private fun checkPendingDownloads() {
        if (allBooks.isEmpty()) return
        
        val pendingIds = getPendingDownloads()
        if (pendingIds.isNotEmpty()) {
            val pendingBooks = allBooks.filter { pendingIds.contains(it.id) }
            pendingBooks.forEach { book ->
                downloadBook(book) 
            }
        }
    }
    private var allBooks = listOf<Book>()
    
    var books by mutableStateOf<List<Book>>(emptyList())
    var isLoading by mutableStateOf(false)

    data class DownloadStatus(val progress: Float, val statusText: String)

    var downloadingBooks = mutableStateMapOf<String, DownloadStatus>()

    enum class ViewMode { Home, Library, Authors, Series, Downloads, Processing }
    var currentViewMode by mutableStateOf(ViewMode.Home)
    
    private val LIMIT = 100
    var currentPage by mutableStateOf(0)
    
    fun loadNextPage() {
        currentPage++
        applyFiltersAndSort()
    }

    fun resetPagination() {
        currentPage = 0
    }
    
    var continueReadingBooks by mutableStateOf<List<Book>>(emptyList())
    var continueSeriesBooks by mutableStateOf<List<Book>>(emptyList())
    var downloadedBooks by mutableStateOf<List<Book>>(emptyList())
    
    val totalProcessingCount: Int get() = allBooks.count { it.isReadAloudQueued }
    val hasProcessing: Boolean get() = allBooks.any { it.isReadAloudQueued }
    
    var selectedFilter: String? by mutableStateOf(null)
    
    enum class SortOption { TitleAsc, TitleDesc, AuthorAsc, AuthorDesc, SeriesAsc, SeriesDesc, AddedAsc, AddedDesc }
    var currentSort by mutableStateOf(SortOption.TitleAsc)

    var searchQuery by mutableStateOf("")

    fun onSearchQueryChange(newQuery: String) {
        searchQuery = newQuery
        applyFiltersAndSort()
    }

    var filterHasAudiobook by mutableStateOf(false)
    var filterHasEbook by mutableStateOf(false)
    var filterHasReadAloud by mutableStateOf(false)
    var filterDownloaded by mutableStateOf(false)
    var filterCanCreateReadAloud by mutableStateOf(false)

    fun toggleAudiobookFilter() { 
        filterHasAudiobook = !filterHasAudiobook 
        applyFiltersAndSort()
    }
    fun toggleEbookFilter() { 
        filterHasEbook = !filterHasEbook 
        applyFiltersAndSort()
    }
    fun toggleReadAloudFilter() { 
        filterHasReadAloud = !filterHasReadAloud 
        applyFiltersAndSort()
    }
    fun toggleDownloadedFilter() { 
        filterDownloaded = !filterDownloaded 
        applyFiltersAndSort()
    }
    fun toggleCanCreateReadAloudFilter() {
        filterCanCreateReadAloud = !filterCanCreateReadAloud
        applyFiltersAndSort()
    }

    init {
        startObservingDownloads()
        startPollingProcessingBooks()
        loadBooks()
    }

    private fun startPollingProcessingBooks() {
        viewModelScope.launch {
            while (isActive) {
                val processingBooks = allBooks.filter { it.isReadAloudQueued }
                if (processingBooks.isNotEmpty()) {
                    processingBooks.forEach { book ->
                        try {
                            val details = AppContainer.apiClientManager.getApi().getBookDetails(book.id)
                            val ra = details.readaloud
                            if (ra != null) {
                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    allBooks = allBooks.map { 
                                        if (it.id == book.id) {
                                            it.copy(
                                                hasReadAloud = !ra.filepath.isNullOrBlank(),
                                                isReadAloudQueued = ra.filepath.isNullOrBlank() && ra.status != "STOPPED",
                                                processingStatus = ra.status,
                                                currentProcessingStage = ra.currentStage,
                                                processingProgress = ra.stageProgress?.toFloat(),
                                                queuePosition = ra.queuePosition
                                            )
                                        } else it
                                    }
                                    applyFiltersAndSort()
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("LibraryViewModel", "Error polling book ${book.id}: ${e.message}")
                        }
                    }
                }
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    fun retryProcessing(bookId: String) {
        viewModelScope.launch {
            try {
                AppContainer.apiClientManager.getApi().processBook(bookId, restart = true)
                loadBooks()
            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Failed to retry processing for $bookId: ${e.message}")
            }
        }
    }

    fun stopProcessing(bookId: String) {
        viewModelScope.launch {
            try {
                AppContainer.apiClientManager.getApi().processBook(bookId, restart = true)
            } catch (e: Exception) {
                android.util.Log.w("LibraryViewModel", "Restart before stop failed (proceeding to stop): ${e.message}")
            }

            kotlinx.coroutines.delay(250)

            try {
                val response = AppContainer.apiClientManager.getApi().cancelProcessing(bookId)
                if (response.isSuccessful) {
                    loadBooks()
                } else {
                    android.util.Log.e("LibraryViewModel", "Failed to stop processing for $bookId: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Failed to stop processing for $bookId: ${e.message}")
            }
        }
    }

    fun createReadAloud(bookId: String) {
        val previousBooks = allBooks
        allBooks = allBooks.map { 
            if (it.id == bookId) {
                it.copy(
                    isReadAloudQueued = true,
                    processingStatus = "QUEUED",
                    currentProcessingStage = "Queued",
                    processingProgress = 0f
                )
            } else it
        }
        applyFiltersAndSort()

        viewModelScope.launch {
            try {
                AppContainer.apiClientManager.getApi().processBook(bookId)
            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Failed to create readaloud for $bookId: ${e.message}")
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    allBooks = previousBooks
                    applyFiltersAndSort()
                }
            }
        }
    }

    private fun startObservingDownloads() {
        viewModelScope.launch {
            snapshotFlow { com.pekempy.ReadAloudbooks.data.DownloadManager.activeDownloads.toList() }.collect { jobs ->
                val currentIds = jobs.map { it.book.id }.toSet()
                
                val toRemove = downloadingBooks.keys.filter { !currentIds.contains(it) }
                toRemove.forEach { downloadingBooks.remove(it) }

                jobs.forEach { job ->
                    if (job.isCompleted) {
                         removePendingDownload(job.book.id)
                         com.pekempy.ReadAloudbooks.data.DownloadManager.removeJob(job)
                         
                         withContext(kotlinx.coroutines.Dispatchers.Main) {
                             allBooks = allBooks.map {
                                 if (it.id == job.book.id) {
                                     it.copy(
                                        isDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isBookDownloaded(AppContainer.context.filesDir, it),
                                        isAudiobookDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isAudiobookDownloaded(AppContainer.context.filesDir, it),
                                        isEbookDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isEbookDownloaded(AppContainer.context.filesDir, it),
                                        isReadAloudDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isReadAloudDownloaded(AppContainer.context.filesDir, it)
                                     )
                                 } else it
                             }
                             applyFiltersAndSort()
                             launch { updateHomeData() }
                         }
                    } else if (job.isFailed) {
                        com.pekempy.ReadAloudbooks.data.DownloadManager.removeJob(job)
                    } else {
                        downloadingBooks[job.book.id] = DownloadStatus(job.progress, job.status)
                    }
                }
            }
        }
    }

    fun loadBooks() {
        viewModelScope.launch {
            isLoading = true
            try {
                val credentials = repository.userCredentials.first()
                if (credentials != null) {
                    val currentSsid = com.pekempy.ReadAloudbooks.util.NetworkUtils.getCurrentSsid(AppContainer.context)
                    val shouldUseLocal = credentials.useLocalOnWifi && 
                                        credentials.wifiSsid.isNotEmpty() &&
                                        currentSsid == credentials.wifiSsid
                    
                    val targetUrl = when {
                        shouldUseLocal && credentials.localUrl.isNotEmpty() -> credentials.localUrl
                        credentials.url.isNotEmpty() -> credentials.url
                        else -> credentials.localUrl 
                    }
                    
                    if (targetUrl.isEmpty()) {
                        android.util.Log.w("LibraryViewModel", "No target URL available for sync (url and localUrl both empty or not on home wifi)")
                        isLoading = false
                        return@launch
                    }

                    val apiManager = AppContainer.apiClientManager
                    apiManager.updateConfig(targetUrl, credentials.token)
                    
                    val response = apiManager.getApi().listBooks()
                    
                    val progressMap = repository.allBookProgress.first()
                    val bookProgress = mutableMapOf<String, Float>()
    
                    progressMap.forEach { (bookId, value) ->
                        val up = com.pekempy.ReadAloudbooks.data.UnifiedProgress.fromString(value)
                        if (up != null) {
                            bookProgress[bookId] = up.getOverallProgress()
                        }
                    }
                    
                    allBooks = response.map { apiBook ->
                        val apiSeries = apiBook.series?.firstOrNull()
                        val apiCollection = apiBook.collections?.firstOrNull()
                        
                        val book = Book(
                            id = apiBook.uuid,
                            title = apiBook.title,
                            author = apiBook.authors.joinToString(", ") { it.name },
                            narrator = apiBook.narrators?.joinToString(", ") { it.name },
                            coverUrl = if (apiBook.ebook != null) apiManager.getEbookCoverUrl(apiBook.uuid, apiBook.updatedAt) 
                                        else if (apiBook.audiobook != null) apiManager.getAudiobookCoverUrl(apiBook.uuid, apiBook.updatedAt)
                                        else apiManager.getCoverUrl(apiBook.uuid, apiBook.updatedAt),
                            description = apiBook.description,
                            hasReadAloud = apiBook.readaloud != null && !apiBook.readaloud.filepath.isNullOrBlank(),
                            hasEbook = apiBook.ebook != null,
                            hasAudiobook = apiBook.audiobook != null,
                            syncedUrl = apiManager.getSyncDownloadUrl(apiBook.uuid),
                            audiobookUrl = apiManager.getAudiobookDownloadUrl(apiBook.uuid),
                            ebookUrl = apiManager.getEbookDownloadUrl(apiBook.uuid),
                            series = apiSeries?.name ?: apiCollection?.name,
                            seriesIndex = apiBook.series?.firstNotNullOfOrNull { it.seriesIndex }
                                ?: apiBook.collections?.firstNotNullOfOrNull { it.seriesIndex },
                            addedDate = System.currentTimeMillis(),
                            ebookCoverUrl = if (apiBook.ebook != null) apiManager.getEbookCoverUrl(apiBook.uuid, apiBook.updatedAt) else null,
                            audiobookCoverUrl = if (apiBook.audiobook != null) apiManager.getAudiobookCoverUrl(apiBook.uuid, apiBook.updatedAt) else null,
                            progress = bookProgress[apiBook.uuid],
                            updatedAt = apiBook.updatedAt
                        )
                        book.copy(
                            isDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isBookDownloaded(AppContainer.context.filesDir, book),
                            isAudiobookDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isAudiobookDownloaded(AppContainer.context.filesDir, book),
                            isEbookDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isEbookDownloaded(AppContainer.context.filesDir, book),
                            isReadAloudDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isReadAloudDownloaded(AppContainer.context.filesDir, book),
                            isReadAloudQueued = apiBook.readaloud != null && apiBook.readaloud.filepath.isNullOrBlank() && apiBook.readaloud.status != "STOPPED",
                            processingStatus = apiBook.readaloud?.status,
                            currentProcessingStage = apiBook.readaloud?.currentStage,
                            processingProgress = apiBook.readaloud?.stageProgress?.toFloat(),
                            queuePosition = apiBook.readaloud?.queuePosition
                        )
                    }
                    applyFiltersAndSort()
                    checkPendingDownloads()
                    
                     updateHomeData()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun updateHomeData() {
        val progressMap = repository.allBookProgress.first()
        val bookTimestamps = mutableMapOf<String, Long>()
        val bookProgress = mutableMapOf<String, Float>()

        android.util.Log.d("LibraryViewModel", "Updating home data. totalBooks=${allBooks.size}")

        progressMap.forEach { (bookId, value) ->
            val up = com.pekempy.ReadAloudbooks.data.UnifiedProgress.fromString(value)
            if (up != null) {
                bookTimestamps[bookId] = up.lastUpdated
                bookProgress[bookId] = up.getOverallProgress()
            }
        }

        val inProgressBooks = allBooks
            .filter { progressMap.containsKey(it.id) && (it.progress ?: 0f) < 0.95f }
            .sortedByDescending { bookTimestamps[it.id] ?: 0L }
            .take(10)

        continueReadingBooks = inProgressBooks

        val finishedBooks = allBooks
            .filter { progressMap.containsKey(it.id) && (it.progress ?: 0f) >= 0.95f }
            .sortedByDescending { bookTimestamps[it.id] ?: 0L }

        val nextInSeries = mutableListOf<Book>()
        val processedSeries = mutableSetOf<String>()

        finishedBooks.forEach { finishedBook ->
            val seriesName = finishedBook.series ?: return@forEach
            if (processedSeries.contains(seriesName)) return@forEach
            val currentIndex = finishedBook.seriesIndex?.toDoubleOrNull() ?: 0.0

            val nextBook = allBooks
                .filter { it.series == seriesName }
                .filter { (it.seriesIndex?.toDoubleOrNull() ?: -1.0) > currentIndex }
                .sortedBy { it.seriesIndex?.toDoubleOrNull() ?: 0.0 }
                .firstOrNull()

            if (nextBook != null) {
                if (!inProgressBooks.any { it.id == nextBook.id }) {
                    nextInSeries.add(nextBook)
                    processedSeries.add(seriesName)
                }
            }
        }
        continueSeriesBooks = nextInSeries

        val downloaded = allBooks
            .filter { it.isDownloaded }
            .sortedBy { it.title }

        android.util.Log.d("LibraryViewModel", "Found ${downloaded.size} downloaded books for Ready to Read")
        downloadedBooks = downloaded
    }

    
    fun cancelDownload(book: Book) {
        val activeJob = com.pekempy.ReadAloudbooks.data.DownloadManager.activeDownloads.find { it.book.id == book.id }
        val fileNameToDelete = activeJob?.fileName

        removePendingDownload(book.id)
        
        com.pekempy.ReadAloudbooks.data.DownloadManager.cancelDownload(book.id)
        
        downloadingBooks.remove(book.id)
        
        if (fileNameToDelete != null) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val filesDir = AppContainer.context.filesDir
                val bookDir = com.pekempy.ReadAloudbooks.util.DownloadUtils.getBookDir(filesDir, book)
                val file = java.io.File(bookDir, fileNameToDelete)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    fun downloadBook(book: Book) {
        addPendingDownload(book.id)
        downloadingBooks[book.id] = DownloadStatus(0f, "Queued")

        DownloadService.startDownload(AppContainer.context, book, com.pekempy.ReadAloudbooks.data.DownloadManager.DownloadType.All)
    }

    fun downloadSeries(seriesName: String) {
        val seriesBooks = allBooks.filter { it.series == seriesName }
        seriesBooks.forEach { book ->
            if (!book.isDownloaded) {
                downloadBook(book)
            }
        }
    }

    fun setViewMode(mode: ViewMode) {
        currentViewMode = mode
        selectedFilter = null
        resetPagination()
        applyFiltersAndSort()
    }

    fun selectFilter(filter: String) {
        selectedFilter = filter
        resetPagination()
        applyFiltersAndSort()
    }
    
    fun setSort(sort: SortOption) {
        currentSort = sort
        resetPagination()
        applyFiltersAndSort()
    }

    private fun applyFiltersAndSort() {
        var result = if (selectedFilter != null) {
            when (currentViewMode) {
                ViewMode.Authors -> allBooks.filter { it.author == selectedFilter }
                ViewMode.Series -> allBooks.filter { it.series == selectedFilter }
                ViewMode.Processing -> allBooks.filter { it.isReadAloudQueued }
                ViewMode.Downloads -> allBooks.filter { downloadingBooks.containsKey(it.id) }
                else -> allBooks
            }
        } else {
            when (currentViewMode) {
                ViewMode.Processing -> allBooks.filter { it.isReadAloudQueued }
                ViewMode.Downloads -> allBooks.filter { downloadingBooks.containsKey(it.id) }
                else -> allBooks
            }
        }

        result = applyGlobalFilters(result)

        result = if (currentViewMode == ViewMode.Series && selectedFilter != null) {
            result.sortedWith(compareBy { it.seriesIndex?.toDoubleOrNull() ?: Double.MAX_VALUE })
        } else {
            when (currentSort) {
                SortOption.TitleAsc -> result.sortedBy { com.pekempy.ReadAloudbooks.util.StringUtils.normalizeTitle(it.title) }
                SortOption.TitleDesc -> result.sortedByDescending { com.pekempy.ReadAloudbooks.util.StringUtils.normalizeTitle(it.title) }
                SortOption.AuthorAsc -> result.sortedBy { com.pekempy.ReadAloudbooks.util.StringUtils.normalizeTitle(it.author) }
                SortOption.AuthorDesc -> result.sortedByDescending { com.pekempy.ReadAloudbooks.util.StringUtils.normalizeTitle(it.author) }
                SortOption.SeriesAsc -> result.sortedBy { com.pekempy.ReadAloudbooks.util.StringUtils.normalizeTitle(it.series) }
                SortOption.SeriesDesc -> result.sortedByDescending { com.pekempy.ReadAloudbooks.util.StringUtils.normalizeTitle(it.series) }
                SortOption.AddedAsc -> result.sortedBy { it.addedDate }
                SortOption.AddedDesc -> result.sortedByDescending { it.addedDate }
            }
        }
        
        
        books = result.take((currentPage + 1) * LIMIT)
    }

    private fun applyGlobalFilters(baseList: List<Book>): List<Book> {
        var result = baseList
        if (filterHasAudiobook) result = result.filter { it.hasAudiobook }
        if (filterHasEbook) result = result.filter { it.hasEbook }
        if (filterHasReadAloud) result = result.filter { it.hasReadAloud }
        if (filterDownloaded) result = result.filter { it.isDownloaded }
        if (filterCanCreateReadAloud) {
            result = result.filter { !it.hasReadAloud && it.hasEbook && it.hasAudiobook && !it.isReadAloudQueued }
        }

        if (searchQuery.isNotBlank()) {
            val query = searchQuery.trim().lowercase()
            result = result.filter { book ->
                book.title.lowercase().contains(query) ||
                (book.series?.lowercase()?.contains(query) == true) ||
                book.author.lowercase().contains(query) ||
                (book.narrator?.lowercase()?.contains(query) == true)
            }
        }
        return result
    }
    
    private fun getFilteredMasterList(): List<Book> {
        return applyGlobalFilters(allBooks)
    }

    fun getUniqueAuthors(): List<String> {
        val allAuthors = getFilteredMasterList().map { it.author }.distinct().sortedBy { com.pekempy.ReadAloudbooks.util.StringUtils.normalizeTitle(it) }
        return allAuthors.take((currentPage + 1) * LIMIT)
    }

    fun getUniqueSeries(): List<String> {
        val allSeries = getFilteredMasterList().mapNotNull { it.series }.distinct().sortedBy { com.pekempy.ReadAloudbooks.util.StringUtils.normalizeTitle(it) }
        return allSeries.take((currentPage + 1) * LIMIT)
    }

    fun getCoversForAuthor(author: String): List<String> {
        return allBooks.filter { it.author == author }.mapNotNull { it.coverUrl }.distinct().take(4)
    }

    fun getCoversForSeries(series: String): List<String> {
        return allBooks.filter { it.series == series }.mapNotNull { it.coverUrl }.distinct().take(4)
    }

    fun deleteProgress(bookId: String) {
        viewModelScope.launch {
            repository.deleteBookProgress(bookId)
            loadBooks()
        }
    }
}
