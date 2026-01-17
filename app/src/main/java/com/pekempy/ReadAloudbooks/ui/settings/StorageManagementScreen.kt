package com.pekempy.ReadAloudbooks.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.pekempy.ReadAloudbooks.R
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.Crossfade
import androidx.activity.compose.BackHandler
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageManagementScreen(
    viewModel: StorageManagementViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.loadFiles(context.filesDir)
    }

    var showSortMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = viewModel.selectedBookItem != null) {
        viewModel.selectedBookItem = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(viewModel.selectedBookItem?.book?.title ?: "Storage Management") 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (viewModel.selectedBookItem != null) {
                            viewModel.selectedBookItem = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                actions = {
                    if (viewModel.selectedBookItem == null) {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(painterResource(R.drawable.ic_sort), contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            StorageManagementViewModel.SortOption.values().forEach { option ->
                                val label = when (option) {
                                    StorageManagementViewModel.SortOption.RecentAsc -> "Oldest first"
                                    StorageManagementViewModel.SortOption.RecentDesc -> "Newest first"
                                    StorageManagementViewModel.SortOption.SizeAsc -> "Smallest first"
                                    StorageManagementViewModel.SortOption.SizeDesc -> "Largest first"
                                }
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.setSort(option)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    } else {
                         IconButton(onClick = { viewModel.deleteBook(viewModel.selectedBookItem!!) }) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete All", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Crossfade(targetState = viewModel.selectedBookItem, modifier = Modifier.padding(padding)) { selectedBook ->
            if (viewModel.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (selectedBook == null) {
                if (viewModel.bookItems.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No downloaded items found.", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    val activeDownloads = com.pekempy.ReadAloudbooks.data.DownloadManager.activeDownloads
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (activeDownloads.isNotEmpty()) {
                            item {
                                Text("Ongoing Downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            items(activeDownloads) { job ->
                                ActiveDownloadCard(job = job, onRemove = { com.pekempy.ReadAloudbooks.data.DownloadManager.removeJob(job) })
                            }
                            item {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        item {
                            StorageSummary(viewModel.bookItems)
                            Spacer(Modifier.height(16.dp))
                        }
                        items(viewModel.bookItems) { item ->
                            StorageBookCard(
                                item = item, 
                                onClick = { viewModel.selectedBookItem = item },
                                onDelete = { viewModel.deleteBook(item) }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedBook.items) { fileItem ->
                        StorageFileItem(
                            item = fileItem, 
                            onDelete = { viewModel.deleteFile(selectedBook, fileItem) }
                        )
                    }
                }
            }
        }
    }
    
    viewModel.deleteDialogItem?.let { item ->
        DeleteFilesDialog(
            item = item,
            onDismiss = { viewModel.deleteDialogItem = null },
            onConfirm = { filesToDelete ->
                viewModel.confirmDeleteFiles(item, filesToDelete)
            }
        )
    }
}

@Composable
fun DeleteFilesDialog(
    item: BookStorageItem,
    onDismiss: () -> Unit,
    onConfirm: (List<StorageItem>) -> Unit
) {
    var selectedFiles by remember { mutableStateOf(item.items.toSet()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Files") },
        text = {
            Column {
                Text(
                    "Select which files to delete for \"${item.book.title}\":",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                
                item.items.forEach { fileItem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                selectedFiles = if (fileItem in selectedFiles) {
                                    selectedFiles - fileItem
                                } else {
                                    selectedFiles + fileItem
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = fileItem in selectedFiles,
                            onCheckedChange = { checked ->
                                selectedFiles = if (checked) {
                                    selectedFiles + fileItem
                                } else {
                                    selectedFiles - fileItem
                                }
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            val fileType = when {
                                fileItem.name.contains("(readaloud)", ignoreCase = true) -> "ReadAloud"
                                fileItem.name.endsWith(".m4b", ignoreCase = true) -> "Audiobook"
                                fileItem.name.endsWith(".epub", ignoreCase = true) -> "eBook"
                                else -> "File"
                            }
                            Text(
                                fileType,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                formatSize(fileItem.sizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                
                Text(
                    "Total to delete: ${formatSize(selectedFiles.sumOf { it.sizeBytes })}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedFiles.toList()) },
                enabled = selectedFiles.isNotEmpty()
            ) {
                Text("Delete Selected")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun StorageSummary(items: List<BookStorageItem>) {
    val totalBytes = items.sumOf { it.totalSize }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Total Used Storage", style = MaterialTheme.typography.labelLarge)
            Text(
                text = formatSize(totalBytes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text("${items.size} books downloaded", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun StorageBookCard(item: BookStorageItem, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp, 90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (item.book.coverUrl != null) {
                    AsyncImage(
                        model = item.book.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.ic_book),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = item.book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${item.items.size} files • ${formatSize(item.totalSize)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete Book", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun StorageFileItem(item: StorageItem, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { 
            Text(item.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) 
        },
        supportingContent = { 
            Text("${formatSize(item.sizeBytes)} • ${formatDate(item.lastModified)}") 
        },
        leadingContent = {
            val iconRes = when {
                item.name.contains("(readaloud)", ignoreCase = true) -> R.drawable.ic_menu_book
                item.name.endsWith(".m4b", ignoreCase = true) -> R.drawable.ic_headset
                else -> R.drawable.ic_book
            }
            Icon(painterResource(iconRes), contentDescription = null)
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete File", tint = MaterialTheme.colorScheme.error)
            }
        }
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
fun ActiveDownloadCard(job: com.pekempy.ReadAloudbooks.data.DownloadJob, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = job.book.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp, 60.dp).clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(job.book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(job.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                if (job.isCompleted || job.isFailed) {
                    IconButton(onClick = onRemove) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "Clear")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { 
                            com.pekempy.ReadAloudbooks.data.DownloadManager.cancelProcessing(job.book.id)
                        }) {
                            Icon(
                                painterResource(R.drawable.ic_close), 
                                contentDescription = "Cancel",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { job.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb > 1 -> "%.2f GB".format(gb)
        mb > 1 -> "%.2f MB".format(mb)
        else -> "%.0f KB".format(kb)
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
