package com.pekempy.ReadAloudbooks.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.LinearProgressIndicator


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced Settings") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_settings),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Advanced Settings",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Power user options\n\nComing in v0.11.1",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingAnalyticsScreen(
    onBack: () -> Unit
) {
    val viewModel: com.pekempy.ReadAloudbooks.ui.analytics.ReadingAnalyticsViewModel = 
        androidx.lifecycle.viewmodel.compose.viewModel()
    val stats by viewModel.stats.collectAsState(initial = null)
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
                if (s.readingByBook.isNotEmpty()) {
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
                        s.readingByBook.take(5).forEach { book ->
                            BookReadCard(
                                title = book.title.ifEmpty { book.bookId },
                                totalTime = book.totalTimeMs
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
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BookReadCard(
    title: String,
    totalTime: Long,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )
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
