package com.pekempy.ReadAloudbooks.data.repository

import com.pekempy.ReadAloudbooks.data.local.dao.AudioBookmarkDao
import com.pekempy.ReadAloudbooks.data.local.entities.AudioBookmark
import kotlinx.coroutines.flow.Flow

class AudioBookmarkRepository(private val audioBookmarkDao: AudioBookmarkDao) {

    fun getAudioBookmarksForBook(bookId: String): Flow<List<AudioBookmark>> {
        return audioBookmarkDao.getAudioBookmarksForBook(bookId)
    }

    suspend fun getAudioBookmarkById(id: Long): AudioBookmark? {
        return audioBookmarkDao.getAudioBookmarkById(id)
    }

    suspend fun addAudioBookmark(audioBookmark: AudioBookmark): Long {
        return audioBookmarkDao.insertAudioBookmark(audioBookmark)
    }

    suspend fun updateAudioBookmark(audioBookmark: AudioBookmark) {
        audioBookmarkDao.updateAudioBookmark(audioBookmark)
    }

    suspend fun deleteAudioBookmark(audioBookmark: AudioBookmark) {
        audioBookmarkDao.deleteAudioBookmark(audioBookmark)
    }

    suspend fun deleteAudioBookmarksForBook(bookId: String) {
        audioBookmarkDao.deleteAudioBookmarksForBook(bookId)
    }

    fun getAllAudioBookmarks(): Flow<List<AudioBookmark>> {
        return audioBookmarkDao.getAllAudioBookmarks()
    }
}
