package com.pekempy.ReadAloudbooks.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ReadingStatsRepository(private val context: Context) {
    
    private val dbHelper = ReadingSessionsDbHelper(context)
    
    suspend fun insertSession(session: ReadingSession) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("book_id", session.bookId)
            put("start_time", session.startTime)
            put("end_time", session.endTime)
            put("duration_ms", session.durationMs)
            put("book_type", session.bookType)
        }
        db.insert("reading_sessions", null, values)
    }
    
    suspend fun getStats(): ReadingStats = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - (30 * 24 * 60 * 60 * 1000)
        
        // Get total reading time
        val totalCursor = db.rawQuery(
            "SELECT SUM(duration_ms) as total FROM reading_sessions",
            null
        )
        val totalTime = if (totalCursor.moveToFirst()) {
            totalCursor.getLong(0)
        } else {
            0L
        }
        totalCursor.close()
        
        // Get average session time
        val avgCursor = db.rawQuery(
            "SELECT AVG(duration_ms) as avg FROM reading_sessions WHERE duration_ms > 10000",
            null
        )
        val avgTime = if (avgCursor.moveToFirst()) {
            avgCursor.getLong(0)
        } else {
            0L
        }
        avgCursor.close()
        
        // Get reading by day (last 30 days)
        val daysCursor = db.rawQuery(
            """
            SELECT DATE(start_time / 1000, 'unixepoch') as date, SUM(duration_ms) as duration
            FROM reading_sessions
            WHERE start_time >= ?
            GROUP BY DATE(start_time / 1000, 'unixepoch')
            ORDER BY date DESC
            """,
            arrayOf(thirtyDaysAgo.toString())
        )
        
        val readingByDay = mutableMapOf<String, Long>()
        while (daysCursor.moveToNext()) {
            readingByDay[daysCursor.getString(0)] = daysCursor.getLong(1)
        }
        daysCursor.close()
        
        // Calculate current streak
        val currentStreak = calculateStreak(db, now)
        
        // Get reading by book
        val readingByBook = getReadingByBook(db)
        
        return@withContext ReadingStats(
            totalReadingTimeMs = totalTime,
            booksFinished = 0, // Would need to implement finish tracking
            currentStreak = currentStreak,
            averageSessionMs = avgTime,
            readingByDay = readingByDay,
            readingByBook = readingByBook
        )
    }
    
    suspend fun getSessionsForBook(bookId: String): List<ReadingSession> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "reading_sessions",
            arrayOf("book_id", "start_time", "end_time", "duration_ms", "book_type"),
            "book_id = ?",
            arrayOf(bookId),
            null,
            null,
            "start_time DESC"
        )
        
        val sessions = mutableListOf<ReadingSession>()
        while (cursor.moveToNext()) {
            sessions.add(
                ReadingSession(
                    bookId = cursor.getString(0),
                    startTime = cursor.getLong(1),
                    endTime = cursor.getLong(2),
                    durationMs = cursor.getLong(3),
                    bookType = cursor.getString(4)
                )
            )
        }
        cursor.close()
        return@withContext sessions
    }
    
    private fun calculateStreak(db: SQLiteDatabase, now: Long): Int {
        val cursor = db.rawQuery(
            """
            SELECT DATE(start_time / 1000, 'unixepoch') as date
            FROM reading_sessions
            GROUP BY DATE(start_time / 1000, 'unixepoch')
            ORDER BY date DESC
            """,
            null
        )
        
        var streak = 0
        var expectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        
        while (cursor.moveToNext()) {
            val date = cursor.getString(0)
            if (date == expectedDate) {
                streak++
                val cal = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                }
                expectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            } else {
                break
            }
        }
        cursor.close()
        return streak
    }
    
    private fun getReadingByBook(db: SQLiteDatabase): List<BookStats> {
        val cursor = db.rawQuery(
            """
            SELECT book_id, SUM(duration_ms) as total, MAX(start_time) as last_read
            FROM reading_sessions
            GROUP BY book_id
            ORDER BY last_read DESC
            """,
            null
        )
        
        val stats = mutableListOf<BookStats>()
        while (cursor.moveToNext()) {
            stats.add(
                BookStats(
                    bookId = cursor.getString(0),
                    title = "", // Would need to join with books table to get title
                    totalTimeMs = cursor.getLong(1),
                    progress = 0f, // Would need progress tracking from books
                    lastRead = cursor.getLong(2)
                )
            )
        }
        cursor.close()
        return stats
    }
}

private class ReadingSessionsDbHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        const val DATABASE_NAME = "reading_sessions.db"
        const val DATABASE_VERSION = 1
    }
    
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE reading_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                book_id TEXT NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                book_type TEXT NOT NULL
            )
            """
        )
        db.execSQL("CREATE INDEX idx_book_id ON reading_sessions(book_id)")
        db.execSQL("CREATE INDEX idx_start_time ON reading_sessions(start_time)")
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS reading_sessions")
        onCreate(db)
    }
}
