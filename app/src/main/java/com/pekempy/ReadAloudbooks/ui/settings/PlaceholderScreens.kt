package com.pekempy.ReadAloudbooks.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pekempy.ReadAloudbooks.R
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.material3.LinearProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingAnalyticsScreen(
    onBack: () -> Unit
) {
    val viewModel: com.pekempy.ReadAloudbooks.ui.analytics.ReadingAnalyticsViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()
    val stats by viewModel.stats.collectAsState(initial = null)
    val resolvedBooks by viewModel.resolvedBooks.collectAsState(initial = emptyList())
    val favoriteBook by viewModel.favoriteBook.collectAsState(initial = null)
    val favoriteAuthor by viewModel.favoriteAuthor.collectAsState(initial = null)
    val isLoading by viewModel.isLoading.collectAsState(initial = false)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading Analytics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        if (isLoading || stats == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                val s = stats!!
                
                // Summary Cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Total Time",
                        value = formatDuration(s.totalReadingTimeMs),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Current Streak",
                        value = "${s.currentStreak} days",
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Avg Session Time
                StatCard(
                    title = "Avg Session",
                    value = formatDuration(s.averageSessionMs),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                )

                // Favourites
                if (favoriteBook != null || favoriteAuthor != null) {
                    Text(
                        "Favourites",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        favoriteBook?.let { fav ->
                            FavoriteCard(
                                label = "Favourite Book",
                                title = fav.book?.title?.takeIf { it.isNotBlank() } ?: "Unknown book",
                                subtitle = formatDuration(fav.stat.totalTimeMs),
                                coverUrl = fav.book?.ebookCoverUrl ?: fav.book?.audiobookCoverUrl ?: fav.book?.coverUrl,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        favoriteAuthor?.let { fav ->
                            FavoriteCard(
                                label = "Favourite Author",
                                title = fav.author,
                                subtitle = "${formatDuration(fav.totalTimeMs)} · ${fav.bookCount} book${if (fav.bookCount != 1) "s" else ""}",
                                coverUrl = null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                // Weekly Progress
                if (s.readingByDay.isNotEmpty()) {
                    Text(
                        "Reading by Day (Last 30 Days)",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    val maxTime = s.readingByDay.values.maxOrNull() ?: 1L
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        s.readingByDay.entries.take(7).forEach { (date, duration) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    date,
                                    modifier = Modifier
                                        .width(80.dp)
                                        .padding(end = 8.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                
                                val progress = (duration.toFloat() / maxTime).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                Text(
                                    formatDuration(duration),
                                    modifier = Modifier
                                        .width(60.dp)
                                        .padding(start = 8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
                
                // Books Read
                if (resolvedBooks.isNotEmpty()) {
                    Text(
                        "Reading by Book",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        resolvedBooks.take(5).forEach { entry ->
                            BookReadCard(
                                title = entry.book?.title?.takeIf { it.isNotBlank() } ?: "Unknown book",
                                author = entry.book?.author,
                                coverUrl = entry.book?.ebookCoverUrl ?: entry.book?.audiobookCoverUrl ?: entry.book?.coverUrl,
                                totalTime = entry.stat.totalTimeMs
                            )
                        }
                    }
                } else {
                    Text(
                        "No reading data yet. Start reading to see your stats!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FavoriteCard(
    label: String,
    title: String,
    subtitle: String,
    coverUrl: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (coverUrl != null) {
                    coil.compose.AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_book),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BookReadCard(
    title: String,
    totalTime: Long,
    author: String? = null,
    coverUrl: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            coil.compose.AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )
                if (!author.isNullOrBlank()) {
                    Text(
                        author,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    formatDuration(totalTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return when {
        hours > 0 -> String.format("%dh %dm", hours, minutes)
        minutes > 0 -> String.format("%dm %ds", minutes, secs)
        else -> String.format("%ds", secs)
    }
}
