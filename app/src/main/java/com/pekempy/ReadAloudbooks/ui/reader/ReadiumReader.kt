package com.pekempy.ReadAloudbooks.ui.reader

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.LocalContext
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.AbsoluteUrl
import android.graphics.PointF
import android.view.GestureDetector
import android.view.MotionEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubSettings
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import com.pekempy.ReadAloudbooks.data.UserSettings
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ReadiumReader(
    publication: Publication,
    initialLocator: Locator?,
    userSettings: UserSettings? = null,
    modifier: Modifier = Modifier,
    accentColor: String = "#0000FF",
    onTap: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    onReady: () -> Unit = {},
    onElementTap: (String) -> Unit = {},
    onLocatorChange: (Locator) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity ?: return
    val fragmentManager = activity.supportFragmentManager
    
    val containerId = remember { android.view.View.generateViewId() }
    
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            FragmentContainerView(ctx).apply {
                id = containerId
                
                val factory = EpubNavigatorFactory(publication)
                val listener = object : EpubNavigatorFragment.Listener {
                    override fun onExternalLinkActivated(url: AbsoluteUrl) {
                        // Link handling
                    }
                }

                val initialPrefs = userSettings?.let { settings ->
                    EpubPreferences(
                        fontSize = settings.readerFontSize / 16.0,
                        theme = when(settings.readerTheme) {
                            1 -> Theme.SEPIA
                            2, 3 -> Theme.DARK
                            else -> Theme.LIGHT
                        },
                        fontFamily = FontFamily(settings.readerFontFamily)
                    )
                } ?: EpubPreferences()

                fragmentManager.fragmentFactory = factory.createFragmentFactory(
                    initialLocator = initialLocator,
                    listener = listener,
                    initialPreferences = initialPrefs
                )
                
                fragmentManager.commit {
                    replace(containerId, EpubNavigatorFragment::class.java, null, "readium_navigator")
                }
                
                val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        onDoubleTap()
                        return true
                    }
                })
                
                setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                    false
                }
            }
        },
        update = { _ ->
            // Fragment management is handled via LaunchedEffect and fragmentManager
        }
    )
    
    // Manage fragment lifecycle, listeners and initial settings
    LaunchedEffect(containerId) {
        var fragment: EpubNavigatorFragment? = null
        while (fragment == null) {
            fragment = fragmentManager.findFragmentById(containerId) as? EpubNavigatorFragment
            if (fragment == null) fragment = fragmentManager.findFragmentByTag("readium_navigator") as? EpubNavigatorFragment
            if (fragment == null) kotlinx.coroutines.delay(100)
        }
        
        onReady()
        
        // Apply initial preferences once ready
        userSettings?.let { settings ->
            val initialPrefs = EpubPreferences(
                fontSize = settings.readerFontSize / 16.0,
                theme = when(settings.readerTheme) {
                    1 -> Theme.SEPIA
                    2, 3 -> Theme.DARK
                    else -> Theme.LIGHT
                },
                backgroundColor = if (settings.readerTheme >= 2) ReadiumColor(0xFF121212.toInt()) else null,
                fontFamily = FontFamily(settings.readerFontFamily)
            )
            fragment?.submitPreferences(initialPrefs)
        }
        
        var lastTapTime = 0L
        val doubleTapTimeout = android.view.ViewConfiguration.getDoubleTapTimeout()
        
        fragment.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTapTime < doubleTapTimeout) {
                    onDoubleTap()
                    lastTapTime = 0L
                    return true
                }
                lastTapTime = currentTime
                return false
            }
        })
        
        fragment.currentLocator.collectLatest { locator ->
            onLocatorChange(locator)
        }
    }
    
    // Update preferences when settings change later
    LaunchedEffect(userSettings) {
        userSettings?.let { settings ->
            val fragment = fragmentManager.findFragmentById(containerId) as? EpubNavigatorFragment
            fragment?.let { 
                val newPrefs = EpubPreferences(
                    fontSize = settings.readerFontSize / 16.0,
                    theme = when(settings.readerTheme) {
                        1 -> Theme.SEPIA
                        2, 3 -> Theme.DARK
                        else -> Theme.LIGHT
                    },
                    backgroundColor = if (settings.readerTheme >= 2) ReadiumColor(0xFF121212.toInt()) else null,
                    fontFamily = FontFamily(settings.readerFontFamily)
                )
                it.submitPreferences(newPrefs)
            }
        }
    }
    
    // Jump to locator when initialLocator changes from outside
    LaunchedEffect(initialLocator) {
        initialLocator?.let { locator ->
            val fragment = fragmentManager.findFragmentById(containerId) as? EpubNavigatorFragment
            if (fragment != null && fragment.currentLocator.value != locator) {
                fragment.go(locator, animated = true)
            }
        }
    }
}
