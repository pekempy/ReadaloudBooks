package com.pekempy.ReadAloudbooks.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pekempy.ReadAloudbooks.data.local.dao.*
import com.pekempy.ReadAloudbooks.data.local.entities.*

@Database(
    entities = [
        Highlight::class,
        Bookmark::class,
        ReadingSession::class,
        AudioBookmark::class,
        ReadingGoal::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun highlightDao(): HighlightDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun audioBookmarkDao(): AudioBookmarkDao
    abstract fun readingGoalDao(): ReadingGoalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "readaloud_books_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
