package com.pekempy.ReadAloudbooks.data.local.dao

import androidx.room.*
import com.pekempy.ReadAloudbooks.data.local.entities.Bookmark
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY chapterIndex, scrollPercent")
    fun getBookmarksForBook(bookId: String): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun getBookmarkById(id: Long): Bookmark?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark): Long

    @Update
    suspend fun updateBookmark(bookmark: Bookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteBookmarksForBook(bookId: String)

    @Query("SELECT * FROM bookmarks WHERE syncedToServer = 0")
    suspend fun getUnsyncedBookmarks(): List<Bookmark>

    @Query("UPDATE bookmarks SET syncedToServer = 1, serverId = :serverId WHERE id = :id")
    suspend fun markAsSynced(id: Long, serverId: String)

    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<Bookmark>>
}
