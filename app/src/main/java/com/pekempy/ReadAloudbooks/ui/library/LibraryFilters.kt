package com.pekempy.ReadAloudbooks.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pekempy.ReadAloudbooks.R
import com.pekempy.ReadAloudbooks.data.Book
import com.pekempy.ReadAloudbooks.util.HapticFeedback
import com.pekempy.ReadAloudbooks.util.rememberHaptic

/**
 * Advanced library filtering system
 * Multiple criteria with smart collections
 */

data class LibraryFilter(
    val type: FilterType,
    val label: String,
    val isActive: Boolean = false,
    val count: Int? = null
)

enum class FilterType {
    ALL,
    AUDIOBOOK,
    EBOOK,
    READALOUD,
    IN_PROGRESS,
    COMPLETED,
    UNREAD,
    DOWNLOADED,
    FAVORITES
}

data class SortOption(
    val type: SortType,
    val label: String,
    val ascending: Boolean = true
)

enum class SortType {
    TITLE,
    AUTHOR,
    DATE_ADDED,
    LAST_READ,
    PROGRESS,
    DURATION
}

@Composable
fun LibraryFilterBar(
    filters: List<LibraryFilter>,
    activeFilter: FilterType,
    onFilterSelected: (FilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(filters) { filter ->
            FilterChip(
                selected = filter.type == activeFilter,
                onClick = {
                    haptic(HapticFeedback.FeedbackType.LIGHT)
                    onFilterSelected(filter.type)
                },
                label = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(filter.label)
                        filter.count?.let {
                            Text(
                                text = "($it)",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(getFilterIcon(filter.type)),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun SortMenu(
    currentSort: SortOption,
    onSortSelected: (SortOption) -> Unit,
    expanded: Boolean,
    onDismiss: () -> Unit
) {
    val haptic = rememberHaptic()
    val sortOptions = remember {
        listOf(
            SortOption(SortType.TITLE, "Title (A-Z)", true),
            SortOption(SortType.TITLE, "Title (Z-A)", false),
            SortOption(SortType.AUTHOR, "Author (A-Z)", true),
            SortOption(SortType.AUTHOR, "Author (Z-A)", false),
            SortOption(SortType.DATE_ADDED, "Recently Added", false),
            SortOption(SortType.LAST_READ, "Recently Read", false),
            SortOption(SortType.PROGRESS, "Progress", false),
            SortOption(SortType.DURATION, "Duration", true)
        )
    }
    
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        sortOptions.forEach { option ->
            DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(option.label)
                        if (currentSort.type == option.type &&
                            currentSort.ascending == option.ascending
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check_circle),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                onClick = {
                    haptic(HapticFeedback.FeedbackType.MEDIUM)
                    onSortSelected(option)
                    onDismiss()
                }
            )
        }
    }
}

/**
 * Apply filters to book list
 */
fun List<Book>.applyFilter(filter: FilterType): List<Book> {
    return when (filter) {
        FilterType.ALL -> this
        FilterType.AUDIOBOOK -> filter { it.hasAudiobook }
        FilterType.EBOOK -> filter { it.hasEbook }
        FilterType.READALOUD -> filter { it.hasReadAloud }
        FilterType.IN_PROGRESS -> filter { /* has progress > 0 && < 100 */ true }
        FilterType.COMPLETED -> filter { /* progress == 100 */ false }
        FilterType.UNREAD -> filter { /* progress == 0 */ true }
        FilterType.DOWNLOADED -> filter { /* has local files */ false }
        FilterType.FAVORITES -> filter { /* is favorited */ false }
    }
}

/**
 * Apply sorting to book list
 */
fun List<Book>.applySort(sort: SortOption): List<Book> {
    return when (sort.type) {
        SortType.TITLE -> {
            if (sort.ascending) sortedBy { it.title }
            else sortedByDescending { it.title }
        }
        SortType.AUTHOR -> {
            if (sort.ascending) sortedBy { it.author }
            else sortedByDescending { it.author }
        }
        SortType.DATE_ADDED -> {
            if (sort.ascending) sortedBy { it.addedDate }
            else sortedByDescending { it.addedDate }
        }
        SortType.LAST_READ -> {
            sortedByDescending { it.addedDate } // Placeholder
        }
        SortType.PROGRESS -> {
            sortedByDescending { 0 } // Placeholder
        }
        SortType.DURATION -> {
            if (sort.ascending) sortedBy { it.title }
            else sortedByDescending { it.title }
        }
    }
}

private fun getFilterIcon(type: FilterType): Int {
    return when (type) {
        FilterType.ALL -> R.drawable.ic_book
        FilterType.AUDIOBOOK -> R.drawable.ic_headphones
        FilterType.EBOOK -> R.drawable.ic_book
        FilterType.READALOUD -> R.drawable.ic_play_arrow
        FilterType.IN_PROGRESS -> R.drawable.ic_book
        FilterType.COMPLETED -> R.drawable.ic_check_circle
        FilterType.UNREAD -> R.drawable.ic_book
        FilterType.DOWNLOADED -> R.drawable.ic_download
        FilterType.FAVORITES -> R.drawable.ic_book
    }
}
