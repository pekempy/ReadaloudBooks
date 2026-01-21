package com.pekempy.ReadAloudbooks.ui.reader

import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.painterResource
import com.pekempy.ReadAloudbooks.R
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.compose.ui.viewinterop.AndroidView
import com.pekempy.ReadAloudbooks.data.UserSettings
import com.pekempy.ReadAloudbooks.util.HighlightExporter
import com.pekempy.ReadAloudbooks.util.rememberFoldableState
import com.pekempy.ReadAloudbooks.util.ReaderScreenMode
import com.pekempy.ReadAloudbooks.data.Book
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    bookId: String,
    isReadAloud: Boolean,
    onBack: () -> Unit
) {
    val userSettings = viewModel.settings

    var showSearchSheet by remember { mutableStateOf(false) }
    var showHighlightsSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showLongPressMenu by remember { mutableStateOf(false) }

    val highlights by viewModel.getHighlightsForBook().collectAsState(initial = emptyList())
    val bookmarks by viewModel.bookmarks

    // Foldable device support
    val foldableState by rememberFoldableState()
    val isTwoPageMode = foldableState.screenMode == ReaderScreenMode.TWO_PAGE

    // Update ViewModel when fold state changes
    LaunchedEffect(foldableState.screenMode) {
        viewModel.updateScreenMode(foldableState.screenMode, foldableState.foldBoundsPx)
    }

    val view = LocalView.current
    val window = (view.context as? android.app.Activity)?.window

    LaunchedEffect(bookId) {
        viewModel.loadEpub(bookId, isReadAloud)
    }

    // Apply brightness setting
    LaunchedEffect(userSettings?.readerBrightness) {
        userSettings?.readerBrightness?.let { brightness ->
            window?.let { w ->
                val layoutParams = w.attributes
                layoutParams.screenBrightness = brightness
                w.attributes = layoutParams
            }
        }
    }

    // Apply fullscreen mode
    LaunchedEffect(userSettings?.readerFullscreenMode) {
        userSettings?.readerFullscreenMode?.let { fullscreen ->
            window?.let { w ->
                if (fullscreen) {
                    WindowCompat.setDecorFitsSystemWindows(w, false)
                    WindowInsetsControllerCompat(w, view).let { controller ->
                        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                } else {
                    WindowCompat.setDecorFitsSystemWindows(w, true)
                    WindowInsetsControllerCompat(w, view).show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    // Adjust status bar icons based on theme (light/dark)
    LaunchedEffect(userSettings?.readerTheme) {
        window?.let { w ->
            val controller = WindowInsetsControllerCompat(w, view)
            // Themes 0 (white) and 1 (sepia) are light backgrounds - use dark status bar icons
            // Themes 2 (dark) and 3 (AMOLED black) are dark backgrounds - use light status bar icons
            val isLightBackground = (userSettings?.readerTheme ?: 0) <= 1
            controller.isAppearanceLightStatusBars = isLightBackground
        }
    }

    // Restore brightness and fullscreen when leaving
    DisposableEffect(Unit) {
        onDispose {
            window?.let { w ->
                // Restore default brightness
                val layoutParams = w.attributes
                layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = layoutParams

                // Restore system bars
                WindowCompat.setDecorFitsSystemWindows(w, true)
                WindowInsetsControllerCompat(w, view).show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Collect highlight events from ViewModel using SharedFlow
    // This bypasses Compose state observation issues
    LaunchedEffect(Unit) {
        android.util.Log.e("ReaderScreen", "=== Starting highlightEvents collector ===")
        viewModel.highlightEvents.collect { event ->
            android.util.Log.e("ReaderScreen", "=== RECEIVED event: $event ===")
            when (event) {
                is ReaderViewModel.HighlightEvent.ShowLongPressMenu -> {
                    android.util.Log.e("ReaderScreen", "=== SHOWING long press menu for: ${event.elementId} ===")
                    showLongPressMenu = true
                }
                is ReaderViewModel.HighlightEvent.ShowColorPicker -> {
                    android.util.Log.e("ReaderScreen", "=== SHOWING color picker for: ${event.pendingHighlight.text.take(20)} ===")
                    showColorPicker = true
                }
            }
        }
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

    if (viewModel.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (viewModel.error != null) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Error Opening Book") },
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
    } else if (userSettings != null && viewModel.totalChapters > 0) {
        val theme = getReaderTheme(userSettings.readerTheme)
        
        val accentColor = MaterialTheme.colorScheme.primary
        val accentHex = String.format("#%06X", (0xFFFFFF and accentColor.toArgb()))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(theme.bgInt))
        ) {
            EpubWebView(
                html = viewModel.getCurrentChapterHtml() ?: "",
                userSettings = userSettings,
                viewModel = viewModel,
                accentHex = accentHex,
                highlightId = viewModel.currentHighlightId,
                syncTrigger = viewModel.syncTrigger,
                activeSearch = viewModel.activeSearchHighlight,
                activeSearchMatchIndex = viewModel.activeSearchMatchIndex,
                pendingAnchor = viewModel.pendingAnchorId.value,
                clearSelectionTrigger = viewModel.clearSelectionTrigger,
                onTap = { viewModel.showControls = !viewModel.showControls },
                isTwoPageMode = isTwoPageMode,
                pageGapDp = viewModel.innerScreenSettings?.pageGap ?: 16
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color(theme.bgInt).copy(alpha = 0.95f))
                    .statusBarsPadding()
                    .height(56.dp) 
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back", tint = Color(theme.textInt))
                }
                Text(
                    viewModel.epubTitle + " (v2.1)", 
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, 
                    maxLines = 1,
                    color = Color(theme.textInt)
                )
                IconButton(onClick = {
                    viewModel.clearSearch()
                    showSearchSheet = true
                }) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = "Search", tint = Color(theme.textInt))
                }
                
                Box(contentAlignment = Alignment.TopEnd) {
                    IconButton(onClick = { showHighlightsSheet = true }) {
                        Icon(painterResource(R.drawable.ic_highlight), contentDescription = "Highlights", tint = Color.Magenta)
                    }
                    if (highlights.isNotEmpty()) {
                        Badge(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(8.dp)
                        )
                    }
                }

                IconButton(onClick = { showBookmarksSheet = true }) {
                    Icon(painterResource(R.drawable.ic_bookmark), contentDescription = "Bookmarks", tint = Color(theme.textInt))
                }
                IconButton(onClick = { viewModel.showControls = !viewModel.showControls }) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", tint = Color(theme.textInt))
                }
            }

            // Scrim to dismiss settings when tapping outside
            if (viewModel.showControls) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { viewModel.showControls = false }
                )
            }

            AnimatedVisibility(
                visible = viewModel.showControls,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            ) {
                ReaderControls(
                    viewModel = viewModel,
                    userSettings = userSettings,
                    currentChapter = viewModel.currentChapterIndex,
                    totalChapters = viewModel.totalChapters,
                    onFontSizeChange = if (isTwoPageMode) viewModel::updateInnerFontSize else viewModel::updateFontSize,
                    onThemeChange = if (isTwoPageMode) viewModel::updateInnerTheme else viewModel::updateTheme,
                    onFontFamilyChange = if (isTwoPageMode) viewModel::updateInnerFontFamily else viewModel::updateFontFamily,
                    onChapterChange = viewModel::changeChapter,
                    backgroundColor = Color(theme.bgInt).copy(alpha = 0.95f),
                    contentColor = Color(theme.textInt),
                    isTwoPageMode = isTwoPageMode
                )
            }
        }
        
        if (showSearchSheet) {
            ModalBottomSheet(onDismissRequest = { showSearchSheet = false }) {
                com.pekempy.ReadAloudbooks.ui.player.SearchContent(
                    viewModel = viewModel,
                    onResultClick = { result, query ->
                        viewModel.navigateToSearchResult(result, query)
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
                        viewModel.changeChapter(highlight.chapterIndex)
                        viewModel.currentHighlightId = highlight.elementId
                        viewModel.forceScrollUpdate()
                        showHighlightsSheet = false
                    },
                    onDeleteHighlight = { viewModel.deleteHighlight(it) },
                    onExportClick = { showExportDialog = true }
                )
            }
        }

        if (showBookmarksSheet) {
            ModalBottomSheet(onDismissRequest = { showBookmarksSheet = false }) {
                BookmarksSheet(
                    bookmarks = bookmarks,
                    onBookmarkClick = { bookmark ->
                        viewModel.navigateToBookmark(bookmark)
                        showBookmarksSheet = false
                    },
                    onDeleteBookmark = { viewModel.deleteBookmark(it) },
                    onAddBookmark = {
                        viewModel.createBookmark()
                        showBookmarksSheet = false
                    }
                )
            }
        }

        if (showColorPicker) {
            ColorPickerDialog(
                selectedColor = viewModel.selectedHighlightColor,
                onColorSelected = { color ->
                    viewModel.selectedHighlightColor = color
                    viewModel.pendingHighlight?.let { pending ->
                        viewModel.createHighlight(
                            chapterIndex = pending.chapterIndex,
                            elementId = pending.elementId,
                            text = pending.text,
                            color = color
                        )
                        viewModel.pendingHighlight = null
                    }
                    showColorPicker = false
                },
                onDismiss = {
                    showColorPicker = false
                    viewModel.pendingHighlight = null
                }
            )
        }

        val context = LocalContext.current
        if (showExportDialog) {
            ExportHighlightsDialog(
                onExportMarkdown = {
                    viewModel.viewModelScope.launch {
                        viewModel.exportHighlightsToMarkdown(context)
                        showExportDialog = false
                    }
                },
                onExportCsv = {
                    viewModel.viewModelScope.launch {
                        viewModel.exportHighlightsToCsv(context)
                        showExportDialog = false
                    }
                },
                onDismiss = { showExportDialog = false }
            )
        }

        if (viewModel.clickedHighlight != null) {
            val highlight = viewModel.clickedHighlight!!
            var showEditDialog by remember { mutableStateOf(false) }

            if (showEditDialog) {
                com.pekempy.ReadAloudbooks.ui.components.HighlightDialog(
                    highlight = highlight,
                    selectedText = highlight.text,
                    selectedColor = highlight.color,
                    onDismiss = { showEditDialog = false },
                    onSave = { color, note ->
                        if (color != highlight.color) viewModel.updateHighlightColor(highlight.id, color)
                        if (note != highlight.note) viewModel.updateHighlightNote(highlight.id, note ?: "")
                        showEditDialog = false
                        viewModel.clickedHighlight = null
                    },
                    onDelete = {
                        viewModel.deleteHighlight(highlight)
                        showEditDialog = false
                        viewModel.clickedHighlight = null
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
                        viewModel.clickedHighlight = null
                    },
                    onDelete = {
                        viewModel.deleteHighlight(highlight)
                        viewModel.clickedHighlight = null
                    },
                    onDismiss = { viewModel.clickedHighlight = null }
                )
            }
        }

        if (showLongPressMenu && viewModel.longPressedElementId != null) {
            LongPressContextMenu(
                onNavigateToPosition = {
                    viewModel.jumpToElementRequest.value = viewModel.longPressedElementId
                    viewModel.longPressedElementId = null
                },
                onDismiss = {
                    showLongPressMenu = false
                    viewModel.longPressedElementId = null
                }
            )
        }
    }
}

