package com.pekempy.ReadAloudbooks.data

data class ReadingSession(
    val bookId: String,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val bookType: String // "readaloud", "audiobook", "ebook"
)

data class ReadingStats(
    val totalReadingTimeMs: Long,
    val booksFinished: Int,
    val currentStreak: Int,
    val averageSessionMs: Long,
    val readingByDay: Map<String, Long>, // Date -> duration
    val readingByBook: List<BookStats>
)

data class BookStats(
    val bookId: String,
    val title: String,
    val totalTimeMs: Long,
    val progress: Float,
    val lastRead: Long
)
