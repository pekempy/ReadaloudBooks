package com.pekempy.ReadAloudbooks

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.pekempy.ReadAloudbooks.data.api.AppContainer
import com.pekempy.ReadAloudbooks.data.RepositoryProvider
import com.pekempy.ReadAloudbooks.data.local.AppDatabase

class ReadAloudApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AppContainer.context = this

        // Initialize database and repositories
        val database = AppDatabase.getDatabase(this)
        RepositoryProvider.initialize(database)
    }

    override fun newImageLoader(): ImageLoader {
        val client = AppContainer.apiClientManager.okHttpClient 
            ?: okhttp3.OkHttpClient()
            
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.15)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.10)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }
}
