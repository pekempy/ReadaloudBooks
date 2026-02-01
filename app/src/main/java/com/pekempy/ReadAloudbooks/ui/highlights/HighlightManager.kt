package com.pekempy.ReadAloudbooks.ui.highlights

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pekempy.ReadAloudbooks.data.local.entities.Highlight
import com.pekempy.ReadAloudbooks.data.repository.HighlightRepository
import com.pekempy.ReadAloudbooks.data.Book
import com.pekempy.ReadAloudbooks.util.HighlightExporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing highlights in the reader
 */
class HighlightManagerViewModel(
    private val highlightRepository: HighlightRepository,
    private val bookId: String
) : ViewModel() {

    val highlights: Flow<List<Highlight>> = highlightRepository.getHighlightsForBook(bookId)

    var selectedHighlight by mutableStateOf<Highlight?>(null)
    var showHighlightDialog by mutableStateOf(false)
    var showColorPicker by mutableStateOf(false)
    var selectedColor by mutableStateOf(HighlightColors.YELLOW.hex)

    // For creating new highlights
    var pendingHighlight by mutableStateOf<PendingHighlight?>(null)

    data class PendingHighlight(
        val chapterIndex: Int,
        val elementId: String,
        val text: String,
        val startOffset: Int = 0,
        val endOffset: Int = 0
    )

    fun createHighlight(
        chapterIndex: Int,
        elementId: String,
        text: String,
        color: String = selectedColor,
        note: String? = null,
        startOffset: Int = 0,
        endOffset: Int = 0
    ) {
        viewModelScope.launch {
            val highlight = Highlight(
                bookId = bookId,
                chapterIndex = chapterIndex,
                elementId = elementId,
                text = text,
                color = color,
                note = note,
                startOffset = startOffset,
                endOffset = endOffset,
                timestamp = System.currentTimeMillis()
            )
            highlightRepository.addHighlight(highlight)
        }
    }

    fun updateHighlightNote(highlightId: Long, note: String) {
        viewModelScope.launch {
            val highlight = highlightRepository.getHighlightById(highlightId)
            if (highlight != null) {
                highlightRepository.updateHighlight(
                    highlight.copy(note = note)
                )
            }
        }
    }

    fun updateHighlightColor(highlightId: Long, color: String) {
        viewModelScope.launch {
            val highlight = highlightRepository.getHighlightById(highlightId)
            if (highlight != null) {
                highlightRepository.updateHighlight(
                    highlight.copy(color = color)
                )
            }
        }
    }

    fun deleteHighlight(highlight: Highlight) {
        viewModelScope.launch {
            highlightRepository.deleteHighlight(highlight)
        }
    }

    suspend fun exportHighlights(
        context: Context,
        book: Book,
        chapterTitles: Map<Int, String>,
        format: ExportFormat
    ) {
        val allHighlights = highlightRepository.getHighlightsForBook(bookId)
        // Collect highlights from Flow
        val exporter = HighlightExporter()

        // This would need to collect the flow first
        // For now, showing the structure
    }

    fun showHighlightOptions(highlight: Highlight) {
        selectedHighlight = highlight
        showHighlightDialog = true
    }

    fun dismissHighlightDialog() {
        showHighlightDialog = false
        selectedHighlight = null
    }

    enum class ExportFormat {
        MARKDOWN, CSV
    }
}

/**
 * Predefined highlight colors
 */
enum class HighlightColors(val hex: String, val displayName: String) {
    YELLOW("#FFEB3B", "Yellow"),
    GREEN("#4CAF50", "Green"),
    BLUE("#2196F3", "Blue"),
    PURPLE("#9C27B0", "Purple"),
    ORANGE("#FF9800", "Orange"),
    PINK("#E91E63", "Pink"),
    RED("#F44336", "Red")
}
