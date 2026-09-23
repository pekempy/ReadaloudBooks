package com.pekempy.ReadAloudbooks.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pekempy.ReadAloudbooks.util.HapticFeedback
import com.pekempy.ReadAloudbooks.util.rememberHaptic
import kotlin.math.roundToInt

/**
 * Chapter markers on audiobook progress bar
 * Visual indicators showing chapter boundaries with tap-to-seek
 */

data class Chapter(
    val title: String,
    val startOffset: Long, // milliseconds
    val duration: Long,    // milliseconds
    val index: Int
)

@Composable
fun ChapterProgressBar(
    currentPosition: Long,
    totalDuration: Long,
    chapters: List<Chapter>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    val progress = if (totalDuration > 0) {
        (currentPosition.toFloat() / totalDuration).coerceIn(0f, 1f)
    } else 0f
    
    Column(modifier = modifier) {
        // Current chapter indicator
        val currentChapter = remember(currentPosition, chapters) {
            chapters.find { chapter ->
                currentPosition >= chapter.startOffset &&
                currentPosition < chapter.startOffset + chapter.duration
            }
        }
        
        currentChapter?.let {
            Text(
                text = it.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        // Progress bar with chapter markers
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {
            // Background track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            
            // Progress track
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            
            // Chapter markers
            chapters.forEach { chapter ->
                val markerPosition = if (totalDuration > 0) {
                    (chapter.startOffset.toFloat() / totalDuration).coerceIn(0f, 1f)
                } else 0f
                
                // Skip first marker (0:00)
                if (markerPosition > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .offset(x = (markerPosition * 100).dp) // Approximate positioning
                            .width(2.dp)
                            .align(Alignment.CenterStart)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(12.dp)
                                .align(Alignment.Center)
                                .background(
                                    color = if (currentPosition >= chapter.startOffset) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    }
                                )
                        )
                    }
                }
            }
            
            // Seek thumb
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .offset(x = (progress * 100).dp) // Approximate positioning
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        
        // Time labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentPosition),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTime(totalDuration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ChapterList(
    chapters: List<Chapter>,
    currentPosition: Long,
    onChapterClick: (Chapter) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Chapters",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        chapters.forEach { chapter ->
            val isCurrentChapter = currentPosition >= chapter.startOffset &&
                    currentPosition < chapter.startOffset + chapter.duration
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic(HapticFeedback.FeedbackType.MEDIUM)
                        onChapterClick(chapter)
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrentChapter) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Chapter number
                    Surface(
                        shape = CircleShape,
                        color = if (isCurrentChapter) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${chapter.index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrentChapter) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Chapter info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrentChapter) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatTime(chapter.startOffset)} • ${formatDuration(chapter.duration)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Playing indicator
                    if (isCurrentChapter) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(8.dp)
                        ) {}
                    }
                }
            }
        }
    }
}

/**
 * Compact chapter navigation for mini player
 */
@Composable
fun ChapterNavigation(
    chapters: List<Chapter>,
    currentChapter: Chapter?,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    val currentIndex = currentChapter?.index ?: -1
    val hasPrevious = currentIndex > 0
    val hasNext = currentIndex < chapters.size - 1
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                if (hasPrevious) {
                    haptic(HapticFeedback.FeedbackType.MEDIUM)
                    onPreviousChapter()
                }
            },
            enabled = hasPrevious
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(
                    com.pekempy.ReadAloudbooks.R.drawable.ic_arrow_back
                ),
                contentDescription = "Previous Chapter",
                tint = if (hasPrevious) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        }
        
        Text(
            text = currentChapter?.title ?: "No Chapter",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        
        IconButton(
            onClick = {
                if (hasNext) {
                    haptic(HapticFeedback.FeedbackType.MEDIUM)
                    onNextChapter()
                }
            },
            enabled = hasNext
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(
                    com.pekempy.ReadAloudbooks.R.drawable.ic_arrow_forward
                ),
                contentDescription = "Next Chapter",
                tint = if (hasNext) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalMinutes = (milliseconds / 60000).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
