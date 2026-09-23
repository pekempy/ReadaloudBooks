package com.pekempy.ReadAloudbooks.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pekempy.ReadAloudbooks.R
import com.pekempy.ReadAloudbooks.data.Book
import com.pekempy.ReadAloudbooks.util.HapticFeedback
import com.pekempy.ReadAloudbooks.util.rememberHaptic

/**
 * Premium download format selection dialog
 * Allows users to choose which formats to download with smooth animations
 */

data class DownloadOption(
    val type: DownloadType,
    val title: String,
    val subtitle: String,
    val icon: Int,
    val available: Boolean,
    val downloaded: Boolean = false,
    val sizeEstimate: String? = null
)

enum class DownloadType {
    AUDIOBOOK,
    EBOOK,
    READALOUD,
    ALL
}

@Composable
fun DownloadDialog(
    book: Book,
    onDismiss: () -> Unit,
    onDownloadSelected: (DownloadType) -> Unit
) {
    val haptic = rememberHaptic()
    
    // Build available options
    val options = remember(book) {
        buildList {
            if (book.hasAudiobook) {
                add(DownloadOption(
                    type = DownloadType.AUDIOBOOK,
                    title = "Audiobook",
                    subtitle = "Full narration audio files",
                    icon = R.drawable.ic_headphones,
                    available = true,
                    downloaded = book.isAudiobookDownloaded,
                    sizeEstimate = estimateSize(book, DownloadType.AUDIOBOOK)
                ))
            }
            if (book.hasEbook) {
                add(DownloadOption(
                    type = DownloadType.EBOOK,
                    title = "E-book",
                    subtitle = "Text version for reading",
                    icon = R.drawable.ic_book,
                    available = true,
                    downloaded = book.isEbookDownloaded,
                    sizeEstimate = estimateSize(book, DownloadType.EBOOK)
                ))
            }
            if (book.hasReadAloud) {
                add(DownloadOption(
                    type = DownloadType.READALOUD,
                    title = "Read Aloud",
                    subtitle = "Synchronized audiobook + ebook highlighting",
                    icon = R.drawable.ic_play_arrow,
                    available = true,
                    downloaded = book.isReadAloudDownloaded,
                    sizeEstimate = estimateSize(book, DownloadType.READALOUD)
                ))
            }
            
            // Only show "All" if multiple formats available and not all downloaded
            val allDownloaded = book.isAudiobookDownloaded && book.isEbookDownloaded && book.isReadAloudDownloaded
            if (size > 1 && !allDownloaded) {
                add(DownloadOption(
                    type = DownloadType.ALL,
                    title = "Download All",
                    subtitle = "All available formats",
                    icon = R.drawable.ic_download,
                    available = true,
                    downloaded = false,
                    sizeEstimate = "Combined: ${estimateTotalSize(book)}"
                ))
            }
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var isVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { isVisible = true }
        
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(300)) + scaleIn(
                initialScale = 0.8f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Text(
                        text = "Download Options",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Download options
                    options.forEachIndexed { index, option ->
                        DownloadOptionCard(
                            option = option,
                            onClick = {
                                haptic(HapticFeedback.FeedbackType.MEDIUM)
                                onDownloadSelected(option.type)
                                onDismiss()
                            },
                            delay = index * 50L
                        )
                        
                        if (index < options.lastIndex) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Cancel button
                    TextButton(
                        onClick = {
                            haptic(HapticFeedback.FeedbackType.LIGHT)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadOptionCard(
    option: DownloadOption,
    onClick: () -> Unit,
    delay: Long = 0L
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay)
        isVisible = true
    }
    
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "card_scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "card_alpha"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(alpha)
            .clickable(enabled = option.available) { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 8.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(option.icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (option.available) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    }
                )
                
                Text(
                    text = option.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (option.available) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
                
                option.sizeEstimate?.let { size ->
                    Text(
                        text = size,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            // Downloaded checkmark or arrow
            if (option.downloaded) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check_circle),
                            contentDescription = "Downloaded",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Downloaded",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (option.available) {
                Icon(
                    painter = painterResource(R.drawable.ic_keyboard_arrow_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun estimateSize(book: Book, type: DownloadType): String {
    // Rough estimates based on typical book lengths
    // Could be enhanced with actual metadata from API
    return when (type) {
        DownloadType.AUDIOBOOK -> "~200-500 MB"
        DownloadType.EBOOK -> "~5-15 MB"
        DownloadType.READALOUD -> "~150-400 MB"
        DownloadType.ALL -> estimateTotalSize(book)
    }
}

private fun estimateTotalSize(book: Book): String {
    val formats = buildList {
        if (book.hasAudiobook) add("Audiobook")
        if (book.hasEbook) add("E-book")
        if (book.hasReadAloud) add("Read Aloud")
    }
    
    return when (formats.size) {
        1 -> "~200-500 MB"
        2 -> "~400-900 MB"
        3 -> "~550-1.2 GB"
        else -> "Unknown"
    }
}
