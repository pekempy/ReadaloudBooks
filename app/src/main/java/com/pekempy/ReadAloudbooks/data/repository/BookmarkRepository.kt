package com.pekempy.ReadAloudbooks.data.repository

import com.pekempy.ReadAloudbooks.data.local.dao.BookmarkDao
import com.pekempy.ReadAloudbooks.data.local.entities.Bookmark
import kotlinx.coroutines.flow.Flow

class BookmarkRepository(private val bookmarkDao: BookmarkDao) {

    fun getBookmarksForBook(bookId: String): Flow<List<Bookmark>> {
        return bookmarkDao.getBookmarksForBook(bookId)
    }

    suspend fun getBookmarkById(id: Long): Bookmark? {
        return bookmarkDao.getBookmarkById(id)
    }

    suspend fun addBookmark(bookmark: Bookmark): Long {
        return bookmarkDao.insertBookmark(bookmark)
    }

    suspend fun updateBookmark(bookmark: Bookmark) {
        bookmarkDao.updateBookmark(bookmark)
    }

    suspend fun deleteBookmark(bookmark: Bookmark) {
        bookmarkDao.deleteBookmark(bookmark)
    }

    suspend fun deleteBookmarksForBook(bookId: String) {
        bookmarkDao.deleteBookmarksForBook(bookId)
    }

    fun getAllBookmarks(): Flow<List<Bookmark>> {
        return bookmarkDao.getAllBookmarks()
    }
}
