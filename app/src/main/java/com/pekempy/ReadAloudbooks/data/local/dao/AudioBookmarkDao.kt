package com.pekempy.ReadAloudbooks.data.local.dao

import androidx.room.*
import com.pekempy.ReadAloudbooks.data.local.entities.AudioBookmark
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioBookmarkDao {
    @Query("SELECT * FROM audio_bookmarks WHERE bookId = :bookId ORDER BY timestampMillis")
    fun getAudioBookmarksForBook(bookId: String): Flow<List<AudioBookmark>>

    @Query("SELECT * FROM audio_bookmarks WHERE id = :id")
    suspend fun getAudioBookmarkById(id: Long): AudioBookmark?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioBookmark(audioBookmark: AudioBookmark): Long

    @Update
    suspend fun updateAudioBookmark(audioBookmark: AudioBookmark)

    @Delete
    suspend fun deleteAudioBookmark(audioBookmark: AudioBookmark)

    @Query("DELETE FROM audio_bookmarks WHERE bookId = :bookId")
    suspend fun deleteAudioBookmarksForBook(bookId: String)

    @Query("SELECT * FROM audio_bookmarks WHERE syncedToServer = 0")
    suspend fun getUnsyncedAudioBookmarks(): List<AudioBookmark>

    @Query("UPDATE audio_bookmarks SET syncedToServer = 1, serverId = :serverId WHERE id = :id")
    suspend fun markAsSynced(id: Long, serverId: String)

    @Query("SELECT * FROM audio_bookmarks ORDER BY timestamp DESC")
    fun getAllAudioBookmarks(): Flow<List<AudioBookmark>>
}
