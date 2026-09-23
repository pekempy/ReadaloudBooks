package com.pekempy.ReadAloudbooks.ui.library

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pekempy.ReadAloudbooks.R
import com.pekempy.ReadAloudbooks.data.Book
import com.pekempy.ReadAloudbooks.util.HapticFeedback
import com.pekempy.ReadAloudbooks.util.rememberHaptic

enum class BatchAction {
    DOWNLOAD,
    DELETE,
    ADD_TAG,
    REMOVE_TAG,
    MARK_READ,
    MARK_UNREAD,
    EXPORT
}

@Composable
fun BatchOperationBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onAction: (BatchAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    
    AnimatedVisibility(
        visible = selectedCount > 0,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = {
                        haptic(HapticFeedback.FeedbackType.LIGHT)
                        onCancel()
                    }) {
                        Icon(painterResource(R.drawable.ic_close), "Cancel")
                    }
                    
                    Text(
                        text = "$selectedCount selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = {
                        haptic(HapticFeedback.FeedbackType.MEDIUM)
                        onAction(BatchAction.DOWNLOAD)
                    }) {
                        Icon(painterResource(R.drawable.ic_download), "Download")
                    }
                    
                    IconButton(onClick = {
                        haptic(HapticFeedback.FeedbackType.MEDIUM)
                        onAction(BatchAction.ADD_TAG)
                    }) {
                        Icon(painterResource(R.drawable.ic_add), "Add Tag")
                    }
                    
                    IconButton(onClick = {
                        haptic(HapticFeedback.FeedbackType.MEDIUM)
                        onAction(BatchAction.DELETE)
                    }) {
                        Icon(painterResource(R.drawable.ic_delete), "Delete")
                    }
                }
            }
        }
    }
}

@Composable
fun BatchActionSheet(
    selectedBooks: List<Book>,
    action: BatchAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val haptic = rememberHaptic()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(getBatchActionTitle(action, selectedBooks.size))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(getBatchActionDescription(action, selectedBooks.size))
                
                if (selectedBooks.size <= 5) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    selectedBooks.forEach { book ->
                        Text(
                            text = "• ${book.title}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptic(HapticFeedback.FeedbackType.HEAVY)
                    onConfirm()
                },
                colors = if (action == BatchAction.DELETE) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                } else ButtonDefaults.buttonColors()
            ) {
                Text(getBatchActionButtonText(action))
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
fun SelectableBookList(
    books: List<Book>,
    selectedBooks: Set<String>,
    onBookSelectionChanged: (Book, Boolean) -> Unit,
    onBookClick: (Book) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(books, key = { it.id }) { book ->
            val isSelected = book.id in selectedBooks
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                onClick = {
                    haptic(HapticFeedback.FeedbackType.LIGHT)
                    if (selectedBooks.isEmpty()) {
                        onBookClick(book)
                    } else {
                        onBookSelectionChanged(book, !isSelected)
                    }
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedBooks.isNotEmpty()) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = {
                                haptic(HapticFeedback.FeedbackType.LIGHT)
                                onBookSelectionChanged(book, it)
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = book.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun getBatchActionTitle(action: BatchAction, count: Int): String {
    return when (action) {
        BatchAction.DOWNLOAD -> "Download $count books?"
        BatchAction.DELETE -> "Delete $count books?"
        BatchAction.ADD_TAG -> "Add tag to $count books?"
        BatchAction.REMOVE_TAG -> "Remove tag from $count books?"
        BatchAction.MARK_READ -> "Mark $count books as read?"
        BatchAction.MARK_UNREAD -> "Mark $count books as unread?"
        BatchAction.EXPORT -> "Export $count books?"
    }
}

private fun getBatchActionDescription(action: BatchAction, count: Int): String {
    return when (action) {
        BatchAction.DOWNLOAD -> "This will download all available formats for $count books."
        BatchAction.DELETE -> "This action cannot be undone. Local files will be removed."
        BatchAction.ADD_TAG -> "Select a tag to add to all selected books."
        BatchAction.REMOVE_TAG -> "Select a tag to remove from all selected books."
        BatchAction.MARK_READ -> "This will mark all selected books as completed."
        BatchAction.MARK_UNREAD -> "This will reset progress for all selected books."
        BatchAction.EXPORT -> "Export book data and metadata."
    }
}

private fun getBatchActionButtonText(action: BatchAction): String {
    return when (action) {
        BatchAction.DOWNLOAD -> "Download All"
        BatchAction.DELETE -> "Delete"
        BatchAction.ADD_TAG -> "Add Tag"
        BatchAction.REMOVE_TAG -> "Remove Tag"
        BatchAction.MARK_READ -> "Mark Read"
        BatchAction.MARK_UNREAD -> "Mark Unread"
        BatchAction.EXPORT -> "Export"
    }
}
