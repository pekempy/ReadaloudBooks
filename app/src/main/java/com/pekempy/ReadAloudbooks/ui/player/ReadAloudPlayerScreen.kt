package com.pekempy.ReadAloudbooks.ui.player

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.pekempy.ReadAloudbooks.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.ImageLoader
import com.pekempy.ReadAloudbooks.data.api.AppContainer
import com.pekempy.ReadAloudbooks.ui.reader.*
import com.pekempy.ReadAloudbooks.ui.player.AudiobookViewModel
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.pekempy.ReadAloudbooks.util.FormatUtils
import com.pekempy.ReadAloudbooks.util.rememberFoldableState
import com.pekempy.ReadAloudbooks.util.ReaderScreenMode
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadAloudPlayerScreen(
    readerViewModel: ReaderViewModel,
    readAloudAudioViewModel: ReadAloudAudioViewModel,
    bookId: String,
    initiallyExpanded: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    DisposableEffect(readAloudAudioViewModel.isPlaying) {
        view.keepScreenOn = readAloudAudioViewModel.isPlaying
        onDispose {
            view.keepScreenOn = false
        }
    }
    val userSettings = readerViewModel.settings
    var isPlayerExpanded by remember { mutableStateOf(initiallyExpanded) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showChaptersSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showHighlightsSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }

    // Collect highlights for the book
    val highlights by readerViewModel.getHighlightsForBook().collectAsState(initial = emptyList())
    val bookmarks by readerViewModel.bookmarks

    // Foldable device support
    val foldableState by rememberFoldableState()
    val isTwoPageMode = foldableState.screenMode == ReaderScreenMode.TWO_PAGE

    // Update ViewModel when fold state changes
    LaunchedEffect(foldableState.screenMode) {
        readerViewModel.updateScreenMode(foldableState.screenMode, foldableState.foldBoundsPx)
    }

    // Adjust status bar icons based on theme (light/dark)
    val window = (view.context as? android.app.Activity)?.window
    LaunchedEffect(userSettings?.readerTheme) {
        window?.let { w ->
            val controller = androidx.core.view.WindowInsetsControllerCompat(w, view)
            // Themes 0 (white) and 1 (sepia) are light backgrounds - use dark status bar icons
            // Themes 2 (dark) and 3 (AMOLED black) are dark backgrounds - use light status bar icons
            val isLightBackground = (userSettings?.readerTheme ?: 0) <= 1
            controller.isAppearanceLightStatusBars = isLightBackground
        }
    }

    // Highlight UI state
    var showColorPicker by remember { mutableStateOf(false) }
    var showLongPressMenu by remember { mutableStateOf(false) }

    // Collect highlight events from ViewModel using SharedFlow
    LaunchedEffect(Unit) {
        android.util.Log.e("ReadAloudPlayerScreen", "=== Starting highlightEvents collector ===")
        readerViewModel.highlightEvents.collect { event ->
            android.util.Log.e("ReadAloudPlayerScreen", "=== RECEIVED event: $event ===")
            when (event) {
                is ReaderViewModel.HighlightEvent.ShowLongPressMenu -> {
                    android.util.Log.e("ReadAloudPlayerScreen", "=== SHOWING long press menu ===")
                    showLongPressMenu = true
                }
                is ReaderViewModel.HighlightEvent.ShowColorPicker -> {
                    android.util.Log.e("ReadAloudPlayerScreen", "=== SHOWING color picker ===")
                    showColorPicker = true
                }
            }
        }
    }

    LaunchedEffect(bookId) {
        readerViewModel.loadEpub(bookId, isReadAloud = true)
        readAloudAudioViewModel.initializePlayer(context)
    }
    
    val highlightId = readAloudAudioViewModel.currentElementId
    
    LaunchedEffect(readerViewModel.lazyBook, readAloudAudioViewModel.isLoading) {
        if (readerViewModel.lazyBook != null && !readAloudAudioViewModel.isLoading) {
            val audioCh = readAloudAudioViewModel.currentChapterIndex
            val audioId = readAloudAudioViewModel.currentElementId
            if (audioCh >= 0 && audioCh != readerViewModel.currentChapterIndex) {
                 readerViewModel.changeChapter(audioCh, readAloudAudioViewModel.currentPosition)
            }
            if (audioId != null) {
                readerViewModel.currentHighlightId = audioId
                readerViewModel.forceScrollUpdate()
            }
        }
    }

    LaunchedEffect(highlightId, readAloudAudioViewModel.isLoading) {
        if (!readAloudAudioViewModel.isLoading && highlightId != null && readerViewModel.currentHighlightId != highlightId) {
            readerViewModel.currentHighlightId = highlightId
            readerViewModel.forceScrollUpdate()
        }
    }

    // Track play/pause for history
    var wasPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(readAloudAudioViewModel.isPlaying) {
        // Only record after initial load (when duration is known)
        if (readAloudAudioViewModel.duration > 0) {
            if (readAloudAudioViewModel.isPlaying && !wasPlaying) {
                // Started playing
                readerViewModel.addHistoryEvent(
                    ReaderViewModel.HistoryEventType.PLAY,
                    "Started playback",
                    readAloudAudioViewModel.currentPosition
                )
            } else if (!readAloudAudioViewModel.isPlaying && wasPlaying) {
                // Paused
                readerViewModel.addHistoryEvent(
                    ReaderViewModel.HistoryEventType.PAUSE,
                    "Paused playback",
                    readAloudAudioViewModel.currentPosition
                )
            }
        }
        wasPlaying = readAloudAudioViewModel.isPlaying
    }

    // Set up callback for manual seek/skip (for "Return to Position" button)
    LaunchedEffect(Unit) {
        readAloudAudioViewModel.onManualSeek = { previousPosition ->
            readerViewModel.saveCurrentPositionForReturn(previousPosition)
        }
    }

    // Auto-dismiss return button after 8 seconds
    LaunchedEffect(readerViewModel.showReturnButton) {
        if (readerViewModel.showReturnButton) {
            delay(8000)
            readerViewModel.dismissReturnButton()
        }
    }

    LaunchedEffect(readAloudAudioViewModel.isPlaying) {
        if (readAloudAudioViewModel.isPlaying) {
            while (true) {
                readerViewModel.forceScrollUpdate()
                kotlinx.coroutines.delay(500)
            }
        }
    }

    LaunchedEffect(readAloudAudioViewModel.currentPosition) {
        if (!readAloudAudioViewModel.isLoading && readAloudAudioViewModel.currentPosition > 0) {
            readerViewModel.currentAudioPos = readAloudAudioViewModel.currentPosition
        }
    }

    LaunchedEffect(readerViewModel.syncData, readerViewModel.chapterOffsets) {
        if (readerViewModel.syncData.isNotEmpty()) {
            val spineHrefs = readerViewModel.lazyBook?.spineHrefs ?: emptyList()
            readAloudAudioViewModel.loadBook(
                bookId = bookId,
                smilData = readerViewModel.syncData,
                chapterOffsets = readerViewModel.chapterOffsets,
                spineHrefs = spineHrefs,
                spineTitles = readerViewModel.lazyBook?.spineTitles ?: emptyMap(),
                autoPlay = false  // Don't auto-start playback on resume
            )
        }
    }

    LaunchedEffect(readAloudAudioViewModel.currentChapterIndex, readAloudAudioViewModel.isLoading) {
        if (!readAloudAudioViewModel.isLoading) {
            val audioCh = readAloudAudioViewModel.currentChapterIndex
            if (audioCh >= 0 && audioCh != readerViewModel.currentChapterIndex) {
                readerViewModel.changeChapter(audioCh, readAloudAudioViewModel.currentPosition)
            }
        }
    }

    // Sync audio position when user manually navigates pages (while not playing)
    // Track if initial audio position has been restored to avoid overwriting saved progress
    var audioPositionInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(readAloudAudioViewModel.currentPosition, readAloudAudioViewModel.duration, readAloudAudioViewModel.isLoading) {
        // Mark as initialized once audio has loaded (duration > 0 indicates chapters are ready)
        // Position could be 0 for fresh starts, so use duration as the indicator
        if (!readAloudAudioViewModel.isLoading && readAloudAudioViewModel.duration > 0) {
            audioPositionInitialized = true
        }
    }

    // Sync audio to the visible element when user swipes pages (while not playing)
    // Also save position for "Return" button when manually navigating
    var lastSavedElementId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(readerViewModel.visibleElementId) {
        val elementId = readerViewModel.visibleElementId
        // Only sync reader -> audio when NOT playing (to avoid fighting with audio -> reader sync)
        // Also wait until audio has initialized its position from saved progress
        if (!readAloudAudioViewModel.isPlaying && !readAloudAudioViewModel.isLoading && audioPositionInitialized && elementId != null) {
            // Only seek if audio element is different (to avoid unnecessary seeks)
            if (elementId != readAloudAudioViewModel.currentElementId) {
                // Save position for "Return" button BEFORE syncing (only once per navigation sequence)
                if (lastSavedElementId != readAloudAudioViewModel.currentElementId) {
                    readerViewModel.saveCurrentPositionForReturn(readAloudAudioViewModel.currentPosition)
                    lastSavedElementId = readAloudAudioViewModel.currentElementId
                }
                android.util.Log.d("ReadAloudPlayerScreen", "User navigated to element $elementId, syncing audio position")
                readAloudAudioViewModel.seekToElement(elementId)
            }
        }
    }

    LaunchedEffect(readerViewModel.jumpToElementRequest.value) {
        readerViewModel.jumpToElementRequest.value?.let { elementId ->
            readAloudAudioViewModel.seekToElement(elementId)
            readerViewModel.jumpToElementRequest.value = null
        }
    }

    if (readerViewModel.isLoading || readAloudAudioViewModel.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (readerViewModel.error != null) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Error Opening Book") },
            text = { Text(readerViewModel.error ?: "Unknown error") },
            confirmButton = {
                TextButton(
                    onClick = {
                        readerViewModel.redownloadBook(context)
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
    } else if (readAloudAudioViewModel.error != null) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Error Opening Audio") },
            text = { Text(readAloudAudioViewModel.error ?: "Unknown error") },
            confirmButton = {
                TextButton(
                    onClick = {
                        readerViewModel.redownloadBook(context)
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

    val audioSync = readAloudAudioViewModel.syncConfirmation
    val readerSync = readerViewModel.syncConfirmation

    if (audioSync != null) {
        AlertDialog(
            onDismissRequest = { readAloudAudioViewModel.dismissSync() },
            title = { Text("Progress Sync") },
            text = { 
                Text("Progress is out of sync with Storyteller.")
            },
            confirmButton = {
                Button(
                    onClick = { readAloudAudioViewModel.confirmSync() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use server (${"%.1f".format(audioSync.progressPercent)}%)")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { readAloudAudioViewModel.dismissSync() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use local (${"%.1f".format(audioSync.localProgressPercent)}%)")
                }
            }
        )
    } else if (readerSync != null) {
        AlertDialog(
            onDismissRequest = { readerViewModel.dismissSync() },
            title = { Text("Progress Sync") },
            text = { 
                Text("Progress is out of sync with Storyteller.")
            },
            confirmButton = {
                Button(
                    onClick = { readerViewModel.confirmSync() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use server (${"%.1f".format(readerSync.progressPercent)}%)")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { readerViewModel.dismissSync() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use local (${"%.1f".format(readerSync.localProgressPercent)}%)")
                }
            }
        )
    }

    if (userSettings != null && readerViewModel.totalChapters > 0) {
        val theme = getReaderTheme(userSettings.readerTheme)
        val accentColor = MaterialTheme.colorScheme.primary
        val accentHex = String.format("#%06X", (0xFFFFFF and accentColor.toArgb()))
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(theme.bgInt))
        ) {
            EpubWebView(
                html = readerViewModel.getCurrentChapterHtml() ?: "",
                userSettings = userSettings,
                viewModel = readerViewModel,
                accentHex = accentHex,
                highlightId = if (readerViewModel.activeSearchHighlight == null) readerViewModel.currentHighlightId else null,
                syncTrigger = readerViewModel.syncTrigger,
                activeSearch = readerViewModel.activeSearchHighlight,
                activeSearchMatchIndex = readerViewModel.activeSearchMatchIndex,
                pendingAnchor = readerViewModel.pendingAnchorId.value,
                onTap = {  },
                isTwoPageMode = isTwoPageMode,
                pageGapDp = readerViewModel.innerScreenSettings?.pageGap ?: 16
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(theme.bgInt).copy(alpha = 0.95f))
                    .statusBarsPadding()
                    .height(40.dp)
                    .padding(horizontal = 4.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painterResource(R.drawable.ic_keyboard_arrow_down), 
                        contentDescription = "Back",
                        tint = Color(theme.textInt)
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (readAloudAudioViewModel.sleepTimerRemaining > 0 || readAloudAudioViewModel.isWaitingForChapterEnd) {
                        Text(
                            text = if (readAloudAudioViewModel.isWaitingForChapterEnd) "Stopping at end of chapter" else FormatUtils.formatSleepTime(readAloudAudioViewModel.sleepTimerRemaining),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(theme.textInt),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    IconButton(onClick = {
                        if (readAloudAudioViewModel.isPlaying) readAloudAudioViewModel.togglePlayPause()
                        readerViewModel.clearSearch()
                        showSearchSheet = true
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_search),
                            contentDescription = "Search",
                            tint = Color(theme.textInt)
                        )
                    }
                    IconButton(onClick = { showHighlightsSheet = true }) {
                        Icon(
                            painterResource(R.drawable.ic_highlight),
                            contentDescription = "Highlights",
                            tint = Color(theme.textInt)
                        )
                    }
                    IconButton(onClick = { showBookmarksSheet = true }) {
                        Icon(
                            painterResource(R.drawable.ic_bookmark),
                            contentDescription = "Bookmarks",
                            tint = Color(theme.textInt)
                        )
                    }
                    IconButton(onClick = { showHistorySheet = true }) {
                        Icon(
                            painterResource(R.drawable.ic_history),
                            contentDescription = "History",
                            tint = Color(theme.textInt)
                        )
                    }
                    IconButton(onClick = { 
                        if (readAloudAudioViewModel.sleepTimerRemaining <= 0) {
                            readAloudAudioViewModel.applyDefaultSleepTimer()
                        }
                        showSleepTimerSheet = true 
                    }) {
                        Icon(
                            painterResource(if (readAloudAudioViewModel.sleepTimerRemaining > 0) R.drawable.ic_snooze else R.drawable.ic_bedtime),
                            contentDescription = "Sleep Timer",
                            tint = Color(theme.textInt)
                        )
                    }
                    IconButton(onClick = { readerViewModel.showControls = !readerViewModel.showControls }) {
                        Icon(
                            painterResource(R.drawable.ic_settings), 
                            contentDescription = "Preferences",
                            tint = Color(theme.textInt)
                        )
                    }
                }
            }

            // Floating "Return to Reading Position" button
            AnimatedVisibility(
                visible = readerViewModel.showReturnButton,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)  // Below header bar
            ) {
                val savedPos = readerViewModel.savedReadingPosition
                if (savedPos != null) {
                    ElevatedButton(
                        onClick = {
                            val position = readerViewModel.returnToSavedPosition()
                            if (position != null) {
                                if (position.chapterIndex != readerViewModel.currentChapterIndex) {
                                    readerViewModel.changeChapter(position.chapterIndex)
                                }
                                if (position.elementId != null) {
                                    readerViewModel.currentHighlightId = position.elementId
                                }
                                readerViewModel.lastScrollPercent = position.scrollPercent
                                readerViewModel.forceScrollUpdate()
                                readAloudAudioViewModel.seekTo(position.audioPositionMs)
                            }
                        },
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 6.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Return to ${savedPos.chapterTitle}")
                    }
                }
            }

            // Scrim to dismiss settings when tapping outside
            if (readerViewModel.showControls && !isPlayerExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { readerViewModel.showControls = false }
                )
            }

            AnimatedVisibility(
                visible = !isPlayerExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Column {
                    AnimatedVisibility(
                        visible = readerViewModel.showControls,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it }
                    ) {
                        ReaderControls(
                            viewModel = readerViewModel,
                            userSettings = userSettings,
                            currentChapter = readerViewModel.currentChapterIndex,
                            totalChapters = readerViewModel.totalChapters,
                            onFontSizeChange = readerViewModel::updateFontSize,
                            onThemeChange = readerViewModel::updateTheme,
                            onFontFamilyChange = readerViewModel::updateFontFamily,
                            onChapterChange = readerViewModel::changeChapter,
                            backgroundColor = Color(theme.bgInt).copy(alpha = 0.95f),
                            contentColor = Color(theme.textInt)
                        )
                    }
                    ReadAloudMinimalCard(
                        audiobookViewModel = readAloudAudioViewModel,
                        theme = theme,
                        onClick = { isPlayerExpanded = true }
                    )
                }
            }

            AnimatedVisibility(
                visible = isPlayerExpanded,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                ReadAloudFullPlayerOverlay(
                    viewModel = readAloudAudioViewModel,
                    onClose = { isPlayerExpanded = false },
                    onShowSpeed = { showSpeedSheet = true },
                    onShowChapters = { showChaptersSheet = true },
                    onShowSleep = { showSleepTimerSheet = true }
                )
            }
        }

        if (showSpeedSheet) {
            ModalBottomSheet(onDismissRequest = { showSpeedSheet = false }) {
                AudioSpeedContent(viewModel = readAloudAudioViewModel)
            }
        }

        if (showSleepTimerSheet) {
            ModalBottomSheet(onDismissRequest = { showSleepTimerSheet = false }) {
                SleepTimerContent(viewModel = readAloudAudioViewModel)
            }
        }

        if (showChaptersSheet) {
            ModalBottomSheet(onDismissRequest = { showChaptersSheet = false }) {
                ChaptersContent(
                    viewModel = readAloudAudioViewModel,
                    onChapterClick = { index ->
                        // Save current position for "Return" button
                        readerViewModel.saveCurrentPositionForReturn(readAloudAudioViewModel.currentPosition)
                        readAloudAudioViewModel.skipToChapter(index)
                        showChaptersSheet = false
                    }
                )
            }
        }
        
        if (showSearchSheet) {
            ModalBottomSheet(onDismissRequest = { showSearchSheet = false }) {
                SearchContent(
                    viewModel = readerViewModel,
                    onResultClick = { result, query ->
                        // Save current position for "Return" button
                        readerViewModel.saveCurrentPositionForReturn(readAloudAudioViewModel.currentPosition)
                        readerViewModel.navigateToSearchResult(result, query)
                        showSearchSheet = false
                    }
                )
            }
        }

        if (showHighlightsSheet) {
            ModalBottomSheet(onDismissRequest = { showHighlightsSheet = false }) {
                HighlightsSheet(
                    highlights = highlights,
                    onHighlightClick = { highlight ->
                        // Save current position for "Return" button
                        readerViewModel.saveCurrentPositionForReturn(readAloudAudioViewModel.currentPosition)
                        // Record current position in history before navigating
                        readerViewModel.addHistoryEvent(
                            ReaderViewModel.HistoryEventType.HIGHLIGHT_CLICK,
                            "Before viewing highlight",
                            readAloudAudioViewModel.currentPosition
                        )
                        readerViewModel.changeChapter(highlight.chapterIndex)
                        readerViewModel.currentHighlightId = highlight.elementId
                        readerViewModel.forceScrollUpdate()
                        showHighlightsSheet = false
                    },
                    onDeleteHighlight = { readerViewModel.deleteHighlight(it) },
                    onExportClick = { showExportDialog = true }
                )
            }
        }

        if (showHistorySheet) {
            ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
                HistorySheet(
                    historyEvents = readerViewModel.historyEvents,
                    currentAudioPosition = readAloudAudioViewModel.currentPosition,
                    onEventClick = { event ->
                        // Navigate to the history event position
                        readerViewModel.navigateToHistoryEvent(event)
                        // Also sync audio position
                        readAloudAudioViewModel.seekTo(event.audioPositionMs)
                        showHistorySheet = false
                    }
                )
            }
        }

        if (showBookmarksSheet) {
            ModalBottomSheet(onDismissRequest = { showBookmarksSheet = false }) {
                BookmarksSheet(
                    bookmarks = bookmarks,
                    onBookmarkClick = { bookmark ->
                        readerViewModel.navigateToBookmark(bookmark)
                        showBookmarksSheet = false
                    },
                    onDeleteBookmark = { readerViewModel.deleteBookmark(it) },
                    onAddBookmark = {
                        readerViewModel.createBookmark()
                        showBookmarksSheet = false
                    }
                )
            }
        }

        if (showExportDialog) {
            ExportHighlightsDialog(
                onExportMarkdown = {
                    coroutineScope.launch {
                        readerViewModel.exportHighlightsToMarkdown(context)
                        showExportDialog = false
                    }
                },
                onExportCsv = {
                    coroutineScope.launch {
                        readerViewModel.exportHighlightsToCsv(context)
                        showExportDialog = false
                    }
                },
                onDismiss = { showExportDialog = false }
            )
        }

        // Color picker dialog for highlighting
        if (showColorPicker && readerViewModel.pendingHighlight != null) {
            ColorPickerDialog(
                selectedColor = readerViewModel.selectedHighlightColor,
                onColorSelected = { color ->
                    readerViewModel.selectedHighlightColor = color
                    readerViewModel.pendingHighlight?.let { pending ->
                        readerViewModel.createHighlight(
                            chapterIndex = pending.chapterIndex,
                            elementId = pending.elementId,
                            text = pending.text,
                            color = color
                        )
                    }
                    showColorPicker = false
                    showLongPressMenu = false
                    readerViewModel.pendingHighlight = null
                    readerViewModel.longPressedElementId = null
                },
                onDismiss = {
                    showColorPicker = false
                    readerViewModel.pendingHighlight = null
                }
            )
        }

        // Highlight actions when clicking an existing highlight
        if (readerViewModel.clickedHighlight != null) {
            val highlight = readerViewModel.clickedHighlight!!
            var showEditDialog by remember { mutableStateOf(false) }

            if (showEditDialog) {
                com.pekempy.ReadAloudbooks.ui.components.HighlightDialog(
                    highlight = highlight,
                    selectedColor = highlight.color,
                    onDismiss = { showEditDialog = false },
                    onSave = { color, note ->
                        if (color != highlight.color) readerViewModel.updateHighlightColor(highlight.id, color)
                        if (note != highlight.note) readerViewModel.updateHighlightNote(highlight.id, note ?: "")
                        showEditDialog = false
                        readerViewModel.clickedHighlight = null
                    },
                    onDelete = {
                        readerViewModel.deleteHighlight(highlight)
                        showEditDialog = false
                        readerViewModel.clickedHighlight = null
                    }
                )
            } else {
                com.pekempy.ReadAloudbooks.ui.components.HighlightActionsSheet(
                    highlight = highlight,
                    onEdit = { showEditDialog = true },
                    onChangeColor = { showEditDialog = true },
                    onCopy = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Highlight", highlight.text)
                        clipboard.setPrimaryClip(clip)
                        readerViewModel.clickedHighlight = null
                    },
                    onDelete = {
                        readerViewModel.deleteHighlight(highlight)
                        readerViewModel.clickedHighlight = null
                    },
                    onDismiss = { readerViewModel.clickedHighlight = null }
                )
            }
        }

        // Long press context menu
        if (showLongPressMenu && readerViewModel.longPressedElementId != null) {
            LongPressContextMenu(
                onNavigateToPosition = {
                    readerViewModel.jumpToElementRequest.value = readerViewModel.longPressedElementId
                    readerViewModel.longPressedElementId = null
                    showLongPressMenu = false
                },
                onDismiss = {
                    showLongPressMenu = false
                    readerViewModel.longPressedElementId = null
                }
            )
        }
    }
}

