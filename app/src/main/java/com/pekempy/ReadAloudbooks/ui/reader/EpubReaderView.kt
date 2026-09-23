package com.pekempy.ReadAloudbooks.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pekempy.ReadAloudbooks.R
import com.pekempy.ReadAloudbooks.data.UserSettings
import kotlinx.coroutines.launch

/** Native (non-WebView) reader colour palette, independent of the app's Material theme. */
data class ReaderTheme(
    val background: Color,
    val text: Color,
    val secondaryText: Color,
    val highlight: Color,
    val highlightText: Color
)

fun readerThemeFor(themeId: Int, accent: Color): ReaderTheme = when (themeId) {
    1 -> ReaderTheme( // Sepia
        background = Color(0xFFF4ECD8),
        text = Color(0xFF5B4636),
        secondaryText = Color(0xFF8A7560),
        highlight = accent,
        highlightText = Color(0xFF3A2C1E)
    )
    2 -> ReaderTheme( // Dark
        background = Color(0xFF121212),
        text = Color(0xFFE0E0E0),
        secondaryText = Color(0xFF9A9A9A),
        highlight = accent,
        highlightText = Color.White
    )
    3 -> ReaderTheme( // OLED black
        background = Color.Black,
        text = Color.White,
        secondaryText = Color(0xFFAAAAAA),
        highlight = accent,
        highlightText = Color.White
    )
    else -> ReaderTheme( // Light
        background = Color.White,
        text = Color(0xFF1A1A1A),
        secondaryText = Color(0xFF666666),
        highlight = accent,
        highlightText = Color.Black
    )
}

/** Standard system font unless the reader explicitly opts into a custom family. */
fun readerFontFamilyFor(settings: UserSettings): FontFamily {
    if (!settings.readerUseCustomFont) return FontFamily.Default
    return when (settings.readerFontFamily) {
        "sans-serif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.Serif
    }
}

/**
 * The device's actual Material You (Android 12+ dynamic colour) primary, independent of whether
 * the app's own theme setting has dynamic colour enabled — for reader highlight colouring, which
 * offers it as a distinct explicit choice. Null on API < 31 or if dynamic colour isn't available.
 */
@Composable
fun rememberMaterialYouColor(dark: Boolean): Color? {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return null
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context, dark) {
        val scheme = if (dark) {
            androidx.compose.material3.dynamicDarkColorScheme(context)
        } else {
            androidx.compose.material3.dynamicLightColorScheme(context)
        }
        scheme.primary
    }
}

/** How the currently-spoken sentence is painted. */
enum class HighlightStyle { FILL, UNDERLINE, OUTLINE }

fun highlightStyleFor(id: Int): HighlightStyle = when (id) {
    1 -> HighlightStyle.UNDERLINE
    2 -> HighlightStyle.OUTLINE
    else -> HighlightStyle.FILL
}

/** Preset swatches for the highlight colour picker; 0 in settings means "use theme default". */
val HIGHLIGHT_COLOR_PRESETS = listOf(
    0xFFFFD54F, 0xFFFFA726, 0xFF66BB6A, 0xFF42A5F5, 0xFFAB47BC, 0xFFEF5350
).map { Color(it) }

/** Sentinel values for `UserSettings.readerHighlightColor`; any other value is a literal ARGB color. */
const val HIGHLIGHT_COLOR_THEME_DEFAULT = 0
const val HIGHLIGHT_COLOR_MATERIAL_YOU = -1
const val HIGHLIGHT_COLOR_BOOK_THEME = -2

fun readerHighlightColorFor(
    settings: UserSettings,
    theme: ReaderTheme,
    materialYouColor: Color? = null,
    bookThemeColor: Color? = null
): Color {
    val chosen = when (settings.readerHighlightColor) {
        HIGHLIGHT_COLOR_THEME_DEFAULT -> theme.highlight
        HIGHLIGHT_COLOR_MATERIAL_YOU -> materialYouColor ?: theme.highlight
        HIGHLIGHT_COLOR_BOOK_THEME -> bookThemeColor ?: theme.highlight
        else -> Color(settings.readerHighlightColor)
    }
    // Book-cover/Material You/custom colours can land anywhere in brightness; keep the
    // highlight visible (and any underline/outline stroke or text drawn in it legible)
    // against whichever reader theme background is active.
    return com.pekempy.ReadAloudbooks.util.ContrastUtils.ensureContrast(chosen, theme.background, minContrast = 2.2f)
}

/** One sentence's location within the merged, chapter-wide text flow. */
private data class FlowSentence(val range: IntRange, val id: String?)

private class ChapterFlow(val annotated: AnnotatedString, val sentences: List<FlowSentence>)

