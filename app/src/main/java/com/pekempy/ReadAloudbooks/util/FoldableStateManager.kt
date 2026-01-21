package com.pekempy.ReadAloudbooks.util

import android.app.Activity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.Flow

/**
 * Screen mode for reader layout
 */
enum class ReaderScreenMode {
    SINGLE_PAGE,    // Folded outer screen or regular phone
    TWO_PAGE        // Unfolded inner screen (book-like spread)
}

/**
 * Data class holding foldable state information
 */
data class FoldableState(
    val isFoldable: Boolean = false,
    val foldingFeature: FoldingFeature? = null,
    val screenMode: ReaderScreenMode = ReaderScreenMode.SINGLE_PAGE,
    val foldBoundsPx: Int = 0  // X position of fold line in pixels
) {
    val isUnfolded: Boolean
        get() = foldingFeature?.state == FoldingFeature.State.FLAT

    val isHalfOpened: Boolean
        get() = foldingFeature?.state == FoldingFeature.State.HALF_OPENED

    val isVerticalFold: Boolean
        get() = foldingFeature?.orientation == FoldingFeature.Orientation.VERTICAL
}

/**
 * Manager class for detecting and observing foldable device state
 */
class FoldableStateManager(private val activity: Activity) {

    private val windowInfoTracker = WindowInfoTracker.getOrCreate(activity)

    /**
     * Flow of WindowLayoutInfo for observing fold state changes
     */
    fun windowLayoutInfoFlow(): Flow<WindowLayoutInfo> {
        return windowInfoTracker.windowLayoutInfo(activity)
    }

    /**
     * Determine screen mode from layout info
     */
    fun getScreenMode(layoutInfo: WindowLayoutInfo): FoldableState {
        val foldingFeature = layoutInfo.displayFeatures
            .filterIsInstance<FoldingFeature>()
            .firstOrNull()

        return if (foldingFeature != null) {
            val screenMode = when {
                // Fully flat with vertical fold = book mode (two-page spread)
                foldingFeature.state == FoldingFeature.State.FLAT &&
                foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL -> {
                    ReaderScreenMode.TWO_PAGE
                }
                // Half-opened (book posture) with vertical fold = book mode
                foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
                foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL -> {
                    ReaderScreenMode.TWO_PAGE
                }
                // Any other configuration (horizontal fold, etc.) = single page
                else -> ReaderScreenMode.SINGLE_PAGE
            }

            FoldableState(
                isFoldable = true,
                foldingFeature = foldingFeature,
                screenMode = screenMode,
                foldBoundsPx = if (foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL) {
                    foldingFeature.bounds.left
                } else {
                    0
                }
            )
        } else {
            // Not a foldable device or no folding feature detected
            FoldableState(
                isFoldable = false,
                screenMode = ReaderScreenMode.SINGLE_PAGE
            )
        }
    }
}

/**
 * Composable hook to observe foldable state changes
 * Returns a State<FoldableState> that updates when the device fold state changes
 */
@Composable
fun rememberFoldableState(): State<FoldableState> {
    val context = LocalContext.current
    val activity = context as? Activity

    val foldableState = remember { mutableStateOf(FoldableState()) }

    if (activity != null) {
        val manager = remember { FoldableStateManager(activity) }

        LaunchedEffect(Unit) {
            manager.windowLayoutInfoFlow().collect { layoutInfo ->
                foldableState.value = manager.getScreenMode(layoutInfo)
            }
        }
    }

    return foldableState
}
