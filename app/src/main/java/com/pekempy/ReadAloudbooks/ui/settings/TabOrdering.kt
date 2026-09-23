package com.pekempy.ReadAloudbooks.ui.settings

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pekempy.ReadAloudbooks.R
import com.pekempy.ReadAloudbooks.util.HapticFeedback
import com.pekempy.ReadAloudbooks.util.rememberHaptic

data class TabItem(
    val id: String,
    val name: String,
    val icon: Int,
    val enabled: Boolean = true
)

@Composable
fun TabOrderingScreen(
    tabs: List<TabItem>,
    onReorder: (List<TabItem>) -> Unit,
    onToggle: (TabItem) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    var currentTabs by remember { mutableStateOf(tabs) }
    
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Drag to reorder tabs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Long press and drag to change order",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            itemsIndexed(
                items = currentTabs,
                key = { _, item -> item.id }
            ) { index, tab ->
                DraggableTabCard(
                    tab = tab,
                    onToggle = {
                        haptic(HapticFeedback.FeedbackType.LIGHT)
                        onToggle(tab)
                    },
                    onDragStart = {
                        haptic(HapticFeedback.FeedbackType.MEDIUM)
                    }
                )
            }
        }
        
        Button(
            onClick = {
                haptic(HapticFeedback.FeedbackType.HEAVY)
                onReorder(currentTabs)
                onSave()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Save Order")
        }
    }
}

@Composable
private fun DraggableTabCard(
    tab: TabItem,
    onToggle: () -> Unit,
    onDragStart: () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = if (isDragging) 0.7f else 1f
                scaleX = if (isDragging) 1.05f else 1f
                scaleY = if (isDragging) 1.05f else 1f
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        isDragging = true
                        onDragStart()
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { _, _ -> }
                )
            }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_folder),
                contentDescription = "Drag handle",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Icon(
                painter = painterResource(tab.icon),
                contentDescription = null,
                tint = if (tab.enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )
            
            Text(
                text = tab.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                color = if (tab.enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                }
            )
            
            Switch(
                checked = tab.enabled,
                onCheckedChange = { onToggle() }
            )
        }
    }
}
