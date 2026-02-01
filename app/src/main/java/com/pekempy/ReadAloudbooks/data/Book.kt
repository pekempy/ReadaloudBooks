package com.pekempy.ReadAloudbooks.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val narrator: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val hasReadAloud: Boolean = false,
    val hasEbook: Boolean = false,
    val hasAudiobook: Boolean = false,
    val syncedUrl: String? = null,
    val audiobookUrl: String? = null,
    val ebookUrl: String? = null,
    val addedDate: Long = 0L,
    val series: String? = null,
    val seriesIndex: String? = null,
    val isDownloaded: Boolean = false,
    val isAudiobookDownloaded: Boolean = false,
    val isEbookDownloaded: Boolean = false,
    val isReadAloudDownloaded: Boolean = false,
    val isReadAloudQueued: Boolean = false,
    val processingStatus: String? = null,
    val currentProcessingStage: String? = null,
    val processingProgress: Float? = null,
    val queuePosition: Int? = null,
    val progress: Float? = null,
    val ebookCoverUrl: String? = null,
    val audiobookCoverUrl: String? = null,
    val updatedAt: String? = null
) : Parcelable
