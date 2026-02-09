package com.pekempy.ReadAloudbooks.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.pekempy.ReadAloudbooks.data.Book
import com.google.gson.Gson

class LocalDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "readaloud_books.db"
        const val DATABASE_VERSION = 1

        const val TABLE_BOOKS = "books"
        const val COLUMN_ID = "id"
        const val COLUMN_TITLE = "title"
        const val COLUMN_AUTHOR = "author"
        const val COLUMN_NARRATOR = "narrator"
        const val COLUMN_COVER_URL = "cover_url"
        const val COLUMN_DESCRIPTION = "description"
        const val COLUMN_HAS_READALOUD = "has_readaloud"
        const val COLUMN_HAS_EBOOK = "has_ebook"
        const val COLUMN_HAS_AUDIOBOOK = "has_audiobook"
        const val COLUMN_SYNCED_URL = "synced_url"
        const val COLUMN_AUDIOBOOK_URL = "audiobook_url"
        const val COLUMN_EBOOK_URL = "ebook_url"
        const val COLUMN_ADDED_DATE = "added_date"
        const val COLUMN_SERIES = "series"
        const val COLUMN_COLLECTION = "collection"
        const val COLUMN_SERIES_INDEX = "series_index"
        const val COLUMN_UPDATED_AT = "updated_at" // Server timestamp
        const val COLUMN_PROGRESS = "progress" // Local progress
        const val COLUMN_PROGRESS_TIMESTAMP = "progress_timestamp" // Local progress timestamp
        const val COLUMN_EBOOK_COVER_URL = "ebook_cover_url"
        const val COLUMN_AUDIOBOOK_COVER_URL = "audiobook_cover_url"

        // Processing status fields
        const val COLUMN_IS_READALOUD_QUEUED = "is_readaloud_queued"
        const val COLUMN_PROCESSING_STATUS = "processing_status"
        const val COLUMN_CURRENT_PROCESSING_STAGE = "current_processing_stage"
        const val COLUMN_PROCESSING_PROGRESS = "processing_progress"
        const val COLUMN_QUEUE_POSITION = "queue_position"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_BOOKS (
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_TITLE TEXT,
                $COLUMN_AUTHOR TEXT,
                $COLUMN_NARRATOR TEXT,
                $COLUMN_COVER_URL TEXT,
                $COLUMN_DESCRIPTION TEXT,
                $COLUMN_HAS_READALOUD INTEGER,
                $COLUMN_HAS_EBOOK INTEGER,
                $COLUMN_HAS_AUDIOBOOK INTEGER,
                $COLUMN_SYNCED_URL TEXT,
                $COLUMN_AUDIOBOOK_URL TEXT,
                $COLUMN_EBOOK_URL TEXT,
                $COLUMN_ADDED_DATE INTEGER,
                $COLUMN_SERIES TEXT,
                $COLUMN_COLLECTION TEXT,
                $COLUMN_SERIES_INDEX TEXT,
                $COLUMN_UPDATED_AT TEXT,
                $COLUMN_PROGRESS REAL,
                $COLUMN_PROGRESS_TIMESTAMP INTEGER,
                $COLUMN_EBOOK_COVER_URL TEXT,
                $COLUMN_AUDIOBOOK_COVER_URL TEXT,
                $COLUMN_IS_READALOUD_QUEUED INTEGER,
                $COLUMN_PROCESSING_STATUS TEXT,
                $COLUMN_CURRENT_PROCESSING_STAGE TEXT,
                $COLUMN_PROCESSING_PROGRESS REAL,
                $COLUMN_QUEUE_POSITION INTEGER
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BOOKS")
        onCreate(db)
    }

    fun insertOrUpdateBook(book: Book) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ID, book.id)
            put(COLUMN_TITLE, book.title)
            put(COLUMN_AUTHOR, book.author)
            put(COLUMN_NARRATOR, book.narrator)
            put(COLUMN_COVER_URL, book.coverUrl)
            put(COLUMN_DESCRIPTION, book.description)
            put(COLUMN_HAS_READALOUD, if (book.hasReadAloud) 1 else 0)
            put(COLUMN_HAS_EBOOK, if (book.hasEbook) 1 else 0)
            put(COLUMN_HAS_AUDIOBOOK, if (book.hasAudiobook) 1 else 0)
            put(COLUMN_SYNCED_URL, book.syncedUrl)
            put(COLUMN_AUDIOBOOK_URL, book.audiobookUrl)
            put(COLUMN_EBOOK_URL, book.ebookUrl)
            put(COLUMN_ADDED_DATE, book.addedDate)
            put(COLUMN_SERIES, book.series)
            put(COLUMN_COLLECTION, book.collection)
            put(COLUMN_SERIES_INDEX, book.seriesIndex)
            put(COLUMN_UPDATED_AT, book.updatedAt)
            put(COLUMN_EBOOK_COVER_URL, book.ebookCoverUrl)
            put(COLUMN_AUDIOBOOK_COVER_URL, book.audiobookCoverUrl)
            put(COLUMN_IS_READALOUD_QUEUED, if (book.isReadAloudQueued) 1 else 0)
            put(COLUMN_PROCESSING_STATUS, book.processingStatus)
            put(COLUMN_CURRENT_PROCESSING_STAGE, book.currentProcessingStage)
            put(COLUMN_PROCESSING_PROGRESS, book.processingProgress)
            put(COLUMN_QUEUE_POSITION, book.queuePosition)
            // Progress is handled separately to avoid overwriting local progress with stale server data blindly,
            // but for initial insert we might want it. However, repository level logic should handle merging.
            // For now, let's assume we update everything EXCEPT progress if it's already there? 
            // OR we use insertWithOnConflict with REPLACE which overwrites everything.
            // Better to query first or use a merge strategy.
        }
        
        // Simple strategy: Check existence. If exists, update non-progress fields. If new, insert.
        // Actually, for sync, we want to update progress IF server is newer. 
        // But this method receives a Book object which is already "merged" presumably?
        // Let's assume the caller handles merging.
        
        // However, if we blindly replace, we lose local progress timestamp.
        // So we should read existing first.
        
        val existing = getBook(book.id)
        if (existing != null) {
            // Merge logic could be here, but let's keep this class dumb and just do what it's told.
            // But wait, if I use this for "Sync", I need to be careful.
            
            // Let's update all fields.
             if (book.progress != null) {
                 values.put(COLUMN_PROGRESS, book.progress)
                 // We don't update timestamp here because we don't know if this update comes from local read or server sync.
                 // We'll leave timestamp management to a separate method or argument.
             }
             
             db.update(TABLE_BOOKS, values, "$COLUMN_ID = ?", arrayOf(book.id))
        } else {
             if (book.progress != null) values.put(COLUMN_PROGRESS, book.progress)
             values.put(COLUMN_PROGRESS_TIMESTAMP, System.currentTimeMillis()) // Initial timestamp
             db.insert(TABLE_BOOKS, null, values)
        }
    }
    
    fun deleteBook(id: String) {
        val db = writableDatabase
        db.delete(TABLE_BOOKS, "$COLUMN_ID = ?", arrayOf(id))
    }
    
    fun updateBookProgress(bookId: String, progress: Float, timestamp: Long) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PROGRESS, progress)
            put(COLUMN_PROGRESS_TIMESTAMP, timestamp)
        }
        db.update(TABLE_BOOKS, values, "$COLUMN_ID = ?", arrayOf(bookId))
    }
    fun updateProcessingStatus(bookId: String, ra: com.pekempy.ReadAloudbooks.data.api.ReadAloudResponse) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_IS_READALOUD_QUEUED, if (ra.filepath.isNullOrBlank() && ra.status != "STOPPED") 1 else 0)
            put(COLUMN_PROCESSING_STATUS, ra.status)
            put(COLUMN_CURRENT_PROCESSING_STAGE, ra.currentStage)
            put(COLUMN_PROCESSING_PROGRESS, ra.stageProgress?.toFloat())
            put(COLUMN_QUEUE_POSITION, ra.queuePosition)
            if (!ra.filepath.isNullOrBlank()) {
                put(COLUMN_HAS_READALOUD, 1)
            }
        }
        db.update(TABLE_BOOKS, values, "$COLUMN_ID = ?", arrayOf(bookId))
    }

    fun getAllBooks(): List<Book> {
        val db = readableDatabase
        val cursor = db.query(TABLE_BOOKS, null, null, null, null, null, null)
        val books = mutableListOf<Book>()
        
        if (cursor.moveToFirst()) {
            do {
                books.add(cursorToBook(cursor))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return books
    }
    
    fun getBook(id: String): Book? {
        val db = readableDatabase
        val cursor = db.query(TABLE_BOOKS, null, "$COLUMN_ID = ?", arrayOf(id), null, null, null)
        var book: Book? = null
        if (cursor.moveToFirst()) {
            book = cursorToBook(cursor)
        }
        cursor.close()
        return book
    }
    
    fun getBookProgressTimestamp(id: String): Long {
        val db = readableDatabase
        val cursor = db.query(TABLE_BOOKS, arrayOf(COLUMN_PROGRESS_TIMESTAMP), "$COLUMN_ID = ?", arrayOf(id), null, null, null)
        var ts = 0L
        if (cursor.moveToFirst()) {
            ts = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_PROGRESS_TIMESTAMP))
        }
        cursor.close()
        return ts
    }

    fun getBookCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_BOOKS", null)
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        return count
    }

    private fun cursorToBook(cursor: android.database.Cursor): Book {
        fun getString(columnName: String): String? {
            val index = cursor.getColumnIndex(columnName)
            return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
        }
        
        fun getInt(columnName: String): Int {
            val index = cursor.getColumnIndex(columnName)
            return if (index >= 0 && !cursor.isNull(index)) cursor.getInt(index) else 0
        }
        
        fun getLong(columnName: String): Long {
            val index = cursor.getColumnIndex(columnName)
            return if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else 0L
        }
        
        fun getFloat(columnName: String): Float? {
            val index = cursor.getColumnIndex(columnName)
            return if (index >= 0 && !cursor.isNull(index)) cursor.getFloat(index) else null
        }
        
        return Book(
            id = getString(COLUMN_ID) ?: "",
            title = getString(COLUMN_TITLE) ?: "",
            author = getString(COLUMN_AUTHOR) ?: "",
            narrator = getString(COLUMN_NARRATOR),
            coverUrl = getString(COLUMN_COVER_URL),
            description = getString(COLUMN_DESCRIPTION),
            hasReadAloud = getInt(COLUMN_HAS_READALOUD) == 1,
            hasEbook = getInt(COLUMN_HAS_EBOOK) == 1,
            hasAudiobook = getInt(COLUMN_HAS_AUDIOBOOK) == 1,
            syncedUrl = getString(COLUMN_SYNCED_URL),
            audiobookUrl = getString(COLUMN_AUDIOBOOK_URL),
            ebookUrl = getString(COLUMN_EBOOK_URL),
            addedDate = getLong(COLUMN_ADDED_DATE),
            series = getString(COLUMN_SERIES),
            collection = getString(COLUMN_COLLECTION),
            seriesIndex = getString(COLUMN_SERIES_INDEX),
            updatedAt = getString(COLUMN_UPDATED_AT),
            ebookCoverUrl = getString(COLUMN_EBOOK_COVER_URL),
            audiobookCoverUrl = getString(COLUMN_AUDIOBOOK_COVER_URL),
            progress = getFloat(COLUMN_PROGRESS),
            isReadAloudQueued = getInt(COLUMN_IS_READALOUD_QUEUED) == 1,
            processingStatus = getString(COLUMN_PROCESSING_STATUS),
            currentProcessingStage = getString(COLUMN_CURRENT_PROCESSING_STAGE),
            processingProgress = getFloat(COLUMN_PROCESSING_PROGRESS),
            queuePosition = getInt(COLUMN_QUEUE_POSITION).let { if (it == 0) null else it },
            // These calculated fields need to be re-calculated or passed in
            // For now default to false, caller (Repository) should enrich them with proper file checks
            isDownloaded = false,
            isAudiobookDownloaded = false,
            isEbookDownloaded = false,
            isReadAloudDownloaded = false
        )
    }
}
