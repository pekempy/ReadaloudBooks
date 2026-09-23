package com.pekempy.ReadAloudbooks.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pekempy.ReadAloudbooks.R
import java.util.*

/**
 * Personalized greeting for home screen
 * Time-based messages with user stats
 */

@Composable
fun PersonalizedGreeting(
    userName: String? = null,
    currentStreak: Int = 0,
    booksInProgress: Int = 0,
    modifier: Modifier = Modifier
) {
    val greeting = remember { getTimeBasedGreeting() }
    val motivationalMessage = remember(currentStreak, booksInProgress) {
        getMotivationalMessage(currentStreak, booksInProgress)
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Main greeting
        Text(
            text = if (!userName.isNullOrBlank()) {
                "$greeting, $userName"
            } else {
                greeting
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        // Motivational message
        AnimatedVisibility(
            visible = motivationalMessage.isNotBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(getMotivationalIcon(currentStreak)),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                
                Text(
                    text = motivationalMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MotivationalCard(
    currentStreak: Int,
    nextMilestone: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_calendar_today),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getMilestoneMessage(currentStreak, nextMilestone),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                val progress = currentStreak.toFloat() / nextMilestone
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.tertiary
                )
                
                Text(
                    text = "${nextMilestone - currentStreak} days to go",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun getTimeBasedGreeting(): String {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..4 -> "Still up?"
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Good night"
    }
}

private fun getMotivationalMessage(streak: Int, booksInProgress: Int): String {
    return when {
        streak >= 7 -> "Amazing $streak day streak!"
        streak > 0 -> "Keep it up, $streak days"
        booksInProgress > 3 -> "You have $booksInProgress books in progress"
        booksInProgress == 1 -> "1 book waiting for you"
        else -> "Ready to start something new?"
    }
}

private fun getMotivationalIcon(streak: Int): Int {
    return when {
        streak >= 7 -> R.drawable.ic_check_circle
        streak > 0 -> R.drawable.ic_calendar_today
        else -> R.drawable.ic_book
    }
}

private fun getMilestoneMessage(current: Int, next: Int): String {
    return when (next) {
        7 -> "Reach a 7-day streak"
        14 -> "Two weeks in a row"
        30 -> "One month streak"
        100 -> "Century club"
        else -> "Reach $next days"
    }
}
