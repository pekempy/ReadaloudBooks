package com.pekempy.ReadAloudbooks.ui.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.dp
import com.pekempy.ReadAloudbooks.util.HapticFeedback
import com.pekempy.ReadAloudbooks.util.rememberHaptic
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Swipe gesture handling for audiobook player
 * - Swipe left: Skip forward 30 seconds
 * - Swipe right: Skip back 10 seconds
 * - Visual feedback with haptics
 */

data class SwipeConfig(
    val skipForwardSeconds: Int = 30,
    val skipBackSeconds: Int = 10,
    val threshold: Float = 100f,
    val enableHaptics: Boolean = true
)

enum class SwipeDirection {
    LEFT, RIGHT, NONE
}

@Composable
fun rememberSwipeState(
    config: SwipeConfig = SwipeConfig(),
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {}
): SwipeGestureState {
    val haptic = rememberHaptic()
    return remember(config) {
        SwipeGestureState(
            config = config,
            haptic = if (config.enableHaptics) haptic else null,
            onSwipeLeft = onSwipeLeft,
            onSwipeRight = onSwipeRight
        )
    }
}

class SwipeGestureState(
    private val config: SwipeConfig,
    private val haptic: ((HapticFeedback.FeedbackType) -> Unit)?,
    private val onSwipeLeft: () -> Unit,
    private val onSwipeRight: () -> Unit
) {
    var offsetX by mutableStateOf(0f)
    var swipeProgress by mutableStateOf(0f)
    var direction by mutableStateOf(SwipeDirection.NONE)
    
    private var hasTriggeredHaptic = false
    
    suspend fun onDrag(dragAmount: Offset) {
        offsetX += dragAmount.x
        swipeProgress = (abs(offsetX) / config.threshold).coerceIn(0f, 1f)
        
        direction = when {
            offsetX < -config.threshold / 2 -> SwipeDirection.LEFT
            offsetX > config.threshold / 2 -> SwipeDirection.RIGHT
            else -> SwipeDirection.NONE
        }
        
        // Haptic at 50% threshold
        if (swipeProgress >= 0.5f && !hasTriggeredHaptic) {
            haptic?.invoke(HapticFeedback.FeedbackType.MEDIUM)
            hasTriggeredHaptic = true
        }
    }
    
    suspend fun onDragEnd() {
        val triggeredDirection = direction
        
        // Trigger action if threshold met
        if (abs(offsetX) >= config.threshold) {
            when (triggeredDirection) {
                SwipeDirection.LEFT -> {
                    haptic?.invoke(HapticFeedback.FeedbackType.HEAVY)
                    onSwipeLeft()
                }
                SwipeDirection.RIGHT -> {
                    haptic?.invoke(HapticFeedback.FeedbackType.HEAVY)
                    onSwipeRight()
                }
                SwipeDirection.NONE -> {}
            }
        }
        
        // Reset
        offsetX = 0f
        swipeProgress = 0f
        direction = SwipeDirection.NONE
        hasTriggeredHaptic = false
    }
    
    fun getSwipeModifier(): Modifier = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = { kotlinx.coroutines.runBlocking { onDragEnd() } },
            onDragCancel = { kotlinx.coroutines.runBlocking { onDragEnd() } },
            onHorizontalDrag = { _, dragAmount ->
                kotlinx.coroutines.runBlocking { onDrag(Offset(dragAmount, 0f)) }
            }
        )
    }
}

/**
 * Modifier extension for swipe gestures
 */
fun Modifier.swipeGesture(state: SwipeGestureState): Modifier {
    return this
        .then(state.getSwipeModifier())
        .graphicsLayer {
            translationX = state.offsetX * 0.3f // Subtle visual feedback
            alpha = 1f - (state.swipeProgress * 0.1f)
        }
}

/**
 * Visual indicator for swipe feedback
 */
@Composable
fun SwipeFeedbackIndicator(
    state: SwipeGestureState,
    config: SwipeConfig,
    modifier: Modifier = Modifier
) {
    val targetAlpha = if (state.swipeProgress > 0.3f) 0.8f else 0f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(150),
        label = "swipe_indicator_alpha"
    )
    
    if (alpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(60.dp)
                .graphicsLayer { this.alpha = alpha }
        ) {
            Surface(
                color = when (state.direction) {
                    SwipeDirection.LEFT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    SwipeDirection.RIGHT -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                    SwipeDirection.NONE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                },
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = when (state.direction) {
                        SwipeDirection.LEFT -> Arrangement.End
                        SwipeDirection.RIGHT -> Arrangement.Start
                        SwipeDirection.NONE -> Arrangement.Center
                    },
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = when (state.direction) {
                            SwipeDirection.LEFT -> "+${config.skipForwardSeconds}s"
                            SwipeDirection.RIGHT -> "-${config.skipBackSeconds}s"
                            SwipeDirection.NONE -> ""
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Example usage in player screen:
 * 
 * val swipeState = rememberSwipeState(
 *     onSwipeLeft = { viewModel.seekForward(30) },
 *     onSwipeRight = { viewModel.seekBackward(10) }
 * )
 * 
 * Box(modifier = Modifier.swipeGesture(swipeState)) {
 *     // Player UI content
 *     SwipeFeedbackIndicator(swipeState, SwipeConfig())
 * }
 */
