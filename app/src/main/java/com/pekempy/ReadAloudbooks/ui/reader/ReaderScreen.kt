package com.pekempy.ReadAloudbooks.ui.reader

import android.view.ViewGroup
import android.webkit.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.painterResource
import com.pekempy.ReadAloudbooks.R
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.compose.ui.viewinterop.AndroidView
import com.pekempy.ReadAloudbooks.data.UserSettings

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
    
    LaunchedEffect(bookId) {
        viewModel.loadEpub(bookId, isReadAloud)
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
                onTap = { viewModel.showControls = !viewModel.showControls }
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
                    viewModel.epubTitle, 
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
                IconButton(onClick = { viewModel.showControls = !viewModel.showControls }) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", tint = Color(theme.textInt))
                }
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
                    userSettings = userSettings,
                    currentChapter = viewModel.currentChapterIndex,
                    totalChapters = viewModel.totalChapters,
                    onFontSizeChange = viewModel::updateFontSize,
                    onThemeChange = viewModel::updateTheme,
                    onFontFamilyChange = viewModel::updateFontFamily,
                    onChapterChange = viewModel::changeChapter,
                    backgroundColor = Color(theme.bgInt).copy(alpha = 0.95f),
                    contentColor = Color(theme.textInt)
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
    onTap: () -> Unit
) {
    val theme = getReaderTheme(userSettings.readerTheme)

    
    key(userSettings.readerTheme, userSettings.readerFontFamily, userSettings.readerFontSize) {
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
                        fun onBodyClick(x: Float, width: Float) {
                            val ratio = x / width
                            when {
                                ratio < 0.25f -> {
                                    this@apply.post { this@apply.evaluateJavascript("pageLeft()", null) }
                                }
                                ratio > 0.75f -> {
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
                                // Go to previous chapter and jump to the last page (100% scroll)
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
                            viewModel.viewModelScope.launch {
                                viewModel.jumpToElementRequest.value = id
                            }
                        }

                        @JavascriptInterface
                        fun onReaderReady() {
                            android.util.Log.d("EpubWebView", "Reader reported ready.")
                            viewModel.markReady()
                        }
                    }, "Android")
                }
            },
            update = { webView ->
                val currentHighlightId = highlightId
                val trigger = syncTrigger
                val chapterPath = viewModel.getCurrentChapterPath()
                val baseUrl = "https://epub-internal/$chapterPath"
                
                val contentSignature = "$baseUrl-${userSettings.readerTheme}-${userSettings.readerFontSize}-${userSettings.readerFontFamily}"
                val lastSignature = webView.tag as? String
                
                if (lastSignature != contentSignature) {
                    val styledHtml = wrapHtml(html, userSettings, theme, viewModel.lastScrollPercent, accentHex, highlightId)
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
                    val lastSearch = webView.getTag(com.pekempy.ReadAloudbooks.R.id.search_tag) as? String
                    if (lastSearch != activeSearch) {
                        android.util.Log.d("EpubWebView", "Update: scheduling findAndHighlight('$activeSearch', 0, $activeSearchMatchIndex)")
                        webView.postDelayed({
                            android.util.Log.d("EpubWebView", "Update: executing findAndHighlight")
                            webView.evaluateJavascript("if (typeof findAndHighlight === 'function') findAndHighlight('$activeSearch', 0, $activeSearchMatchIndex)", null)
                        }, 300)
                        webView.setTag(com.pekempy.ReadAloudbooks.R.id.search_tag, activeSearch)
                    }
                }
                
                if (pendingAnchor != null) {
                    val lastAnchor = webView.getTag(com.pekempy.ReadAloudbooks.R.id.anchor_tag) as? String
                    if (lastAnchor != pendingAnchor) {
                         webView.evaluateJavascript("if (typeof highlightElement === 'function') highlightElement('$pendingAnchor', 0)", null)
                         webView.setTag(com.pekempy.ReadAloudbooks.R.id.anchor_tag, pendingAnchor)
                    }
                }
            }
        )
    }
}

