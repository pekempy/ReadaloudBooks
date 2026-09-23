package com.pekempy.ReadAloudbooks.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pekempy.ReadAloudbooks.R

enum class PageLayout(val displayName: String, val description: String) {
    GRID("Grid", "Books in a grid layout"),
    LIST("List", "Detailed list view"),
    COMPACT("Compact", "Dense list view"),
    CARDS("Cards", "Large cards with details")
}

data class PageLayoutConfig(
    val libraryLayout: PageLayout = PageLayout.GRID,
    val downloadsLayout: PageLayout = PageLayout.LIST,
    val searchLayout: PageLayout = PageLayout.GRID,
    val seriesLayout: PageLayout = PageLayout.LIST
)

@Composable
fun LayoutSelectorDialog(
    currentLayout: PageLayout,
    onLayoutSelected: (PageLayout) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Layout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PageLayout.values().forEach { layout ->
                    LayoutOption(
                        layout = layout,
                        selected = layout == currentLayout,
                        onClick = {
                            onLayoutSelected(layout)
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun LayoutOption(
    layout: PageLayout,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(12.dp)
                    )
                } else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = layout.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = layout.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun PerPageLayoutSettings(
    config: PageLayoutConfig,
    onConfigChange: (PageLayoutConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Page Layouts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        PageLayoutSetting(
            pageName = "Library",
            currentLayout = config.libraryLayout,
            onLayoutChange = { onConfigChange(config.copy(libraryLayout = it)) }
        )
        
        PageLayoutSetting(
            pageName = "Downloads",
            currentLayout = config.downloadsLayout,
            onLayoutChange = { onConfigChange(config.copy(downloadsLayout = it)) }
        )
        
        PageLayoutSetting(
            pageName = "Search",
            currentLayout = config.searchLayout,
            onLayoutChange = { onConfigChange(config.copy(searchLayout = it)) }
        )
        
        PageLayoutSetting(
            pageName = "Series",
            currentLayout = config.seriesLayout,
            onLayoutChange = { onConfigChange(config.copy(seriesLayout = it)) }
        )
    }
}

@Composable
private fun PageLayoutSetting(
    pageName: String,
    currentLayout: PageLayout,
    onLayoutChange: (PageLayout) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pageName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = currentLayout.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Icon(
                painter = painterResource(R.drawable.ic_arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    if (showDialog) {
        LayoutSelectorDialog(
            currentLayout = currentLayout,
            onLayoutSelected = onLayoutChange,
            onDismiss = { showDialog = false }
        )
    }
}
