package com.pekempy.ReadAloudbooks.data

import com.pekempy.ReadAloudbooks.data.local.AppDatabase
import com.pekempy.ReadAloudbooks.data.repository.*

/**
 * Central provider for all repositories. Initialized once in Application class.
 */
object RepositoryProvider {
    lateinit var highlightRepository: HighlightRepository
        private set
    lateinit var bookmarkRepository: BookmarkRepository
        private set
    lateinit var readingStatisticsRepository: ReadingStatisticsRepository
        private set
    lateinit var audioBookmarkRepository: AudioBookmarkRepository
        private set

    fun initialize(database: AppDatabase) {
        highlightRepository = HighlightRepository(database.highlightDao())
        bookmarkRepository = BookmarkRepository(database.bookmarkDao())
        readingStatisticsRepository = ReadingStatisticsRepository(
            database.readingSessionDao()
        )
        audioBookmarkRepository = AudioBookmarkRepository(database.audioBookmarkDao())
    }
}
