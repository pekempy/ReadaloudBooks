package com.pekempy.ReadAloudbooks.ui.reader

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.painterResource
import com.pekempy.ReadAloudbooks.R
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    var showContentsSheet by remember { mutableStateOf(false) }

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
        val context = androidx.compose.ui.platform.LocalContext.current
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
        val accentColor = MaterialTheme.colorScheme.primary
        val theme = readerThemeFor(userSettings.readerTheme, accentColor)
        val fontFamily = readerFontFamilyFor(userSettings)
        val materialYouColor = rememberMaterialYouColor(dark = userSettings.readerTheme == 2 || userSettings.readerTheme == 3)
        val bookThemeColor = if (userSettings.bookThemeColor != 0) Color(userSettings.bookThemeColor) else null

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.background)
        ) {
            ReaderBody(
                viewModel = viewModel,
                theme = theme,
                fontFamily = fontFamily,
                userSettings = userSettings,
                isReadAloud = false,
                highlightId = null,
                searchQuery = viewModel.activeSearchHighlight,
                materialYouColor = materialYouColor,
                bookThemeColor = bookThemeColor,
                onTap = { viewModel.showControls = !viewModel.showControls }
            )

            if (viewModel.showControls) {
                AnimatedVisibility(
                    visible = viewModel.showControls,
                    enter = slideInVertically { -it },
                    exit = slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.background.copy(alpha = 0.95f))
                            .statusBarsPadding()
                            .height(56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back", tint = theme.text)
                        }
                        Text(
                            viewModel.epubTitle,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            color = theme.text
                        )
                        IconButton(onClick = {
                            viewModel.clearSearch()
                            showSearchSheet = true
                        }) {
                            Icon(painterResource(R.drawable.ic_search), contentDescription = "Search", tint = theme.text)
                        }
                        IconButton(onClick = { showContentsSheet = true }) {
                            Icon(painterResource(R.drawable.ic_list), contentDescription = "Contents", tint = theme.text)
                        }
                        IconButton(onClick = { viewModel.showControls = !viewModel.showControls }) {
                            Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", tint = theme.text)
                        }
                    }
                }
            }

            if (viewModel.showControls) {
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
                        onUseCustomFontChange = viewModel::updateUseCustomFont,
                        onHighlightStyleChange = viewModel::updateHighlightStyle,
                        onHighlightColorChange = viewModel::updateHighlightColor,
                        onHighlightRoundedChange = viewModel::updateHighlightRounded,
                        materialYouColor = materialYouColor,
                        bookThemeColor = bookThemeColor,
                        onChapterChange = viewModel::changeChapter,
                        backgroundColor = theme.background.copy(alpha = 0.95f),
                        contentColor = theme.text
                    )
                }
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

        if (showContentsSheet) {
            ModalBottomSheet(onDismissRequest = { showContentsSheet = false }) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = "Contents",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val chapterCount = viewModel.lazyBook?.spineHrefs?.size ?: 0

                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(chapterCount) { index ->
                            val title = viewModel.getChapterTitle(index)
                            val isPart = title.contains("Part", ignoreCase = true)

                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = title,
                                        fontWeight = if (isPart) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.clickable {
                                    viewModel.changeChapter(index)
                                    showContentsSheet = false
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = if (viewModel.currentChapterIndex == index)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * Shared native reader body used by both the plain ebook screen and the combined
 * readaloud reader+player screen. Renders the current chapter's paragraphs, keeps the
 * scroll position saved as reading progress, and (in readaloud mode) highlights whichever
 * sentence [highlightId] points at.
 */
@Composable
fun ReaderBody(
    viewModel: ReaderViewModel,
    theme: ReaderTheme,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    userSettings: UserSettings,
    isReadAloud: Boolean,
    highlightId: String?,
    searchQuery: String? = null,
    materialYouColor: Color? = null,
    bookThemeColor: Color? = null,
    onTap: () -> Unit
) {
    val chapterIndex = viewModel.currentChapterIndex
    val paragraphs = remember(chapterIndex, viewModel.lazyBook) { viewModel.getCurrentChapterParagraphs() }

    LaunchedEffect(paragraphs.isNotEmpty()) {
        if (paragraphs.isNotEmpty()) viewModel.markReady()
    }

    EpubReaderContent(
        paragraphs = paragraphs,
        theme = theme,
        fontFamily = fontFamily,
        fontSize = userSettings.readerFontSize,
        highlightId = highlightId,
        highlightStyle = highlightStyleFor(userSettings.readerHighlightStyle),
        highlightColor = readerHighlightColorFor(userSettings, theme, materialYouColor, bookThemeColor),
        highlightRounded = userSettings.readerHighlightRounded,
        isReadAloud = isReadAloud,
        initialCharOffsetFraction = viewModel.lastScrollPercent,
        searchQuery = searchQuery,
        onPageProgress = { fraction, sentenceId ->
            // Readaloud progress is driven by audio position instead (see ReadAloudPlayerScreen);
            // only plain ebook reading persists page position from this callback.
            if (!isReadAloud) {
                viewModel.saveProgress(chapterIndex, fraction, null, sentenceId)
            }
        },
        onSentenceLongPress = { sentenceId ->
            viewModel.jumpToElementRequest.value = sentenceId
        },
        onCenterTap = onTap,
        onPrevChapter = {
            if (chapterIndex > 0) viewModel.changeChapter(chapterIndex - 1, scrollToEnd = true)
        },
        onNextChapter = {
            if (chapterIndex < viewModel.totalChapters - 1) viewModel.changeChapter(chapterIndex + 1)
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun ReaderControls(
    userSettings: UserSettings,
    currentChapter: Int,
    totalChapters: Int,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (Int) -> Unit,
    onFontFamilyChange: (String) -> Unit,
    onUseCustomFontChange: (Boolean) -> Unit,
    onHighlightStyleChange: (Int) -> Unit,
    onHighlightColorChange: (Int) -> Unit,
    onHighlightRoundedChange: (Boolean) -> Unit,
    materialYouColor: Color? = null,
    bookThemeColor: Color? = null,
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
            HorizontalDivider(Modifier.padding(bottom = 8.dp), color = contentColor.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Custom font", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = userSettings.readerUseCustomFont, onCheckedChange = onUseCustomFontChange)
            }

            if (userSettings.readerUseCustomFont) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FontButton("Serif", userSettings.readerFontFamily == "serif") { onFontFamilyChange("serif") }
                    FontButton("Sans", userSettings.readerFontFamily == "sans-serif") { onFontFamilyChange("sans-serif") }
                    FontButton("Mono", userSettings.readerFontFamily == "monospace") { onFontFamilyChange("monospace") }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(Modifier.padding(bottom = 8.dp), color = contentColor.copy(alpha = 0.2f))

            Text("Highlight", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FontButton("Fill", userSettings.readerHighlightStyle == 0) { onHighlightStyleChange(0) }
                FontButton("Underline", userSettings.readerHighlightStyle == 1) { onHighlightStyleChange(1) }
                FontButton("Outline", userSettings.readerHighlightStyle == 2) { onHighlightStyleChange(2) }
            }

            Spacer(Modifier.height(4.dp))
            if (materialYouColor != null || bookThemeColor != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (materialYouColor != null) {
                        HighlightColorChip(
                            label = "Material You",
                            color = materialYouColor,
                            selected = userSettings.readerHighlightColor == HIGHLIGHT_COLOR_MATERIAL_YOU
                        ) { onHighlightColorChange(HIGHLIGHT_COLOR_MATERIAL_YOU) }
                    }
                    if (bookThemeColor != null) {
                        HighlightColorChip(
                            label = "Book Theme",
                            color = bookThemeColor,
                            selected = userSettings.readerHighlightColor == HIGHLIGHT_COLOR_BOOK_THEME
                        ) { onHighlightColorChange(HIGHLIGHT_COLOR_BOOK_THEME) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HighlightColorSwatch(
                    color = null,
                    selected = userSettings.readerHighlightColor == HIGHLIGHT_COLOR_THEME_DEFAULT,
                    borderColor = contentColor
                ) { onHighlightColorChange(HIGHLIGHT_COLOR_THEME_DEFAULT) }
                HIGHLIGHT_COLOR_PRESETS.forEach { preset ->
                    Spacer(Modifier.width(8.dp))
                    HighlightColorSwatch(
                        color = preset,
                        selected = userSettings.readerHighlightColor == preset.toArgb(),
                        borderColor = contentColor
                    ) { onHighlightColorChange(preset.toArgb()) }
                }
            }

            if (userSettings.readerHighlightStyle != 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Rounded corners", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = userSettings.readerHighlightRounded, onCheckedChange = onHighlightRoundedChange)
                }
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

@Composable
fun HighlightColorSwatch(color: Color?, selected: Boolean, borderColor: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(28.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = color ?: Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else borderColor.copy(alpha = 0.4f))
    ) {
        if (color == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("A", color = borderColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun HighlightColorChip(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    )
}
