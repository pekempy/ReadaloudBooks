package com.pekempy.ReadAloudbooks.data

import com.google.gson.Gson
import com.pekempy.ReadAloudbooks.data.api.Position
import com.pekempy.ReadAloudbooks.data.api.Locator
import com.pekempy.ReadAloudbooks.data.api.Locations

data class UnifiedProgress(
    val chapterIndex: Int,
    val elementId: String?,
    val audioTimestampMs: Long,
    val scrollPercent: Float,
    val lastUpdated: Long,
    val totalChapters: Int,
    val totalDurationMs: Long = 0L,
    val href: String? = null,
    val mediaType: String? = null
) {
    fun getOverallProgress(): Float {
        if (totalDurationMs > 0 && audioTimestampMs > 0) {
            return (audioTimestampMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        }
        if (totalChapters <= 1) return scrollPercent.coerceIn(0f, 1f)
        return ((chapterIndex.toFloat() + scrollPercent.coerceIn(0f, 1f)) / totalChapters.toFloat()).coerceIn(0f, 1f)
    }

    fun toPosition(): Position {
        return Position(
            locator = Locator(
                href = href ?: "chapter_$chapterIndex",
                mediaType = mediaType ?: "application/xhtml+xml",
                locations = Locations(
                    progression = scrollPercent.toDouble().coerceIn(0.0, 1.0),
                    totalProgression = getOverallProgress().toDouble().coerceIn(0.0, 1.0),
                    audioTimestampMs = audioTimestampMs,
                    chapterIndex = chapterIndex,
                    elementId = elementId,
                    totalChapters = totalChapters,
                    totalDurationMs = totalDurationMs
                )
            ),
            timestamp = lastUpdated / 1000
        )
    }

    companion object {
        private val gson = Gson()

        fun fromPosition(pos: Position, totalChaptersCount: Int = 1): UnifiedProgress {
            val loc = pos.locator.locations
            return UnifiedProgress(
                chapterIndex = loc.chapterIndex ?: 0,
                elementId = loc.elementId,
                audioTimestampMs = loc.audioTimestampMs ?: 0L,
                scrollPercent = loc.progression.toFloat(),
                lastUpdated = pos.timestamp * 1000,
                totalChapters = loc.totalChapters ?: totalChaptersCount,
                totalDurationMs = loc.totalDurationMs ?: 0L,
                href = pos.locator.href,
                mediaType = pos.locator.mediaType
            )
        }

        fun fromString(progressStr: String?): UnifiedProgress? {
            if (progressStr.isNullOrBlank()) return null
            
            if (progressStr.trim().startsWith("{")) {
                return try {
                    val pos = gson.fromJson(progressStr, Position::class.java)
                    fromPosition(pos)
                } catch (e: Exception) {
                    android.util.Log.e("UnifiedProgress", "Failed to parse JSON progress", e)
                    null
                }
            }

            val parts = progressStr.split(":")
            if (parts.isEmpty()) return null
            
            return try {
                if (parts.size >= 7) {
                    UnifiedProgress(
                        chapterIndex = parts[0].toIntOrNull() ?: 0,
                        elementId = parts[1].takeIf { it.isNotBlank() },
                        audioTimestampMs = parts[2].toLongOrNull() ?: 0L,
                        scrollPercent = parts[3].toFloatOrNull() ?: 0f,
                        lastUpdated = parts[4].toLongOrNull() ?: System.currentTimeMillis(),
                        totalChapters = parts[5].toIntOrNull() ?: 1,
                        totalDurationMs = parts[6].toLongOrNull() ?: 0L
                    )
                }
                else if (parts.size >= 6) {
                    UnifiedProgress(
                        chapterIndex = parts[0].toIntOrNull() ?: 0,
                        elementId = parts[1].takeIf { it.isNotBlank() },
                        audioTimestampMs = parts[2].toLongOrNull() ?: 0L,
                        scrollPercent = parts[3].toFloatOrNull() ?: 0f,
                        lastUpdated = parts[4].toLongOrNull() ?: System.currentTimeMillis(),
                        totalChapters = parts[5].toIntOrNull() ?: 1
                    )
                }
                else if (parts.size >= 5) {
                    UnifiedProgress(
                        chapterIndex = parts[0].toIntOrNull() ?: 0,
                        elementId = null,
                        audioTimestampMs = parts[4].toLongOrNull() ?: 0L,
                        scrollPercent = parts[1].toFloatOrNull() ?: 0f,
                        lastUpdated = parts[2].toLongOrNull() ?: System.currentTimeMillis(),
                        totalChapters = parts[3].toIntOrNull() ?: 1
                    )
                }
                else if (parts.size >= 4) {
                    UnifiedProgress(
                        chapterIndex = parts[0].toIntOrNull() ?: 0,
                        elementId = null,
                        audioTimestampMs = 0L,
                        scrollPercent = parts[1].toFloatOrNull() ?: 0f,
                        lastUpdated = parts[2].toLongOrNull() ?: System.currentTimeMillis(),
                        totalChapters = parts[3].toIntOrNull() ?: 1
                    )
                }
                else if (parts.size >= 2) {
                    UnifiedProgress(
                        chapterIndex = parts[0].toIntOrNull() ?: 0,
                        elementId = null,
                        audioTimestampMs = 0L,
                        scrollPercent = parts[1].toFloatOrNull() ?: 0f,
                        lastUpdated = System.currentTimeMillis(),
                        totalChapters = 1
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e("UnifiedProgress", "Failed to parse legacy progress: $progressStr", e)
                null
            }
        }
    }
    
    override fun toString(): String {
        return Gson().toJson(toPosition())
    }
    
    fun withAudioPosition(
        newAudioTimestampMs: Long,
        newElementId: String?,
        newChapterIndex: Int? = null
    ): UnifiedProgress {
        return copy(
            chapterIndex = newChapterIndex ?: chapterIndex,
            elementId = newElementId,
            audioTimestampMs = newAudioTimestampMs,
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    fun withScrollPosition(
        newChapterIndex: Int,
        newScrollPercent: Float,
        newElementId: String?,
        newAudioTimestampMs: Long?
    ): UnifiedProgress {
        return copy(
            chapterIndex = newChapterIndex,
            elementId = newElementId,
            audioTimestampMs = newAudioTimestampMs ?: audioTimestampMs,
            scrollPercent = newScrollPercent,
            lastUpdated = System.currentTimeMillis()
        )
    }
}
