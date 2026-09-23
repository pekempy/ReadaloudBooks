# Dynamic Theme System - Audiobook Cover Colors

## Overview
Premium feature that extracts colors from audiobook cover art and dynamically applies them to Material You theme.

## How It Works

### Color Extraction
Uses Android Palette API to extract:
- **Vibrant**: Primary UI elements, buttons, highlights
- **Muted**: Backgrounds, surfaces, containers
- **Dominant**: Overall theme mood

### Automatic Adjustments
- **Contrast checking**: Ensures text readability
- **Luminance calculation**: Smart light/dark text selection
- **Color harmonization**: Creates full Material You scheme from cover

## Usage

### In Player Screen
```kotlin
val imageLoader = ImageLoader(LocalContext.current)
val dynamicScheme by rememberDynamicColorScheme(
    imageUrl = currentBook.coverUrl,
    darkTheme = isSystemInDarkTheme(),
    imageLoader = imageLoader
)

MaterialTheme(
    colorScheme = dynamicScheme ?: MaterialTheme.colorScheme
) {
    // Player UI adapts to book colors
}
```

### Features
1. **Smooth transitions** when changing books
2. **Light/Dark mode** support with proper contrast
3. **Fallback** to system theme if extraction fails
4. **Performance optimized** - cached results

## Color Mapping

### Light Theme
- Primary: Vibrant swatch from cover
- Secondary: Muted complement
- Tertiary: Dark vibrant accent
- Containers: 80% lightened versions
- Background: Pure white (always readable)

### Dark Theme
- Primary: 60% lightened vibrant
- Secondary: 60% lightened muted
- Tertiary: Lightened dark vibrant
- Containers: 70% darkened versions
- Background: Material Dark (0xFF1C1B1F)

## Settings Integration
User can toggle:
- **Dynamic theme**: ON/OFF
- **Theme mode**: Always Light / Always Dark / System
- **Color intensity**: Subtle / Normal / Bold

## Examples

### Fantasy Book (Blue/Purple cover)
- Primary: Deep blue (#1E3A8A)
- Secondary: Purple accent (#7C3AED)
- Tertiary: Dark indigo (#4C1D95)
- UI feels magical and immersive

### Thriller (Red/Black cover)
- Primary: Deep red (#DC2626)
- Secondary: Charcoal (#374151)
- Tertiary: Crimson (#991B1B)
- UI feels intense and focused

### Romance (Pink/Gold cover)
- Primary: Soft pink (#EC4899)
- Secondary: Warm gold (#F59E0B)
- Tertiary: Rose (#BE185D)
- UI feels warm and inviting

## Performance
- Async extraction (non-blocking)
- Cached per book (instant on revisit)
- Smooth animated transitions (300ms ease)
- Zero impact on playback

## Quality
- Professional color theory
- Accessibility compliant (WCAG contrast)
- Polished like premium music apps
- No emojis, clean implementation

**Status**: Utility implemented, ready to integrate
