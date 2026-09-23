package com.pekempy.ReadAloudbooks.ui.home

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pekempy.ReadAloudbooks.R
import com.pekempy.ReadAloudbooks.util.HapticFeedback
import com.pekempy.ReadAloudbooks.util.rememberHaptic

/**
 * Draggable widget system for customizable home page
 * Users can reorder and enable/disable widgets
 */

enum class WidgetType {
    CONTINUE_READING,
    QUICK_STATS,
    RECENT_BOOKS,
    RECOMMENDATIONS,
    READING_GOALS,
    QUICK_ACTIONS
}

data class HomeWidget(
    val id: String,
    val type: WidgetType,
    val name: String,
    val description: String,
    val icon: Int,
    val enabled: Boolean = true,
    val order: Int = 0
)

@Composable
fun WidgetCustomizer(
    widgets: List<HomeWidget>,
    onReorder: (List<HomeWidget>) -> Unit,
    onToggle: (HomeWidget) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    var currentWidgets by remember { mutableStateOf(widgets.sortedBy { it.order }) }
    
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Customize Home Page",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )
        
        Text(
            text = "Long press and drag to reorder widgets",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = currentWidgets,
                key = { _, widget -> widget.id }
            ) { index, widget ->
                DraggableWidget(
                    widget = widget,
                    onToggle = {
                        haptic(HapticFeedback.FeedbackType.LIGHT)
                        onToggle(widget)
                    },
                    onDragStart = {
                        haptic(HapticFeedback.FeedbackType.MEDIUM)
                    }
                )
            }
        }
        
        HorizontalDivider()
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    currentWidgets = widgets
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Reset")
            }
            
            Button(
                onClick = {
                    haptic(HapticFeedback.FeedbackType.HEAVY)
                    onReorder(currentWidgets)
                    onSave()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun DraggableWidget(
    widget: HomeWidget,
    onToggle: () -> Unit,
    onDragStart: () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = if (isDragging) 0.8f else 1f
                scaleX = if (isDragging) 1.03f else 1f
                scaleY = if (isDragging) 1.03f else 1f
                shadowElevation = if (isDragging) 8f else 2f
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
            },
        colors = CardDefaults.cardColors(
            containerColor = if (widget.enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle
            Icon(
                painter = painterResource(R.drawable.ic_folder),
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            // Widget icon
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (widget.enabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(widget.icon),
                        contentDescription = null,
                        tint = if (widget.enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // Widget info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = widget.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (widget.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    }
                )
                Text(
                    text = widget.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Enable/disable switch
            Switch(
                checked = widget.enabled,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

/**
 * Predefined widget configurations
 */
fun getDefaultWidgets(): List<HomeWidget> = listOf(
    HomeWidget(
        id = "continue_reading",
        type = WidgetType.CONTINUE_READING,
        name = "Continue Reading",
        description = "Your in-progress books",
        icon = R.drawable.ic_book,
        enabled = true,
        order = 0
    ),
    HomeWidget(
        id = "quick_stats",
        type = WidgetType.QUICK_STATS,
        name = "Quick Stats",
        description = "Reading progress at a glance",
        icon = R.drawable.ic_calendar_today,
        enabled = true,
        order = 1
    ),
    HomeWidget(
        id = "recent_books",
        type = WidgetType.RECENT_BOOKS,
        name = "Recent Books",
        description = "Recently added to library",
        icon = R.drawable.ic_download,
        enabled = true,
        order = 2
    ),
    HomeWidget(
        id = "recommendations",
        type = WidgetType.RECOMMENDATIONS,
        name = "Recommendations",
        description = "Suggested for you",
        icon = R.drawable.ic_search,
        enabled = false,
        order = 3
    ),
    HomeWidget(
        id = "reading_goals",
        type = WidgetType.READING_GOALS,
        name = "Reading Goals",
        description = "Weekly and monthly targets",
        icon = R.drawable.ic_check_circle,
        enabled = false,
        order = 4
    ),
    HomeWidget(
        id = "quick_actions",
        type = WidgetType.QUICK_ACTIONS,
        name = "Quick Actions",
        description = "Shortcuts to common tasks",
        icon = R.drawable.ic_settings,
        enabled = true,
        order = 5
    )
)
