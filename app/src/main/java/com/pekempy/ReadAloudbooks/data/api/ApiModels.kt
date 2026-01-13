package com.pekempy.ReadAloudbooks.data.api

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val username: String,
    val password: String
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String
)

data class BookResponse(
    val uuid: String,
    val title: String,
    val id: Long? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val language: String? = null,
    val rating: Float? = null,
    val suffix: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val publicationDate: String? = null,
    val alignedByStorytellerVersion: String? = null,
    val alignedAt: String? = null,
    val alignedWith: String? = null,
    val authors: List<AuthorResponse>,
    val narrators: List<NarratorResponse>? = null,
    val series: List<SeriesResponse>? = null,
    val collections: List<SeriesResponse>? = null,
    val tags: List<TagResponse>? = null,
    val status: Any? = null,
    val position: Any? = null,
    val audiobook: AudiobookResponse? = null,
    val ebook: EbookResponse? = null,
    @SerializedName("readaloud") val readaloud: ReadAloudResponse? = null
)

data class SeriesResponse(
    val uuid: String,
    val name: String,
    val featured: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    @SerializedName("index") val index: Any? = null,
    @SerializedName("sequence") val sequence: Any? = null,
    @SerializedName("position") val position: Any? = null,
    @SerializedName("series_index") val series_index: Any? = null,
    @SerializedName("seriesIndex") val seriesIndexField: Any? = null
) {
    val seriesIndex: String?
        get() {
            val raw = index ?: sequence ?: position ?: series_index ?: seriesIndexField
            if (raw == null) return null
            
            val s = raw.toString()
            val cleanS = if (s.endsWith(".0")) s.substringBefore(".0") else s
            
            return if (cleanS.isBlank() || cleanS.equals("null", ignoreCase = true)) null else cleanS
        }

    fun getIdentifier(): String? = uuid
}

fun Any?.extractId(): String? {
    if (this == null) return null
    if (this is String) return this
    if (this is Map<*, *>) return this["uuid"]?.toString() ?: this["id"]?.toString()
    return this.toString()
}

fun Any?.extractValue(): Any? {
    if (this == null) return null
    if (this is String || this is Number || this is Boolean) return this
    if (this is Map<*, *>) return this["value"] ?: this["uuid"] ?: this["id"]
    return this.toString()
}

data class AuthorResponse(
    val uuid: String,
    val name: String,
    val fileAs: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class NarratorResponse(
    val uuid: String,
    val name: String,
    val fileAs: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class TagResponse(
    val uuid: String,
    val name: String,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class AudiobookResponse(
    val uuid: String,
    val filepath: String?,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class EbookResponse(
    val uuid: String,
    val filepath: String?,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class ReadAloudResponse(
    val uuid: String,
    val status: String? = null,
    val filepath: String? = null,
    val queuePosition: Int? = null,
    val currentStage: String? = null,
    val stageProgress: Double? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class Position(
    val locator: Locator,
    val timestamp: Long = System.currentTimeMillis()
)

data class Locator(
    val href: String,
    @SerializedName("type") val mediaType: String,
    val title: String? = null,
    val locations: Locations
)

data class Locations(
    val progression: Double,
    val position: Int? = null,
    @SerializedName("totalProgression") val totalProgression: Double? = null,
    
    val audioTimestampMs: Long? = null,
    val chapterIndex: Int? = null,
    val elementId: String? = null,
    val totalChapters: Int? = null,
    val totalDurationMs: Long? = null
)
