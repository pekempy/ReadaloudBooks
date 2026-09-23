package com.pekempy.ReadAloudbooks.ui.reader

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** One sentence-level run of text inside a chapter, matching a SMIL `<par>` id when present. */
data class ReaderSentence(
    val id: String?,
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false
)

/** One paragraph (block element) of chapter content, made up of one or more sentences. */
data class ReaderParagraph(
    val sentences: List<ReaderSentence>,
    val isHeading: Boolean = false
) {
    val plainText: String by lazy { sentences.joinToString(" ") { it.text }.trim() }
}

/**
 * Parses the raw XHTML of a single EPUB chapter into paragraphs of sentences, using the
 * `<span id="...">` markers EPUB3 Media Overlay (readaloud) books use to align text to audio.
 * Falls back to whole-paragraph text (no sentence ids) for plain ebooks without readaloud sync.
 */
object EpubContentParser {
    private val BLOCK_TAGS = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "li")
    private val CONTAINER_TAGS = setOf("div", "section", "article", "body", "ul", "ol", "header", "footer", "main")

    fun parse(html: String): List<ReaderParagraph> {
        if (html.isBlank()) return emptyList()
        val body = try {
            Jsoup.parseBodyFragment(html).body()
        } catch (e: Exception) {
            return emptyList()
        }
        val paragraphs = mutableListOf<ReaderParagraph>()
        walk(body, paragraphs)
        return promoteHeadingSubtitles(paragraphs)
    }

    /**
     * A "CHAPTER ONE" heading is usually followed immediately by a short, unpunctuated subtitle
     * paragraph ("The Boy Who Lived") that should render at the same larger/bold heading style
     * instead of looking like ordinary body text. Only considered near the top of the chapter.
     */
    private fun promoteHeadingSubtitles(paragraphs: List<ReaderParagraph>): List<ReaderParagraph> {
        return paragraphs.mapIndexed { index, paragraph ->
            val previous = paragraphs.getOrNull(index - 1)
            val text = paragraph.plainText
            if (!paragraph.isHeading &&
                index in 1..2 &&
                previous?.isHeading == true &&
                looksLikeHeadingText(previous.plainText) &&
                text.isNotBlank() &&
                text.length <= 80 &&
                !text.endsWith(".")
            ) {
                paragraph.copy(isHeading = true)
            } else {
                paragraph
            }
        }
    }

    private fun walk(el: Element, out: MutableList<ReaderParagraph>) {
        for (child in el.children()) {
            val tag = child.tagName().lowercase()
            when {
                tag in BLOCK_TAGS -> {
                    val sentences = extractSentences(child)
                    if (sentences.isNotEmpty()) {
                        val isHeadingTag = tag.length == 2 && tag[0] == 'h' && tag[1].isDigit()
                        out.add(ReaderParagraph(sentences, isHeading = isHeadingTag || looksLikeHeadingText(sentences.joinToString(" ") { it.text })))
                    }
                }
                tag in CONTAINER_TAGS -> walk(child, out)
                else -> walk(child, out)
            }
        }
    }

    private fun extractSentences(block: Element): List<ReaderSentence> {
        val idSpans = block.select("span[id]")
        if (idSpans.isEmpty()) {
            val text = block.text().trim()
            return if (text.isEmpty()) emptyList() else listOf(ReaderSentence(id = null, text = text))
        }
        return idSpans.mapNotNull { span ->
            val text = span.text().trim()
            if (text.isEmpty()) return@mapNotNull null
            ReaderSentence(
                id = span.id(),
                text = text,
                bold = looksEmphasized(span, "bold", "b", "strong"),
                italic = looksEmphasized(span, "italic", "i", "em")
            )
        }
    }

    private fun looksEmphasized(span: Element, className: String, vararg tags: String): Boolean {
        if (span.hasClass(className)) return true
        if (span.select(tags.joinToString(",")).isNotEmpty()) return true
        for (cls in span.classNames()) {
            if (cls.contains(className, ignoreCase = true)) return true
        }
        val style = span.attr("style")
        return when (className) {
            "bold" -> style.contains("font-weight:bold", ignoreCase = true) || style.contains("font-weight: bold", ignoreCase = true)
            "italic" -> style.contains("font-style:italic", ignoreCase = true) || style.contains("font-style: italic", ignoreCase = true)
            else -> false
        }
    }

    private val HEADING_KEYWORDS = Regex(
        "^[\\s\\p{Pd}—:]*(chapter|part|book|prologue|epilogue|introduction|afterword|foreword|preface|volume|interlude)\\b",
        RegexOption.IGNORE_CASE
    )

    private fun looksLikeHeadingText(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.length > 80) return false
        return HEADING_KEYWORDS.containsMatchIn(t)
    }

    private fun cleanHeadingText(s: String): String {
        var t = s.trim().trim('—', '-', '\u2013', '\u2014', ' ', ':')
        if (t.isNotEmpty() && t == t.uppercase() && t.any { it.isLetter() }) {
            t = t.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
        }
        return t.trim()
    }

    /**
     * Derives a human display title for a chapter from its actual body content (e.g. "Chapter One
     * — The Boy Who Lived"), independent of the EPUB's table of contents (which readaloud EPUBs
     * frequently lack). Returns null if no heading-like text is found near the top of the chapter.
     */
    fun extractChapterTitle(paragraphs: List<ReaderParagraph>): String? {
        val texts = paragraphs.take(8).map { it.plainText }.filter { it.isNotBlank() }
        if (texts.isEmpty()) return null
        val firstIdx = texts.indexOfFirst { looksLikeHeadingText(it) }
        if (firstIdx == -1) return null
        var title = cleanHeadingText(texts[firstIdx])
        if (title.isBlank()) return null
        val next = texts.getOrNull(firstIdx + 1)
        if (next != null && next.length in 1..80 && !looksLikeHeadingText(next) && !next.trim().endsWith(".")) {
            val cleanedNext = cleanHeadingText(next)
            if (cleanedNext.isNotBlank()) title = "$title — $cleanedNext"
        }
        return title
    }
}
