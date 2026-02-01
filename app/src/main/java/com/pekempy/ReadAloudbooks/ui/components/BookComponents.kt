package com.pekempy.ReadAloudbooks.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import com.pekempy.ReadAloudbooks.R
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pekempy.ReadAloudbooks.data.Book

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BookItem(
    book: Book, 
    downloadProgress: Float? = null, 
    onClick: () -> Unit, 
    onLongClick: (() -> Unit)? = null,
    onDownloadClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDownloading = downloadProgress != null
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .aspectRatio(0.7f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(20.dp)
                        .alpha(0.6f),
                    contentScale = ContentScale.Crop
                )

                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    error = painterResource(id = android.R.drawable.ic_menu_gallery),
                    placeholder = painterResource(id = android.R.drawable.ic_menu_gallery)
                )
                
                if (book.coverUrl == null) {
                    Icon(
                        painterResource(R.drawable.ic_book),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }
                
                if (onDownloadClick != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable(enabled = !isDownloading) { onDownloadClick() }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                progress = { downloadProgress ?: 0f },
                                modifier = Modifier.size(24.dp),
                                color = Color.Green,
                                strokeWidth = 2.dp,
                                trackColor = Color.LightGray.copy(alpha = 0.3f)
                            )
                        }
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = "Download",
                            modifier = Modifier.size(20.dp),
                            tint = if (book.isDownloaded) Color.Green else Color.LightGray
                        )
                    }
                }

                if (book.isReadAloudQueued) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_history),
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Processing",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                if (!book.seriesIndex.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "#${book.seriesIndex}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                if (book.progress != null) {
                    LinearProgressIndicator(
                        progress = { book.progress },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CategoryListItem(
    name: String,
    covers: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp, 60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (covers.isEmpty()) {
                    Icon(painterResource(R.drawable.ic_book), contentDescription = null, modifier = Modifier.size(32.dp))
                } else {
                    when {
                        covers.size == 1 -> {
                            AsyncImage(
                                model = covers[0],
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        covers.size < 4 -> {
                            Row(Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    AsyncImage(
                                        model = covers[0],
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    if (covers.size > 1) {
                                        AsyncImage(
                                            model = covers[1],
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            Column(Modifier.fillMaxSize()) {
                                Row(modifier = Modifier.weight(1f)) {
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        AsyncImage(model = covers[0], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    }
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        AsyncImage(model = covers[1], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    }
                                }
                                Row(modifier = Modifier.weight(1f)) {
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        AsyncImage(model = covers[2], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    }
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        AsyncImage(model = covers[3], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            Icon(painterResource(R.drawable.ic_keyboard_arrow_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun BookActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    book: Book?,
    onReadEbook: (Book) -> Unit,
    onPlayReadAloud: (Book) -> Unit,
    onPlayAudiobook: (Book) -> Unit,
    onMarkFinished: (Book) -> Unit,
    onMarkUnread: (Book) -> Unit,
    onEdit: (Book) -> Unit,
    onRemoveFromHome: ((Book) -> Unit)? = null
) {
    if (book == null) return

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        if (book.hasReadAloud) {
            DropdownMenuItem(
                text = { Text("Read & Listen") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_menu_book), contentDescription = null) },
                onClick = {
                    onPlayReadAloud(book)
                    onDismissRequest()
                }
            )
        } else {
            if (book.hasEbook) {
                DropdownMenuItem(
                    text = { Text("Read eBook") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_book), contentDescription = null) },
                    onClick = { 
                        onReadEbook(book)
                        onDismissRequest()
                    }
                )
            }
            if (book.hasAudiobook) {
                DropdownMenuItem(
                    text = { Text("Play Audiobook") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_headphones), contentDescription = null) },
                    onClick = {
                        onPlayAudiobook(book)
                        onDismissRequest()
                    }
                )
            }
        }

        HorizontalDivider()

        DropdownMenuItem(
            text = { Text("Mark Finished") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null) },
            onClick = {
                onMarkFinished(book)
                onDismissRequest()
            }
        )

        DropdownMenuItem(
            text = { Text("Mark Unread") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_history), contentDescription = null) },
            onClick = {
                onMarkUnread(book)
                onDismissRequest()
            }
        )

        DropdownMenuItem(
            text = { Text("Edit Metadata") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
            onClick = {
                onEdit(book)
                onDismissRequest()
            }
        )

        if (onRemoveFromHome != null) {
            DropdownMenuItem(
                text = { Text("Remove from Home") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                onClick = {
                    onRemoveFromHome(book)
                    onDismissRequest()
                }
            )
        }
    }
}

@Composable
fun SeriesActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    seriesName: String?,
    onDownloadSeries: (String) -> Unit
) {
    if (seriesName == null) return

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = { Text("Download All") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_download), contentDescription = null) },
            onClick = {
                onDownloadSeries(seriesName)
                onDismissRequest()
            }
        )
    }
}

