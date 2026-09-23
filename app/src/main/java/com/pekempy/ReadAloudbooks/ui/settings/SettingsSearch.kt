package com.pekempy.ReadAloudbooks.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pekempy.ReadAloudbooks.R

data class SettingCategory(val displayName: String)

data class SettingItem(
    val title: String,
    val subtitle: String? = null,
    val icon: Int? = null,
    val category: SettingCategory,
    val route: String
)

/**
 * Settings search with fuzzy matching
 */

@Composable
fun SettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search settings...") },
        leadingIcon = {
            Icon(painterResource(R.drawable.ic_search), contentDescription = "Search")
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(painterResource(R.drawable.ic_close), "Clear")
                }
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
        ),
        singleLine = true
    )
}

@Composable
fun SettingsSearchResults(
    results: List<SettingItem>,
    onSettingClick: (SettingItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(results) { setting ->
            SettingSearchResultCard(
                setting = setting,
                onClick = { onSettingClick(setting) }
            )
        }
        
        if (results.isEmpty()) {
            item {
                EmptySearchResults()
            }
        }
    }
}

@Composable
private fun SettingSearchResultCard(
    setting: SettingItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            setting.icon?.let {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = setting.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                
                setting.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Text(
                    text = setting.category.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun EmptySearchResults() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "No settings found",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = "Try a different search term",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Fuzzy search implementation
 */
fun searchSettings(query: String, settings: List<SettingItem>): List<SettingItem> {
    if (query.isBlank()) return emptyList()
    
    val lowerQuery = query.lowercase()
    return settings.filter { setting ->
        setting.title.lowercase().contains(lowerQuery) ||
        setting.subtitle?.lowercase()?.contains(lowerQuery) == true ||
        setting.category.displayName.lowercase().contains(lowerQuery) ||
        fuzzyMatch(lowerQuery, setting.title.lowercase())
    }.sortedByDescending { setting ->
        when {
            setting.title.lowercase().startsWith(lowerQuery) -> 3
            setting.title.lowercase().contains(lowerQuery) -> 2
            setting.subtitle?.lowercase()?.contains(lowerQuery) == true -> 1
            else -> 0
        }
    }
}

private fun fuzzyMatch(query: String, text: String): Boolean {
    var queryIndex = 0
    for (char in text) {
        if (queryIndex < query.length && char == query[queryIndex]) {
            queryIndex++
        }
    }
    return queryIndex == query.length
}