@Composable
fun EpubWebView(
    html: String,
    userSettings: UserSettings,
    viewModel: ReaderViewModel,
    accentHex: String,
    highlightId: String?,
    syncTrigger: Int,
    activeSearch: String? = null,
    activeSearchMatchIndex: Int = 0,
    pendingAnchor: String? = null,
    clearSelectionTrigger: Int = 0,
    onTap: () -> Unit,
    isTwoPageMode: Boolean = false,
    pageGapDp: Int = 16
) {
    val theme = getReaderTheme(userSettings.readerTheme)
    val isReadAloud = viewModel.isReadAloudMode


    key(userSettings.readerTheme, userSettings.readerFontFamily, userSettings.readerFontSize, userSettings.readerLineSpacing, userSettings.readerMarginSize, userSettings.readerTextAlignment, isTwoPageMode) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(theme.bgInt)
                    
                    this.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }
                    
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url?.toString() ?: return null
                            if (url.startsWith("https://epub-internal/")) {
                                return viewModel.getResourceResponse(url)
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.startsWith("https://epub-internal/")) {
                                val path = url.removePrefix("https://epub-internal/")
                                viewModel.navigateToHref(path)
                                return true
                            }
                            return false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            
                            viewModel.activeSearchHighlight?.let { search ->
                                val index = viewModel.activeSearchMatchIndex
                                android.util.Log.d("EpubWebView", "onPageFinished: scheduling findAndHighlight('$search', 0, $index)")
                                view?.postDelayed({
                                    android.util.Log.d("EpubWebView", "onPageFinished: executing findAndHighlight")
                                    view.evaluateJavascript("findAndHighlight('$search', 0, $index)", null)
                                }, 300)
                                view?.setTag(com.pekempy.ReadAloudbooks.R.id.search_tag, search)
                            }
                            
                            viewModel.pendingAnchorId.value?.let { anchor ->
                                view?.evaluateJavascript("if (typeof highlightElement === 'function') highlightElement('$anchor', 0)", null)
                                view?.setTag(com.pekempy.ReadAloudbooks.R.id.anchor_tag, anchor)
                            }
                        }
                    }
                    
                    setOnTouchListener { _, event ->
                        if (event.action == android.view.MotionEvent.ACTION_UP) {
                        }
                        false 
                    }
                    
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onBodyClick(x: Float, width: Float, isTwoPage: Boolean) {
                            val ratio = x / width
                            // In two-page mode, use smaller edge zones (12.5%) to avoid accidental navigation
                            // In single-page mode, use standard zones (25%)
                            val leftZone = if (isTwoPage) 0.125f else 0.25f
                            val rightZone = if (isTwoPage) 0.875f else 0.75f
                            when {
                                ratio < leftZone -> {
                                    this@apply.post { this@apply.evaluateJavascript("pageLeft()", null) }
                                }
                                ratio > rightZone -> {
                                    this@apply.post { this@apply.evaluateJavascript("pageRight()", null) }
                                }
                                else -> {
                                    onTap()
                                }
                            }
                        }

                        @JavascriptInterface
                        fun onNextChapter() {
                            viewModel.viewModelScope.launch {
                                viewModel.changeChapter(viewModel.currentChapterIndex + 1)
                            }
                        }

                        @JavascriptInterface
                        fun onPrevChapter() {
                            viewModel.viewModelScope.launch {
                                viewModel.changeChapter(viewModel.currentChapterIndex - 1, scrollToEnd = true)
                            }
                        }

                        @JavascriptInterface
                        fun onScroll(percent: Float) {
                            viewModel.saveProgress(viewModel.currentChapterIndex, percent)
                        }

                        @JavascriptInterface
                        fun onScrollWithId(percent: Float, elementId: String?) {
                            var audioTime: Long? = null
                            if (!elementId.isNullOrEmpty()) {
                                val time = viewModel.getTimeAtElement(viewModel.currentChapterIndex, elementId)
                                if (time != null) {
                                    audioTime = (time * 1000).toLong()
                                }
                            }
                            viewModel.saveProgress(viewModel.currentChapterIndex, percent, audioTime, elementId)
                        }

                        @JavascriptInterface
                        fun onElementLongPress(id: String) {
                            android.util.Log.d("ReaderScreen", "onElementLongPress called: id=$id")
                            // Must run on Main thread for Compose recomposition
                            viewModel.viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                viewModel.longPressedElementId = id
                                // Long-press is only for navigation - don't set pendingHighlight
                                // Text selection auto-creates highlights separately
                                android.util.Log.d("ReaderScreen", "longPressedElementId set to: $id (navigation only)")
                                // Emit event to trigger UI update
                                viewModel.emitLongPressEvent(id)
                            }
                        }

                        @JavascriptInterface
                        fun onReaderReady() {
                            android.util.Log.d("EpubWebView", "Reader reported ready.")
                            viewModel.markReady()
                        }

                        @JavascriptInterface
                        fun onTextSelected(elementId: String, selectedText: String) {
                            android.util.Log.d("ReaderScreen", "onTextSelected called: elementId=$elementId, text=${selectedText.take(30)}...")
                            if (selectedText.isNotBlank()) {
                                android.util.Log.d("ReaderScreen", "Auto-creating highlight with current color")
                                // Must run on Main thread for Compose recomposition
                                viewModel.viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    // Directly create highlight with current selected color (no popup)
                                    viewModel.createHighlight(
                                        chapterIndex = viewModel.currentChapterIndex,
                                        elementId = elementId,
                                        text = selectedText.trim(),
                                        color = viewModel.selectedHighlightColor
                                    )
                                    android.util.Log.d("ReaderScreen", "Highlight created with color: ${viewModel.selectedHighlightColor}")
                                }
                            }
                        }

                        @JavascriptInterface
                        fun onHighlightClick(idStr: String) {
                            android.util.Log.d("ReaderScreen", "onHighlightClick called: id=$idStr")
                            try {
                                val id = idStr.toLong()
                                viewModel.viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    android.util.Log.d("ReaderScreen", "Looking for highlight $id in ${viewModel.highlightsForCurrentChapter.value.size} highlights")
                                    val highlight = viewModel.highlightsForCurrentChapter.value.find { it.id == id }
                                    if (highlight != null) {
                                        android.util.Log.d("ReaderScreen", "Found highlight, setting clickedHighlight")
                                        viewModel.clickedHighlight = highlight
                                    } else {
                                        android.util.Log.d("ReaderScreen", "Highlight $id NOT found in current chapter highlights")
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ReaderScreen", "Error parsing highlight ID", e)
                            }
                        }
                    }, "Android")
                }
            },
            update = { webView ->
                val currentHighlightId = highlightId
                val trigger = syncTrigger
                val chapterPath = viewModel.getCurrentChapterPath()
                val baseUrl = "https://epub-internal/$chapterPath"

                val contentSignature = "$baseUrl-${userSettings.readerTheme}-${userSettings.readerFontSize}-${userSettings.readerFontFamily}-${userSettings.readerLineSpacing}-${userSettings.readerMarginSize}-${userSettings.readerTextAlignment}-$isReadAloud-$isTwoPageMode-$pageGapDp-v3"
                val lastSignature = webView.tag as? String
                
                if (lastSignature != contentSignature) {
                    val styledHtml = wrapHtml(html, userSettings, theme, viewModel.lastScrollPercent, accentHex, highlightId, isReadAloud, isTwoPageMode, pageGapDp)
                    android.util.Log.d("EpubWebView", "Reloading content. Signature changed: $contentSignature")
                    webView.loadDataWithBaseURL(baseUrl, styledHtml, "text/html", "UTF-8", null)
                    webView.scrollTo(0, 0)
                    webView.tag = contentSignature
                    webView.setTag(com.pekempy.ReadAloudbooks.R.id.highlight_tag, highlightId)
                    webView.setTag(com.pekempy.ReadAloudbooks.R.id.trigger_tag, trigger)
                    webView.setTag(com.pekempy.ReadAloudbooks.R.id.search_tag, null)
                    webView.setTag(com.pekempy.ReadAloudbooks.R.id.anchor_tag, null)
                }

                if (currentHighlightId != null) {
                    val id = currentHighlightId
                    if (id.isNotEmpty()) {
                        val lastId = webView.getTag(com.pekempy.ReadAloudbooks.R.id.highlight_tag) as? String
                        val lastTrigger = webView.getTag(com.pekempy.ReadAloudbooks.R.id.trigger_tag) as? Int ?: -1
                        
                        if (lastId != id || lastTrigger != trigger) {
                            android.util.Log.d("EpubWebView", "Highlighting: $id (trigger $trigger)")
                            webView.evaluateJavascript("if (typeof highlightElement === 'function') highlightElement('$id', 0, true)", null)
                            webView.setTag(com.pekempy.ReadAloudbooks.R.id.highlight_tag, id)
                            webView.setTag(com.pekempy.ReadAloudbooks.R.id.trigger_tag, trigger)
                        }
                    }
                } else {
                    val lastTrigger = webView.getTag(com.pekempy.ReadAloudbooks.R.id.trigger_tag) as? Int ?: -1
                    if (lastTrigger != trigger) {
                        val percent = viewModel.lastScrollPercent
                        android.util.Log.d("EpubWebView", "Scroll jump to $percent (trigger $trigger)")
                        webView.evaluateJavascript("if (typeof scrollToPercent === 'function') scrollToPercent($percent)", null)
                        webView.setTag(com.pekempy.ReadAloudbooks.R.id.trigger_tag, trigger)
                    }
                }
                
                if (activeSearch != null) {
                    val searchSignature = "$activeSearch|$activeSearchMatchIndex"
                    val lastSignature = webView.getTag(com.pekempy.ReadAloudbooks.R.id.search_tag) as? String
                    
                    if (lastSignature != searchSignature) {
                        android.util.Log.d("EpubWebView", "Update: scheduling findAndHighlight('$activeSearch', 0, $activeSearchMatchIndex)")
                        webView.postDelayed({
                            android.util.Log.d("EpubWebView", "Update: executing findAndHighlight")
                            webView.evaluateJavascript("if (typeof findAndHighlight === 'function') findAndHighlight('$activeSearch', 0, $activeSearchMatchIndex)", null)
                        }, 300)
                        webView.setTag(com.pekempy.ReadAloudbooks.R.id.search_tag, searchSignature)
                    }
                }
                
                if (pendingAnchor != null) {
                    val lastAnchor = webView.getTag(com.pekempy.ReadAloudbooks.R.id.anchor_tag) as? String
                    if (lastAnchor != pendingAnchor) {
                         webView.evaluateJavascript("if (typeof highlightElement === 'function') highlightElement('$pendingAnchor', 0)", null)
                         webView.setTag(com.pekempy.ReadAloudbooks.R.id.anchor_tag, pendingAnchor)
                    }
                }

                // Apply user highlights for current chapter
                viewModel.highlightsForCurrentChapter.value.let { highlights ->
                    val highlightsJson = if (highlights.isNotEmpty()) {
                        highlights.map { h ->
                            // Use proper JSON escaping for all characters
                            val escapedText = buildString {
                                for (c in h.text) {
                                    when (c) {
                                        '\\' -> append("\\\\")
                                        '"' -> append("\\\"")
                                        '\n' -> append("\\n")
                                        '\r' -> append("\\r")
                                        '\t' -> append("\\t")
                                        '\b' -> append("\\b")
                                        '\u000C' -> append("\\f")
                                        else -> if (c.code < 32) {
                                            // Escape other control characters as unicode
                                            append("\\u${c.code.toString(16).padStart(4, '0')}")
                                        } else {
                                            append(c)
                                        }
                                    }
                                }
                            }
                            """{"id":${h.id},"elementId":"${h.elementId}","text":"${escapedText}","color":"${h.color}"}"""
                        }.joinToString(",", "[", "]")
                    } else {
                        "[]"
                    }

                    val highlightsSignature = "highlights_${highlights.size}_${highlights.hashCode()}"
                    val lastHighlightsSignature = webView.getTag(com.pekempy.ReadAloudbooks.R.id.user_highlights_tag) as? String

                    if (lastHighlightsSignature != highlightsSignature) {
                        android.util.Log.d("EpubWebView", "Applying ${highlights.size} highlights to WebView")
                        webView.postDelayed({
                            // Double-escape backslashes for JS string literal, then escape single quotes
                            val jsEscaped = highlightsJson.replace("\\", "\\\\").replace("'", "\\'")
                            webView.evaluateJavascript("if (typeof setHighlights === 'function') setHighlights('$jsEscaped')", null)
                        }, 300)
                        webView.setTag(com.pekempy.ReadAloudbooks.R.id.user_highlights_tag, highlightsSignature)
                    }
                }

                // Clear text selection when trigger changes
                val lastClearTrigger = webView.getTag(com.pekempy.ReadAloudbooks.R.id.clear_selection_tag) as? Int ?: -1
                if (clearSelectionTrigger > 0 && clearSelectionTrigger != lastClearTrigger) {
                    android.util.Log.d("EpubWebView", "Clearing text selection (trigger: $clearSelectionTrigger)")
                    webView.postDelayed({
                        webView.evaluateJavascript("if (typeof clearTextSelection === 'function') clearTextSelection()", null)
                    }, 400)  // Wait a bit longer than highlight application
                    webView.setTag(com.pekempy.ReadAloudbooks.R.id.clear_selection_tag, clearSelectionTrigger)
                }
            }
        )
    }
}

