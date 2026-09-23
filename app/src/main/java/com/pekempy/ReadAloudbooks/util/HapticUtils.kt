package com.pekempy.ReadAloudbooks.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView

/**
 * Premium haptic feedback utility for professional app feel
 * No emojis, just clean feedback
 */
object HapticFeedback {
    
    enum class FeedbackType {
        LIGHT,      // Selection, hover
        MEDIUM,     // Button press, toggle
        HEAVY,      // Important action
        SUCCESS,    // Completion
        ERROR,      // Failure
        LONG_PRESS  // Context menu
    }
    
    fun perform(view: View, type: FeedbackType) {
        when (type) {
            FeedbackType.LIGHT -> 
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            
            FeedbackType.MEDIUM -> 
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            
            FeedbackType.HEAVY,
            FeedbackType.SUCCESS -> 
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            
            FeedbackType.ERROR -> 
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            
            FeedbackType.LONG_PRESS -> 
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
    
    fun vibrate(context: Context, type: FeedbackType) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        if (!vibrator.hasVibrator()) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (type) {
                FeedbackType.LIGHT -> 
                    VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE)
                
                FeedbackType.MEDIUM -> 
                    VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
                
                FeedbackType.HEAVY -> 
                    VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
                
                FeedbackType.SUCCESS -> 
                    VibrationEffect.createWaveform(longArrayOf(0, 20, 40, 20), -1)
                
                FeedbackType.ERROR -> 
                    VibrationEffect.createWaveform(longArrayOf(0, 30, 30, 30), -1)
                
                FeedbackType.LONG_PRESS -> 
                    VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(when (type) {
                FeedbackType.LIGHT -> 10
                FeedbackType.MEDIUM -> 20
                FeedbackType.HEAVY, FeedbackType.SUCCESS -> 30
                FeedbackType.ERROR -> 50
                FeedbackType.LONG_PRESS -> 50
            })
        }
    }
}

@Composable
fun rememberHaptic(): (HapticFeedback.FeedbackType) -> Unit {
    val view = LocalView.current
    return { type -> HapticFeedback.perform(view, type) }
}
