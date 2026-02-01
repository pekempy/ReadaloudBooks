package com.pekempy.ReadAloudbooks.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "audio_bookmarks",
    indices = [Index(value = ["bookId"])]
)
data class AudioBookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: String,
    val timestampMillis: Long, // Audio position
    val chapterIndex: Int? = null,
    val label: String?,
    val note: String? = null,
    val transcription: String? = null, // Auto-transcribed text around the bookmark
    val timestamp: Long = System.currentTimeMillis(),
    val syncedToServer: Boolean = false,
    val serverId: String? = null
)
