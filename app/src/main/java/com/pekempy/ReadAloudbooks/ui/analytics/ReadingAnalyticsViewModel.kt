package com.pekempy.ReadAloudbooks.ui.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pekempy.ReadAloudbooks.data.Book
import com.pekempy.ReadAloudbooks.data.BookStats
import com.pekempy.ReadAloudbooks.data.ReadingStatsRepository
import com.pekempy.ReadAloudbooks.data.ReadingStats
import com.pekempy.ReadAloudbooks.data.UserPreferencesRepository
import com.pekempy.ReadAloudbooks.data.db.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A reading-stats row paired with the book's real title/author/cover, when known locally. */
data class BookStatWithMeta(val stat: BookStats, val book: Book?)

/** An author paired with the combined listening/reading time across all of their books. */
data class AuthorStat(val author: String, val totalTimeMs: Long, val bookCount: Int)

class ReadingAnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val statsRepository = ReadingStatsRepository(application)
    private val bookRepository = BookRepository(application, UserPreferencesRepository(application))

    private val _stats = MutableStateFlow<ReadingStats?>(null)
    val stats: StateFlow<ReadingStats?> = _stats

    private val _resolvedBooks = MutableStateFlow<List<BookStatWithMeta>>(emptyList())
    val resolvedBooks: StateFlow<List<BookStatWithMeta>> = _resolvedBooks

    private val _favoriteBook = MutableStateFlow<BookStatWithMeta?>(null)
    val favoriteBook: StateFlow<BookStatWithMeta?> = _favoriteBook

    private val _favoriteAuthor = MutableStateFlow<AuthorStat?>(null)
    val favoriteAuthor: StateFlow<AuthorStat?> = _favoriteAuthor

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updatedStats = statsRepository.getStats()
                _stats.value = updatedStats

                val booksById = withContext(Dispatchers.IO) {
                    bookRepository.getAllBooksFromLocal().associateBy { it.id }
                }
                val resolved = updatedStats.readingByBook.map { stat ->
                    BookStatWithMeta(stat, booksById[stat.bookId])
                }
                _resolvedBooks.value = resolved

                _favoriteBook.value = resolved.maxByOrNull { it.stat.totalTimeMs }

                _favoriteAuthor.value = resolved
                    .mapNotNull { entry -> entry.book?.author?.takeIf { it.isNotBlank() }?.let { it to entry.stat.totalTimeMs } }
                    .groupBy({ it.first }, { it.second })
                    .map { (author, times) -> AuthorStat(author, times.sum(), times.size) }
                    .maxByOrNull { it.totalTimeMs }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
