package com.pekempy.ReadAloudbooks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pekempy.ReadAloudbooks.data.local.entities.Highlight
import com.pekempy.ReadAloudbooks.ui.highlights.HighlightColors
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dialog for creating or editing a highlight with note
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightDialog(
    highlight: Highlight? = null,
    selectedText: String = "",
    selectedColor: String = HighlightColors.YELLOW.hex,
    onDismiss: () -> Unit,
    onSave: (color: String, note: String?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var color by remember { mutableStateOf(selectedColor) }
    var note by remember { mutableStateOf(highlight?.note ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (highlight != null) "Edit Highlight" else "New Highlight",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // Selected text preview
                if (selectedText.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "\"$selectedText\"",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Color picker
                Text("Highlight Color", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HighlightColors.values().forEach { highlightColor ->
                        ColorCircle(
                            color = Color(android.graphics.Color.parseColor(highlightColor.hex)),
                            isSelected = color == highlightColor.hex,
                            onClick = { color = highlightColor.hex }
                        )
                    }
                }

                // Note field
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Add Note (Optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5
                )

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onDelete != null) {
                        OutlinedButton(
                            onClick = {
                                onDelete()
                                onDismiss()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Delete")
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Button(onClick = {
                        onSave(color, note.ifBlank { null })
                        onDismiss()
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

/**
 * Color selection circle
 */
@Composable
fun ColorCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color, CircleShape)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

/**
 * List of highlights for a book
 */
@Composable
fun HighlightsList(
    highlights: List<Highlight>,
    chapterTitles: Map<Int, String> = emptyMap(),
    onHighlightClick: (Highlight) -> Unit,
    onHighlightLongClick: (Highlight) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(highlights.groupBy { it.chapterIndex }.toSortedMap().entries.toList()) { (chapterIndex, chapterHighlights) ->
            // Chapter header
            Text(
                text = chapterTitles[chapterIndex] ?: "Chapter ${chapterIndex + 1}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            chapterHighlights.forEach { highlight ->
                HighlightCard(
                    highlight = highlight,
                    onClick = { onHighlightClick(highlight) },
                    onLongClick = { onHighlightLongClick(highlight) }
                )
            }
        }
    }
}

/**
 * Individual highlight card
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightCard(
    highlight: Highlight,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(android.graphics.Color.parseColor(highlight.color)).copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Highlighted text
            Text(
                text = "\"${highlight.text}\"",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(android.graphics.Color.parseColor(highlight.color)).copy(alpha = 0.3f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(8.dp)
            )

            // Note if exists
            if (!highlight.note.isNullOrBlank()) {
                Text(
                    text = highlight.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Timestamp
            Text(
                text = dateFormat.format(Date(highlight.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Bottom sheet for highlight actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightActionsSheet(
    highlight: Highlight,
    onEdit: () -> Unit,
    onChangeColor: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Highlight Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            HighlightAction(icon = "✏️", text = "Edit", onClick = onEdit)
            HighlightAction(icon = "🎨", text = "Change Color", onClick = onChangeColor)
            HighlightAction(icon = "📋", text = "Copy Text", onClick = onCopy)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            HighlightAction(
                icon = "🗑️",
                text = "Delete Highlight",
                onClick = onDelete,
                isDestructive = true
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun HighlightAction(
    icon: String,
    text: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}
