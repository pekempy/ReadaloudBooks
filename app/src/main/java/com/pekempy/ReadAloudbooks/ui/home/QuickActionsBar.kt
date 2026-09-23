package com.pekempy.ReadAloudbooks.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pekempy.ReadAloudbooks.R
import com.pekempy.ReadAloudbooks.util.HapticFeedback
import com.pekempy.ReadAloudbooks.util.rememberHaptic

/**
 * Quick actions bar for common tasks
 * Fast access to frequently used features
 */

data class QuickAction(
    val id: String,
    val label: String,
    val icon: Int,
    val onClick: () -> Unit
)

@Composable
fun QuickActionsBar(
    actions: List<QuickAction>,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.take(4).forEach { action ->
            QuickActionButton(
                action = action,
                modifier = Modifier.weight(1f),
                onClickWithHaptic = {
                    haptic(HapticFeedback.FeedbackType.MEDIUM)
                    action.onClick()
                }
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    action: QuickAction,
    modifier: Modifier = Modifier,
    onClickWithHaptic: () -> Unit
) {
    Card(
        onClick = onClickWithHaptic,
        modifier = modifier.height(72.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
        ) {
            Icon(
                painter = painterResource(action.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

/**
 * Predefined quick actions
 */
object QuickActions {
    fun getDefaultActions(
        onSearchClick: () -> Unit,
        onDownloadsClick: () -> Unit,
        onCollectionsClick: () -> Unit,
        onStatsClick: () -> Unit
    ): List<QuickAction> = listOf(
        QuickAction(
            id = "search",
            label = "Search",
            icon = R.drawable.ic_search,
            onClick = onSearchClick
        ),
        QuickAction(
            id = "downloads",
            label = "Downloads",
            icon = R.drawable.ic_download,
            onClick = onDownloadsClick
        ),
        QuickAction(
            id = "collections",
            label = "Collections",
            icon = R.drawable.ic_list,
            onClick = onCollectionsClick
        ),
        QuickAction(
            id = "stats",
            label = "Stats",
            icon = R.drawable.ic_calendar_today,
            onClick = onStatsClick
        )
    )
}
