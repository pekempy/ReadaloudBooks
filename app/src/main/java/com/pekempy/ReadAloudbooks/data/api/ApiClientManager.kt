package com.pekempy.ReadAloudbooks.data.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiClientManager {
    var baseUrl: String? = null
    var token: String? = null
        private set
    private var api: StorytellerApi? = null
    
    var okHttpClient: OkHttpClient? = null
        private set
    
    var downloadClient: OkHttpClient? = null
        private set

    fun updateConfig(url: String, authToken: String?) {
        val cleanUrl = url.let { 
            val withProtocol = if (!it.startsWith("http")) {
                if (it.startsWith("localhost") || 
                    it.startsWith("127.0.0.1") ||
                    it.startsWith("192.168.") || 
                    it.startsWith("10.") || 
                    (it.startsWith("172.") && it.substring(4).toIntOrNull()?.let { num -> num in 16..31 } == true)) {
                    "http://$it"
                } else {
                    "https://$it"
                }
            } else it
            if (withProtocol.endsWith("/")) withProtocol.dropLast(1) else withProtocol
        }
        
        if (cleanUrl == baseUrl && authToken == token && api != null) return

        baseUrl = cleanUrl
        token = authToken

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder().apply {
                token?.let { addHeader("Authorization", "Bearer $it") }
            }.build()
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .build()
        
        okHttpClient = client

        downloadClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        api = Retrofit.Builder()
            .baseUrl("$cleanUrl/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(StorytellerApi::class.java)
    }

    fun getApi(): StorytellerApi {
        return api ?: throw IllegalStateException("API not initialized. Call updateConfig first.")
    }
    
    fun getEbookCoverUrl(bookUuid: String): String {
        return "${baseUrl}/api/v2/books/$bookUuid/cover"
    }

    fun getAudiobookCoverUrl(bookUuid: String): String {
        return "${baseUrl}/api/v2/books/$bookUuid/cover?audio"
    }

    fun getCoverUrl(bookUuid: String): String {
        return "${baseUrl}/api/v2/books/$bookUuid/cover"
    }

    fun getSyncDownloadUrl(bookUuid: String): String {
        return "${baseUrl}/api/v2/books/$bookUuid/files?format=readaloud"
    }

    fun getAudiobookDownloadUrl(bookUuid: String): String {
        return "${baseUrl}/api/v2/books/$bookUuid/files?format=audiobook"
    }

    fun getEbookDownloadUrl(bookUuid: String): String {
        return "${baseUrl}/api/v2/books/$bookUuid/files?format=ebook"
    }
}