@Composable
fun SearchContent(
    viewModel: ReaderViewModel,
    onResultClick: (ReaderViewModel.SearchResult, String) -> Unit
) {
    var query by remember { mutableStateOf(viewModel.activeSearchHighlight ?: "") }
    
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Search in Book", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        
        OutlinedTextField(
            value = query,
            onValueChange = { 
                query = it
                viewModel.search(it)
            },
            label = { Text("Search") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { 
                        query = ""
                        viewModel.clearSearch()
                    }) { Icon(painterResource(R.drawable.ic_clear), null) }
                }
            }
        )
        
        Spacer(Modifier.height(16.dp))
        
        if (viewModel.isSearching) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (viewModel.searchResults.isEmpty() && query.length >= 2) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No matches found", color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            LazyColumn {
                itemsIndexed(viewModel.searchResults) { index, result ->
                    ListItem(
                        headlineContent = { Text(result.title) },
                        supportingContent = { 
                            Text(
                                result.textSnippet, 
                                maxLines = 3, 
                                overflow = TextOverflow.Ellipsis
                            ) 
                        },
                        modifier = Modifier.clickable {
                            onResultClick(result, query)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun AudioSpeedContent(viewModel: ReadAloudAudioViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Playback Speed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("${"%.2f".format(viewModel.playbackSpeed)}x", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Slider(
            value = viewModel.playbackSpeed,
            onValueChange = { viewModel.setSpeed((it * 20).roundToInt() / 20f) },
            valueRange = 0.5f..2.0f,
            steps = 29
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun SleepTimerContent(viewModel: ReadAloudAudioViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Sleep Timer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        val selectedMinutes = if (viewModel.sleepTimerRemaining > 0) (viewModel.sleepTimerRemaining / 60000).toInt() else 0
        Text(if (selectedMinutes > 0) "$selectedMinutes minutes" else "Off", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Slider(
            value = selectedMinutes.toFloat(),
            onValueChange = { viewModel.setSleepTimer(it.roundToInt()) },
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
        TextButton(onClick = { viewModel.setSleepTimer(0) }) { Text("Turn Off") }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun ChaptersContent(
    viewModel: ReadAloudAudioViewModel,
    onChapterClick: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Chapters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
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
                            onChapterClick(index)
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (viewModel.currentPosition in chapter.startOffset..(chapter.startOffset + chapter.duration)) 
                                MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun ReadAloudFullPlayerOverlay(
    viewModel: ReadAloudAudioViewModel,
    onClose: () -> Unit,
    onShowSpeed: () -> Unit,
    onShowChapters: () -> Unit,
    onShowSleep: () -> Unit
) {
    val book = viewModel.currentBook
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.Start)) {
                Icon(painterResource(R.drawable.ic_keyboard_arrow_down), contentDescription = "Close")
            }
            
            Box(modifier = Modifier.weight(1f).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.aspectRatio(1f).fillMaxWidth(0.8f),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    AsyncImage(
                        model = book?.coverUrl ?: book?.audiobookCoverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            
            val currentChapterTitle by remember {
                derivedStateOf {
                    val pos = viewModel.currentPosition
                    viewModel.chapters.find { 
                        pos >= it.startOffset && 
                        pos < it.startOffset + it.duration 
                    }?.title
                }
            }
            
            Text(book?.title ?: "Unknown", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            currentChapterTitle?.let { title ->
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(book?.author ?: "Unknown", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            
            Spacer(Modifier.height(24.dp))
            
            Slider(
                value = if (viewModel.duration > 0) viewModel.currentPosition.toFloat() else 0f,
                onValueChange = { viewModel.seekTo(it.toLong()) },
                valueRange = 0f..(if (viewModel.duration > 0) viewModel.duration.toFloat() else 1f)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(FormatUtils.formatTime(viewModel.currentPosition), style = MaterialTheme.typography.labelMedium)
                Text(FormatUtils.formatTime(viewModel.duration), style = MaterialTheme.typography.labelMedium)
            }
            
            Spacer(Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.rewind10s() }) { Icon(painterResource(R.drawable.ic_replay_10), null, Modifier.size(32.dp)) }
                IconButton(onClick = { viewModel.togglePlayPause() }, modifier = Modifier.size(72.dp)) {
                    Icon(painterResource(if (viewModel.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow), null, Modifier.size(48.dp))
                }
                IconButton(onClick = { viewModel.forward30s() }) { Icon(painterResource(R.drawable.ic_forward_30), null, Modifier.size(32.dp)) }
            }
            
            Spacer(Modifier.height(32.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onShowSpeed) { Text("${"%.2f".format(viewModel.playbackSpeed)}x Speed") }
                IconButton(onClick = onShowChapters) { Icon(painterResource(R.drawable.ic_list), null) }
            }
        }
    }
}

@Composable
fun ReadAloudMinimalCard(
    audiobookViewModel: ReadAloudAudioViewModel,
    theme: ReaderThemeData,
    onClick: () -> Unit
) {
    val book = audiobookViewModel.currentBook
    val context = LocalContext.current
    val imageLoader = remember {
        val client = AppContainer.apiClientManager.okHttpClient 
            ?: return@remember ImageLoader(context)
        ImageLoader.Builder(context)
            .okHttpClient(client)
            .build()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        color = Color(theme.bgInt).copy(alpha = 0.95f),
        border = BorderStroke(1.dp, Color(theme.textInt).copy(alpha = 0.1f)),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = book?.audiobookCoverUrl ?: book?.coverUrl,
                imageLoader = imageLoader,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(Modifier.width(16.dp))
            
            val currentChapterTitle by remember {
                derivedStateOf {
                    val pos = audiobookViewModel.currentPosition
                    audiobookViewModel.chapters.find { 
                        pos >= it.startOffset && 
                        pos < it.startOffset + it.duration 
                    }?.title
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book?.title ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(theme.textInt),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                currentChapterTitle?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(Modifier.height(4.dp))
                
                val progress = if (audiobookViewModel.duration > 0) 
                    audiobookViewModel.currentPosition.toFloat() / audiobookViewModel.duration 
                else 0f
                
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(theme.textInt).copy(alpha = 0.1f)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = com.pekempy.ReadAloudbooks.util.FormatUtils.formatTime(audiobookViewModel.currentPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(theme.textInt).copy(alpha = 0.7f)
                    )
                    Text(
                        text = com.pekempy.ReadAloudbooks.util.FormatUtils.formatTime(audiobookViewModel.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(theme.textInt).copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            FilledIconButton(
                onClick = { audiobookViewModel.togglePlayPause() },
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    painterResource(if (audiobookViewModel.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                    contentDescription = "Play/Pause"
                )
            }
        }
    }
}


