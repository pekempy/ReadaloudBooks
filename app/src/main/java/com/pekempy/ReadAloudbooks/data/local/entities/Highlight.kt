package com.pekempy.ReadAloudbooks.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "highlights",
    indices = [Index(value = ["bookId", "chapterIndex"])]
)
data class Highlight(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: String,
    val chapterIndex: Int,
    val elementId: String,
    val text: String,
    val color: String, // Hex color like "#FFEB3B"
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null,
    val startOffset: Int = 0, // For partial text highlighting
    val endOffset: Int = 0,
    val syncedToServer: Boolean = false,
    val serverId: String? = null
)
