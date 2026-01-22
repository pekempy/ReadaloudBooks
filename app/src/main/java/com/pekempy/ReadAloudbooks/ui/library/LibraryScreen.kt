package com.pekempy.ReadAloudbooks.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import com.pekempy.ReadAloudbooks.R
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pekempy.ReadAloudbooks.data.Book
import com.pekempy.ReadAloudbooks.util.DownloadUtils
import com.pekempy.ReadAloudbooks.ui.components.BookItem
import com.pekempy.ReadAloudbooks.ui.components.BookActionMenu
import com.pekempy.ReadAloudbooks.ui.components.HomeSection
import com.pekempy.ReadAloudbooks.ui.components.CategoryListItem

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onBookClick: (Book) -> Unit,
    onReadEbook: (Book) -> Unit,
    onPlayReadAloud: (Book) -> Unit,
    onPlayAudiobook: (Book) -> Unit,
    onSettingsClick: () -> Unit,
    onLogout: () -> Unit,
    onEditBook: (Book) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var isSearchMode by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    var selectedBookForMenu by remember { mutableStateOf<Book?>(null) }
    var selectedSeriesForMenu by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showSeriesMenu by remember { mutableStateOf(false) }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }

    LaunchedEffect(isSearchMode) {
        if (isSearchMode) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadBooks()
    }

    if (viewModel.selectedFilter != null || isSearchMode || viewModel.searchQuery.isNotEmpty()) {
        BackHandler {
            if (isSearchMode) {
                isSearchMode = false
                viewModel.onSearchQueryChange("")
            } else {
                viewModel.setViewMode(viewModel.currentViewMode)
            }
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isSearchMode) {
                    TextField(
                        value = viewModel.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        placeholder = { Text("Search...") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                        ),
                        singleLine = true,
                        leadingIcon = {
                            IconButton(onClick = { isSearchMode = false; viewModel.onSearchQueryChange("") }) {
                                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null)
                            }
                        },
                        trailingIcon = {
                             if (viewModel.searchQuery.isNotEmpty()) {
                                 IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                     Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear")
                                 }
                             }
                        }
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (viewModel.selectedFilter != null) {
                            IconButton(onClick = { viewModel.setViewMode(viewModel.currentViewMode) }) {
                                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null)
                            }
                            Text(
                                viewModel.selectedFilter!!,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.mipmap.ic_launcher_round),
                                contentDescription = "Logo",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                             Text(
                                when (viewModel.currentViewMode) {
                                    LibraryViewModel.ViewMode.Home -> "Home"
                                    LibraryViewModel.ViewMode.Library -> "My Library"
                                    LibraryViewModel.ViewMode.Authors -> "Authors"
                                    LibraryViewModel.ViewMode.Series -> "Series"
                                    LibraryViewModel.ViewMode.Downloads -> "Active Downloads"
                                    LibraryViewModel.ViewMode.Processing -> "Server Processing"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Row {
                        if (viewModel.currentViewMode != LibraryViewModel.ViewMode.Downloads && viewModel.currentViewMode != LibraryViewModel.ViewMode.Home) {
                            IconButton(onClick = { isSearchMode = true }) {
                                Icon(painterResource(R.drawable.ic_search), contentDescription = "Search", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(
                                    painterResource(R.drawable.ic_filter_alt),
                                    contentDescription = "Filter", 
                                    modifier = Modifier.size(20.dp),
                                    tint = if (viewModel.filterHasAudiobook || viewModel.filterHasEbook || viewModel.filterHasReadAloud || viewModel.filterDownloaded || viewModel.filterCanCreateReadAloud) 
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(painterResource(R.drawable.ic_sort), contentDescription = "Sort", modifier = Modifier.size(20.dp))
                            }
                            
                            DropdownMenu(
                                expanded = showFilterMenu,
                                onDismissRequest = { showFilterMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Audiobooks") },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_headphones), contentDescription = null) },
                                    trailingIcon = { if (viewModel.filterHasAudiobook) Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    onClick = { viewModel.toggleAudiobookFilter() }
                                )
                                DropdownMenuItem(
                                    text = { Text("eBooks") },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_book), contentDescription = null) },
                                    trailingIcon = { if (viewModel.filterHasEbook) Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    onClick = { viewModel.toggleEbookFilter() }
                                )
                                DropdownMenuItem(
                                    text = { Text("ReadAlouds") },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_menu_book), contentDescription = null) },
                                    trailingIcon = { if (viewModel.filterHasReadAloud) Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    onClick = { viewModel.toggleReadAloudFilter() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Downloaded") },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_download), contentDescription = null) },
                                    trailingIcon = { if (viewModel.filterDownloaded) Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    onClick = { viewModel.toggleDownloadedFilter() }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("Can create readaloud") },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                                    trailingIcon = { if (viewModel.filterCanCreateReadAloud) Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    onClick = { viewModel.toggleCanCreateReadAloudFilter() }
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                LibraryViewModel.SortOption.values().forEach { option ->
                                    val (label, iconRes) = when (option) {
                                        LibraryViewModel.SortOption.TitleAsc -> "Title (a-z)" to R.drawable.ic_sort_by_alpha
                                        LibraryViewModel.SortOption.TitleDesc -> "Title (z-a)" to R.drawable.ic_sort_by_alpha
                                        LibraryViewModel.SortOption.AuthorAsc -> "Author (a-z)" to R.drawable.ic_person
                                        LibraryViewModel.SortOption.AuthorDesc -> "Author (z-a)" to R.drawable.ic_person
                                        LibraryViewModel.SortOption.SeriesAsc -> "Series (a-z)" to R.drawable.ic_list
                                        LibraryViewModel.SortOption.SeriesDesc -> "Series (z-a)" to R.drawable.ic_list
                                        LibraryViewModel.SortOption.AddedAsc -> "Oldest First" to R.drawable.ic_history
                                        LibraryViewModel.SortOption.AddedDesc -> "Newest First" to R.drawable.ic_calendar_today
                                    }
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        leadingIcon = { Icon(painterResource(iconRes), contentDescription = null) },
                                        onClick = {
                                            viewModel.setSort(option)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        BookActionMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            book = selectedBookForMenu,
            onReadEbook = onReadEbook,
            onPlayReadAloud = onPlayReadAloud,
            onPlayAudiobook = onPlayAudiobook,
            onMarkFinished = { viewModel.deleteProgress(it.id) },
            onMarkUnread = { viewModel.deleteProgress(it.id) },
            onEdit = onEditBook,
            onRefreshMetadata = { viewModel.forceRefreshMetadata(it) },
            isLocalOnly = viewModel.isLocalOnly
        )



        if (bookToDelete != null) {
            AlertDialog(
                onDismissRequest = { bookToDelete = null },
                title = { Text("Cancel Download") },
                text = { Text("Are you sure you want to cancel the download for '${bookToDelete?.title}'? Any downloaded data will be deleted.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.cancelDownload(bookToDelete!!)
                            bookToDelete = null
                        }
                    ) {
                        Text("Cancel Download", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { bookToDelete = null }) {
                        Text("Keep Downloading")
                    }
                }
            )
        }



        if (viewModel.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
            ) {
                AnimatedContent(
                    targetState = viewModel.currentViewMode,
                    transitionSpec = {
                        if (targetState.ordinal > initialState.ordinal) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut())
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut())
                        }
                    },
                    label = "LibraryTransition"
                ) { targetMode ->
                    Column {
                        if (targetMode == LibraryViewModel.ViewMode.Home) {
                             val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
                             Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(
                                        PaddingValues(
                                            bottom = padding.calculateBottomPadding(),
                                            start = padding.calculateStartPadding(layoutDirection),
                                            end = padding.calculateEndPadding(layoutDirection)
                                        )
                                    )
                                     .padding(top = 8.dp, bottom = 8.dp)
                                     .verticalScroll(rememberScrollState())
                             ) {
                                if (viewModel.continueReadingBooks.isNotEmpty()) {
                                    HomeSection(
                                        title = "Continue Reading",
                                        books = viewModel.continueReadingBooks,
                                        onBookClick = onBookClick,
                                        onBookLongClick = { 
                                            selectedBookForMenu = it
                                            showMenu = true
                                        }
                                    )
                                    Spacer(Modifier.height(24.dp))
                                }

                                if (viewModel.downloadedBooks.isNotEmpty()) {
                                    HomeSection(
                                        title = "Ready to Read",
                                        books = viewModel.downloadedBooks,
                                        onBookClick = onBookClick,
                                        onBookLongClick = { 
                                            selectedBookForMenu = it
                                            showMenu = true
                                        }
                                    )
                                    Spacer(Modifier.height(24.dp))
                                }
                
                                if (viewModel.continueSeriesBooks.isNotEmpty()) {
                                    HomeSection(
                                        title = "Continue Series",
                                        books = viewModel.continueSeriesBooks,
                                        onBookClick = onBookClick,
                                        onBookLongClick = {
                                            selectedBookForMenu = it
                                            showMenu = true
                                        }
                                    )
                                }
                
                                if (viewModel.continueReadingBooks.isEmpty() && viewModel.continueSeriesBooks.isEmpty() && viewModel.downloadedBooks.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Your reading journey starts here. Open a book from the library to see it here!",
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        } else {
                        if (targetMode != LibraryViewModel.ViewMode.Downloads && targetMode != LibraryViewModel.ViewMode.Processing) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    FilterChip(
                                        selected = viewModel.filterHasAudiobook,
                                        onClick = { viewModel.toggleAudiobookFilter() },
                                        label = { Text("Audiobook") },
                                        leadingIcon = {
                                            Icon(
                                                painterResource(R.drawable.ic_headphones),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = viewModel.filterHasEbook,
                                        onClick = { viewModel.toggleEbookFilter() },
                                        label = { Text("eBook") },
                                        leadingIcon = {
                                            Icon(
                                                painterResource(R.drawable.ic_menu_book),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = viewModel.filterHasReadAloud,
                                        onClick = { viewModel.toggleReadAloudFilter() },
                                        label = { Text("ReadAloud") },
                                        leadingIcon = {
                                            Icon(
                                                painterResource(R.drawable.ic_menu_book),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = viewModel.filterDownloaded,
                                        onClick = { viewModel.toggleDownloadedFilter() },
                                        label = { Text("Downloaded") },
                                        leadingIcon = {
                                            Icon(
                                                painterResource(R.drawable.ic_download),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = viewModel.filterCanCreateReadAloud,
                                        onClick = { viewModel.toggleCanCreateReadAloudFilter() },
                                        label = { Text("Can Create ReadAloud") },
                                        leadingIcon = {
                                            Icon(
                                                painterResource(R.drawable.ic_add),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        val contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = padding.calculateBottomPadding() + 16.dp,
                            top = 8.dp
                        )

                        if (targetMode == LibraryViewModel.ViewMode.Library || viewModel.selectedFilter != null) {
                            val gridState = rememberLazyGridState()
                            
                            val totalItems = viewModel.books.size
                            LaunchedEffect(gridState.firstVisibleItemIndex) {
                                if (totalItems > 0 && gridState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                                    val lastVisibleItemIndex = gridState.layoutInfo.visibleItemsInfo.last().index
                                    if (lastVisibleItemIndex >= totalItems - 10) {
                                        viewModel.loadNextPage()
                                    }
                                }
                            }

                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(2),
                                contentPadding = contentPadding,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(viewModel.books) { book ->
                                    BookItem(
                                        book = book,
                                        downloadProgress = viewModel.downloadingBooks[book.id]?.progress,
                                        onClick = { onBookClick(book) },
                                        onLongClick = {
                                            selectedBookForMenu = book
                                            showMenu = true
                                        },
                                        onDownloadClick = if (viewModel.isLocalOnly) null else { { viewModel.downloadBook(book) } }
                                    )
                                }
                            }
                        } else if (targetMode == LibraryViewModel.ViewMode.Authors) {
                            val authors = viewModel.getUniqueAuthors()
                            val listState = rememberLazyListState()

                            LaunchedEffect(listState.firstVisibleItemIndex) {
                                if (authors.isNotEmpty() && listState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                                    val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.last().index
                                    if (lastVisibleIndex >= authors.size - 5) {
                                        viewModel.loadNextPage()
                                    }
                                }
                            }

                            LazyColumn(
                                state = listState,
                                contentPadding = contentPadding,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(authors) { author ->
                                    CategoryListItem(
                                        name = author,
                                        covers = viewModel.getCoversForAuthor(author),
                                        onClick = { viewModel.selectFilter(author) }
                                    )
                                }
                            }
                        } else if (targetMode == LibraryViewModel.ViewMode.Series) {
                            val series = viewModel.getUniqueSeries()
                            val listState = rememberLazyListState()

                            LaunchedEffect(listState.firstVisibleItemIndex) {
                                if (series.isNotEmpty() && listState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                                    val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.last().index
                                    if (lastVisibleIndex >= series.size - 5) {
                                        viewModel.loadNextPage()
                                    }
                                }
                            }

                            LazyColumn(
                                state = listState,
                                contentPadding = contentPadding,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(series) { s ->
                                    Box {
                                        CategoryListItem(
                                            name = s,
                                            covers = viewModel.getCoversForSeries(s),
                                            onClick = { viewModel.selectFilter(s) },
                                            onLongClick = { 
                                                selectedSeriesForMenu = s
                                                showSeriesMenu = true
                                            }
                                        )

                                        com.pekempy.ReadAloudbooks.ui.components.SeriesActionMenu(
                                            expanded = showSeriesMenu && selectedSeriesForMenu == s,
                                            onDismissRequest = { showSeriesMenu = false },
                                            seriesName = s,
                                            onDownloadSeries = { viewModel.downloadSeries(it) }
                                        )
                                    }
                                }
                            }
                        } else if (targetMode == LibraryViewModel.ViewMode.Downloads) {
                            val activeDownloads = com.pekempy.ReadAloudbooks.data.DownloadManager.activeDownloads
                            val downloadOrder = activeDownloads.map { it.book.id }
                            
                            val downloadingBooks = viewModel.books
                                .filter { viewModel.downloadingBooks.containsKey(it.id) }
                                .sortedBy { 
                                    val idx = downloadOrder.indexOf(it.id)
                                    if (idx == -1) Int.MAX_VALUE else idx
                                }
                            LazyColumn(
                                contentPadding = contentPadding,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(downloadingBooks) { book ->
                                    val status = viewModel.downloadingBooks[book.id]
                                    val progress = status?.progress ?: 0f
                                    val statusText = status?.statusText ?: ""
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = book.coverUrl,
                                                contentDescription = null,
                                                modifier = Modifier.size(60.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    book.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    book.author,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                LinearProgressIndicator(
                                                    progress = { progress },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                )
                                                Text(
                                                    if (statusText == "Queued") "Queued" else "${(progress * 100).toInt()}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.align(Alignment.End)
                                                )
                                            }
                                            IconButton(onClick = { bookToDelete = book }) {
                                                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Cancel Download", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (targetMode == LibraryViewModel.ViewMode.Processing) {
                            val processingState = rememberLazyListState()
                            
                            LaunchedEffect(processingState.firstVisibleItemIndex) {
                                if (viewModel.books.isNotEmpty() && processingState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                                    val lastVisible = processingState.layoutInfo.visibleItemsInfo.last().index
                                    if (lastVisible >= viewModel.books.size - 5) {
                                        viewModel.loadNextPage()
                                    }
                                }
                            }

                            LazyColumn(
                                state = processingState,
                                contentPadding = contentPadding,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(viewModel.books.sortedBy { it.queuePosition ?: Int.MAX_VALUE }) { book ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = book.coverUrl,
                                                contentDescription = null,
                                                modifier = Modifier.size(80.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    book.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    book.author,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                                
                                                Spacer(Modifier.height(12.dp))
                                                
                                                val progress = book.processingProgress ?: 0f
                                                val stage = book.currentProcessingStage ?: "Queued"
                                                val queuePos = book.queuePosition
                                                val isError = book.processingStatus == "ERROR"
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = if (isError) "Processing Error" else stage.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                                    )
                                                    
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        if (queuePos != null && queuePos > 0 && !isError) {
                                                            Text(
                                                                text = "Queue: #$queuePos",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.secondary
                                                            )
                                                            Spacer(Modifier.width(8.dp))
                                                        }

                                                        IconButton(
                                                            onClick = { viewModel.stopProcessing(book.id) },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                painterResource(R.drawable.ic_close),
                                                                contentDescription = "Stop",
                                                                modifier = Modifier.size(16.dp),
                                                                tint = MaterialTheme.colorScheme.error
                                                            )
                                                        }

                                                        if (isError) {
                                                            Spacer(Modifier.width(8.dp))
                                                            IconButton(
                                                                onClick = { viewModel.retryProcessing(book.id) },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(
                                                                    painterResource(R.drawable.ic_history),
                                                                    contentDescription = "Retry",
                                                                    modifier = Modifier.size(16.dp),
                                                                    tint = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                
                                                Spacer(Modifier.height(4.dp))
                                                
                                                LinearProgressIndicator(
                                                    progress = { progress },
                                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                    trackColor = (if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.1f)
                                                )
                                                
                                                Text(
                                                    text = "${(progress * 100).toInt()}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.align(Alignment.End),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}


