package com.pekempy.ReadAloudbooks.data.local.dao

import androidx.room.*
import com.pekempy.ReadAloudbooks.data.local.entities.Highlight
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY chapterIndex, timestamp")
    fun getHighlightsForBook(bookId: String): Flow<List<Highlight>>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId AND chapterIndex = :chapterIndex ORDER BY timestamp")
    fun getHighlightsForChapter(bookId: String, chapterIndex: Int): Flow<List<Highlight>>

    @Query("SELECT * FROM highlights WHERE id = :id")
    suspend fun getHighlightById(id: Long): Highlight?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: Highlight): Long

    @Update
    suspend fun updateHighlight(highlight: Highlight)

    @Delete
    suspend fun deleteHighlight(highlight: Highlight)

    @Query("DELETE FROM highlights WHERE bookId = :bookId")
    suspend fun deleteHighlightsForBook(bookId: String)

    @Query("SELECT * FROM highlights WHERE syncedToServer = 0")
    suspend fun getUnsyncedHighlights(): List<Highlight>

    @Query("UPDATE highlights SET syncedToServer = 1, serverId = :serverId WHERE id = :id")
    suspend fun markAsSynced(id: Long, serverId: String)

    @Query("SELECT * FROM highlights ORDER BY timestamp DESC")
    fun getAllHighlights(): Flow<List<Highlight>>
}
