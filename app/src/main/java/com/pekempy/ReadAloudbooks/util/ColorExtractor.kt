package com.pekempy.ReadAloudbooks.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts colors from book cover artwork for dynamic theming
 */
object ColorExtractor {
    
    /**
     * Extract a color scheme from a book cover image
     */
    suspend fun extractColorsFromCover(coverFile: File): BookColorScheme? {
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeFile(coverFile.absolutePath) ?: return@withContext null
                val palette = Palette.from(bitmap).generate()
                
                BookColorScheme(
                    primary = palette.dominantSwatch?.let { Color(it.rgb) } ?: Color(0xFF6750A4),
                    onPrimary = palette.dominantSwatch?.let { Color(it.titleTextColor) } ?: Color.White,
                    primaryContainer = palette.lightVibrantSwatch?.let { Color(it.rgb) } ?: Color(0xFFE8DEF8),
                    onPrimaryContainer = palette.lightVibrantSwatch?.let { Color(it.bodyTextColor) } ?: Color(0xFF21005E),
                    secondary = palette.vibrantSwatch?.let { Color(it.rgb) } ?: Color(0xFF625B71),
                    surface = palette.lightMutedSwatch?.let { Color(it.rgb) } ?: Color(0xFFFFFBFF),
                    background = palette.mutedSwatch?.let { Color(it.rgb) } ?: Color(0xFFFFFBFF)
                )
            } catch (e: Exception) {
                android.util.Log.e("ColorExtractor", "Failed to extract colors", e)
                null
            }
        }
    }
    
    /**
     * Generate a complementary color scheme from a single color
     */
    fun generateScheme(baseColor: Color): BookColorScheme {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(baseColor.toArgb(), hsv)
        
        // Create variations
        val primary = baseColor
        val onPrimary = if (hsv[2] > 0.5f) Color.Black else Color.White
        
        // Lighter version for container
        hsv[2] = (hsv[2] + 0.3f).coerceAtMost(1f)
        val primaryContainer = Color(android.graphics.Color.HSVToColor(hsv))
        
        // Secondary (complementary hue)
        hsv[0] = (hsv[0] + 180f) % 360f
        val secondary = Color(android.graphics.Color.HSVToColor(hsv))
        
        return BookColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = Color.Black,
            secondary = secondary,
            surface = Color.White,
            background = Color.White
        )
    }
}

/**
 * Color scheme extracted from book cover
 */
data class BookColorScheme(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val surface: Color,
    val background: Color
)
