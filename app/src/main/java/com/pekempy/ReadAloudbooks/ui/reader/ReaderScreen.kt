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
    val isReadAloud = viewModel.isReadAloudMode

    
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
                        allowFileAccessFromFileURLs = true
                        allowUniversalAccessFromFileURLs = true
                        allowFileAccess = true
                        allowContentAccess = true
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
                
                val contentSignature = "$baseUrl-${userSettings.readerTheme}-${userSettings.readerFontSize}-${userSettings.readerFontFamily}-$isReadAloud"
                val lastSignature = webView.tag as? String
                
                if (lastSignature != contentSignature) {
                    val styledHtml = wrapHtml(html, userSettings, theme, viewModel.lastScrollPercent, accentHex, highlightId, isReadAloud)
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
            }
        )
    }
}

fun wrapHtml(html: String, userSettings: UserSettings, theme: ReaderThemeData, initialScrollPercent: Float, accentColor: String, initialHighlightId: String? = null, isReadAloud: Boolean = false): String {
    val isDefaultFont = userSettings.readerFontFamily == "default"
    val fontFamily = when(userSettings.readerFontFamily) {
        "serif" -> "serif"
        "sans-serif" -> "sans-serif"
        "monospace" -> "monospace"
        "default" -> ""
        else -> "serif"
    }

    // Extract head and body content from the original HTML, preserving attributes
    val htmlMatch = Regex("<html([^>]*)>", RegexOption.IGNORE_CASE).find(html)
    val htmlAttrs = htmlMatch?.groupValues?.get(1) ?: ""
    
    val headContent = Regex("<head[^>]*>([\\s\\S]*?)</head>", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1) ?: ""
    
    val bodyMatch = Regex("<body([^>]*)>([\\s\\S]*?)</body>", RegexOption.IGNORE_CASE).find(html)
    val bodyAttrs = bodyMatch?.groupValues?.get(1) ?: ""
    val bodyContent = bodyMatch?.groupValues?.get(2) ?: html
    
    return """
        <!DOCTYPE html>
        <html $htmlAttrs>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
            $headContent
            <style>
                :root {
                    --bg-color: ${theme.bg};
                    --text-color: ${theme.text};
                    --font-size: ${userSettings.readerFontSize}px;
                    --font-family: $fontFamily;
                    --padding-left: 16px;
                    --padding-right: 16px;
                    --top-padding: 130px;
                    --bottom-padding: ${if (isReadAloud) "140px" else "60px"};
                    --accent-color: $accentColor;
                }
                
                html, body {
                    margin: 0;
                    padding: 0;
                    height: 100vh;
                    width: 100vw;
                    overflow: hidden;
                    background-color: var(--bg-color) !important;
                    color: var(--text-color) !important;
                    -webkit-user-select: none;
                    
                    /* Maximize text density */
                    line-height: ${if (isDefaultFont) "1.5" else "1.5 !important"};
                    hyphens: auto;
                    -webkit-hyphens: auto;
                    text-align: justify;
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
                    font-size: ${if (isDefaultFont) "var(--font-size)" else "var(--font-size) !important"};
                    line-height: ${if (isDefaultFont) "1.6" else "1.6 !important"};
                    color: var(--text-color) !important;
                    ${if (!isDefaultFont) "font-family: var(--font-family) !important;" else ""}
                    max-width: 100% !important;
                    -webkit-user-select: none;
                    user-select: none;
                    -webkit-touch-callout: none;
                }

                /* Ensure backgrounds don't clash with theme, but don't strip everything */
                .page, .page *:not(img):not(.highlight):not(.search-highlight) {
                     background-color: transparent !important;
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
                
                [data-theme="2"] .highlight, [data-theme="3"] .highlight {
                    border-bottom: 2px solid var(--accent-color) !important;
                }
            </style>
            <script>
                let currentPage = 0;
                let pageCount = 0;
                let currentHighlightId = null;
                let elementPageMap = {};

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
                        
                        if (safe < text.length) {
                             const lastSpace = text.lastIndexOf(' ', safe);
                             if (lastSpace > 0) {
                                 safe = lastSpace + 1; 
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
                }
                
                function gotoPage(index, animate = true) {
                    if (index < 0) index = 0;
                    if (index >= pageCount) index = pageCount - 1;
                    currentPage = index;
                    const wrapper = document.getElementById('pagination-wrapper');
                    if (wrapper) {
                        wrapper.style.transition = animate ? 'transform 0.3s cubic-bezier(0.25, 1, 0.5, 1)' : 'none';
                        wrapper.style.transform = 'translateX(-' + (index * 100) + 'vw)';
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
                    if (currentPage <= 0) {
                        if (window.Android) window.Android.onPrevChapter();
                        return;
                    }
                    gotoPage(currentPage - 1);
                }
                
                function pageRight() {
                    if (currentPage >= pageCount - 1) {
                         if (window.Android) window.Android.onNextChapter();
                         return;
                    }
                    gotoPage(currentPage + 1);
                }
                
                function highlightElement(id, retry = 0, animated = true) {
                    if (!id) return;
                    
                    const parts = Array.from(document.querySelectorAll(`[id="${'$'}{id}"], [data-continuation-of="${'$'}{id}"]`));
                    
                    if (parts.length === 0) {
                        if (retry < 5) setTimeout(() => highlightElement(id, retry+1, animated), 200);
                        return;
                    }

                    if (currentHighlightId !== id) {
                         document.querySelectorAll('.highlight').forEach(o => o.classList.remove('highlight'));
                         parts.forEach(el => el.classList.add('highlight'));
                         currentHighlightId = id;
                    } else {
                         parts.forEach(el => el.classList.add('highlight'));
                    }

                    const wrapper = document.getElementById('pagination-wrapper');
                    
                    let totalLen = 0;
                    parts.forEach(p => totalLen += p.textContent.length);
                    
                    if (totalLen < 40) {
                         const lastPart = parts[parts.length - 1];
                         const page = lastPart.closest('.page');
                         
                         if (page && wrapper) {
                             const pIdx = Array.from(wrapper.children).indexOf(page);
                             
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
                        if (window.Android) window.Android.onBodyClick(tapX, width);
                    }
                }, false);

                window.oncontextmenu = function(event) {
                    event.preventDefault();
                    event.stopPropagation();
                    let target = event.target;
                    while (target && !target.id) {
                        target = target.parentElement;
                    }
                    if (target && target.id && window.Android) {
                        window.Android.onElementLongPress(target.id);
                        return false;
                    }
                };
            </script>
        </head>
        <body $bodyAttrs data-theme="${userSettings.readerTheme}">
            <div id="content-container">
                $bodyContent
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
                FontButton("Default", userSettings.readerFontFamily == "default") { onFontFamilyChange("default") }
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
