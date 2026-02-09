package com.pekempy.ReadAloudbooks

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.pekempy.ReadAloudbooks.data.api.AppContainer

class ReadAloudApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AppContainer.context = this
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .callFactory { 
                // Return a Call.Factory that always delegates to the current AppContainer client
                // This ensures we use the authenticated client even if it's updated after Coil init
                okhttp3.Call.Factory { request ->
                    val client = AppContainer.apiClientManager.okHttpClient ?: okhttp3.OkHttpClient()
                    client.newCall(request)
                }
            }
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
