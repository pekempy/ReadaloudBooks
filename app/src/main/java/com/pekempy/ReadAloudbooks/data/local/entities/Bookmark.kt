package com.pekempy.ReadAloudbooks.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["bookId"])]
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: String,
    val chapterIndex: Int,
    val scrollPercent: Float,
    val elementId: String?,
    val label: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val syncedToServer: Boolean = false,
    val serverId: String? = null
)
