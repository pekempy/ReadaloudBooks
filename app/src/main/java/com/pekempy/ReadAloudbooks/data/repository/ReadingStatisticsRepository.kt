package com.pekempy.ReadAloudbooks.data.repository

import com.pekempy.ReadAloudbooks.data.local.dao.ReadingSessionDao
import com.pekempy.ReadAloudbooks.data.local.entities.ReadingSession
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class ReadingStatisticsRepository(
    private val readingSessionDao: ReadingSessionDao
) {

    fun getRecentSessions(limit: Int = 50): Flow<List<ReadingSession>> {
        return readingSessionDao.getRecentSessions(limit)
    }

    fun getSessionsForBook(bookId: String): Flow<List<ReadingSession>> {
        return readingSessionDao.getSessionsForBook(bookId)
    }

    suspend fun addSession(session: ReadingSession): Long {
        return readingSessionDao.insertSession(session)
    }

    suspend fun getTotalReadingTimeToday(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        val endOfDay = System.currentTimeMillis()
        return readingSessionDao.getTotalReadingTimeInRange(startOfDay, endOfDay) ?: 0L
    }

    suspend fun getTotalReadingTimeThisWeek(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfWeek = calendar.timeInMillis
        val now = System.currentTimeMillis()
        return readingSessionDao.getTotalReadingTimeInRange(startOfWeek, now) ?: 0L
    }

    suspend fun getTotalReadingTimeThisMonth(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.timeInMillis
        val now = System.currentTimeMillis()
        return readingSessionDao.getTotalReadingTimeInRange(startOfMonth, now) ?: 0L
    }

    suspend fun getTotalPagesReadToday(): Int {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        val endOfDay = System.currentTimeMillis()
        return readingSessionDao.getTotalPagesReadInRange(startOfDay, endOfDay) ?: 0
    }

    suspend fun getCurrentReadingStreak(): Int {
        // Calculate consecutive days with reading activity
        val calendar = Calendar.getInstance()
        var streak = 0
        var checkDate = System.currentTimeMillis()

        while (true) {
            calendar.timeInMillis = checkDate
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfDay = calendar.timeInMillis

            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endOfDay = calendar.timeInMillis

            val sessions = readingSessionDao.getSessionsSince(startOfDay)
            val hasActivity = sessions.any { it.startTime >= startOfDay && it.startTime <= endOfDay }

            if (hasActivity) {
                streak++
                checkDate -= 24 * 60 * 60 * 1000 // Go back one day
            } else {
                break
            }

            // Limit check to prevent infinite loop
            if (streak > 365) break
        }

        return streak
    }

    suspend fun getReadingSpeedWPM(bookId: String): Double {
        // Calculate average reading speed in words per minute
        // This is a simplified calculation
        val sessions = readingSessionDao.getSessionsForBook(bookId)
        // Implementation would need to track actual words read
        return 250.0 // Default average reading speed
    }
}
