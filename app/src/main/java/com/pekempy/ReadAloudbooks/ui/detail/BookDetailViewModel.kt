package com.pekempy.ReadAloudbooks.ui.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pekempy.ReadAloudbooks.data.Book
import com.pekempy.ReadAloudbooks.data.api.AppContainer
import kotlinx.coroutines.launch

import com.pekempy.ReadAloudbooks.data.UserPreferencesRepository
import com.pekempy.ReadAloudbooks.data.repository.BookMetadataRepository
import com.pekempy.ReadAloudbooks.data.repository.CollectionRepository
import com.pekempy.ReadAloudbooks.data.local.entities.ReadingStatus
import com.pekempy.ReadAloudbooks.data.local.entities.Collection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow

class BookDetailViewModel(
    private val repository: UserPreferencesRepository,
    private val metadataRepository: BookMetadataRepository,
    private val collectionRepository: CollectionRepository
) : ViewModel() {
    var book by mutableStateOf<Book?>(null)
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var localProgress by mutableStateOf<Float?>(null)
    var serverProgress by mutableStateOf<Float?>(null)
    var readingStatus by mutableStateOf(ReadingStatus.NONE)
    var rating by mutableStateOf(0)
    var showCollectionsDialog by mutableStateOf(false)

    val allCollections: Flow<List<Collection>> = collectionRepository.getAllCollections()

    fun loadBook(uuid: String, showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                isLoading = true
            }
            error = null
            try {
                val apiManager = AppContainer.apiClientManager
                val response = apiManager.getApi().getBookDetails(uuid)
                
                val apiSeries = response.series?.firstOrNull()
                val apiCollection = response.collections?.firstOrNull()
                val seriesName = apiSeries?.name ?: apiCollection?.name
                val seriesIdx = apiSeries?.seriesIndex ?: apiCollection?.seriesIndex

                val tempBook = Book(
                    id = response.uuid,
                    title = response.title,
                    author = response.authors.joinToString(", ") { it.name },
                    narrator = response.narrators?.joinToString(", ") { it.name },
                    coverUrl = apiManager.getCoverUrl(response.uuid, response.updatedAt),
                    description = response.description,
                    hasReadAloud = response.readaloud != null && !response.readaloud.filepath.isNullOrBlank(),
                    hasEbook = response.ebook != null,
                    hasAudiobook = response.audiobook != null,
                    syncedUrl = apiManager.getSyncDownloadUrl(response.uuid),
                    audiobookUrl = apiManager.getAudiobookDownloadUrl(response.uuid),
                    ebookUrl = apiManager.getEbookDownloadUrl(response.uuid),
                    series = seriesName,
                    seriesIndex = seriesIdx,
                    addedDate = System.currentTimeMillis(),
                    ebookCoverUrl = if (response.ebook != null) apiManager.getEbookCoverUrl(response.uuid, response.updatedAt) else null,
                    audiobookCoverUrl = if (response.audiobook != null) apiManager.getAudiobookCoverUrl(response.uuid, response.updatedAt) else null,
                    updatedAt = response.updatedAt
                )
                
                val progressStr = repository.getBookProgress(uuid).first()
                val up = com.pekempy.ReadAloudbooks.data.UnifiedProgress.fromString(progressStr)
                this@BookDetailViewModel.localProgress = up?.getOverallProgress()
                
                try {
                    val serverPos = apiManager.getApi().getPosition(uuid)
                    if (serverPos != null) {
                        val serverTotal = serverPos.locator.locations.totalProgression?.toFloat()
                        if (serverTotal != null) {
                            this@BookDetailViewModel.serverProgress = serverTotal
                        } else {
                            val serverUp = com.pekempy.ReadAloudbooks.data.UnifiedProgress.fromPosition(serverPos)
                            this@BookDetailViewModel.serverProgress = serverUp.getOverallProgress()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("BookDetailVM", "Failed to fetch server position: ${e.message}")
                }

                book = tempBook.copy(
                    isDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isBookDownloaded(AppContainer.context.filesDir, tempBook),
                    isAudiobookDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isAudiobookDownloaded(AppContainer.context.filesDir, tempBook),
                    isEbookDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isEbookDownloaded(AppContainer.context.filesDir, tempBook),
                    isReadAloudDownloaded = com.pekempy.ReadAloudbooks.util.DownloadUtils.isReadAloudDownloaded(AppContainer.context.filesDir, tempBook),
                    isReadAloudQueued = response.readaloud != null && response.readaloud.filepath.isNullOrBlank() && response.readaloud.status != "STOPPED",
                    processingStatus = response.readaloud?.status,
                    currentProcessingStage = response.readaloud?.currentStage,
                    processingProgress = response.readaloud?.stageProgress?.toFloat(),
                    queuePosition = response.readaloud?.queuePosition,
                    progress = this@BookDetailViewModel.localProgress
                )

                // Load metadata (reading status and rating)
                loadMetadata(uuid)
            } catch (e: Exception) {
                error = "Failed to load book: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    private fun loadMetadata(bookId: String) {
        viewModelScope.launch {
            val metadata = metadataRepository.getBookMetadataSync(bookId)
            readingStatus = metadata?.readingStatus ?: ReadingStatus.NONE
            rating = metadata?.rating ?: 0
        }
    }

    fun updateReadingStatus(status: ReadingStatus) {
        val currentBookId = book?.id ?: return
        readingStatus = status
        viewModelScope.launch {
            metadataRepository.updateReadingStatus(currentBookId, status)
        }
    }

    fun updateRating(newRating: Int) {
        val currentBookId = book?.id ?: return
        rating = newRating
        viewModelScope.launch {
            metadataRepository.setBookRating(currentBookId, newRating)
        }
    }

    suspend fun isBookInCollection(collectionId: Long, bookId: String): Boolean {
        return collectionRepository.isBookInCollection(collectionId, bookId)
    }

    fun toggleBookInCollection(collectionId: Long) {
        val currentBookId = book?.id ?: return
        viewModelScope.launch {
            if (collectionRepository.isBookInCollection(collectionId, currentBookId)) {
                collectionRepository.removeBookFromCollection(collectionId, currentBookId)
            } else {
                collectionRepository.addBookToCollection(collectionId, currentBookId)
            }
        }
    }

    val activeDownload: com.pekempy.ReadAloudbooks.data.DownloadJob?
        get() = com.pekempy.ReadAloudbooks.data.DownloadManager.activeDownloads.find { it.book.id == book?.id }

    fun downloadAll(filesDir: java.io.File) {
        val currentBook = book ?: return
        com.pekempy.ReadAloudbooks.data.DownloadManager.downloadAll(currentBook, filesDir)
    }

    fun downloadAudiobook(filesDir: java.io.File) {
        val currentBook = book ?: return
        com.pekempy.ReadAloudbooks.data.DownloadManager.download(currentBook, filesDir, com.pekempy.ReadAloudbooks.data.DownloadManager.DownloadType.Audio)
    }

    fun downloadEbook(filesDir: java.io.File) {
        val currentBook = book ?: return
        com.pekempy.ReadAloudbooks.data.DownloadManager.download(currentBook, filesDir, com.pekempy.ReadAloudbooks.data.DownloadManager.DownloadType.Ebook)
    }

    fun downloadReadAloud(filesDir: java.io.File) {
        val currentBook = book ?: return
        com.pekempy.ReadAloudbooks.data.DownloadManager.download(currentBook, filesDir, com.pekempy.ReadAloudbooks.data.DownloadManager.DownloadType.ReadAloud)
    }

    fun createReadAloud() {
        val currentBook = book ?: return
        
        val previousState = currentBook
        book = currentBook.copy(
            isReadAloudQueued = true,
            processingStatus = "QUEUED",
            currentProcessingStage = "Queued",
            processingProgress = 0f
        )

        viewModelScope.launch {
            try {
                AppContainer.apiClientManager.getApi().processBook(currentBook.id)
            } catch (e: Exception) {
                android.util.Log.e("BookDetailVM", "Failed to create readaloud: ${e.message}")
                loadBook(currentBook.id, showLoading = false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        book = null
    }
}
