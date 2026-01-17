package com.pekempy.ReadAloudbooks.data.api

import retrofit2.http.*

interface StorytellerApi {
    @Multipart
    @POST("api/v2/token")
    suspend fun login(
        @Part("usernameOrEmail") username: okhttp3.RequestBody,
        @Part("password") password: okhttp3.RequestBody
    ): TokenResponse

    @GET("api/v2/books")
    suspend fun listBooks(
        @Query("synced") synced: Boolean = true
    ): List<BookResponse>

    @GET("api/v2/books/{uuid}")
    suspend fun getBookDetails(
        @Path("uuid") uuid: String
    ): BookResponse

    @GET("api/v2/books/{uuid}/positions")
    suspend fun getPosition(
        @Path("uuid") uuid: String
    ): Position?

    @POST("api/v2/books/{uuid}/positions")
    suspend fun updatePosition(
        @Path("uuid") uuid: String,
        @Body position: Position
    )

    @POST("api/v2/books/{uuid}/process")
    suspend fun processBook(
        @Path("uuid") uuid: String,
        @Query("restart") restart: Boolean? = null
    )

    @DELETE("api/v2/books/{uuid}/process")
    suspend fun cancelProcessing(
        @Path("uuid") uuid: String
    )

    @Multipart
    @PUT("api/v2/books/{uuid}")
    suspend fun updateBook(
        @Path("uuid") uuid: String,
        @Part parts: List<okhttp3.MultipartBody.Part>
    ): BookResponse

    @GET("api/v2/creators")
    suspend fun getCreators(): List<AuthorResponse>

    @GET("api/v2/series")
    suspend fun getSeries(): List<SeriesResponse>

    @GET("api/v2/tags")
    suspend fun getTags(): List<TagResponse>

    @GET("api/v2/collections")
    suspend fun getCollections(): List<SeriesResponse>
}