/** A single fixed-viewport page: the slice `[startOffset, endOffset)` of the chapter flow. */
private data class ChapterPage(val startOffset: Int, val endOffset: Int, val topPx: Float)

private class ChapterLayout(val flow: ChapterFlow, val textLayout: TextLayoutResult, val pages: List<ChapterPage>)

private fun buildChapterFlow(paragraphs: List<ReaderParagraph>, theme: ReaderTheme, fontSize: Float): ChapterFlow {
    val sentences = mutableListOf<FlowSentence>()
    val annotated = buildAnnotatedString {
        paragraphs.forEachIndexed { pIndex, paragraph ->
            val paragraphStart = length
            val sizeSp = if (paragraph.isHeading) (fontSize * 1.3f).sp else fontSize.sp
            paragraph.sentences.forEachIndexed { sIndex, sentence ->
                val start = length
                withStyle(
                    SpanStyle(
                        fontWeight = if (sentence.bold || paragraph.isHeading) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (sentence.italic) FontStyle.Italic else FontStyle.Normal,
                        fontSize = sizeSp,
                        color = theme.text
                    )
                ) {
                    append(sentence.text)
                }
                sentences.add(FlowSentence(start until length, sentence.id))
                if (sIndex != paragraph.sentences.lastIndex) append(" ")
            }
            if (paragraph.isHeading) {
                addStyle(ParagraphStyle(textAlign = TextAlign.Center), paragraphStart, length)
            }
            if (pIndex != paragraphs.lastIndex) append("\n\n")
        }
    }
    return ChapterFlow(annotated, sentences)
}

@Composable
private fun rememberChapterLayout(
    paragraphs: List<ReaderParagraph>,
    theme: ReaderTheme,
    fontFamily: FontFamily,
    fontSize: Float,
    contentWidthPx: Int,
    contentHeightPx: Int
): ChapterLayout? {
    val textMeasurer = rememberTextMeasurer()
    return remember(paragraphs, theme, fontFamily, fontSize, contentWidthPx, contentHeightPx) {
        if (paragraphs.isEmpty() || contentWidthPx <= 0 || contentHeightPx <= 0) return@remember null

        val flow = buildChapterFlow(paragraphs, theme, fontSize)
        val style = TextStyle(fontFamily = fontFamily, fontSize = fontSize.sp, lineHeight = (fontSize * 1.55f).sp)
        val textLayout = textMeasurer.measure(
            text = flow.annotated,
            style = style,
            constraints = Constraints(maxWidth = contentWidthPx)
        )

        val pages = mutableListOf<ChapterPage>()
        if (textLayout.lineCount == 0) {
            pages.add(ChapterPage(0, flow.annotated.length, 0f))
        } else {
            var pageStartLine = 0
            for (line in 0 until textLayout.lineCount) {
                val top = textLayout.getLineTop(pageStartLine)
                val bottom = textLayout.getLineBottom(line)
                if (bottom - top > contentHeightPx && line > pageStartLine) {
                    val startOffset = textLayout.getLineStart(pageStartLine)
                    val endOffset = textLayout.getLineEnd(line - 1, visibleEnd = true)
                    pages.add(ChapterPage(startOffset, endOffset, top))
                    pageStartLine = line
                }
            }
            pages.add(
                ChapterPage(
                    startOffset = textLayout.getLineStart(pageStartLine),
                    endOffset = flow.annotated.length,
                    topPx = textLayout.getLineTop(pageStartLine)
                )
            )
        }
        ChapterLayout(flow, textLayout, pages)
    }
}

