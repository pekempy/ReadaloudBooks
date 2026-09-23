package com.pekempy.ReadAloudbooks.ui.library

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pekempy.ReadAloudbooks.R
import com.pekempy.ReadAloudbooks.util.HapticFeedback
import com.pekempy.ReadAloudbooks.util.rememberHaptic

data class BookTag(
    val id: String,
    val name: String,
    val color: TagColor,
    val bookCount: Int = 0
)

enum class TagColor(val color: Color, val label: String) {
    RED(Color(0xFFEF4444), "Red"),
    ORANGE(Color(0xFFF97316), "Orange"),
    YELLOW(Color(0xFFF59E0B), "Yellow"),
    GREEN(Color(0xFF10B981), "Green"),
    BLUE(Color(0xFF3B82F6), "Blue"),
    PURPLE(Color(0xFF8B5CF6), "Purple"),
    PINK(Color(0xFFEC4899), "Pink"),
    GRAY(Color(0xFF6B7280), "Gray")
}

@Composable
fun TagManager(
    tags: List<BookTag>,
    onCreateTag: (String, TagColor) -> Unit,
    onDeleteTag: (BookTag) -> Unit,
    onEditTag: (BookTag, String, TagColor) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    val haptic = rememberHaptic()
    
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tags",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Button(
                onClick = {
                    haptic(HapticFeedback.FeedbackType.MEDIUM)
                    showCreateDialog = true
                }
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Create Tag")
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        if (tags.isEmpty()) {
            EmptyTagsState(
                onCreateClick = { showCreateDialog = true }
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(tags) { tag ->
                    TagChip(
                        tag = tag,
                        onClick = { /* Filter by tag */ },
                        onDelete = { onDeleteTag(tag) }
                    )
                }
            }
        }
    }
    
    if (showCreateDialog) {
        CreateTagDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, color ->
                onCreateTag(name, color)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun TagChip(
    tag: BookTag,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = rememberHaptic()
    
    Surface(
        onClick = {
            haptic(HapticFeedback.FeedbackType.LIGHT)
            onClick()
        },
        shape = RoundedCornerShape(16.dp),
        color = tag.color.color.copy(alpha = 0.2f),
        modifier = Modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(tag.color.color, RoundedCornerShape(6.dp))
            )
            
            Text(
                text = tag.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            if (tag.bookCount > 0) {
                Text(
                    text = "${tag.bookCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(
                onClick = {
                    haptic(HapticFeedback.FeedbackType.LIGHT)
                    onDelete()
                },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = "Delete",
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun CreateTagDialog(
    onDismiss: () -> Unit,
    onCreate: (String, TagColor) -> Unit
) {
    var tagName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(TagColor.BLUE) }
    val haptic = rememberHaptic()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = tagName,
                    onValueChange = { tagName = it },
                    label = { Text("Tag Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelMedium
                )
                
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TagColor.values().toList()) { color ->
                        ColorOption(
                            color = color,
                            selected = color == selectedColor,
                            onClick = {
                                haptic(HapticFeedback.FeedbackType.LIGHT)
                                selectedColor = color
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (tagName.isNotBlank()) {
                        haptic(HapticFeedback.FeedbackType.MEDIUM)
                        onCreate(tagName, selectedColor)
                    }
                },
                enabled = tagName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptic(HapticFeedback.FeedbackType.LIGHT)
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ColorOption(
    color: TagColor,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick)
            .background(
                if (selected) color.color.copy(alpha = 0.3f) else Color.Transparent,
                RoundedCornerShape(20.dp)
            )
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color.color, RoundedCornerShape(16.dp))
        )
    }
}

@Composable
private fun EmptyTagsState(onCreateClick: () -> Unit) {
    val haptic = rememberHaptic()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painterResource(R.drawable.ic_book),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "No tags yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = "Create tags to organize your library",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Button(onClick = {
            haptic(HapticFeedback.FeedbackType.MEDIUM)
            onCreateClick()
        }) {
            Text("Create First Tag")
        }
    }
}