fun wrapHtml(html: String, userSettings: UserSettings, theme: ReaderThemeData, initialScrollPercent: Float, accentColor: String, initialHighlightId: String? = null, isReadAloud: Boolean = false, isTwoPageMode: Boolean = false, pageGapDp: Int = 16): String {
    val fontFamily = when(userSettings.readerFontFamily) {
        "serif" -> "serif"
        "sans-serif" -> "sans-serif"
        "monospace" -> "monospace"
        else -> "serif"
    }

    // Calculate margins based on margin size setting
    val horizontalPadding = when(userSettings.readerMarginSize) {
        0 -> "8px"  // Compact
        1 -> "16px" // Normal
        2 -> "32px" // Wide
        else -> "16px"
    }

    // Text alignment
    val textAlign = when(userSettings.readerTextAlignment) {
        "left" -> "left"
        "center" -> "center"
        "justify" -> "justify"
        else -> "justify"
    }

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
            <style>
                :root {
                    --bg-color: ${theme.bg};
                    --text-color: ${theme.text};
                    --font-size: ${userSettings.readerFontSize}px;
                    --font-family: $fontFamily;
                    --padding-left: $horizontalPadding;
                    --padding-right: $horizontalPadding;
                    --top-padding: 130px;
                    --bottom-padding: ${if (isReadAloud) "180px" else "60px"};
                    --accent-color: $accentColor;
                    --line-spacing: ${userSettings.readerLineSpacing};
                    --text-align: $textAlign;
                }

                html, body {
                    margin: 0;
                    padding: 0;
                    height: 100vh;
                    width: 100vw;
                    overflow: hidden;
                    background-color: var(--bg-color);
                    color: var(--text-color);
                    -webkit-user-select: text;
                    user-select: text;

                    /* Maximize text density */
                    line-height: var(--line-spacing) !important;
                    hyphens: auto;
                    -webkit-hyphens: auto;
                    text-align: var(--text-align);
                    text-indent: 1.5em;
                }

                /* PAGINATION STYLES */
                body {
                    overflow: hidden !important; 
                    width: 100vw;
                    height: 100vh;
                    margin: 0;
                    padding: 0;
                }

                #pagination-wrapper {
                    display: flex;
                    flex-direction: row;
                    height: 100vh;
                    width: max-content; 
                    position: fixed;
                    top: 0;
                    left: 0;
                    will-change: transform;
                    transform: translateX(0);
                }
                
                #pagination-wrapper.animate {
                    transition: transform 0.3s cubic-bezier(0.25, 1, 0.5, 1);
                }

                .page {
                    width: 100vw;
                    height: 100vh;
                    padding: var(--top-padding) var(--padding-right) var(--bottom-padding) var(--padding-left);
                    box-sizing: border-box;
                    overflow: hidden;
                    position: relative;
                    flex-shrink: 0;
                }
                
                #content-container {
                    display: none;
                }

                #content-container.animate {
                    transition: transform 0.6s cubic-bezier(0.22, 1, 0.36, 1);
                }

                /* Standard content styling with support for theme consistency */
                .page, .page *:not(.highlight):not(.search-highlight) {
                    word-wrap: break-word;
                    overflow-wrap: break-word;
                    -webkit-hyphens: auto;
                    hyphens: auto;
                    font-size: var(--font-size) !important;
                    font-family: var(--font-family) !important;
                    line-height: var(--line-spacing) !important;
                    color: var(--text-color) !important;
                    background-color: transparent !important;
                    max-width: 100% !important;
                    -webkit-user-select: text;
                    user-select: text;
                    -webkit-touch-callout: default;
                }

                p, .page p {
                    text-align: var(--text-align) !important;
                    line-height: var(--line-spacing) !important;
                }

                /* Nuclear reset for unwanted lines (ruled paper, global underlining) */
                /* Excludes images, highlights, and intentional headers/emphasis tags */
                html, body, .page, .page *:not(img):not(.highlight):not(.search-highlight):not(h1):not(h2):not(h3):not(h4):not(h5):not(h6):not(u):not(b):not(strong) {
                    background-image: none !important;
                    text-decoration: none !important;
                    border-bottom: none !important;
                    box-shadow: none !important;
                }

                h1, h2, h3 {
                    font-weight: bold !important;
                    break-inside: avoid-column;
                    -webkit-column-break-inside: avoid;
                    break-after: avoid;
                    text-align: left;
                    text-indent: 0;
                }

                p, [id] {
                    orphans: 2;
                    widows: 2;
                }

                img {
                    max-width: 100% !important;
                    max-height: calc(100vh - var(--top-padding) - var(--bottom-padding) - 20px) !important;
                    height: auto !important;
                    display: block;
                    margin: 10px auto;
                    break-inside: avoid;
                }

                a {
                    color: var(--text-color) !important;
                    text-decoration: none;
                }


                .highlight {
                    background-color: transparent !important;
                    border-bottom: 2px solid var(--accent-color) !important;
                }
                
                .search-highlight {
                    color: inherit !important;
                    border-bottom: 3px solid var(--accent-color);
                    border-radius: 0;
                    display: inline;
                    box-shadow: none;
                    position: relative !important;
                    z-index: 9999 !important;
                }

                .user-highlight {
                    border-radius: 2px !important;
                    display: inline !important;
                    padding: 2px 0 !important;
                }

                mark.user-highlight {
                    background-color: #FFEB3B !important;
                }

                [data-theme="2"] .highlight, [data-theme="3"] .highlight {
                    border-bottom: 2px solid var(--accent-color) !important;
                }

                ${if (isTwoPageMode) """
                /* TWO-PAGE MODE STYLES */
                .page {
                    width: calc(50vw - ${pageGapDp / 2}px) !important;
                }

                #pagination-wrapper {
                    gap: ${pageGapDp}px;
                }

                /* Visual divider between pages in a spread */
                .page-divider {
                    position: fixed;
                    top: var(--top-padding);
                    bottom: var(--bottom-padding);
                    left: 50%;
                    width: 1px;
                    background: var(--text-color);
                    opacity: 0.15;
                    pointer-events: none;
                    transform: translateX(-50%);
                }
                """ else ""}
            </style>
            <script>
                let currentPage = 0;
                let pageCount = 0;
                let currentHighlightId = null;
                let elementPageMap = {};
                const isTwoPageMode = $isTwoPageMode;
                const pageGapDp = $pageGapDp;
                let userHighlightsCache = [];  // Store highlights globally

                function getPageWidth() { return window.innerWidth; }

                function paginate() {
                    console.log("Starting pagination...");
                    const wrapper = document.createElement('div');
                    wrapper.id = 'pagination-wrapper';
                    
                    const contentContainer = document.getElementById('content-container');
                    let sourceNodes = [];
                    if (contentContainer) {
                         sourceNodes = Array.from(contentContainer.childNodes);
                         contentContainer.parentNode.removeChild(contentContainer);
                    } else {
                         sourceNodes = Array.from(document.body.childNodes).filter(child => 
                             child.tagName !== 'SCRIPT' && 
                             child.tagName !== 'STYLE' && 
                             child.id !== 'pagination-wrapper'
                         );
                    }

                    const fragment = document.createDocumentFragment();
                    sourceNodes.forEach(node => fragment.appendChild(node));

                    document.body.appendChild(wrapper);

                    let currentPageDiv = createPage();
                    wrapper.appendChild(currentPageDiv);
                    pageCount = 1;

                    const pageLimit = currentPageDiv.clientHeight || window.innerHeight;
                    console.log("Page limit: " + pageLimit);

                    function createPage() {
                        const p = document.createElement('div');
                        p.className = 'page';
                        return p;
                    }

                    function startNewPage() {
                        currentPageDiv = createPage();
                        wrapper.appendChild(currentPageDiv);
                        pageCount++;
                    }

                    function splitTextNode(textNode, container) {
                        container.appendChild(textNode);
                        const text = textNode.textContent;
                        let min = 0;
                        let max = text.length;
                        let safe = 0;
                        
                        while (min <= max) {
                            const mid = Math.floor((min + max) / 2);
                            const chunk = text.substring(0, mid);
                            textNode.textContent = chunk;
                            if (currentPageDiv.scrollHeight <= pageLimit) {
                                safe = mid;
                                min = mid + 1;
                            } else {
                                max = mid - 1;
                            }
                        }
                        
                        // Respect word boundaries
                        if (safe < text.length) {
                             const lastSpace = text.lastIndexOf(' ', safe);
                             if (lastSpace > 0) {
                                 safe = lastSpace + 1; // Include the space on the first page
                             }
                        }
                        
                        const firstPart = text.substring(0, safe);
                        const secondPart = text.substring(safe);
                        textNode.textContent = firstPart;
                        if (!secondPart) return null;
                        return document.createTextNode(secondPart);
                    }
                    
                    function splitElementAcrossPages(element, parentContainer) {
                        const clone = element.cloneNode(false);
                        parentContainer.appendChild(clone);
                        const kids = Array.from(element.childNodes);
                        let subContainer = clone;
                        
                        for (let k = 0; k < kids.length; k++) {
                            const kid = kids[k];
                            // Try append
                            subContainer.appendChild(kid);
                            
                            if (currentPageDiv.scrollHeight > pageLimit) {
                                subContainer.removeChild(kid);
                                
                                if (kid.nodeType === Node.TEXT_NODE) {
                                    const rem = splitTextNode(kid, subContainer);
                                    if (rem) {
                                        startNewPage();
                                        
                                        let newParent;
                                        if (parentContainer.classList && parentContainer.classList.contains('page')) {
                                            newParent = currentPageDiv;
                                        } else {
                                            const parentClone = parentContainer.cloneNode(false);
                                            if(parentClone.id) {
                                                parentClone.setAttribute('data-continuation-of', parentClone.id);
                                                parentClone.removeAttribute('id');
                                            }
                                            currentPageDiv.appendChild(parentClone);
                                            newParent = parentClone;
                                        }
                                        
                                        const elClone = element.cloneNode(false);
                                        if(elClone.id) {
                                            elClone.setAttribute('data-continuation-of', elClone.id);
                                            elClone.removeAttribute('id');
                                        }
                                        newParent.appendChild(elClone);
                                        
                                        subContainer = elClone;
                                        parentContainer = newParent;
                                        
                                        subContainer.appendChild(rem);
                                    }
                                } else if (kid.tagName === 'IMG') {
                                    startNewPage();
                                    
                                    let newParent;
                                    if (parentContainer.classList && parentContainer.classList.contains('page')) {
                                        newParent = currentPageDiv;
                                    } else {
                                        const parentClone = parentContainer.cloneNode(false);
                                        if(parentClone.id) {
                                            parentClone.setAttribute('data-continuation-of', parentClone.id);
                                            parentClone.removeAttribute('id');
                                        }
                                        currentPageDiv.appendChild(parentClone);
                                        newParent = parentClone;
                                    }
                                    
                                    const elClone = element.cloneNode(false);
                                    if(elClone.id) {
                                        elClone.setAttribute('data-continuation-of', elClone.id);
                                        elClone.removeAttribute('id');
                                    }
                                    newParent.appendChild(elClone);
                                    
                                    subContainer = elClone;
                                    parentContainer = newParent;
                                    
                                    subContainer.appendChild(kid);
                                } else {
                                    subContainer = splitElementAcrossPages(kid, subContainer);
                                }
                            }
                        }
                        return subContainer;
                    }

                    while(fragment.childNodes.length > 0) {
                         const node = fragment.childNodes[0];
                         fragment.removeChild(node);
                         currentPageDiv.appendChild(node);
                         
                         if (currentPageDiv.scrollHeight > pageLimit) {
                             currentPageDiv.removeChild(node);
                             if (node.nodeType === Node.TEXT_NODE) {
                                 const rem = splitTextNode(node, currentPageDiv);
                                 if (rem) {
                                     startNewPage();
                                     currentPageDiv.appendChild(rem);
                                 }
                             } else if (node.nodeType === Node.ELEMENT_NODE) {
                                 if (node.tagName === 'IMG') {
                                     startNewPage();
                                     currentPageDiv.appendChild(node);
                                 } else {
                                     splitElementAcrossPages(node, currentPageDiv);
                                 }
                             }
                         }
                    }
                    console.log("Pagination complete. Pages: " + pageCount);

                    const allElements = wrapper.querySelectorAll('[id]');
                    allElements.forEach(el => {
                        const page = el.closest('.page');
                        if (page) {
                            const index = Array.from(wrapper.children).indexOf(page);
                            elementPageMap[el.id] = index;
                        }
                    });

                    // Re-apply user highlights after pagination
                    if (userHighlightsCache.length > 0) {
                        console.log("Re-applying " + userHighlightsCache.length + " highlights after pagination");
                        applyHighlights(userHighlightsCache);
                    }
                }
                
                function gotoPage(index, animate = true) {
                    if (index < 0) index = 0;
                    if (index >= pageCount) index = pageCount - 1;
                    currentPage = index;
                    const wrapper = document.getElementById('pagination-wrapper');
                    if (wrapper) {
                        wrapper.style.transition = animate ? 'transform 0.3s cubic-bezier(0.25, 1, 0.5, 1)' : 'none';

                        if (isTwoPageMode) {
                            // In two-page mode, align to spread boundaries
                            // Each spread shows 2 pages side by side
                            const spreadIndex = Math.floor(index / 2);
                            const pageWidth = 50; // 50vw per page
                            const gapVw = (pageGapDp / window.innerWidth) * 100; // Convert gap to vw
                            const offset = spreadIndex * (100 + gapVw);
                            wrapper.style.transform = 'translateX(-' + offset + 'vw)';
                        } else {
                            wrapper.style.transform = 'translateX(-' + (index * 100) + 'vw)';
                        }

                        if (window.Android) {
                             const percent = pageCount > 1 ? index / (pageCount - 1) : 0;
                             let bestId = null;
                             const page = wrapper.children[index];
                             if (page) {
                                 const firstId = page.querySelector('[id]');
                                 if (firstId) bestId = firstId.id;
                             }
                             window.Android.onScrollWithId(percent, bestId);
                        }
                    }
                }
                
                function scrollToPercent(percent) {
                    const target = Math.round(percent * (pageCount - 1));
                    gotoPage(target, false);
                }
                
                function pageLeft() {
                    if (isTwoPageMode) {
                        // In two-page mode, navigate by spreads (2 pages)
                        const currentSpread = Math.floor(currentPage / 2);
                        if (currentSpread <= 0) {
                            if (window.Android) window.Android.onPrevChapter();
                            return;
                        }
                        // Go to first page of previous spread
                        gotoPage((currentSpread - 1) * 2);
                    } else {
                        if (currentPage <= 0) {
                            if (window.Android) window.Android.onPrevChapter();
                            return;
                        }
                        gotoPage(currentPage - 1);
                    }
                }

                function pageRight() {
                    if (isTwoPageMode) {
                        // In two-page mode, navigate by spreads (2 pages)
                        const currentSpread = Math.floor(currentPage / 2);
                        const maxSpread = Math.floor((pageCount - 1) / 2);
                        if (currentSpread >= maxSpread) {
                            if (window.Android) window.Android.onNextChapter();
                            return;
                        }
                        // Go to first page of next spread
                        gotoPage((currentSpread + 1) * 2);
                    } else {
                        if (currentPage >= pageCount - 1) {
                             if (window.Android) window.Android.onNextChapter();
                             return;
                        }
                        gotoPage(currentPage + 1);
                    }
                }
                
                function highlightElement(id, retry = 0, animated = true) {
                    if (!id) return;
                    
                    // Find all parts (original ID + continuations)
                    const parts = Array.from(document.querySelectorAll(`[id="${'$'}{id}"], [data-continuation-of="${'$'}{id}"]`));
                    
                    if (parts.length === 0) {
                        if (retry < 5) setTimeout(() => highlightElement(id, retry+1, animated), 200);
                        return;
                    }

                    // Highlight visual style
                    if (currentHighlightId !== id) {
                         document.querySelectorAll('.highlight').forEach(o => o.classList.remove('highlight'));
                         parts.forEach(el => el.classList.add('highlight'));
                         currentHighlightId = id;
                    } else {
                         // Ensure new parts are highlighted if something changed
                         parts.forEach(el => el.classList.add('highlight'));
                    }

                    const wrapper = document.getElementById('pagination-wrapper');
                    
                    // HEURISTIC: Short Orphan Check
                    // If the highlight is short and at the end of the page, checking next content.
                    let totalLen = 0;
                    parts.forEach(p => totalLen += p.textContent.length);
                    
                    if (totalLen < 40) {
                         const lastPart = parts[parts.length - 1];
                         const page = lastPart.closest('.page');
                         
                         if (page && wrapper) {
                             const pIdx = Array.from(wrapper.children).indexOf(page);
                             
                             // Traverse forward to find next content node
                             let scan = lastPart;
                             let foundNext = null;
                             while(scan && scan !== wrapper) {
                                 if (scan.nextSibling) {
                                     foundNext = scan.nextSibling;
                                     break;
                                 }
                                 scan = scan.parentNode;
                             }
                             
                             if (foundNext) {
                                 const nextPage = foundNext.closest('.page');
                                 if (nextPage) {
                                      const nextIdx = Array.from(wrapper.children).indexOf(nextPage);
                                      if (nextIdx > pIdx) {
                                          console.log("Short highlight detected at page boundary. Eagerly advancing to Page " + nextIdx);
                                          if (currentPage !== nextIdx) gotoPage(nextIdx, animated);
                                          return;
                                      }
                                 }
                             }
                         }
                    }

                    // STANDARD EAGER STRATEGY: Scroll to the LAST page containing any part of the highlight.
                    const lastPart = parts[parts.length - 1];
                    const page = lastPart.closest('.page');
                    
                    if (page && wrapper) {
                        const pageIndex = Array.from(wrapper.children).indexOf(page);
                        
                        if (pageIndex >= 0 && currentPage !== pageIndex) {
                             gotoPage(pageIndex, animated);
                        }
                    }
                }

                window.onload = function() {
                    paginate();

                    // Add page divider for two-page mode
                    if (isTwoPageMode) {
                        const divider = document.createElement('div');
                        divider.className = 'page-divider';
                        document.body.appendChild(divider);
                    }

                    const highlightId = ${if (initialHighlightId != null) "'$initialHighlightId'" else "null"};
                    const initialPercent = $initialScrollPercent;
                    if (highlightId) {
                        highlightElement(highlightId);
                    } else if (initialPercent > 0) {
                        scrollToPercent(initialPercent);
                    }
                    if (window.Android) window.Android.onReaderReady();
                };

                function findAndHighlight(text, retryCount = 0, matchIndex = 0) {
                     if (!text) return;
                     if (retryCount === 0) {
                          document.querySelectorAll('.search-highlight').forEach(el => {
                              const parent = el.parentNode;
                              while(el.firstChild) parent.insertBefore(el.firstChild, el);
                              parent.removeChild(el);
                          });
                     }
                     
                     const walker = document.createTreeWalker(document.getElementById('pagination-wrapper') || document.body, NodeFilter.SHOW_TEXT, null, false);
                     let node;
                     let currentMatch = 0;
                     let foundRange = null;
                     text = text.toLowerCase();
                     
                     while(node = walker.nextNode()) {
                         const content = node.textContent.toLowerCase();
                         let searchIndex = 0;
                         while(true) {
                             const foundIndex = content.indexOf(text, searchIndex);
                             if (foundIndex === -1) break;
                             if (currentMatch === matchIndex) {
                                 foundRange = document.createRange();
                                 foundRange.setStart(node, foundIndex);
                                 foundRange.setEnd(node, foundIndex + text.length);
                                 break;
                             }
                             currentMatch++;
                             searchIndex = foundIndex + 1;
                         }
                         if (foundRange) break;
                     }
                     
                     if (foundRange) {
                         try {
                             const span = document.createElement('span');
                             span.className = 'search-highlight';
                             foundRange.surroundContents(span);
                             
                             const page = span.closest('.page');
                             if (page) {
                                 const wrapper = document.getElementById('pagination-wrapper');
                                 const index = Array.from(wrapper.children).indexOf(page);
                                 if (index !== -1 && index !== currentPage) {
                                     gotoPage(index, false);
                                 }
                             }
                         } catch (e) {
                             console.error("Highlight error", e);
                         }
                     } else if (retryCount < 5) {
                         setTimeout(() => findAndHighlight(text, retryCount + 1, matchIndex), 100);
                     }
                }

                let touchStartX = 0;
                let touchStartTime = 0;
                
                document.addEventListener('touchstart', function(e) {
                    touchStartX = e.changedTouches[0].screenX;
                    touchStartTime = Date.now();
                }, false);
                
                document.addEventListener('touchend', function(e) {
                    const deltaX = e.changedTouches[0].screenX - touchStartX;
                    const deltaTime = Date.now() - touchStartTime;
                    if (Math.abs(deltaX) > 40 && deltaTime < 300) {
                        if (deltaX > 0) pageLeft();
                        else pageRight();
                    } else if (Math.abs(deltaX) < 10 && deltaTime < 300) {
                        const tapX = e.changedTouches[0].clientX;
                        const width = window.innerWidth;
                        if (window.Android) window.Android.onBodyClick(tapX, width, isTwoPageMode);
                    }
                }, false);

                // Text selection for highlights
                let selectionTimeout = null;
                let isLongPressHandled = false;

                document.addEventListener('selectionchange', function() {
                    const selection = window.getSelection();
                    console.log("Selection changed, text length:", selection ? selection.toString().trim().length : 0);

                    clearTimeout(selectionTimeout);
                    selectionTimeout = setTimeout(function() {
                        console.log("Selection timeout triggered, isLongPressHandled:", isLongPressHandled);

                        // Skip if long-press already handled this
                        if (isLongPressHandled) {
                            console.log("Skipping - long press already handled");
                            isLongPressHandled = false;
                            return;
                        }

                        const selection = window.getSelection();
                        if (selection && selection.toString().trim().length > 0) {
                            const selectedText = selection.toString().trim();
                            let element = selection.anchorNode;

                            // Find parent element with ID
                            while (element && element.nodeType !== Node.ELEMENT_NODE) {
                                element = element.parentNode;
                            }
                            while (element && !element.id && !element.getAttribute('data-continuation-of')) {
                                element = element.parentElement;
                            }

                            const elementId = element ? (element.id || element.getAttribute('data-continuation-of')) : null;
                            console.log("Found element ID:", elementId || "none");

                            if (elementId && window.Android) {
                                console.log("Calling Android.onTextSelected with text: " + selectedText.substring(0, 30) + "...");
                                window.Android.onTextSelected(elementId, selectedText);
                            } else {
                                console.log("NOT calling Android - element:", !!element, "element.id:", element ? element.id : "N/A", "Android:", !!window.Android);
                            }

                            // DO NOT clear selection - let user see what they selected
                            // The selection will be cleared manually after highlight creation
                        } else {
                            console.log("No selection or empty selection");
                        }
                    }, 500);  // Increased delay to ensure long-press is detected first
                });

                // Disable default context menu completely
                window.oncontextmenu = function(event) {
                    event.preventDefault();
                    return false;
                };

                // Custom long-press with 1 second delay and movement detection
                let longPressTimer = null;
                let lpStartX = 0;
                let lpStartY = 0;
                let longPressTarget = null;
                const LONG_PRESS_DELAY = 600; // 600ms
                const MOVE_THRESHOLD = 10; // pixels - if finger moves more than this, it's a drag/selection

                document.addEventListener('touchstart', function(e) {
                    if (e.touches.length !== 1) return;

                    lpStartX = e.touches[0].clientX;
                    lpStartY = e.touches[0].clientY;
                    longPressTarget = e.target;

                    longPressTimer = setTimeout(function() {
                        console.log("Long-press timer fired (1 second, no movement)");
                        isLongPressHandled = true;

                        // Clear any text selection that might have started
                        window.getSelection().removeAllRanges();

                        let target = longPressTarget;
                        while (target && !target.id && !target.getAttribute('data-continuation-of')) {
                            target = target.parentElement;
                        }
                        const targetId = target ? (target.id || target.getAttribute('data-continuation-of')) : null;

                        if (targetId && window.Android) {
                            console.log("Long-press calling Android with ID:", targetId);
                            window.Android.onElementLongPress(targetId);
                        }
                    }, LONG_PRESS_DELAY);
                }, { passive: true });

                document.addEventListener('touchmove', function(e) {
                    if (longPressTimer && e.touches.length === 1) {
                        const dx = Math.abs(e.touches[0].clientX - lpStartX);
                        const dy = Math.abs(e.touches[0].clientY - lpStartY);
                        if (dx > MOVE_THRESHOLD || dy > MOVE_THRESHOLD) {
                            // Finger moved - this is a selection drag, not a long-press
                            console.log("Touch moved, cancelling long-press timer (selection in progress)");
                            clearTimeout(longPressTimer);
                            longPressTimer = null;
                        }
                    }
                }, { passive: true });

                document.addEventListener('touchend', function() {
                    if (longPressTimer) {
                        console.log("Touch ended before 1 second, cancelling long-press");
                        clearTimeout(longPressTimer);
                        longPressTimer = null;
                    }
                }, { passive: true });

                document.addEventListener('touchcancel', function() {
                    clearTimeout(longPressTimer);
                    longPressTimer = null;
                }, { passive: true });

                // Apply highlights to the current page
                function applyHighlights(highlights) {
                    console.log("Applying " + highlights.length + " highlights to DOM");

                    // Remove existing highlight marks
                    document.querySelectorAll('.user-highlight').forEach(mark => {
                        const parent = mark.parentNode;
                        parent.replaceChild(document.createTextNode(mark.textContent), mark);
                        parent.normalize();
                    });

                    // Normalize whitespace for matching
                    function normalizeWs(str) {
                        return str.replace(/\s+/g, ' ').trim();
                    }

                    // Helper function to highlight text that may span multiple text nodes
                    function highlightTextInElement(element, searchText, color, highlightId) {
                        // Collect all text nodes with their positions
                        const textNodes = [];
                        const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT, null, false);
                        let combinedText = '';
                        let node;

                        while (node = walker.nextNode()) {
                            textNodes.push({
                                node: node,
                                start: combinedText.length,
                                end: combinedText.length + node.textContent.length
                            });
                            combinedText += node.textContent;
                        }

                        if (textNodes.length === 0) return false;

                        // Find the search text in combined text
                        const normalizedCombined = normalizeWs(combinedText);
                        const normalizedSearch = normalizeWs(searchText);

                        // Try exact match first
                        let matchStart = combinedText.indexOf(searchText);
                        let matchEnd = matchStart !== -1 ? matchStart + searchText.length : -1;

                        // If not found, try normalized matching
                        if (matchStart === -1) {
                            const normMatchStart = normalizedCombined.indexOf(normalizedSearch);
                            if (normMatchStart !== -1) {
                                // Map normalized position back to original
                                // Count characters up to the normalized position
                                let origPos = 0;
                                let normPos = 0;
                                while (normPos < normMatchStart && origPos < combinedText.length) {
                                    if (/\s/.test(combinedText[origPos])) {
                                        // Skip extra whitespace in original
                                        while (origPos < combinedText.length - 1 && /\s/.test(combinedText[origPos + 1])) {
                                            origPos++;
                                        }
                                    }
                                    origPos++;
                                    normPos++;
                                }
                                matchStart = origPos;

                                // Find end position similarly
                                let searchLen = normalizedSearch.length;
                                while (searchLen > 0 && origPos < combinedText.length) {
                                    if (/\s/.test(combinedText[origPos])) {
                                        while (origPos < combinedText.length - 1 && /\s/.test(combinedText[origPos + 1])) {
                                            origPos++;
                                        }
                                    }
                                    origPos++;
                                    searchLen--;
                                }
                                matchEnd = origPos;
                            }
                        }

                        if (matchStart === -1) return false;

                        // Find which text nodes contain our match
                        const nodesToHighlight = [];
                        for (const tn of textNodes) {
                            if (tn.end > matchStart && tn.start < matchEnd) {
                                const nodeStart = Math.max(0, matchStart - tn.start);
                                const nodeEnd = Math.min(tn.node.textContent.length, matchEnd - tn.start);
                                nodesToHighlight.push({
                                    node: tn.node,
                                    start: nodeStart,
                                    end: nodeEnd
                                });
                            }
                        }

                        if (nodesToHighlight.length === 0) return false;

                        // Apply highlights to each text node portion (in reverse to preserve positions)
                        for (let i = nodesToHighlight.length - 1; i >= 0; i--) {
                            const info = nodesToHighlight[i];
                            try {
                                const range = document.createRange();
                                range.setStart(info.node, info.start);
                                range.setEnd(info.node, info.end);

                                const mark = document.createElement('mark');
                                mark.className = 'user-highlight';
                                mark.setAttribute('style', 'background-color: ' + color + ' !important; color: inherit !important; padding: 2px 0; border-radius: 2px;');
                                mark.dataset.highlightId = highlightId;
                                mark.onclick = function(e) {
                                    e.stopPropagation();
                                    if (window.Android) {
                                        window.Android.onHighlightClick(highlightId.toString());
                                    }
                                };

                                range.surroundContents(mark);
                            } catch (e) {
                                console.error("Failed to highlight node portion:", e);
                            }
                        }

                        return nodesToHighlight.length > 0;
                    }

                    // Helper function to highlight text spanning multiple elements
                    function highlightTextAcrossElements(elements, searchText, color, highlightId) {
                        // Collect all text nodes from all elements with their positions
                        const allTextNodes = [];
                        let combinedText = '';

                        elements.forEach(element => {
                            const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT, null, false);
                            let node;
                            while (node = walker.nextNode()) {
                                allTextNodes.push({
                                    node: node,
                                    start: combinedText.length,
                                    end: combinedText.length + node.textContent.length
                                });
                                combinedText += node.textContent;
                            }
                        });

                        if (allTextNodes.length === 0) return false;

                        // Normalize whitespace for matching
                        const normalizedCombined = normalizeWs(combinedText);
                        const normalizedSearch = normalizeWs(searchText);

                        // Try exact match first
                        let matchStart = combinedText.indexOf(searchText);
                        let matchEnd = matchStart !== -1 ? matchStart + searchText.length : -1;

                        // If not found, try normalized matching
                        if (matchStart === -1) {
                            const normMatchStart = normalizedCombined.indexOf(normalizedSearch);
                            if (normMatchStart !== -1) {
                                // Map normalized position back to original
                                let origPos = 0;
                                let normPos = 0;
                                while (normPos < normMatchStart && origPos < combinedText.length) {
                                    if (/\s/.test(combinedText[origPos])) {
                                        while (origPos < combinedText.length - 1 && /\s/.test(combinedText[origPos + 1])) {
                                            origPos++;
                                        }
                                    }
                                    origPos++;
                                    normPos++;
                                }
                                matchStart = origPos;

                                let searchLen = normalizedSearch.length;
                                while (searchLen > 0 && origPos < combinedText.length) {
                                    if (/\s/.test(combinedText[origPos])) {
                                        while (origPos < combinedText.length - 1 && /\s/.test(combinedText[origPos + 1])) {
                                            origPos++;
                                        }
                                    }
                                    origPos++;
                                    searchLen--;
                                }
                                matchEnd = origPos;
                            }
                        }

                        if (matchStart === -1) return false;

                        // Find which text nodes contain our match
                        const nodesToHighlight = [];
                        for (const tn of allTextNodes) {
                            if (tn.end > matchStart && tn.start < matchEnd) {
                                const nodeStart = Math.max(0, matchStart - tn.start);
                                const nodeEnd = Math.min(tn.node.textContent.length, matchEnd - tn.start);
                                nodesToHighlight.push({
                                    node: tn.node,
                                    start: nodeStart,
                                    end: nodeEnd
                                });
                            }
                        }

                        if (nodesToHighlight.length === 0) return false;

                        // Apply highlights to each text node portion (in reverse to preserve positions)
                        for (let i = nodesToHighlight.length - 1; i >= 0; i--) {
                            const info = nodesToHighlight[i];
                            try {
                                const range = document.createRange();
                                range.setStart(info.node, info.start);
                                range.setEnd(info.node, info.end);

                                const mark = document.createElement('mark');
                                mark.className = 'user-highlight';
                                mark.setAttribute('style', 'background-color: ' + color + ' !important; color: inherit !important; padding: 2px 0; border-radius: 2px;');
                                mark.dataset.highlightId = highlightId;
                                mark.onclick = function(e) {
                                    e.stopPropagation();
                                    if (window.Android) {
                                        window.Android.onHighlightClick(highlightId.toString());
                                    }
                                };

                                range.surroundContents(mark);
                            } catch (e) {
                                console.error("Failed to highlight node portion:", e);
                            }
                        }

                        return nodesToHighlight.length > 0;
                    }

                    // Apply new highlights
                    highlights.forEach(highlight => {
                        // Find the element by ID, including paginated continuations
                        const elements = document.querySelectorAll(
                            `[id="${'$'}{highlight.elementId}"], [data-continuation-of="${'$'}{highlight.elementId}"]`
                        );

                        if (elements.length === 0) {
                            console.warn("Element not found for highlight:", highlight.elementId);
                            return;
                        }

                        // Try to highlight in each occurrence (handles pagination splits)
                        let highlighted = false;
                        elements.forEach(element => {
                            if (!highlighted) {
                                highlighted = highlightTextInElement(element, highlight.text, highlight.color, highlight.id);
                                if (highlighted) {
                                    console.log("Applied highlight ID " + highlight.id + " in element " + highlight.elementId);
                                }
                            }
                        });

                        // If not found in individual elements, try across all elements combined
                        // This handles text that spans pagination splits in two-page mode
                        if (!highlighted && elements.length > 1) {
                            console.log("Trying cross-element highlight for ID " + highlight.id);
                            highlighted = highlightTextAcrossElements(Array.from(elements), highlight.text, highlight.color, highlight.id);
                            if (highlighted) {
                                console.log("Applied cross-element highlight ID " + highlight.id);
                            }
                        }

                        // Final fallback: search entire page content for the text
                        // This handles cases where page layout changed the element structure
                        if (!highlighted) {
                            console.log("Trying global search for highlight ID " + highlight.id);
                            const wrapper = document.getElementById('pagination-wrapper');
                            if (wrapper) {
                                const allPageElements = wrapper.querySelectorAll('.page > *');
                                highlighted = highlightTextAcrossElements(Array.from(allPageElements), highlight.text, highlight.color, highlight.id);
                                if (highlighted) {
                                    console.log("Applied highlight ID " + highlight.id + " via global search");
                                }
                            }
                        }

                        if (!highlighted) {
                            console.warn("Could not apply highlight ID " + highlight.id + " - text not found: " + highlight.text.substring(0, 50) + "...");
                        }
                    });
                }

                // Function to be called from Android
                function setHighlights(highlightsJson) {
                    try {
                        const highlights = JSON.parse(highlightsJson);
                        userHighlightsCache = highlights;  // Store in global cache
                        console.log("Stored " + highlights.length + " highlights in cache");
                        applyHighlights(highlights);
                    } catch (e) {
                        console.error('Failed to apply highlights:', e);
                    }
                }

                // Function to clear selection (called after highlight creation)
                function clearTextSelection() {
                    const selection = window.getSelection();
                    if (selection) {
                        selection.removeAllRanges();
                    }
                }
            </script>
        </head>
        <body data-theme="${userSettings.readerTheme}">
            <div id="content-container">
                $html
            </div>
        </body>
        </html>
    """.trimIndent()
}

@Composable
fun ReaderControls(
    viewModel: ReaderViewModel,
    userSettings: UserSettings,
    currentChapter: Int,
    totalChapters: Int,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (Int) -> Unit,
    onFontFamilyChange: (String) -> Unit,
    onChapterChange: (Int) -> Unit,
    backgroundColor: Color,
    contentColor: Color,
    isTwoPageMode: Boolean = false
) {
    var showAdvancedSettings by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Inner screen settings indicator
            if (isTwoPageMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_book),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Inner Screen Settings (Two-Page Mode)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (currentChapter > 0) onChapterChange(currentChapter - 1) }) {
                    Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = "Prev Chapter")
                }
                Slider(
                    value = currentChapter.toFloat(),
                    onValueChange = { onChapterChange(it.toInt()) },
                    valueRange = 0f..(totalChapters - 1).coerceAtLeast(1).toFloat(),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { if (currentChapter < totalChapters - 1) onChapterChange(currentChapter + 1) }) {
                    Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "Next Chapter")
                }
            }
            Text("Chapter ${currentChapter + 1} of $totalChapters", style = MaterialTheme.typography.labelSmall)

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = contentColor.copy(alpha = 0.2f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_text_format), contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = userSettings.readerFontSize,
                    onValueChange = onFontSizeChange,
                    valueRange = 12f..36f,
                    modifier = Modifier.weight(1f)
                )
                Icon(painterResource(R.drawable.ic_text_format), contentDescription = null, modifier = Modifier.size(24.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ReaderThemeIcon(userSettings.readerTheme == 0, Color.White, Color.Black) { onThemeChange(0) }
                ReaderThemeIcon(userSettings.readerTheme == 1, Color(0xFFF4ECD8), Color(0xFF5B4636)) { onThemeChange(1) }
                ReaderThemeIcon(userSettings.readerTheme == 2, Color(0xFF121212), Color(0xFFE0E0E0)) { onThemeChange(2) }
                ReaderThemeIcon(userSettings.readerTheme == 3, Color.Black, Color.White) { onThemeChange(3) }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FontButton("Serif", userSettings.readerFontFamily == "serif") { onFontFamilyChange("serif") }
                FontButton("Sans", userSettings.readerFontFamily == "sans-serif") { onFontFamilyChange("sans-serif") }
                FontButton("Mono", userSettings.readerFontFamily == "monospace") { onFontFamilyChange("monospace") }
            }

            // Toggle for advanced settings
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = contentColor.copy(alpha = 0.2f))

            TextButton(
                onClick = { showAdvancedSettings = !showAdvancedSettings },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (showAdvancedSettings) "Hide Advanced Settings" else "Show Advanced Settings")
            }

            if (showAdvancedSettings) {
                ReaderAdvancedControls(viewModel, userSettings, contentColor)
            }
        }
    }
}

@Composable
fun ReaderAdvancedControls(
    viewModel: ReaderViewModel,
    userSettings: UserSettings,
    contentColor: Color
) {

    Column {
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = contentColor.copy(alpha = 0.2f))

        // Brightness control
        Text("Brightness", style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("☀", style = MaterialTheme.typography.bodySmall, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Slider(
                value = userSettings.readerBrightness,
                onValueChange = { viewModel.updateBrightness(it) },
                valueRange = 0.1f..1.0f,
                modifier = Modifier.weight(1f)
            )
            Text("☀", style = MaterialTheme.typography.titleMedium, modifier = Modifier.size(24.dp))
        }

        Spacer(Modifier.height(8.dp))

        // Line spacing control
        Text("Line Spacing", style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = userSettings.readerLineSpacing,
                onValueChange = { viewModel.updateLineSpacing(it) },
                valueRange = 1.0f..2.5f,
                modifier = Modifier.weight(1f)
            )
            Text("${String.format("%.1f", userSettings.readerLineSpacing)}x", modifier = Modifier.width(48.dp))
        }

        Spacer(Modifier.height(8.dp))

        // Margin size control
        Text("Margins", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MarginButton("Compact", userSettings.readerMarginSize == 0) { viewModel.updateMarginSize(0) }
            MarginButton("Normal", userSettings.readerMarginSize == 1) { viewModel.updateMarginSize(1) }
            MarginButton("Wide", userSettings.readerMarginSize == 2) { viewModel.updateMarginSize(2) }
        }

        Spacer(Modifier.height(8.dp))

        // Text alignment control
        Text("Text Alignment", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AlignmentButton("Left", userSettings.readerTextAlignment == "left") { viewModel.updateTextAlignment("left") }
            AlignmentButton("Center", userSettings.readerTextAlignment == "center") { viewModel.updateTextAlignment("center") }
            AlignmentButton("Justify", userSettings.readerTextAlignment == "justify") { viewModel.updateTextAlignment("justify") }
        }

        Spacer(Modifier.height(8.dp))

        // Fullscreen mode toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Fullscreen Mode", style = MaterialTheme.typography.labelMedium)
            Switch(
                checked = userSettings.readerFullscreenMode,
                onCheckedChange = { viewModel.updateFullscreenMode(it) }
            )
        }
    }
}

@Composable
fun MarginButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun AlignmentButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun ReaderThemeIcon(selected: Boolean, bg: Color, text: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = bg,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("A", color = text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun FontButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

data class ReaderThemeData(val bg: String, val text: String, val bgInt: Int, val textInt: Int)

fun getReaderTheme(themeId: Int): ReaderThemeData {
    return when(themeId) {
        1 -> ReaderThemeData("#F4ECD8", "#5B4636", 0xFFF4ECD8.toInt(), 0xFF5B4636.toInt())
        2 -> ReaderThemeData("#121212", "#E0E0E0", 0xFF121212.toInt(), 0xFFE0E0E0.toInt())
        3 -> ReaderThemeData("#000000", "#FFFFFF", 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        else -> ReaderThemeData("#FFFFFF", "#000000", 0xFFFFFFFF.toInt(), 0xFF000000.toInt())
    }
}

@Composable
fun HighlightsSheet(
    highlights: List<com.pekempy.ReadAloudbooks.data.local.entities.Highlight>,
    onHighlightClick: (com.pekempy.ReadAloudbooks.data.local.entities.Highlight) -> Unit,
    onDeleteHighlight: (com.pekempy.ReadAloudbooks.data.local.entities.Highlight) -> Unit,
    onExportClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Highlights (${highlights.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (highlights.isNotEmpty()) {
                IconButton(onClick = onExportClick) {
                    Icon(painterResource(R.drawable.ic_share), contentDescription = "Export")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (highlights.isEmpty()) {
            Text(
                "No highlights yet. Select text to create a highlight.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 32.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(highlights) { highlight ->
                    HighlightItem(
                        highlight = highlight,
                        onClick = { onHighlightClick(highlight) },
                        onDelete = { onDeleteHighlight(highlight) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun HighlightItem(
    highlight: com.pekempy.ReadAloudbooks.data.local.entities.Highlight,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Chapter ${highlight.chapterIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Text(
                highlight.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            if (!highlight.note.isNullOrBlank()) {
                Text(
                    "Note: ${highlight.note}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Color indicator
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        ComposeColor(android.graphics.Color.parseColor(highlight.color)),
                        shape = CircleShape
                    )
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Highlight") },
            text = { Text("Are you sure you want to delete this highlight?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun HistorySheet(
    historyEvents: List<ReaderViewModel.HistoryEvent>,
    currentAudioPosition: Long,
    onEventClick: (ReaderViewModel.HistoryEvent) -> Unit
) {
    val dateFormat = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            "Navigation History",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Tap any entry to return to that position",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (historyEvents.isEmpty()) {
            Text(
                "No navigation history yet. History is recorded when you navigate to highlights or change playback state.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 32.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(historyEvents) { event ->
                    HistoryItem(
                        event = event,
                        dateFormat = dateFormat,
                        onClick = { onEventClick(event) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun HistoryItem(
    event: ReaderViewModel.HistoryEvent,
    dateFormat: java.text.SimpleDateFormat,
    onClick: () -> Unit
) {
    val icon = when (event.type) {
        ReaderViewModel.HistoryEventType.PLAY -> R.drawable.ic_play_arrow
        ReaderViewModel.HistoryEventType.PAUSE -> R.drawable.ic_pause
        ReaderViewModel.HistoryEventType.NAVIGATE -> R.drawable.ic_arrow_forward
        ReaderViewModel.HistoryEventType.HIGHLIGHT_CLICK -> R.drawable.ic_highlight
    }

    val typeLabel = when (event.type) {
        ReaderViewModel.HistoryEventType.PLAY -> "Played"
        ReaderViewModel.HistoryEventType.PAUSE -> "Paused"
        ReaderViewModel.HistoryEventType.NAVIGATE -> "Navigated"
        ReaderViewModel.HistoryEventType.HIGHLIGHT_CLICK -> "Viewed Highlight"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(icon),
                contentDescription = typeLabel,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    event.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    event.chapterTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        dateFormat.format(java.util.Date(event.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Page ${((event.scrollPercent * 100).toInt())}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (event.audioPositionMs > 0) {
                        Text(
                            formatAudioTime(event.audioPositionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Icon(
                painterResource(R.drawable.ic_keyboard_arrow_right),
                contentDescription = "Go to",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatAudioTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

@Composable
fun BookmarksSheet(
    bookmarks: List<com.pekempy.ReadAloudbooks.data.local.entities.Bookmark>,
    onBookmarkClick: (com.pekempy.ReadAloudbooks.data.local.entities.Bookmark) -> Unit,
    onDeleteBookmark: (com.pekempy.ReadAloudbooks.data.local.entities.Bookmark) -> Unit,
    onAddBookmark: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Bookmarks (${bookmarks.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onAddBookmark) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = "Add Bookmark")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (bookmarks.isEmpty()) {
            Text(
                "No bookmarks yet. Tap + to create a bookmark at your current location.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 32.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(bookmarks) { bookmark ->
                    BookmarkItem(
                        bookmark = bookmark,
                        onClick = { onBookmarkClick(bookmark) },
                        onDelete = { onDeleteBookmark(bookmark) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun BookmarkItem(
    bookmark: com.pekempy.ReadAloudbooks.data.local.entities.Bookmark,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    bookmark.label ?: "Chapter ${bookmark.chapterIndex + 1}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Chapter ${bookmark.chapterIndex + 1} • ${(bookmark.scrollPercent * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Bookmark") },
            text = { Text("Are you sure you want to delete this bookmark?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ColorPickerDialog(
    selectedColor: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = listOf(
        "#FFEB3B" to "Yellow",
        "#4CAF50" to "Green",
        "#2196F3" to "Blue",
        "#9C27B0" to "Purple",
        "#FF9800" to "Orange",
        "#E91E63" to "Pink",
        "#F44336" to "Red"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Highlight Color") },
        text = {
            Column {
                colors.forEach { (hex, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onColorSelected(hex) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    ComposeColor(android.graphics.Color.parseColor(hex)),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                        if (hex == selectedColor) {
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                painterResource(R.drawable.ic_check),
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ExportHighlightsDialog(
    onExportMarkdown: () -> Unit,
    onExportCsv: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Highlights") },
        text = {
            Column {
                TextButton(
                    onClick = onExportMarkdown,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(painterResource(R.drawable.ic_file), contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export as Markdown (.md)")
                    }
                }
                TextButton(
                    onClick = onExportCsv,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(painterResource(R.drawable.ic_file), contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export as CSV (.csv)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LongPressContextMenu(
    onNavigateToPosition: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start Playback") },
        text = {
            Column {
                TextButton(
                    onClick = {
                        onNavigateToPosition()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painterResource(R.drawable.ic_play_arrow),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Start Reading Here")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
