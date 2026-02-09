package com.pekempy.ReadAloudbooks.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pekempy.ReadAloudbooks.data.Book

@Composable
fun HomeSection(
    title: String,
    books: List<Book>,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
    isOfflineMode: Boolean = false
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(books) { book ->
                BookItem(
                    book = book,
                    onClick = { onBookClick(book) },
                    onLongClick = { onBookLongClick(book) },
                    modifier = Modifier.width(150.dp),
                    isOfflineMode = isOfflineMode
                )
            }
        }
    }
}
