package com.pekempy.ReadAloudbooks.data.repository

import com.pekempy.ReadAloudbooks.data.local.dao.HighlightDao
import com.pekempy.ReadAloudbooks.data.local.entities.Highlight
import kotlinx.coroutines.flow.Flow

class HighlightRepository(private val highlightDao: HighlightDao) {

    fun getHighlightsForBook(bookId: String): Flow<List<Highlight>> {
        return highlightDao.getHighlightsForBook(bookId)
    }

    fun getHighlightsForChapter(bookId: String, chapterIndex: Int): Flow<List<Highlight>> {
        return highlightDao.getHighlightsForChapter(bookId, chapterIndex)
    }

    suspend fun getHighlightById(id: Long): Highlight? {
        return highlightDao.getHighlightById(id)
    }

    suspend fun addHighlight(highlight: Highlight): Long {
        return highlightDao.insertHighlight(highlight)
    }

    suspend fun updateHighlight(highlight: Highlight) {
        highlightDao.updateHighlight(highlight)
    }

    suspend fun deleteHighlight(highlight: Highlight) {
        highlightDao.deleteHighlight(highlight)
    }

    suspend fun deleteHighlightsForBook(bookId: String) {
        highlightDao.deleteHighlightsForBook(bookId)
    }

    fun getAllHighlights(): Flow<List<Highlight>> {
        return highlightDao.getAllHighlights()
    }

    suspend fun syncHighlights() {
        // TODO: Implement server sync logic
        val unsynced = highlightDao.getUnsyncedHighlights()
        // Sync with server and mark as synced
    }
}
