package com.pekempy.ReadAloudbooks.data.local.dao

import androidx.room.*
import com.pekempy.ReadAloudbooks.data.local.entities.ReadingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {
    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY startTime DESC")
    fun getSessionsForBook(bookId: String): Flow<List<ReadingSession>>

    @Query("SELECT * FROM reading_sessions ORDER BY startTime DESC LIMIT :limit")
    fun getRecentSessions(limit: Int = 50): Flow<List<ReadingSession>>

    @Query("SELECT * FROM reading_sessions WHERE startTime >= :startTime AND endTime <= :endTime ORDER BY startTime")
    fun getSessionsInRange(startTime: Long, endTime: Long): Flow<List<ReadingSession>>

    @Query("SELECT SUM(durationMillis) FROM reading_sessions WHERE startTime >= :startTime AND endTime <= :endTime")
    suspend fun getTotalReadingTimeInRange(startTime: Long, endTime: Long): Long?

    @Query("SELECT SUM(pagesRead) FROM reading_sessions WHERE startTime >= :startTime AND endTime <= :endTime")
    suspend fun getTotalPagesReadInRange(startTime: Long, endTime: Long): Int?

    @Query("SELECT COUNT(DISTINCT bookId) FROM reading_sessions WHERE endTime >= :startTime AND endTime <= :endTime AND startTime <= :endTime")
    suspend fun getDistinctBooksReadInRange(startTime: Long, endTime: Long): Int

    @Query("SELECT * FROM reading_sessions WHERE startTime >= :startTime ORDER BY startTime")
    suspend fun getSessionsSince(startTime: Long): List<ReadingSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReadingSession): Long

    @Update
    suspend fun updateSession(session: ReadingSession)

    @Delete
    suspend fun deleteSession(session: ReadingSession)

    @Query("DELETE FROM reading_sessions WHERE bookId = :bookId")
    suspend fun deleteSessionsForBook(bookId: String)

    @Query("SELECT * FROM reading_sessions WHERE syncedToServer = 0")
    suspend fun getUnsyncedSessions(): List<ReadingSession>

    @Query("UPDATE reading_sessions SET syncedToServer = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)
}
