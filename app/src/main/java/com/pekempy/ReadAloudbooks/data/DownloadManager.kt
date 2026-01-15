package com.pekempy.ReadAloudbooks.data

import androidx.compose.runtime.*
import com.pekempy.ReadAloudbooks.data.api.AppContainer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

data class DownloadJob(
    val book: Book,
    val fileName: String,
    val totalFiles: Int,
    val currentFileIndex: Int,
    var progress: Float = 0f,
    var status: String = "",
    var isFailed: Boolean = false,
    var isCompleted: Boolean = false
)

object DownloadManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadSemaphore = Semaphore(3)
    
    var activeDownloads = mutableStateListOf<DownloadJob>()
        private set
    
    private val activeJobs = mutableMapOf<String, Job>()

    enum class DownloadType { Audio, Ebook, ReadAloud, All }

    fun cancelDownload(bookId: String) {
        activeJobs[bookId]?.cancel()
        activeJobs.remove(bookId)
        scope.launch(Dispatchers.Main) {
            activeDownloads.removeAll { it.book.id == bookId }
        }
    }

    fun downloadAll(book: Book, filesDir: File) {
        download(book, filesDir, DownloadType.All)
    }

    fun download(book: Book, filesDir: File, type: DownloadType) {
        scope.launch(Dispatchers.Main) {
            if (activeDownloads.any { it.book.id == book.id && !it.isCompleted && !it.isFailed }) return@launch

            val baseFileName = com.pekempy.ReadAloudbooks.util.DownloadUtils.getBaseFileName(book)
            val bookDir = com.pekempy.ReadAloudbooks.util.DownloadUtils.getBookDir(filesDir, book)
            bookDir.mkdirs()

            val downloads = mutableListOf<Pair<String, String>>()
            val hasReadAloud = book.hasReadAloud && !book.syncedUrl.isNullOrBlank()
            
            var shouldDownloadAudio = false
            var shouldDownloadEbook = false
            var shouldDownloadReadAloud = false

            if (hasReadAloud) {
                shouldDownloadReadAloud = true
            } else {
                shouldDownloadAudio = (type == DownloadType.All || type == DownloadType.Audio) && book.hasAudiobook
                shouldDownloadEbook = (type == DownloadType.All || type == DownloadType.Ebook) && book.hasEbook
                shouldDownloadReadAloud = (type == DownloadType.ReadAloud) 
            }

            if (shouldDownloadAudio) book.audiobookUrl?.takeIf { it.isNotBlank() }?.let { downloads.add(it to "$baseFileName.m4b") }
            if (shouldDownloadEbook) book.ebookUrl?.takeIf { it.isNotBlank() }?.let { downloads.add(it to "$baseFileName.epub") }
            if (shouldDownloadReadAloud) book.syncedUrl?.takeIf { it.isNotBlank() }?.let { downloads.add(it to "$baseFileName (readaloud).epub") }

            if (downloads.isEmpty()) return@launch

            val jobData = DownloadJob(
                book = book,
                fileName = downloads.first().second,
                totalFiles = downloads.size,
                currentFileIndex = 0,
                status = "Queued"
            )
            activeDownloads.removeAll { it.book.id == book.id }
            activeDownloads.add(jobData)

            val downloadJob = launch(Dispatchers.IO) {
                val apiManager = AppContainer.apiClientManager
                val client = apiManager.downloadClient ?: return@launch

                downloadSemaphore.withPermit {
                    var successCount = 0
                    downloads.forEachIndexed { index, (url, fileName) ->
                        withContext(Dispatchers.Main) {
                            val jobIndex = activeDownloads.indexOfFirst { it.book.id == book.id }
                            if (jobIndex != -1) {
                                activeDownloads[jobIndex] = activeDownloads[jobIndex].copy(
                                    fileName = fileName,
                                    currentFileIndex = index,
                                    status = "Downloading ${index + 1}/${downloads.size}: $fileName",
                                    progress = 0f
                                )
                            }
                        }

                        if (!isActive) return@forEachIndexed

                        try {
                            com.pekempy.ReadAloudbooks.util.DownloadUtils.downloadFile(client, url, File(bookDir, fileName)) { progress ->
                                launch(Dispatchers.Main) {
                                    val currentIndex = activeDownloads.indexOfFirst { it.book.id == book.id }
                                    if (currentIndex != -1) {
                                        activeDownloads[currentIndex] = activeDownloads[currentIndex].copy(progress = progress)
                                    }
                                }
                            }
                            successCount++
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                val currentIndex = activeDownloads.indexOfFirst { it.book.id == book.id }
                                if (currentIndex != -1) {
                                    activeDownloads[currentIndex] = activeDownloads[currentIndex].copy(isFailed = true, status = "Failed: ${e.message}")
                                }
                            }
                            if (e is CancellationException) throw e
                            return@forEachIndexed 
                        }
                    }

                    withContext(Dispatchers.Main) {
                        val finalIndex = activeDownloads.indexOfFirst { it.book.id == book.id }
                        if (finalIndex != -1) {
                            activeDownloads[finalIndex] = activeDownloads[finalIndex].copy(
                                isCompleted = true,
                                status = "Completed",
                                progress = 1f
                            )
                        }
                    }
                }
            }
            
            activeJobs[book.id] = downloadJob
            
            downloadJob.invokeOnCompletion { 
                 scope.launch(Dispatchers.Main) {
                     activeJobs.remove(book.id)
                 }
            }
        }
    }

    fun removeJob(job: DownloadJob) {
        scope.launch(Dispatchers.Main) {
            activeDownloads.remove(job)
        }
    }
}
