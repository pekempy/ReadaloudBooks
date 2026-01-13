package com.pekempy.ReadAloudbooks.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.pekempy.ReadAloudbooks.R
import com.pekempy.ReadAloudbooks.ui.library.LibraryViewModel

@Composable
fun AppNavigationBar(
    currentRoute: String?,
    onNavigateToLibrary: () -> Unit,
    currentViewMode: LibraryViewModel.ViewMode = LibraryViewModel.ViewMode.Home,
    onViewModeChange: (LibraryViewModel.ViewMode) -> Unit = {},
    hasDownloads: Boolean = false,
    downloadCount: Int = 0,
    hasProcessing: Boolean = false,
    processingCount: Int = 0
) {
    val isLibraryRoute = currentRoute?.startsWith("library") == true
    var showMoreMenu by remember { mutableStateOf(false) }
    
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        NavigationBarItem(
            icon = { Icon(painterResource(R.drawable.ic_shelves), contentDescription = null) },
            label = { Text("Shelf", maxLines = 1) },
            selected = currentViewMode == LibraryViewModel.ViewMode.Home,
            onClick = {
                onNavigateToLibrary()
                onViewModeChange(LibraryViewModel.ViewMode.Home)
            }
        )
        NavigationBarItem(
            icon = { Icon(painterResource(R.drawable.ic_book), contentDescription = null) },
            label = { Text("Books", maxLines = 1) },
            selected = currentViewMode == LibraryViewModel.ViewMode.Library,
            onClick = {
                onNavigateToLibrary()
                onViewModeChange(LibraryViewModel.ViewMode.Library)
            }
        )
        NavigationBarItem(
            icon = { Icon(painterResource(R.drawable.ic_person), contentDescription = null) },
            label = { Text("Authors", maxLines = 1) },
            selected = currentViewMode == LibraryViewModel.ViewMode.Authors,
            onClick = {
                onNavigateToLibrary()
                onViewModeChange(LibraryViewModel.ViewMode.Authors)
            }
        )
        NavigationBarItem(
            icon = { Icon(painterResource(R.drawable.ic_list), contentDescription = null) },
            label = { Text("Series", maxLines = 1) },
            selected = currentViewMode == LibraryViewModel.ViewMode.Series,
            onClick = {
                onNavigateToLibrary()
                onViewModeChange(LibraryViewModel.ViewMode.Series)
            }
        )
        
        if (hasProcessing && hasDownloads) {
            val totalCount = processingCount + downloadCount
            NavigationBarItem(
                icon = { 
                    Box {
                        BadgedBox(
                            badge = { Badge(modifier = Modifier.offset(x = 4.dp, y = (-2).dp)) { Text("$totalCount") } }
                        ) {
                            Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null) 
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            offset = DpOffset(0.dp, (-48).dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Processing ($processingCount)") },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_history), contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigateToLibrary()
                                    onViewModeChange(LibraryViewModel.ViewMode.Processing)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Downloads ($downloadCount)") },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigateToLibrary()
                                    onViewModeChange(LibraryViewModel.ViewMode.Downloads)
                                }
                            )
                        }
                    }
                },
                label = { Text("More", maxLines = 1) },
                selected = currentViewMode == LibraryViewModel.ViewMode.Processing || currentViewMode == LibraryViewModel.ViewMode.Downloads,
                onClick = { showMoreMenu = true }
            )
        } else if (hasProcessing) {
            NavigationBarItem(
                icon = { 
                    BadgedBox(
                        badge = { Badge(modifier = Modifier.offset(x = 4.dp, y = (-2).dp)) { Text("$processingCount") } }
                    ) {
                        Icon(painterResource(R.drawable.ic_history), contentDescription = null) 
                    }
                },
                label = { Text("Process", maxLines = 1) },
                selected = currentViewMode == LibraryViewModel.ViewMode.Processing,
                onClick = {
                    onNavigateToLibrary()
                    onViewModeChange(LibraryViewModel.ViewMode.Processing)
                }
            )
        } else if (hasDownloads) {
            NavigationBarItem(
                icon = { 
                    BadgedBox(
                        badge = { Badge(modifier = Modifier.offset(x = 4.dp, y = (-2).dp)) { Text("$downloadCount") } }
                    ) {
                        Icon(painterResource(R.drawable.ic_download), contentDescription = null) 
                    }
                },
                label = { Text("Downloads", maxLines = 1) }, 
                selected = currentViewMode == LibraryViewModel.ViewMode.Downloads,
                onClick = {
                    onNavigateToLibrary()
                    onViewModeChange(LibraryViewModel.ViewMode.Downloads)
                }
            )
        }
    }
}
