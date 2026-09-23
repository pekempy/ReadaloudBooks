package com.pekempy.ReadAloudbooks.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG-style contrast helpers used to keep book/Material-You/custom accent colours legible
 * wherever they land as button fills, on-button text, or reader highlight overlays: a colour
 * extracted from a book cover is sometimes near-black or near-white and would otherwise produce
 * unreadable white-on-pale-yellow buttons or invisible highlights.
 */
object ContrastUtils {

    /** WCAG 2.x relative luminance (0 = black, 1 = white). */
    fun relativeLuminance(color: Color): Float {
        fun channel(c: Float): Float =
            if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
    }

    /** WCAG contrast ratio between two colours, in the range 1..21. */
    fun contrastRatio(a: Color, b: Color): Float {
        val l1 = relativeLuminance(a) + 0.05f
        val l2 = relativeLuminance(b) + 0.05f
        return max(l1, l2) / min(l1, l2)
    }

    /** Whichever of pure black/white reads best on top of [background]. */
    fun readableOn(background: Color): Color =
        if (contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background)) Color.Black else Color.White

    /**
     * Nudges [color]'s brightness (HSV value), preserving hue/saturation, until it reaches
     * [minContrast] against [background] — darkening it if the background is light, lightening
     * it if the background is dark. If a saturated hue still caps out short of the target due
     * to human-vision luminance weighting (e.g. blue), bleeds off saturation as a second pass.
     */
    fun ensureContrast(color: Color, background: Color, minContrast: Float = 3.0f): Color {
        if (contrastRatio(color, background) >= minContrast) return color

        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        val darken = relativeLuminance(background) > 0.5f
        val step = if (darken) -0.04f else 0.04f

        var best = color
        var bestRatio = contrastRatio(color, background)

        // Pass 1: adjust brightness (HSV Value), preserving hue and saturation
        var curV = hsv[2]
        var attempts = 0
        while (attempts < 25 && bestRatio < minContrast) {
            val nextV = (curV + step).coerceIn(0f, 1f)
            if (nextV == curV) break
            curV = nextV
            hsv[2] = curV
            val candidate = Color(android.graphics.Color.HSVToColor(hsv))
            val ratio = contrastRatio(candidate, background)
            if (ratio > bestRatio) {
                best = candidate
                bestRatio = ratio
            }
            attempts++
        }

        // Pass 2: bleed off saturation if brightness alone couldn't hit the target
        var curS = hsv[1]
        attempts = 0
        while (attempts < 25 && bestRatio < minContrast) {
            curS = (curS - 0.05f).coerceAtLeast(0f)
            hsv[1] = curS
            val candidate = Color(android.graphics.Color.HSVToColor(hsv))
            val ratio = contrastRatio(candidate, background)
            if (ratio > bestRatio) {
                best = candidate
                bestRatio = ratio
            }
            attempts++
            if (curS <= 0f) break
        }

        return best
    }
}
