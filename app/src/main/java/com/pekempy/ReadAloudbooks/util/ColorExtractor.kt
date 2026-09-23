package com.pekempy.ReadAloudbooks.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object ColorExtractor {
    
    /**
     * Extract dominant color from image URL
     * Returns color as Int (ARGB format) or null if extraction fails
     */
    suspend fun extractDominantColor(
        imageUrl: String?,
        context: Context
    ): Int? = withContext(Dispatchers.IO) {
        if (imageUrl.isNullOrBlank()) return@withContext null
        
        try {
            // Add authentication header if needed
            val token = com.pekempy.ReadAloudbooks.data.api.AppContainer.apiClientManager.token
            val connection = URL(imageUrl).openConnection()
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            
            // Download and decode bitmap
            val bitmap = connection.getInputStream().use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            } ?: return@withContext null
            
            // Scale down for faster processing
            val scaledBitmap = if (bitmap.width > 200 || bitmap.height > 200) {
                Bitmap.createScaledBitmap(bitmap, 200, 200, false).also {
                    bitmap.recycle()
                }
            } else {
                bitmap
            }
            
            // Extract palette
            val palette = Palette.from(scaledBitmap).generate()
            scaledBitmap.recycle()
            
            // Try to get vibrant color first, fall back to dominant
            palette.vibrantSwatch?.rgb 
                ?: palette.dominantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: palette.darkVibrantSwatch?.rgb
        } catch (e: Exception) {
            android.util.Log.e("ColorExtractor", "Failed to extract color from $imageUrl", e)
            null
        }
    }
    
    /**
     * Check if color is too dark or too light
     * Returns true if color is usable for theming
     */
    fun isColorUsable(color: Int): Boolean {
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        
        // Calculate luminance
        val luminance = 0.299f * r + 0.587f * g + 0.114f * b
        
        // Reject colors that are too light or too dark
        return luminance in 0.15f..0.85f
    }
}
