package com.pekempy.ReadAloudbooks.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.res.painterResource
import com.pekempy.ReadAloudbooks.R
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import coil.ImageLoader
import coil.compose.AsyncImage
import com.pekempy.ReadAloudbooks.data.api.AppContainer
import com.pekempy.ReadAloudbooks.data.Book

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookDetailScreen(
    viewModel: BookDetailViewModel,
    audiobookViewModel: com.pekempy.ReadAloudbooks.ui.player.AudiobookViewModel,
    readAloudViewModel: com.pekempy.ReadAloudbooks.ui.player.ReadAloudAudioViewModel,
    bookId: String,
    onBack: () -> Unit,
    onPlay: (Book) -> Unit,
    onAuthorClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onRead: (String, Boolean) -> Unit,
    onNavigateToDownloads: () -> Unit,
    onEdit: (String) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    val activeJob = viewModel.activeDownload
    LaunchedEffect(activeJob?.isCompleted) {
        if (activeJob?.isCompleted == true) {
            viewModel.loadBook(bookId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(8.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back), 
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onEdit(bookId) },
                        modifier = Modifier
                            .padding(8.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_edit), 
                            contentDescription = "Edit",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                val book = viewModel.book
                if (book != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = padding.calculateBottomPadding() + 16.dp)
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        DualCoverView(
                            ebookCover = book.ebookCoverUrl,
                            audiobookCover = book.audiobookCoverUrl,
                            fallbackCover = book.coverUrl,
                            seriesIndex = book.seriesIndex
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable { onAuthorClick(book.author) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_person),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = book.author,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            if (!book.narrator.isNullOrEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_mic),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = book.narrator,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }

                        if (!book.series.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val seriesText = if (!book.seriesIndex.isNullOrBlank()) {
                                "${book.series} #${book.seriesIndex}"
                            } else {
                                book.series
                            }
                            SuggestionChip(
                                onClick = { onSeriesClick(book.series) },
                                label = { Text(seriesText) },
                                icon = { Icon(painterResource(R.drawable.ic_list), contentDescription = null) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            FormatIndicator(
                                label = "ReadAloud",
                                iconRes = R.drawable.ic_menu_book,
                                isAvailable = book.hasReadAloud
                            )
                            FormatIndicator(
                                label = "Audio",
                                iconRes = R.drawable.ic_headset,
                                isAvailable = book.hasAudiobook
                            )
                            FormatIndicator(
                                label = "eBook",
                                iconRes = R.drawable.ic_book,
                                isAvailable = book.hasEbook
                            )
                        }
                        
                        if (book.isReadAloudQueued) {
                            val isError = book.processingStatus == "ERROR"
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background((if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer).copy(alpha = 0.5f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_history),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = if (isError) "Processing failed. Please retry from the Processing tab." else "Server is processing the readaloud for this book",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else if (!book.hasReadAloud && book.hasEbook && book.hasAudiobook) {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = { viewModel.createReadAloud() },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Create ReadAloud")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        val localProgress = viewModel.localProgress ?: 0f
                        val serverProgress = viewModel.serverProgress ?: 0f
                        
                        if (localProgress > 0f || serverProgress > 0f) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Position",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        if (serverProgress > 0f) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
                                                Text(
                                                    text = "Cloud: ${(serverProgress * 100).toInt()}%",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                        if (localProgress > 0f) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                                Text(
                                                    text = "Local: ${(localProgress * 100).toInt()}%",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                if (serverProgress > 0f) {
                                    LinearProgressIndicator(
                                        progress = { serverProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = MaterialTheme.colorScheme.secondary,
                                        trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                LinearProgressIndicator(
                                    progress = { localProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }

                        val currentActiveJob = viewModel.activeDownload
                        if (!book.isDownloaded) {
                            Button(
                                onClick = { viewModel.downloadAll(context.filesDir) },
                                enabled = currentActiveJob == null,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                if (currentActiveJob != null) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(Modifier.width(12.dp))
                                    Text("Downloading...")
                                } else {
                                    Icon(painterResource(R.drawable.ic_download), contentDescription = null)
                                    Spacer(Modifier.width(12.dp))
                                    Text("Download")
                                }
                            }
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                            ) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    val sections = mutableListOf<@Composable RowScope.() -> Unit>()

                                    if (book.hasReadAloud) {
                                        sections.add {
                                            val isDownloaded = book.isReadAloudDownloaded
                                            val isCurrentReadAloud = readAloudViewModel.currentBook?.id == book.id
                                            val label = if (isCurrentReadAloud) "Resume" else if (isDownloaded) "Read & Listen" else "Download\nReadAloud"
                                            
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .clickable { 
                                                        if (isDownloaded) {
                                                            if (isCurrentReadAloud && !readAloudViewModel.isPlaying) {
                                                                readAloudViewModel.play()
                                                            }
                                                            onRead(book.id, true) 
                                                        } else viewModel.downloadReadAloud(context.filesDir)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        painterResource(if (isDownloaded) {
                                                            if (isCurrentReadAloud && readAloudViewModel.isPlaying) R.drawable.ic_pause else R.drawable.ic_menu_book
                                                        } else R.drawable.ic_download), 
                                                        contentDescription = null, 
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Text(
                                                        label, 
                                                        style = MaterialTheme.typography.labelMedium, 
                                                        fontWeight = FontWeight.Bold,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        if (book.hasAudiobook) {
                                            sections.add {
                                                val isDownloaded = book.isAudiobookDownloaded
                                                val isCurrentAudio = audiobookViewModel.currentBook?.id == book.id
                                                val label = if (isCurrentAudio) "Resume" else if (isDownloaded) "Audio" else "Download\nAudio"
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                        .clickable { 
                                                            if (isDownloaded) {
                                                                if (isCurrentAudio && !audiobookViewModel.isPlaying) {
                                                                    audiobookViewModel.play()
                                                                }
                                                                onPlay(book) 
                                                            } else viewModel.downloadAudiobook(context.filesDir)
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Icon(
                                                            painterResource(if (isDownloaded) {
                                                                if (isCurrentAudio && audiobookViewModel.isPlaying) R.drawable.ic_pause else R.drawable.ic_headset
                                                            } else R.drawable.ic_download), 
                                                            contentDescription = null, 
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Text(
                                                            label, 
                                                            style = MaterialTheme.typography.labelSmall, 
                                                            fontWeight = FontWeight.Bold,
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (book.hasEbook) {
                                            sections.add {
                                                val isDownloaded = book.isEbookDownloaded
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                        .clickable { 
                                                            if (isDownloaded) onRead(book.id, false) 
                                                            else viewModel.downloadEbook(context.filesDir)
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Icon(
                                                            painterResource(if (isDownloaded) R.drawable.ic_book else R.drawable.ic_download), 
                                                            contentDescription = null, 
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Text(
                                                            if (isDownloaded) "eBook" else "Download\neBook", 
                                                            style = MaterialTheme.typography.labelSmall, 
                                                            fontWeight = FontWeight.Bold,
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    sections.forEachIndexed { index, section ->
                                        section()
                                        if (index < sections.size - 1) {
                                            VerticalDivider(
                                                modifier = Modifier.padding(vertical = 12.dp),
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (currentActiveJob != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToDownloads() },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = currentActiveJob.fileName, 
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${(currentActiveJob.progress * 100).toInt()}%", 
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { currentActiveJob.progress },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                    )
                                    
                                    if (currentActiveJob.status.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            currentActiveJob.status,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "Description",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val descriptionString = book.description ?: "No description available."
                        val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
                        val networkLinkColor = MaterialTheme.colorScheme.primary.toArgb()
                        
                        var isExpanded by remember { mutableStateOf(false) }
                        val displayDescription = if (!isExpanded && descriptionString.length > 300) {
                            descriptionString.take(300) + "..."
                        } else {
                            descriptionString
                        }
                        
                        Column(modifier = Modifier.fillMaxWidth()) {
                            AndroidView(
                                factory = { ctx ->
                                    android.widget.TextView(ctx).apply {
                                        textSize = 16f
                                        setTextColor(textColor)
                                        setLinkTextColor(networkLinkColor)
                                        movementMethod = android.text.method.LinkMovementMethod.getInstance()
                                    }
                                },
                                update = { textView ->
                                    textView.text = HtmlCompat.fromHtml(
                                        displayDescription,
                                        HtmlCompat.FROM_HTML_MODE_COMPACT
                                    )
                                    textView.setTextColor(textColor)
                                    textView.setLinkTextColor(networkLinkColor)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            if (descriptionString.length > 300) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = { isExpanded = !isExpanded },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(if (isExpanded) "Show less" else "Show more")
                                }
                            }
                        }
                    }
                } else if (viewModel.error != null) {
                    Text(
                        text = viewModel.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

    }
}

@Composable
fun FormatIndicator(
    label: String,
    iconRes: Int,
    isAvailable: Boolean
) {
    val alpha = if (isAvailable) 1f else 0.38f
    val color = if (isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.alpha(alpha)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = if (isAvailable) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
fun DualCoverView(
    ebookCover: String?,
    audiobookCover: String?,
    fallbackCover: String?,
    seriesIndex: String?
) {
    val hasAudio = audiobookCover != null
    val hasEbook = ebookCover != null

    if (hasAudio && hasEbook) {
        var showAudiobookFront by remember { mutableStateOf(false) }
        val transition = updateTransition(targetState = showAudiobookFront, label = "CoverSwitch")
        
        Box(
            modifier = Modifier
                .height(320.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { showAudiobookFront = !showAudiobookFront },
            contentAlignment = Alignment.TopCenter
        ) {
            val audioScale by transition.animateFloat(label = "audioScale") { if (it) 1f else 0.85f }
            val audioOffset by transition.animateDp(label = "audioOffset") { if (it) 0.dp else 90.dp }
            val audioAlpha by transition.animateFloat(label = "audioAlpha") { if (it) 1f else 0.8f }
            val audioZIndex by transition.animateFloat(label = "audioZ") { if (it) 1f else 0f }

            CoverCard(
                coverUrl = audiobookCover,
                fallbackUrl = audiobookCover,
                modifier = Modifier
                    .zIndex(audioZIndex)
                    .graphicsLayer {
                        scaleX = audioScale
                        scaleY = audioScale
                        translationX = audioOffset.toPx()
                        translationY = (-audioOffset).toPx() / 6
                        alpha = audioAlpha
                    },
                seriesIndex = if (showAudiobookFront) seriesIndex else null,
                isSquare = true
            )

            val ebookScale by transition.animateFloat(label = "ebookScale") { if (it) 0.85f else 1f }
            val ebookOffset by transition.animateDp(label = "ebookOffset") { if (it) (-90).dp else 0.dp }
            val ebookAlpha by transition.animateFloat(label = "ebookAlpha") { if (it) 0.85f else 1f }
            val ebookZIndex by transition.animateFloat(label = "ebookZ") { if (it) 0f else 1f }

            CoverCard(
                coverUrl = ebookCover,
                fallbackUrl = ebookCover,
                modifier = Modifier
                    .zIndex(ebookZIndex)
                    .graphicsLayer {
                        scaleX = ebookScale
                        scaleY = ebookScale
                        translationX = ebookOffset.toPx()
                        translationY = (-ebookOffset).toPx() / 10
                        alpha = ebookAlpha
                    },
                seriesIndex = if (!showAudiobookFront) seriesIndex else null,
                isSquare = false
            )
        }
    } else if (hasAudio) {
        SingleCoverWithBlur(audiobookCover, true, seriesIndex)
    } else if (hasEbook) {
        SingleCoverWithBlur(ebookCover, false, seriesIndex)
    } else {
        SingleCoverWithBlur(fallbackCover, true, seriesIndex)
    }
}

@Composable
fun SingleCoverWithBlur(
    coverUrl: String?,
    isSquare: Boolean,
    seriesIndex: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 3f
                    scaleY = 3f
                }
                .blur(40.dp)
                .alpha(0.3f),
            contentScale = ContentScale.Crop
        )
        
        CoverCard(
            coverUrl = coverUrl,
            fallbackUrl = coverUrl,
            isSquare = isSquare,
            seriesIndex = seriesIndex
        )
    }
}

@Composable
fun CoverCard(
    coverUrl: String?,
    fallbackUrl: String?,
    modifier: Modifier = Modifier,
    seriesIndex: String? = null,
    isSquare: Boolean = true
) {
    Card(
        modifier = modifier
            .height(340.dp)
            .aspectRatio(if (isSquare) 1f else 0.7f),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = coverUrl ?: fallbackUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                placeholder = painterResource(id = android.R.drawable.ic_menu_gallery),
                error = painterResource(id = android.R.drawable.ic_menu_gallery)
            )
            if (seriesIndex != null) {
                SeriesTag(seriesIndex, Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

@Composable
fun SeriesTag(index: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "#$index",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

