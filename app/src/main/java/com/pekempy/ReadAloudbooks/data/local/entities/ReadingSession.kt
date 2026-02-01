package com.pekempy.ReadAloudbooks.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "reading_sessions",
    indices = [Index(value = ["bookId"]), Index(value = ["startTime"])]
)
data class ReadingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: String,
    val bookTitle: String,
    val startTime: Long,
    val endTime: Long,
    val durationMillis: Long,
    val pagesRead: Int = 0,
    val chapterIndex: Int,
    val isAudio: Boolean = false, // true for audiobook listening, false for reading
    val syncedToServer: Boolean = false
)