/**
 * Native, paginated EPUB chapter renderer. Replaces the previous WebView + injected-JS reader
 * entirely: the whole chapter is measured once into a single text flow, then sliced into fixed
 * pages sized to the actual viewport (recomputed whenever screen size or font size changes), so
 * "turning a page" is a hard, screen-sized cut rather than an arbitrary scroll distance.
 *
 * Left/right edge taps (or swipes) turn pages with an animation; long-pressing a sentence seeks/
 * jumps the highlight there (a plain tap toggles the surrounding reader chrome).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpubReaderContent(
    paragraphs: List<ReaderParagraph>,
    theme: ReaderTheme,
    fontFamily: FontFamily,
    fontSize: Float,
    highlightId: String?,
    highlightStyle: HighlightStyle,
    highlightColor: Color,
    highlightRounded: Boolean,
    isReadAloud: Boolean,
    initialCharOffsetFraction: Float,
    searchQuery: String?,
    onPageProgress: (charOffsetFraction: Float, sentenceId: String?) -> Unit,
    onSentenceLongPress: (String) -> Unit,
    onCenterTap: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var viewportSize by remember(paragraphs) { mutableStateOf(IntSize.Zero) }

    val horizontalPaddingPx = with(density) { 24.dp.roundToPx() }
    val verticalPaddingPx = with(density) { 48.dp.roundToPx() }
    val contentWidthPx = (viewportSize.width - horizontalPaddingPx * 2)
    val contentHeightPx = (viewportSize.height - verticalPaddingPx * 2)

    val chapterLayout = rememberChapterLayout(paragraphs, theme, fontFamily, fontSize, contentWidthPx, contentHeightPx)
    val pageCount = chapterLayout?.pages?.size ?: 1

    val pagerState = key(paragraphs) {
        rememberPagerState(initialPage = 0) { pageCount }
    }

    var appliedInitialPosition by remember(paragraphs) { mutableStateOf(false) }
    var isFollowing by remember(paragraphs) { mutableStateOf(true) }

    LaunchedEffect(pagerState) {
        pagerState.interactionSource.interactions.collect { interaction ->
            if (interaction is androidx.compose.foundation.interaction.DragInteraction.Start) isFollowing = false
        }
    }

    // Restore the saved reading position (a 0..1 fraction through the chapter's character flow)
    // once pagination is known, then start reporting page-progress/highlight-follow.
    LaunchedEffect(chapterLayout) {
        val layout = chapterLayout ?: return@LaunchedEffect
        if (!appliedInitialPosition) {
            val targetOffset = (initialCharOffsetFraction * layout.flow.annotated.length).toInt()
            val targetPage = layout.pages.indexOfLast { targetOffset >= it.startOffset }.coerceAtLeast(0)
            if (targetPage != pagerState.currentPage) pagerState.scrollToPage(targetPage)
            appliedInitialPosition = true
        }
    }

    LaunchedEffect(pagerState.currentPage, chapterLayout, appliedInitialPosition) {
        val layout = chapterLayout ?: return@LaunchedEffect
        if (!appliedInitialPosition) return@LaunchedEffect
        val page = layout.pages.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        val totalLength = layout.flow.annotated.length.coerceAtLeast(1)
        val fraction = (page.startOffset.toFloat() / totalLength).coerceIn(0f, 1f)
        val sentenceId = layout.flow.sentences.firstOrNull { it.range.first >= page.startOffset }?.id
        onPageProgress(fraction, sentenceId)
    }

    val highlightParagraphPage = remember(chapterLayout, highlightId) {
        val layout = chapterLayout
        if (layout == null || highlightId == null) -1
        else {
            val sentence = layout.flow.sentences.firstOrNull { it.id == highlightId }
            if (sentence == null) -1
            else layout.pages.indexOfLast { sentence.range.first >= it.startOffset }.coerceAtLeast(0)
        }
    }

    // Keyed directly on highlightId (the source signal from audio playback) rather than a
    // derived/remembered page number, so a fresh page lookup always runs the instant the spoken
    // sentence changes — avoids relying on remember-cache invalidation timing for something that
    // needs to fire on every sentence change.
    val latestChapterLayout by rememberUpdatedState(chapterLayout)
    val latestIsFollowing by rememberUpdatedState(isFollowing)
    LaunchedEffect(highlightId, appliedInitialPosition) {
        if (!appliedInitialPosition || highlightId == null) return@LaunchedEffect
        val layout = latestChapterLayout ?: return@LaunchedEffect
        val sentence = layout.flow.sentences.firstOrNull { it.id == highlightId } ?: return@LaunchedEffect
        val targetPage = layout.pages.indexOfLast { sentence.range.first >= it.startOffset }.coerceAtLeast(0)
        if (latestIsFollowing && targetPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(chapterLayout, searchQuery, appliedInitialPosition) {
        val layout = chapterLayout ?: return@LaunchedEffect
        val query = searchQuery
        if (appliedInitialPosition && !query.isNullOrBlank()) {
            val matchOffset = layout.flow.annotated.text.indexOf(query, ignoreCase = true)
            if (matchOffset >= 0) {
                val page = layout.pages.indexOfLast { matchOffset >= it.startOffset }.coerceAtLeast(0)
                pagerState.animateScrollToPage(page)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .onSizeChanged { viewportSize = it }
    ) {
        if (chapterLayout != null) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true
            ) { pageIndex ->
                ChapterPageView(
                    layout = chapterLayout,
                    page = chapterLayout.pages[pageIndex],
                    highlightId = if (isReadAloud) highlightId else null,
                    highlightStyle = highlightStyle,
                    highlightColor = highlightColor,
                    highlightRounded = highlightRounded,
                    horizontalPaddingPx = horizontalPaddingPx,
                    verticalPaddingPx = verticalPaddingPx,
                    onSentenceLongPress = onSentenceLongPress,
                    onBlankTap = onCenterTap
                )
            }

            // Invisible left/right edge zones turn pages with the pager's own animation; the
            // wide centre strip is a no-op Spacer so taps/long-presses fall through to the page.
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.16f)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            isFollowing = false
                            scope.launch {
                                if (pagerState.currentPage > 0) pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                else onPrevChapter()
                            }
                        }
                )
                Spacer(modifier = Modifier.weight(0.68f).fillMaxHeight())
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.16f)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            isFollowing = false
                            scope.launch {
                                if (pagerState.currentPage < pageCount - 1) pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                else onNextChapter()
                            }
                        }
                )
            }
        }

        if (isReadAloud && !isFollowing && highlightParagraphPage >= 0 && chapterLayout != null) {
            FilledTonalButton(
                onClick = {
                    isFollowing = true
                    scope.launch { pagerState.animateScrollToPage(highlightParagraphPage) }
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            ) {
                Icon(painterResource(R.drawable.ic_headphones), contentDescription = null)
                Text("  Resume following")
            }
        }
    }
}

@Composable
private fun ChapterPageView(
    layout: ChapterLayout,
    page: ChapterPage,
    highlightId: String?,
    highlightStyle: HighlightStyle,
    highlightColor: Color,
    highlightRounded: Boolean,
    horizontalPaddingPx: Int,
    verticalPaddingPx: Int,
    onSentenceLongPress: (String) -> Unit,
    onBlankTap: () -> Unit
) {
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { if (highlightRounded) 6.dp.toPx() else 0f }
    val strokeWidthPx = with(density) { 1.6.dp.toPx() }
    val underlineThicknessPx = with(density) { 2.5.dp.toPx() }
    val padPx = with(density) { 2.dp.toPx() }
    val fillColor = remember(highlightColor) { highlightColor.copy(alpha = 0.35f) }

    val highlightRange = remember(layout, highlightId, page) {
        if (highlightId == null) null
        else {
            val sentence = layout.flow.sentences.firstOrNull { it.id == highlightId } ?: return@remember null
            if (sentence.range.first >= page.endOffset || sentence.range.last < page.startOffset) null
            else sentence.range
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = with(density) { horizontalPaddingPx.toDp() }, vertical = with(density) { verticalPaddingPx.toDp() })
            .clipToBounds()
            .drawWithContent {
                drawText(layout.textLayout, topLeft = Offset(0f, -page.topPx))

                val range = highlightRange
                if (range != null) {
                    val result = layout.textLayout
                    val startLine = result.getLineForOffset(range.first)
                    val endLine = result.getLineForOffset(range.last)
                    val textLength = layout.flow.annotated.length
                    val endOffset = (range.last + 1).coerceAtMost(textLength)
                    for (line in startLine..endLine) {
                        val left = if (line == startLine) result.getHorizontalPosition(range.first, true) else result.getLineLeft(line)
                        val right = if (line == endLine) result.getHorizontalPosition(endOffset, true) else result.getLineRight(line)
                        if (right <= left) continue
                        val top = result.getLineTop(line) - page.topPx
                        val bottom = result.getLineBottom(line) - page.topPx
                        when (highlightStyle) {
                            HighlightStyle.UNDERLINE -> drawRoundRect(
                                color = highlightColor,
                                topLeft = Offset(left - padPx, bottom - underlineThicknessPx),
                                size = Size((right - left) + padPx * 2, underlineThicknessPx),
                                cornerRadius = CornerRadius(underlineThicknessPx / 2f)
                            )
                            HighlightStyle.OUTLINE -> drawRoundRect(
                                color = highlightColor,
                                topLeft = Offset(left - padPx, top),
                                size = Size((right - left) + padPx * 2, bottom - top),
                                cornerRadius = CornerRadius(cornerRadiusPx),
                                style = Stroke(width = strokeWidthPx)
                            )
                            HighlightStyle.FILL -> drawRoundRect(
                                color = fillColor,
                                topLeft = Offset(left - padPx, top),
                                size = Size((right - left) + padPx * 2, bottom - top),
                                cornerRadius = CornerRadius(cornerRadiusPx)
                            )
                        }
                    }
                }
                drawContent()
            }
            .pointerInput(layout, page) {
                detectTapGestures(
                    onTap = { onBlankTap() },
                    onLongPress = { offset ->
                        val local = Offset(offset.x, offset.y + page.topPx)
                        val charOffset = layout.textLayout.getOffsetForPosition(local)
                        val sentence = layout.flow.sentences.firstOrNull { charOffset in it.range }
                        sentence?.id?.let(onSentenceLongPress)
                    }
                )
            }
    )
}
