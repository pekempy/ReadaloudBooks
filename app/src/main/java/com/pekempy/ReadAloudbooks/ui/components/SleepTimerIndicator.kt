package com.pekempy.ReadAloudbooks.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pekempy.ReadAloudbooks.R

/**
 * Sleep timer button styled as a clock face: while a timer is running the remaining
 * time is shown as digits inside the clock outline instead of a plain bell/moon icon.
 */
@Composable
fun SleepTimerIndicator(
    remainingMs: Long,
    isWaitingForChapterEnd: Boolean,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = remainingMs > 0 || isWaitingForChapterEnd
    val color = if (active) activeColor else tint

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(
                if (active) Modifier.border(BorderStroke(1.5.dp, color), CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            isWaitingForChapterEnd -> Icon(
                painter = painterResource(R.drawable.ic_snooze),
                contentDescription = "Sleep timer: stopping at end of chapter",
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            active -> {
                val totalSeconds = remainingMs / 1000
                val minutes = (totalSeconds / 60).toInt()
                val seconds = (totalSeconds % 60).toInt()
                Text(
                    text = if (minutes > 0) "$minutes" else "${seconds}s",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            else -> Icon(
                painter = painterResource(R.drawable.ic_bedtime),
                contentDescription = "Sleep timer",
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
