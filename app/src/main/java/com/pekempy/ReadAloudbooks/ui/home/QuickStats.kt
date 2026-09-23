package com.pekempy.ReadAloudbooks.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pekempy.ReadAloudbooks.R

/**
 * Quick stats widget for home screen
 * Shows reading progress at a glance
 */

data class QuickStatsData(
    val booksRead: Int,
    val currentStreak: Int,
    val hoursListened: Float,
    val weeklyGoalProgress: Float // 0.0 to 1.0
)

@Composable
fun QuickStatsWidget(
    stats: QuickStatsData,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Progress",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                TextButton(
                    onClick = onViewAllClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("View All", style = MaterialTheme.typography.labelMedium)
                    Icon(
                        painterResource(R.drawable.ic_arrow_forward),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            
            // Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickStatItem(
                    value = stats.booksRead.toString(),
                    label = "Books",
                    icon = R.drawable.ic_book,
                    color = MaterialTheme.colorScheme.primary
                )
                
                QuickStatItem(
                    value = stats.currentStreak.toString(),
                    label = "Day Streak",
                    icon = R.drawable.ic_calendar_today,
                    color = MaterialTheme.colorScheme.tertiary
                )
                
                QuickStatItem(
                    value = "${stats.hoursListened.toInt()}h",
                    label = "Listened",
                    icon = R.drawable.ic_headphones,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            
            // Weekly goal progress
            if (stats.weeklyGoalProgress > 0) {
                WeeklyGoalProgress(stats.weeklyGoalProgress)
            }
        }
    }
}

@Composable
private fun QuickStatItem(
    value: String,
    label: String,
    icon: Int,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.22f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WeeklyGoalProgress(progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "progress"
    )
    
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Weekly Goal",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

/**
 * Compact version for smaller spaces
 */
@Composable
fun CompactStatsRow(
    stats: QuickStatsData,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        CompactStatItem(
            value = stats.booksRead.toString(),
            label = "Books",
            icon = R.drawable.ic_book
        )
        
        VerticalDivider(modifier = Modifier.height(40.dp))
        
        CompactStatItem(
            value = "${stats.hoursListened.toInt()}h",
            label = "Hours",
            icon = R.drawable.ic_headphones
        )
        
        VerticalDivider(modifier = Modifier.height(40.dp))
        
        CompactStatItem(
            value = "${(stats.weeklyGoalProgress * 100).toInt()}%",
            label = "Goal",
            icon = R.drawable.ic_check_circle
        )
    }
}

@Composable
private fun CompactStatItem(value: String, label: String, icon: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
