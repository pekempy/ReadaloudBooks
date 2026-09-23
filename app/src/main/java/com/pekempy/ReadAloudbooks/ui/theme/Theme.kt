package com.pekempy.ReadAloudbooks.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun ReadAloudBooksTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColour: Boolean = true,
    themeSource: Int = 0,
    amoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColour && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            val seedColor = when {
                themeSource == 0 -> Purple40
                themeSource in 1..4 -> when(themeSource) {
                    1 -> androidx.compose.ui.graphics.Color(0xFF2196F3) 
                    2 -> androidx.compose.ui.graphics.Color(0xFFF44336) 
                    3 -> androidx.compose.ui.graphics.Color(0xFF4CAF50) 
                    4 -> androidx.compose.ui.graphics.Color(0xFFFF9800) 
                    else -> Purple40 
                }
                else -> androidx.compose.ui.graphics.Color(themeSource)
            }

            // Book-cover/custom colours can land anywhere in brightness; guarantee they still
            // read clearly as a button fill (and that the text drawn on top of them is legible)
            // instead of trusting Material3's default onPrimary/onSecondary/onTertiary (fixed
            // white/black tokens that assume a mid-tone seed).
            if (darkTheme) {
                val safeSeed = com.pekempy.ReadAloudbooks.util.ContrastUtils.ensureContrast(
                    seedColor, androidx.compose.ui.graphics.Color(0xFF1C1B1F), minContrast = 3.5f
                )
                val onSeed = com.pekempy.ReadAloudbooks.util.ContrastUtils.readableOn(safeSeed)
                darkColorScheme(
                    primary = safeSeed,
                    onPrimary = onSeed,
                    secondary = safeSeed,
                    onSecondary = onSeed,
                    tertiary = safeSeed,
                    onTertiary = onSeed
                )
            } else {
                val safeSeed = com.pekempy.ReadAloudbooks.util.ContrastUtils.ensureContrast(
                    seedColor, androidx.compose.ui.graphics.Color(0xFFFFFBFE), minContrast = 3.5f
                )
                val onSeed = com.pekempy.ReadAloudbooks.util.ContrastUtils.readableOn(safeSeed)
                lightColorScheme(
                    primary = safeSeed,
                    onPrimary = onSeed,
                    secondary = safeSeed,
                    onSecondary = onSeed,
                    tertiary = safeSeed,
                    onTertiary = onSeed
                )
            }
        }
    }.let {
        if (amoled && darkTheme) {
            it.copy(
                background = androidx.compose.ui.graphics.Color.Black,
                surface = androidx.compose.ui.graphics.Color.Black,
                surfaceContainer = androidx.compose.ui.graphics.Color.Black,
                surfaceContainerHigh = androidx.compose.ui.graphics.Color.Black,
                surfaceContainerHighest = androidx.compose.ui.graphics.Color.Black,
                surfaceContainerLow = androidx.compose.ui.graphics.Color.Black,
                surfaceContainerLowest = androidx.compose.ui.graphics.Color.Black,
            )
        } else {
            it
        }
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