fun wrapHtml(html: String, userSettings: UserSettings, theme: ReaderThemeData, initialScrollPercent: Float, accentColor: String, initialHighlightId: String? = null): String {
    val fontFamily = when(userSettings.readerFontFamily) {
        "serif" -> "serif"
        "sans-serif" -> "sans-serif"
        "monospace" -> "monospace"
        else -> "serif"
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
                    --padding-left: 16px;
                    --padding-right: 16px;
                    --top-padding: 100px;
                    --bottom-padding: 120px;
                    --accent-color: $accentColor;
                }
                
                html, body {
                    margin: 0;
                    padding: 0;
                    height: 100vh;
                    width: 100vw;
                    overflow: hidden;
                    background-color: var(--bg-color);
                    color: var(--text-color);
                    -webkit-user-select: none;
                    
                    /* Maximize text density */
                    line-height: 1.5 !important;
                    hyphens: auto;
                    -webkit-hyphens: auto;
                    text-align: justify;
                    text-indent: 1.5em;
                }

                #content-container {
                    box-sizing: border-box;
                    width: 100vw;
                    height: 100vh;
                    padding: var(--top-padding) var(--padding-right) var(--bottom-padding) var(--padding-left);
                    
                    column-width: calc(100vw - var(--padding-left) - var(--padding-right));
                    -webkit-column-width: calc(100vw - var(--padding-left) - var(--padding-right));
                    
                    column-gap: calc(var(--padding-left) + var(--padding-right));
                    -webkit-column-gap: calc(var(--padding-left) + var(--padding-right));
                    
                    column-fill: auto;
                    overflow: visible;
                    overflow-wrap: break-word;
                    word-wrap: break-word;
                    
                    will-change: transform;
                    backface-visibility: hidden;
                    -webkit-backface-visibility: hidden;
                    
                    transition: none;
                }

                #content-container.animate {
                    transition: transform 0.6s cubic-bezier(0.22, 1, 0.36, 1);
                }

                /* Standard content styling with support for theme consistency */
                #content-container, #content-container *:not(.highlight):not(.search-highlight) {
                    font-size: var(--font-size) !important;
                    font-family: var(--font-family) !important;
                    line-height: 1.6 !important;
                    color: var(--text-color) !important;
                    background-color: transparent !important;
                    max-width: 100% !important;
                    -webkit-user-select: none;
                    user-select: none;
                    -webkit-touch-callout: none;
                }

                /* Nuclear reset for unwanted lines (ruled paper, global underlining) */
                /* Excludes images, highlights, and intentional headers/emphasis tags */
                html, body, #content-container, #content-container *:not(img):not(.highlight):not(.search-highlight):not(h1):not(h2):not(h3):not(h4):not(h5):not(h6):not(u):not(b):not(strong) {
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
                
                [data-theme="2"] .highlight, [data-theme="3"] .highlight {
                    border-bottom: 2px solid var(--accent-color) !important;
                }
            </style>
            <script>
                let currentPage = 0;
                let currentHighlightId = null;
                let highlightInProgress = false;
                let pendingHighlight = null;
                
                function highlightElement(id, retryCount = 0, animated = true) {
                    if (!id) {
                        if (window.Android) window.Android.onReaderReady();
                        return;
                    }
                    
                    if (id && id.match && id.match(/^c\d+$/) && currentHighlightId && currentHighlightId.match && (currentHighlightId.includes('sentence') || currentHighlightId.includes('word') || currentHighlightId.includes('text'))) {
                        return;
                    }
                    
                    if (highlightInProgress && pendingHighlight !== id && retryCount === 0) {
                        pendingHighlight = id;
                        return;
                    }
                    
                    highlightInProgress = true;
                    pendingHighlight = null;
                    
                    const el = document.getElementById(id);
                    if (!el || (el.offsetWidth === 0 && el.offsetHeight === 0)) {
                        if (retryCount < 30) {
                            setTimeout(() => highlightElement(id, retryCount + 1, animated), 100);
                        } else {
                            highlightInProgress = false;
                            if (window.Android) window.Android.onReaderReady();
                            if (pendingHighlight && pendingHighlight !== id) {
                                highlightElement(pendingHighlight, 0, animated);
                            }
                        }
                        return;
                    }
                    
                    if (currentHighlightId !== id) {
                        const highlights = document.querySelectorAll('.highlight');
                        highlights.forEach(h => h.classList.remove('highlight'));
                        el.classList.add('highlight');
                        currentHighlightId = id;
                    }
                    
                    const container = document.getElementById('content-container');
                    const step = getStep();
                    if (step > 0 && container) {
                        const rect = el.getBoundingClientRect();
                        const containerRect = container.getBoundingClientRect();
                        
                        // Use a more stable left calculation
                        const absoluteLeft = rect.left - containerRect.left;
                        const elementCenter = absoluteLeft + (el.offsetWidth / 2);
                        
                        const targetPage = Math.floor((elementCenter + 1) / step);
                        const totalWidth = container.scrollWidth;
                        const maxPages = Math.max(1, Math.ceil(totalWidth / step));
                        const clampedPage = Math.max(0, Math.min(targetPage, maxPages - 1));
                        
                        console.log("Highlight: id=" + id + ", absLeft=" + absoluteLeft + ", step=" + step + ", page=" + clampedPage + "/" + maxPages);
                        
                        if (clampedPage !== currentPage) {
                            currentPage = clampedPage;
                            updateTransform(animated);
                        }
                    }
                    
                    highlightInProgress = false;
                    
                    if (pendingHighlight && pendingHighlight !== id) {
                        const nextHighlight = pendingHighlight;
                        pendingHighlight = null;
                        highlightElement(nextHighlight, 0, animated);
                    } else {
                        if (window.Android) window.Android.onReaderReady();
                    }
                }
                
                function elementLeftAbsolute(el, container) {
                    const rect = el.getBoundingClientRect();
                    const containerRect = container.getBoundingClientRect();
                    return rect.left - containerRect.left;
                }
                
                function getStep() {
                    const width = window.innerWidth || document.documentElement.clientWidth || document.body.clientWidth;
                    return Math.floor(width);
                }

                function updateTransform(animate = true) {
                    const container = document.getElementById('content-container');
                    if (!container) return;
                    const step = getStep();
                    const offset = Math.round(currentPage * step);
                    
                    if (animate) {
                        container.classList.add('animate');
                    } else {
                        container.classList.remove('animate');
                    }
                    
                    container.style.transform = 'translateX(-' + offset + 'px)';
                    container.offsetHeight; 
                    
                    if (window.Android) {
                        const totalWidth = container.scrollWidth;
                        if (totalWidth <= 0 || step <= 0) return;
                        
                        const maxPages = Math.max(1, Math.ceil(totalWidth / step));
                        const percent = maxPages > 1 ? (currentPage / (maxPages - 1)) : 0;
                        
                        let bestId = "";
                        const elements = container.querySelectorAll('p[id], div[id], h1[id], h2[id], h3[id], section[id], span[id]');
                        for (let i = 0; i < elements.length; i++) {
                            const el = elements[i];
                            if (el.classList.contains('highlight')) continue;
                            const left = elementLeftAbsolute(el, container);
                            if (left <= offset + 50) {
                                bestId = el.id;
                            } else if (left > offset + step) {
                                break;
                            }
                        }
                        
                        window.Android.onScrollWithId(percent, bestId);
                    }
                }

                function scrollToPercent(percent) {
                    const container = document.getElementById('content-container');
                    if (!container) return;
                    
                    function attemptScroll() {
                        const totalWidth = container.scrollWidth;
                        const step = getStep();
                        if (totalWidth <= 0 || step <= 0) {
                            setTimeout(attemptScroll, 100);
                            return;
                        }
                        const maxPages = Math.max(1, Math.ceil(totalWidth / step));
                        currentPage = Math.round(percent * (maxPages - 1));
                        updateTransform(false);
                    }
                    attemptScroll();
                }

                function pageLeft() {
                    if (currentPage <= 0) {
                        if (window.Android) window.Android.onPrevChapter();
                        return;
                    }
                    currentPage--;
                    updateTransform(true);
                }

                function findAndHighlight(text, retryCount = 0, matchIndex = 0) {
                    console.log("findAndHighlight called for: " + text + ", index: " + matchIndex + ", retry: " + retryCount);
                    if (!text) return;
                    
                    if (retryCount === 0) {
                         const oldHighlights = document.querySelectorAll('.search-highlight');
                         oldHighlights.forEach(el => {
                             const parent = el.parentNode;
                             while(el.firstChild) parent.insertBefore(el.firstChild, el);
                             parent.removeChild(el);
                         });
                    }

                    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
                    let node;
                    let currentMatch = 0;
                    let foundRange = null;

                    text = text.toLowerCase();
                    
                    while (node = walker.nextNode()) {
                        const content = node.textContent.toLowerCase();
                        let searchIndex = 0;
                        
                        while (true) {
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

                    if (!foundRange) {
                        console.log("Text not found via TreeWalker: " + text + ". Retrying: " + retryCount);
                        if (retryCount < 20) {
                             setTimeout(() => findAndHighlight(text, retryCount + 1, matchIndex), 100);
                        }
                        return;
                    }

                    console.log("TreeWalker found range!");
                    
                    try {
                        const span = document.createElement('span');
                        span.className = 'search-highlight';
                        foundRange.surroundContents(span);
                        
                        const rect = span.getBoundingClientRect();
                        const container = document.getElementById('content-container');
                        const containerRect = container.getBoundingClientRect();
                        const step = getStep();
                        
                        console.log("Calculated Step: " + step);

                        if (step > 0) {
                             const absoluteLeft = Math.round(rect.left - containerRect.left);
                             const targetPage = Math.floor((absoluteLeft + 1) / step);
                             
                             console.log("Rect Left: " + rect.left + ", Container Left: " + containerRect.left + ", AbsLeft: " + absoluteLeft + ", Step: " + step + ", TargetPage: " + targetPage);

                             if (targetPage !== currentPage) {
                                 console.log("Search: Jumping to page " + targetPage);
                                 currentPage = targetPage;
                                 updateTransform(false);
                             }
                        } else {
                            console.warn("Step is 0! Retrying...");
                             if (retryCount < 20) {
                                 const parent = span.parentNode;
                                 while(span.firstChild) parent.insertBefore(span.firstChild, span);
                                 parent.removeChild(span);
                                 
                                 setTimeout(() => findAndHighlight(text, retryCount + 1, matchIndex), 100);
                                 return;
                            }
                        }

                    } catch (e) {
                         console.error("Failed to wrap highlighting", e);
                         const rect = foundRange.getBoundingClientRect();
                         const container = document.getElementById('content-container');
                         const containerRect = container.getBoundingClientRect();
                         const step = getStep();
                         
                         if (step > 0) {
                              const absoluteLeft = Math.round(rect.left - containerRect.left);
                              const targetPage = Math.floor((absoluteLeft + 1) / step);
                              if (targetPage !== currentPage) {
                                  currentPage = targetPage;
                                  updateTransform(false);
                              }
                         }
                    }
                }
                
                function pageRight() {
                    const container = document.getElementById('content-container');
                    const totalWidth = container.scrollWidth;
                    const maxPages = Math.max(1, Math.ceil(totalWidth / getStep()));
                    
                    if (currentPage >= maxPages - 1) { 
                        if (window.Android) window.Android.onNextChapter();
                        return;
                    }
                    currentPage++;
                    updateTransform(true);
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
                        if (window.Android) window.Android.onBodyClick(tapX, width);
                    }
                }, false);

                window.oncontextmenu = function(event) {
                    event.preventDefault();
                    event.stopPropagation();
                    let target = event.target;
                    while (target && (!target.id || target.id === 'content-container')) {
                        target = target.parentElement;
                    }
                    if (target && target.id && window.Android) {
                        window.Android.onElementLongPress(target.id);
                        return false;
                    }
                };

                window.onload = function() {
                    const highlightId = ${if (initialHighlightId != null) "'$initialHighlightId'" else "null"};
                    const initialPercent = $initialScrollPercent;
                    
                    let lastScrollWidth = -1;
                    let stableCount = 0;

                    function stabilizeAndStart() {
                        const container = document.getElementById('content-container');
                        if (!container) {
                            setTimeout(stabilizeAndStart, 100);
                            return;
                        }
                        
                        const currentWidth = container.scrollWidth;
                        if (currentWidth <= 100) { 
                            setTimeout(stabilizeAndStart, 150);
                            return;
                        }

                        if (currentWidth === lastScrollWidth) {
                            stableCount++;
                        } else {
                            stableCount = 0;
                            lastScrollWidth = currentWidth;
                        }

                        if (stableCount < 2) {
                            setTimeout(stabilizeAndStart, 150);
                            return;
                        }
                        
                        console.log("Layout stable. Width: " + currentWidth + ". Start pos: " + initialPercent + ", highlight: " + highlightId);
                        
                        if (highlightId) {
                            highlightElement(highlightId, 0, false);
                        } else {
                            scrollToPercent(initialPercent);
                        }
                    }
                    
                    stabilizeAndStart();
                };
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
    userSettings: UserSettings,
    currentChapter: Int,
    totalChapters: Int,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (Int) -> Unit,
    onFontFamilyChange: (String) -> Unit,
    onChapterChange: (Int) -> Unit,
    backgroundColor: Color,
    contentColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
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
        }
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
