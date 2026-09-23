@file:OptIn(ExperimentalMaterial3Api::class)

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
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val haptic = rememberHaptic()
    
    // Create tab items from settings
    val tabs = remember(
        viewModel.showBooksTab,
        viewModel.showAuthorsTab,
        viewModel.showSeriesTab,
        viewModel.showCollectionsTab
    ) {
        listOf(
            TabItem("shelf", "Shelf", R.drawable.ic_shelves, enabled = true),
            TabItem("books", "Books", R.drawable.ic_book, viewModel.showBooksTab),
            TabItem("authors", "Authors", R.drawable.ic_person, viewModel.showAuthorsTab),
            TabItem("series", "Series", R.drawable.ic_list, viewModel.showSeriesTab),
            TabItem("collections", "Collections", R.drawable.ic_folder, viewModel.showCollectionsTab)
        )
    }
    
    var currentTabs by remember { mutableStateOf(tabs) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tab Ordering") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
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
                        isEnabled = !tab.id.startsWith("shelf"),
                        onToggle = {
                            haptic(HapticFeedback.FeedbackType.LIGHT)
                            viewModel.toggleTab(tab.id)
                            // Update local state
                            currentTabs = currentTabs.map {
                                if (it.id == tab.id) it.copy(enabled = !it.enabled)
                                else it
                            }
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
                    viewModel.updateTabOrder(currentTabs.map { it.id })
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Save Order")
            }
        }
    }
}

@Composable
private fun DraggableTabCard(
    tab: TabItem,
    isEnabled: Boolean,
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
            
            if (isEnabled) {
                Switch(
                    checked = tab.enabled,
                    onCheckedChange = { onToggle() }
                )
            }
        }
    }
}
