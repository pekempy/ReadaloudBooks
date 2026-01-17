package com.pekempy.ReadAloudbooks.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.pekempy.ReadAloudbooks.data.Book
import com.pekempy.ReadAloudbooks.data.api.AppContainer
import java.io.File
import java.util.Date

data class StorageItem(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long
)

data class BookStorageItem(
    val book: Book,
    val directory: File,
    val items: List<StorageItem>,
    val totalSize: Long,
    val lastModified: Long
)

class StorageManagementViewModel(private val repository: com.pekempy.ReadAloudbooks.data.UserPreferencesRepository) : ViewModel() {
    var bookItems by mutableStateOf<List<BookStorageItem>>(emptyList())
    var isLoading by mutableStateOf(false)
    var selectedBookItem by mutableStateOf<BookStorageItem?>(null)
    var deleteDialogItem by mutableStateOf<BookStorageItem?>(null)

    enum class SortOption { RecentAsc, RecentDesc, SizeAsc, SizeDesc }
    var currentSort by mutableStateOf(SortOption.RecentDesc)

    fun loadFiles(filesDir: java.io.File) {
        viewModelScope.launch {
            isLoading = true
            
            val allBooks = try {
                val credentials = repository.userCredentials.first()
                if (credentials != null) {
                    val apiManager = AppContainer.apiClientManager
                    apiManager.getApi().listBooks().map { apiBook ->
                        val apiSeries = apiBook.series?.firstOrNull()
                        val apiCollection = apiBook.collections?.firstOrNull()
                        Book(
                            id = apiBook.uuid,
                            title = apiBook.title,
                            author = apiBook.authors.joinToString(", ") { it.name },
                            coverUrl = apiManager.getCoverUrl(apiBook.uuid),
                            series = apiSeries?.name ?: apiCollection?.name,
                            seriesIndex = apiBook.series?.firstNotNullOfOrNull { it.seriesIndex } ?: apiBook.collections?.firstNotNullOfOrNull { it.seriesIndex },
                            addedDate = 0L
                        )
                    }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }


            val allFiles = mutableListOf<Pair<File, StorageItem>>()
            
            fun scanDir(dir: File) {
                val files = dir.listFiles() ?: return
                files.forEach { file ->
                    if (file.isDirectory) {
                        scanDir(file)
                    } else if (file.name.endsWith(".epub") || file.name.endsWith(".m4b")) {
                        allFiles.add(
                            dir to StorageItem(
                                file = file,
                                name = file.name,
                                sizeBytes = file.length(),
                                lastModified = file.lastModified()
                            )
                        )
                    }
                }
            }
            
            scanDir(filesDir)
            
            val bookGroups = mutableMapOf<String, MutableList<Pair<File, StorageItem>>>()
            val unmatchedFiles = mutableListOf<Pair<File, StorageItem>>()
            
            allFiles.forEach { (dir, item) ->
                var matched = false
                
                for (book in allBooks) {
                    val bookDir = com.pekempy.ReadAloudbooks.util.DownloadUtils.getBookDir(filesDir, book)
                    val baseFileName = com.pekempy.ReadAloudbooks.util.DownloadUtils.getBaseFileName(book)
                    
                    if (dir.absolutePath == bookDir.absolutePath && 
                        (item.name == "$baseFileName.epub" || 
                         item.name == "$baseFileName (readaloud).epub" || 
                         item.name == "$baseFileName.m4b")) {
                        bookGroups.getOrPut(book.id) { mutableListOf() }.add(dir to item)
                        matched = true
                        break
                    }
                }
                
                if (!matched) {
                    unmatchedFiles.add(dir to item)
                }
            }
            
            val finalBookItems = mutableListOf<BookStorageItem>()
            
            bookGroups.forEach { (bookId, fileList) ->
                val book = allBooks.find { it.id == bookId }!!
                val dir = fileList.first().first
                val items = fileList.map { it.second }
                
                finalBookItems.add(
                    BookStorageItem(
                        book = book,
                        directory = dir,
                        items = items,
                        totalSize = items.sumOf { it.sizeBytes },
                        lastModified = items.maxOf { it.lastModified }
                    )
                )
            }
            
            val unmatchedGroups = unmatchedFiles.groupBy { (dir, item) ->
                val baseName = item.name
                    .replace(" (readaloud)", "")
                    .substringBeforeLast(".")
                "${dir.absolutePath}/$baseName"
            }
            
            unmatchedGroups.forEach { (key, fileList) ->
                val dir = fileList.first().first
                val items = fileList.map { it.second }
                
                val parentDir = dir.parentFile
                val author = if (parentDir != null && parentDir != filesDir) {
                    parentDir.name
                } else {
                    "Unknown Author"
                }
                
                val title = items.first().name
                    .replace(" (readaloud)", "")
                    .substringBeforeLast(".")
                
                val displayBook = Book(
                    id = "local_${key.hashCode()}",
                    title = title,
                    author = author,
                    coverUrl = null,
                    series = null,
                    seriesIndex = null,
                    addedDate = items.maxOfOrNull { it.lastModified } ?: 0L
                )
                
                finalBookItems.add(
                    BookStorageItem(
                        book = displayBook,
                        directory = dir,
                        items = items,
                        totalSize = items.sumOf { it.sizeBytes },
                        lastModified = items.maxOf { it.lastModified }
                    )
                )
            }
            
            bookItems = sortItems(finalBookItems, currentSort)
            isLoading = false
        }
    }

    fun setSort(sort: SortOption) {
        currentSort = sort
        bookItems = sortItems(bookItems, sort)
    }

    private fun sortItems(items: List<BookStorageItem>, sort: SortOption): List<BookStorageItem> {
        return when (sort) {
            SortOption.RecentAsc -> items.sortedBy { it.lastModified }
            SortOption.RecentDesc -> items.sortedByDescending { it.lastModified }
            SortOption.SizeAsc -> items.sortedBy { it.totalSize }
            SortOption.SizeDesc -> items.sortedByDescending { it.totalSize }
        }
    }

    fun deleteBook(item: BookStorageItem) {
        if (item.items.size > 1) {
            deleteDialogItem = item
        } else {
            confirmDeleteFiles(item, item.items)
        }
    }

    fun confirmDeleteFiles(item: BookStorageItem, filesToDelete: List<StorageItem>) {
        viewModelScope.launch {
            filesToDelete.forEach { it.file.delete() }
            
            val remainingItems = item.items.filter { it !in filesToDelete }
            
            if (remainingItems.isEmpty()) {
                var currentDir = item.directory
                while (currentDir.listFiles()?.isEmpty() == true) {
                    val parent = currentDir.parentFile
                    currentDir.delete()
                    if (parent == null) break
                    currentDir = parent
                }
                bookItems = bookItems.filter { it.book.id != item.book.id }
                if (selectedBookItem?.book?.id == item.book.id) {
                    selectedBookItem = null
                }
            } else {
                val updatedItem = item.copy(
                    items = remainingItems,
                    totalSize = remainingItems.sumOf { it.sizeBytes },
                    lastModified = remainingItems.maxOf { it.lastModified }
                )
                bookItems = bookItems.map { 
                    if (it.book.id == item.book.id) updatedItem else it
                }
                if (selectedBookItem?.book?.id == item.book.id) {
                    selectedBookItem = updatedItem
                }
            }
            
            deleteDialogItem = null
        }
    }

    fun deleteFile(bookItem: BookStorageItem, fileItem: StorageItem) {
        viewModelScope.launch {
            if (fileItem.file.delete()) {
                val newFiles = bookItem.items.filter { it.file != fileItem.file }
                if (newFiles.isEmpty()) {
                    deleteBook(bookItem)
                } else {
                    val updatedBook = bookItem.copy(
                        items = newFiles,
                        totalSize = newFiles.sumOf { f -> f.sizeBytes },
                        lastModified = newFiles.maxOf { f -> f.lastModified }
                    )
                    bookItems = bookItems.map { 
                        if (it.book.id == bookItem.book.id) updatedBook else it
                    }
                    if (selectedBookItem?.book?.id == bookItem.book.id) {
                        selectedBookItem = updatedBook
                    }
                }
            }
        }
    }
}
