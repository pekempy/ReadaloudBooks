package com.pekempy.ReadAloudbooks.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.pekempy.ReadAloudbooks.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.ImageLoader
import com.pekempy.ReadAloudbooks.data.api.AppContainer
import com.pekempy.ReadAloudbooks.util.FormatUtils
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookPlayerScreen(
    viewModel: AudiobookViewModel,
    bookId: String,
    onBack: () -> Unit,
    onSwitchToReadAloud: (String) -> Unit
) {
    val context = LocalContext.current
    val imageLoader = remember {
        val client = AppContainer.apiClientManager.okHttpClient 
            ?: return@remember ImageLoader(context)
        ImageLoader.Builder(context)
            .okHttpClient(client)
            .build()
    }

    var showSpeedSheet by remember { mutableStateOf(false) }
    var showChaptersSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkLabel by remember { mutableStateOf("") }

    LaunchedEffect(bookId) {
        viewModel.initializePlayer(context)
        viewModel.loadBook(bookId)
    }

    viewModel.syncConfirmation?.let { sync ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSync() },
            title = { Text("Progress Sync") },
            text = { 
                Text("Progress is out of sync with Storyteller.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmSync() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use server (${"%.1f".format(sync.progressPercent)}%)")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissSync() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use local (${"%.1f".format(sync.localProgressPercent)}%)")
                }
            }
        )
    }

    if (viewModel.error != null) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Error Opening Audiobook") },
            text = { Text(viewModel.error ?: "Unknown error") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.redownloadBook(context)
                        onBack()
                    }
                ) {
                    Text("Redownload")
                }
            },
            dismissButton = {
                TextButton(onClick = onBack) {
                    Text("Go Back")
                }
            }
        )
    }

    Scaffold { padding ->
        if (viewModel.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val book = viewModel.currentBook
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_keyboard_arrow_down), contentDescription = "Close")
                    }
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Box(contentAlignment = Alignment.TopEnd) {
                        IconButton(onClick = { 
                            if (viewModel.sleepTimerRemaining <= 0 && !viewModel.isWaitingForChapterEnd) {
                                viewModel.applyDefaultSleepTimer()
                            }
                            showSleepTimerSheet = true 
                        }) {
                            Icon(
                                painterResource(if (viewModel.sleepTimerRemaining > 0 || viewModel.isWaitingForChapterEnd) R.drawable.ic_snooze else R.drawable.ic_bedtime),
                                contentDescription = "Sleep Timer",
                                tint = if (viewModel.sleepTimerRemaining > 0 || viewModel.isWaitingForChapterEnd) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                        if (viewModel.sleepTimerRemaining > 0 || viewModel.isWaitingForChapterEnd) {
                            Text(
                                text = if (viewModel.isWaitingForChapterEnd) "Stopping at end of chapter" else FormatUtils.formatSleepTime(viewModel.sleepTimerRemaining),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 32.dp, end = 8.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .maxHeight(350.dp),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        AsyncImage(
                            model = book?.audiobookCoverUrl ?: book?.coverUrl,
                            imageLoader = imageLoader,
                            contentDescription = "Book Cover",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (book?.series != null) {
                        Text(
                            text = "${book.series}${if (book.seriesIndex != null) " #${book.seriesIndex}" else ""}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Text(
                        text = book?.title ?: "Unknown Title",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = book?.author ?: "Unknown Author",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    if (!book?.narrator.isNullOrBlank()) {
                        Text(
                            text = "Narrated by ${book?.narrator}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Slider(
                        value = if (viewModel.duration > 0) viewModel.currentPosition.toFloat() else 0f,
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..(if (viewModel.duration > 0) viewModel.duration.toFloat() else 1f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = FormatUtils.formatTime(viewModel.currentPosition), style = MaterialTheme.typography.labelMedium)
                        Text(text = FormatUtils.formatTime(viewModel.duration), style = MaterialTheme.typography.labelMedium)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.rewind10s() }) {
                        Icon(painterResource(R.drawable.ic_replay_10), contentDescription = "Rewind 10s", Modifier.size(32.dp))
                    }
                    IconButton(onClick = { 
                        if (viewModel.chapters.isNotEmpty()) {
                        }
                    }) {
                        Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = "Previous", Modifier.size(40.dp))
                    }
                    FilledIconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.size(72.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(
                            painterResource(if (viewModel.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                            contentDescription = "Play/Pause",
                            Modifier.size(40.dp)
                        )
                    }
                    IconButton(onClick = { 
                        if (viewModel.chapters.isNotEmpty()) {
                        }
                    }) {
                        Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "Next", Modifier.size(40.dp))
                    }
                    IconButton(onClick = { viewModel.forward30s() }) {
                        Icon(painterResource(R.drawable.ic_forward_30), contentDescription = "Forward 30s", Modifier.size(32.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showSpeedSheet = true }
                    ) {
                        Text("${"%.2f".format(viewModel.playbackSpeed)}x Speed")
                    }

                    Row {
                        Box(contentAlignment = Alignment.TopCenter) {
                            IconButton(
                                onClick = { showBookmarksSheet = true }
                            ) {
                                Icon(painterResource(R.drawable.ic_bookmark), contentDescription = "Bookmarks")
                            }
                            if (viewModel.audioBookmarks.value.isNotEmpty()) {
                                Badge(
                                    modifier = Modifier.padding(top = 8.dp, end = 8.dp)
                                ) {
                                    Text("${viewModel.audioBookmarks.value.size}")
                                }
                            }
                        }

                        IconButton(
                            onClick = { showChaptersSheet = true }
                        ) {
                            Icon(painterResource(R.drawable.ic_list), contentDescription = "Chapters")
                        }
                    }
                }
            }

            if (showSpeedSheet) {
                ModalBottomSheet(onDismissRequest = { showSpeedSheet = false }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Playback Speed",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "${"%.2f".format(viewModel.playbackSpeed)}x",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Slider(
                            value = viewModel.playbackSpeed,
                            onValueChange = { 
                                val rounded = (it * 20).roundToInt() / 20f
                                viewModel.setSpeed(rounded) 
                            },
                            valueRange = 0.5f..2.0f,
                            steps = 29
                        )
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }

            if (showChaptersSheet) {
                ModalBottomSheet(onDismissRequest = { showChaptersSheet = false }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Chapters",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        if (viewModel.chapters.isEmpty()) {
                            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                Text("No internal chapters found", color = MaterialTheme.colorScheme.secondary)
                            }
                        } else {
                            LazyColumn {
                                itemsIndexed(viewModel.chapters) { index, chapter ->
                                    ListItem(
                                        headlineContent = { Text(chapter.title) },
                                        supportingContent = { Text(FormatUtils.formatTime(chapter.startOffset)) },
                                        trailingContent = { Text(FormatUtils.formatTime(chapter.duration)) },
                                        modifier = Modifier.clickable {
                                            viewModel.skipToChapter(index)
                                            showChaptersSheet = false
                                        },
                                        colors = ListItemDefaults.colors(
                                            containerColor = if (viewModel.currentPosition in chapter.startOffset..(chapter.startOffset + chapter.duration)) 
                                                MaterialTheme.colorScheme.primaryContainer 
                                            else MaterialTheme.colorScheme.surface
                                        )
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }

            if (showSleepTimerSheet) {
                ModalBottomSheet(onDismissRequest = { showSleepTimerSheet = false }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Sleep Timer",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(16.dp))
                        
                        val selectedMinutes = if (viewModel.sleepTimerRemaining > 0) 
                            (viewModel.sleepTimerRemaining / 60000).toInt() 
                        else 0

                        Text(
                            text = if (selectedMinutes > 0) "$selectedMinutes minutes" else "Off",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Slider(
                            value = selectedMinutes.toFloat(),
                            onValueChange = { 
                                viewModel.setSleepTimer(it.roundToInt())
                            },
                            valueRange = 0f..120f,
                            steps = 119
                        )
                        
                        Spacer(Modifier.height(8.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleSleepTimerFinishChapter() }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                "Play until end of chapter", 
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = viewModel.sleepTimerFinishChapter,
                                onCheckedChange = { viewModel.toggleSleepTimerFinishChapter() }
                            )
                        }
                        
                        TextButton(
                            onClick = { 
                                viewModel.setSleepTimer(0)
                                showSleepTimerSheet = false
                            },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Turn Off")
                        }
                        
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }

            if (showAddBookmarkDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showAddBookmarkDialog = false
                        bookmarkLabel = ""
                    },
                    title = { Text("Add Bookmark") },
                    text = {
                        Column {
                            Text(
                                text = "Bookmark at ${FormatUtils.formatTime(viewModel.currentPosition)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(
                                value = bookmarkLabel,
                                onValueChange = { bookmarkLabel = it },
                                label = { Text("Label (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.addAudioBookmark(
                                    label = bookmarkLabel.ifBlank { null }
                                )
                                showAddBookmarkDialog = false
                                bookmarkLabel = ""
                            }
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showAddBookmarkDialog = false
                                bookmarkLabel = ""
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showBookmarksSheet) {
                ModalBottomSheet(onDismissRequest = { showBookmarksSheet = false }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bookmarks",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            FilledTonalButton(
                                onClick = {
                                    showBookmarksSheet = false
                                    showAddBookmarkDialog = true
                                }
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_bookmark_add),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Add")
                            }
                        }

                        if (viewModel.audioBookmarks.value.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No bookmarks yet",
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        } else {
                            LazyColumn {
                                itemsIndexed(viewModel.audioBookmarks.value.sortedBy { it.timestampMillis }) { index, bookmark ->
                                    ListItem(
                                        headlineContent = {
                                            Text(
                                                bookmark.label ?: "Bookmark ${index + 1}",
                                                fontWeight = FontWeight.Medium
                                            )
                                        },
                                        supportingContent = {
                                            Column {
                                                Text(FormatUtils.formatTime(bookmark.timestampMillis))
                                                if (bookmark.chapterIndex != null && bookmark.chapterIndex in viewModel.chapters.indices) {
                                                    Text(
                                                        viewModel.chapters[bookmark.chapterIndex].title,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                        },
                                        leadingContent = {
                                            Icon(
                                                painterResource(R.drawable.ic_bookmark),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        trailingContent = {
                                            Row {
                                                IconButton(
                                                    onClick = {
                                                        viewModel.navigateToAudioBookmark(bookmark)
                                                        showBookmarksSheet = false
                                                    }
                                                ) {
                                                    Icon(
                                                        painterResource(R.drawable.ic_play_arrow),
                                                        contentDescription = "Play"
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.deleteAudioBookmark(bookmark) }
                                                ) {
                                                    Icon(
                                                        painterResource(R.drawable.ic_delete),
                                                        contentDescription = "Delete",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.clickable {
                                            viewModel.navigateToAudioBookmark(bookmark)
                                            showBookmarksSheet = false
                                        }
                                    )
                                    if (index < viewModel.audioBookmarks.value.size - 1) {
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

fun Modifier.maxHeight(max: androidx.compose.ui.unit.Dp) = this.then(
    Modifier.heightIn(max = max)
)
