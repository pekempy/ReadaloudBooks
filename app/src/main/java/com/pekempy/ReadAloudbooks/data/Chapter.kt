package com.pekempy.ReadAloudbooks.data

/**
 * A single chapter/section in an audiobook or readaloud book's timeline.
 * Shared by [com.pekempy.ReadAloudbooks.ui.player.AudiobookViewModel] and
 * [com.pekempy.ReadAloudbooks.ui.player.ReadAloudAudioViewModel] so chapter UI
 * (mini player, progress bar, chapter list) works identically for both.
 */
data class Chapter(
    val title: String,
    val startOffset: Long,
    val duration: Long
)
