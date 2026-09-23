package com.pekempy.ReadAloudbooks.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dynamic theme extraction from audiobook cover art
 * Creates Material You color schemes that match the book's aesthetic
 */
object DynamicTheme {
    
    data class ExtractedColors(
        val primary: Color,
        val secondary: Color,
        val tertiary: Color,
        val background: Color,
        val surface: Color,
        val vibrant: Color? = null,
        val muted: Color? = null
    )
    
    suspend fun extractColorsFromUrl(
        context: Context,
        imageUrl: String?,
        imageLoader: ImageLoader
    ): ExtractedColors? {
        if (imageUrl.isNullOrBlank()) return null
        
        return withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .allowHardware(false) // Palette needs software bitmap
                    .build()
                
                val result = imageLoader.execute(request)
                
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    bitmap?.let { extractColorsFromBitmap(it) }
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    fun extractColorsFromBitmap(bitmap: Bitmap): ExtractedColors {
        val palette = Palette.from(bitmap).generate()
        
        // Extract key colors
        val vibrant = palette.vibrantSwatch?.rgb
        val vibrantLight = palette.lightVibrantSwatch?.rgb
        val vibrantDark = palette.darkVibrantSwatch?.rgb
        val muted = palette.mutedSwatch?.rgb
        val mutedLight = palette.lightMutedSwatch?.rgb
        val mutedDark = palette.darkMutedSwatch?.rgb
        
        // Determine primary color (prefer vibrant, fallback to muted)
        val primaryColor = vibrant ?: vibrantDark ?: muted ?: 0xFF6200EE.toInt()
        
        // Determine secondary (prefer muted complement)
        val secondaryColor = mutedLight ?: muted ?: vibrantLight ?: 0xFF03DAC6.toInt()
        
        // Tertiary (darker vibrant or muted)
        val tertiaryColor = vibrantDark ?: mutedDark ?: 0xFF3700B3.toInt()
        
        // Background/surface (use dominant or muted)
        val dominantColor = palette.dominantSwatch?.rgb ?: 0xFFFFFFFF.toInt()
        
        return ExtractedColors(
            primary = Color(primaryColor),
            secondary = Color(secondaryColor),
            tertiary = Color(tertiaryColor),
            background = Color(dominantColor),
            surface = Color(dominantColor),
            vibrant = vibrant?.let { Color(it) },
            muted = muted?.let { Color(it) }
        )
    }
    
    fun createLightColorScheme(extracted: ExtractedColors): ColorScheme {
        return lightColorScheme(
            primary = extracted.primary,
            onPrimary = getContrastColor(extracted.primary),
            primaryContainer = lighten(extracted.primary, 0.8f),
            onPrimaryContainer = darken(extracted.primary, 0.9f),
            
            secondary = extracted.secondary,
            onSecondary = getContrastColor(extracted.secondary),
            secondaryContainer = lighten(extracted.secondary, 0.8f),
            onSecondaryContainer = darken(extracted.secondary, 0.9f),
            
            tertiary = extracted.tertiary,
            onTertiary = getContrastColor(extracted.tertiary),
            tertiaryContainer = lighten(extracted.tertiary, 0.8f),
            onTertiaryContainer = darken(extracted.tertiary, 0.9f),
            
            background = Color.White,
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black,
            surfaceVariant = lighten(extracted.primary, 0.95f),
            onSurfaceVariant = Color(0xFF444746)
        )
    }
    
    fun createDarkColorScheme(extracted: ExtractedColors): ColorScheme {
        return darkColorScheme(
            primary = lighten(extracted.primary, 0.6f),
            onPrimary = darken(extracted.primary, 0.9f),
            primaryContainer = darken(extracted.primary, 0.7f),
            onPrimaryContainer = lighten(extracted.primary, 0.8f),
            
            secondary = lighten(extracted.secondary, 0.6f),
            onSecondary = darken(extracted.secondary, 0.9f),
            secondaryContainer = darken(extracted.secondary, 0.7f),
            onSecondaryContainer = lighten(extracted.secondary, 0.8f),
            
            tertiary = lighten(extracted.tertiary, 0.6f),
            onTertiary = darken(extracted.tertiary, 0.9f),
            tertiaryContainer = darken(extracted.tertiary, 0.7f),
            onTertiaryContainer = lighten(extracted.tertiary, 0.8f),
            
            background = Color(0xFF1C1B1F),
            onBackground = Color(0xFFE6E1E5),
            surface = Color(0xFF1C1B1F),
            onSurface = Color(0xFFE6E1E5),
            surfaceVariant = Color(0xFF49454F),
            onSurfaceVariant = Color(0xFFCAC4D0)
        )
    }
    
    private fun getContrastColor(color: Color): Color {
        val luminance = calculateLuminance(color)
        return if (luminance > 0.5f) Color.Black else Color.White
    }
    
    private fun calculateLuminance(color: Color): Float {
        // Relative luminance formula
        return 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    }
    
    private fun lighten(color: Color, factor: Float): Color {
        return Color(
            red = color.red + (1f - color.red) * factor,
            green = color.green + (1f - color.green) * factor,
            blue = color.blue + (1f - color.blue) * factor,
            alpha = color.alpha
        )
    }
    
    private fun darken(color: Color, factor: Float): Color {
        return Color(
            red = color.red * factor,
            green = color.green * factor,
            blue = color.blue * factor,
            alpha = color.alpha
        )
    }
}

@Composable
fun rememberDynamicColorScheme(
    imageUrl: String?,
    darkTheme: Boolean,
    imageLoader: ImageLoader
): State<ColorScheme?> {
    val context = LocalContext.current
    val colorScheme = remember { mutableStateOf<ColorScheme?>(null) }
    
    LaunchedEffect(imageUrl, darkTheme) {
        if (!imageUrl.isNullOrBlank()) {
            val extracted = DynamicTheme.extractColorsFromUrl(context, imageUrl, imageLoader)
            colorScheme.value = extracted?.let {
                if (darkTheme) {
                    DynamicTheme.createDarkColorScheme(it)
                } else {
                    DynamicTheme.createLightColorScheme(it)
                }
            }
        } else {
            colorScheme.value = null
        }
    }
    
    return colorScheme
}
