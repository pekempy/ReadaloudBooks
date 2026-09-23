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

class ReadingAnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val statsRepository = ReadingStatsRepository(application)
    private val bookRepository = BookRepository(application, UserPreferencesRepository(application))

    private val _stats = MutableStateFlow<ReadingStats?>(null)
    val stats: StateFlow<ReadingStats?> = _stats

    private val _resolvedBooks = MutableStateFlow<List<BookStatWithMeta>>(emptyList())
    val resolvedBooks: StateFlow<List<BookStatWithMeta>> = _resolvedBooks

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
                _resolvedBooks.value = updatedStats.readingByBook.map { stat ->
                    BookStatWithMeta(stat, booksById[stat.bookId])
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
