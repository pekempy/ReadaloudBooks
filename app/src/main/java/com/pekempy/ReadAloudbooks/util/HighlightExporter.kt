package com.pekempy.ReadAloudbooks.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.pekempy.ReadAloudbooks.data.Book
import com.pekempy.ReadAloudbooks.data.local.entities.Highlight
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class HighlightExporter {

    fun exportToMarkdown(
        book: Book,
        highlights: List<Highlight>,
        chapterTitles: Map<Int, String> = emptyMap()
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val exportDate = dateFormat.format(Date())

        val builder = StringBuilder()

        // Header
        builder.appendLine("# ${book.title}")
        builder.appendLine()
        builder.appendLine("**Author:** ${book.author}")
        if (book.narrator != null) {
            builder.appendLine("**Narrator:** ${book.narrator}")
        }
        builder.appendLine("**Exported:** $exportDate")
        builder.appendLine()
        builder.appendLine("---")
        builder.appendLine()

        // Group highlights by chapter
        val highlightsByChapter = highlights.groupBy { it.chapterIndex }.toSortedMap()

        for ((chapterIndex, chapterHighlights) in highlightsByChapter) {
            val chapterTitle = chapterTitles[chapterIndex] ?: "Chapter ${chapterIndex + 1}"
            builder.appendLine("## $chapterTitle")
            builder.appendLine()

            for (highlight in chapterHighlights.sortedBy { it.timestamp }) {
                // Quote
                builder.appendLine("> ${highlight.text}")
                builder.appendLine()

                // Note if exists
                if (!highlight.note.isNullOrBlank()) {
                    builder.appendLine("**Note:** ${highlight.note}")
                    builder.appendLine()
                }

                // Metadata
                val highlightDate = dateFormat.format(Date(highlight.timestamp))
                builder.appendLine("*Highlighted on $highlightDate*")
                builder.appendLine()
                builder.appendLine("---")
                builder.appendLine()
            }
        }

        return builder.toString()
    }

    fun exportToCsv(
        book: Book,
        highlights: List<Highlight>,
        chapterTitles: Map<Int, String> = emptyMap()
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val builder = StringBuilder()

        // CSV Header
        builder.appendLine("Book Title,Author,Chapter,Chapter Title,Highlighted Text,Note,Color,Date")

        for (highlight in highlights.sortedWith(compareBy({ it.chapterIndex }, { it.timestamp }))) {
            val chapterTitle = chapterTitles[highlight.chapterIndex] ?: "Chapter ${highlight.chapterIndex + 1}"
            val date = dateFormat.format(Date(highlight.timestamp))

            // Escape quotes and commas in CSV
            val escapedText = "\"${highlight.text.replace("\"", "\"\"")}\""
            val escapedNote = if (highlight.note != null) "\"${highlight.note.replace("\"", "\"\"")}\"" else "\"\""
            val escapedBookTitle = "\"${book.title.replace("\"", "\"\"")}\""
            val escapedAuthor = "\"${book.author.replace("\"", "\"\"")}\""
            val escapedChapterTitle = "\"${chapterTitle.replace("\"", "\"\"")}\""

            builder.appendLine(
                "$escapedBookTitle,$escapedAuthor,${highlight.chapterIndex + 1},$escapedChapterTitle,$escapedText,$escapedNote,${highlight.color},$date"
            )
        }

        return builder.toString()
    }

    suspend fun saveAndShareMarkdown(
        context: Context,
        book: Book,
        highlights: List<Highlight>,
        chapterTitles: Map<Int, String> = emptyMap()
    ) {
        val markdown = exportToMarkdown(book, highlights, chapterTitles)
        val fileName = "${book.title.replace("[^a-zA-Z0-9]".toRegex(), "_")}_highlights.md"

        val file = File(context.cacheDir, fileName)
        file.writeText(markdown)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Highlights from ${book.title}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Export Highlights"))
    }

    suspend fun saveAndShareCsv(
        context: Context,
        book: Book,
        highlights: List<Highlight>,
        chapterTitles: Map<Int, String> = emptyMap()
    ) {
        val csv = exportToCsv(book, highlights, chapterTitles)
        val fileName = "${book.title.replace("[^a-zA-Z0-9]".toRegex(), "_")}_highlights.csv"

        val file = File(context.cacheDir, fileName)
        file.writeText(csv)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Highlights from ${book.title}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Export Highlights"))
    }
}
