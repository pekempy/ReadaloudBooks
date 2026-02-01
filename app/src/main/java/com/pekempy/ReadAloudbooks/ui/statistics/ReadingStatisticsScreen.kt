package com.pekempy.ReadAloudbooks.ui.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pekempy.ReadAloudbooks.data.local.entities.ReadingSession
import com.pekempy.ReadAloudbooks.data.repository.ReadingStatisticsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * ViewModel for Reading Statistics
 */
class ReadingStatisticsViewModel(
    private val repository: ReadingStatisticsRepository
) : ViewModel() {

    private val _stats = MutableStateFlow<ReadingStats?>(null)
    val stats: StateFlow<ReadingStats?> = _stats

    private val _recentSessions = MutableStateFlow<List<ReadingSession>>(emptyList())
    val recentSessions: StateFlow<List<ReadingSession>> = _recentSessions

    data class ReadingStats(
        val todayMinutes: Long,
        val weekMinutes: Long,
        val monthMinutes: Long,
        val pagesReadToday: Int,
        val booksFinishedThisYear: Int,
        val currentStreak: Int,
        val longestStreak: Int = 0
    )

    init {
        loadStatistics()
    }

    fun loadStatistics() {
        viewModelScope.launch {
            val todayTime = repository.getTotalReadingTimeToday()
            val weekTime = repository.getTotalReadingTimeThisWeek()
            val monthTime = repository.getTotalReadingTimeThisMonth()
            val todayPages = repository.getTotalPagesReadToday()
            val streak = repository.getCurrentReadingStreak()

            _stats.value = ReadingStats(
                todayMinutes = TimeUnit.MILLISECONDS.toMinutes(todayTime),
                weekMinutes = TimeUnit.MILLISECONDS.toMinutes(weekTime),
                monthMinutes = TimeUnit.MILLISECONDS.toMinutes(monthTime),
                pagesReadToday = todayPages,
                booksFinishedThisYear = 0, // Feature removed
                currentStreak = streak
            )
        }

        viewModelScope.launch {
            repository.getRecentSessions(30).collect { sessions ->
                _recentSessions.value = sessions
            }
        }
    }

    fun formatDuration(minutes: Long): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) {
            "${hours}h ${mins}m"
        } else {
            "${mins}m"
        }
    }
}

/**
 * Reading Statistics Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingStatisticsScreen(
    viewModel: ReadingStatisticsViewModel,
    onBack: () -> Unit
) {
    val stats by viewModel.stats.collectAsState()
    val recentSessions by viewModel.recentSessions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading Statistics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary Cards
            item {
                Text(
                    "Overview",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (stats != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(
                            title = "Today",
                            value = viewModel.formatDuration(stats!!.todayMinutes),
                            subtitle = "${stats!!.pagesReadToday} pages",
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "Streak",
                            value = "${stats!!.currentStreak}",
                            subtitle = "days",
                            modifier = Modifier.weight(1f),
                            icon = "🔥"
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(
                            title = "This Week",
                            value = viewModel.formatDuration(stats!!.weekMinutes),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "This Month",
                            value = viewModel.formatDuration(stats!!.monthMinutes),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    StatCard(
                        title = "Books Finished This Year",
                        value = "${stats!!.booksFinishedThisYear}",
                        subtitle = "books completed",
                        modifier = Modifier.fillMaxWidth(),
                        icon = "📚"
                    )
                }
            } else {
                item {
                    CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                }
            }

            // Recent Reading Sessions
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Recent Reading Sessions",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            items(recentSessions.take(10)) { session ->
                ReadingSessionCard(session)
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon != null) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun ReadingSessionCard(session: ReadingSession) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val durationMinutes = TimeUnit.MILLISECONDS.toMinutes(session.durationMillis)

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.bookTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = dateFormat.format(Date(session.startTime)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (durationMinutes > 60) {
                        "${durationMinutes / 60}h ${durationMinutes % 60}m"
                    } else {
                        "${durationMinutes}m"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (session.pagesRead > 0) {
                    Text(
                        text = "${session.pagesRead} pages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
