package com.pekempy.ReadAloudbooks.ui.settings

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.pekempy.ReadAloudbooks.util.HapticFeedback
import com.pekempy.ReadAloudbooks.util.rememberHaptic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Advanced settings architecture with categories, search, and backup
 * Premium customization for power users
 */

sealed class SettingItem {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String?
    abstract val category: SettingCategory
    abstract val icon: Int?
    
    data class Toggle(
        override val id: String,
        override val title: String,
        override val subtitle: String? = null,
        override val category: SettingCategory,
        override val icon: Int? = null,
        val value: Boolean,
        val onChanged: (Boolean) -> Unit
    ) : SettingItem()
    
    data class Choice(
        override val id: String,
        override val title: String,
        override val subtitle: String? = null,
        override val category: SettingCategory,
        override val icon: Int? = null,
        val options: List<String>,
        val selectedIndex: Int,
        val onSelected: (Int) -> Unit
    ) : SettingItem()
    
    data class Slider(
        override val id: String,
        override val title: String,
        override val subtitle: String? = null,
        override val category: SettingCategory,
        override val icon: Int? = null,
        val value: Float,
        val range: ClosedFloatingPointRange<Float>,
        val steps: Int = 0,
        val valueLabel: (Float) -> String = { it.toString() },
        val onChanged: (Float) -> Unit
    ) : SettingItem()
    
    data class Action(
        override val id: String,
        override val title: String,
        override val subtitle: String? = null,
        override val category: SettingCategory,
        override val icon: Int? = null,
        val onClick: () -> Unit
    ) : SettingItem()
}

enum class SettingCategory(val displayName: String, val icon: Int) {
    APPEARANCE("Appearance", R.drawable.ic_palette),
    PLAYBACK("Playback", R.drawable.ic_play_arrow),
    DOWNLOADS("Downloads", R.drawable.ic_download),
    ORGANIZATION("Organization", R.drawable.ic_folder),
    ADVANCED("Advanced", R.drawable.ic_settings),
    ABOUT("About", R.drawable.ic_info)
}

class SettingsManager {
    private val settings = MutableStateFlow<List<SettingItem>>(emptyList())
    
    fun registerSettings(items: List<SettingItem>) {
        settings.value = items
    }
    
    fun getSettings(): Flow<List<SettingItem>> = settings
    
    fun search(query: String): List<SettingItem> {
        if (query.isBlank()) return settings.value
        
        val lowerQuery = query.lowercase()
        return settings.value.filter {
            it.title.lowercase().contains(lowerQuery) ||
            it.subtitle?.lowercase()?.contains(lowerQuery) == true ||
            it.category.displayName.lowercase().contains(lowerQuery)
        }
    }
    
    fun exportSettings(): Map<String, Any> {
        return settings.value.associate { setting ->
            val value: Any = when (setting) {
                is SettingItem.Toggle -> setting.value
                is SettingItem.Choice -> setting.selectedIndex
                is SettingItem.Slider -> setting.value
                is SettingItem.Action -> ""
            }
            setting.id to value
        }
    }
    
    fun importSettings(data: Map<String, Any>) {
        // Apply imported values to settings
        // This would need coordination with the actual preference storage
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    val haptic = rememberHaptic()
    var searchQuery by remember { mutableStateOf("") }
    val allSettings by settingsManager.getSettings().collectAsState(emptyList())
    
    val displayedSettings = remember(searchQuery, allSettings) {
        if (searchQuery.isBlank()) allSettings else settingsManager.search(searchQuery)
    }
    
    val groupedSettings = remember(displayedSettings) {
        displayedSettings.groupBy { it.category }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic(HapticFeedback.FeedbackType.LIGHT)
                        onBack()
                    }) {
                        Icon(painterResource(R.drawable.ic_arrow_back), "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        haptic(HapticFeedback.FeedbackType.MEDIUM)
                        // Export settings
                    }) {
                        Icon(painterResource(R.drawable.ic_download), "Export")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search settings...") },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_search), null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            haptic(HapticFeedback.FeedbackType.LIGHT)
                            searchQuery = ""
                        }) {
                            Icon(painterResource(R.drawable.ic_close), "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
            
            // Settings list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                groupedSettings.forEach { (category, settings) ->
                    item {
                        SettingCategoryHeader(category)
                    }
                    
                    items(settings) { setting ->
                        SettingItemView(setting, haptic)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingCategoryHeader(category: SettingCategory) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(category.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = category.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingItemView(
    setting: SettingItem,
    haptic: (HapticFeedback.FeedbackType) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        when (setting) {
            is SettingItem.Toggle -> ToggleSettingView(setting, haptic)
            is SettingItem.Choice -> ChoiceSettingView(setting, haptic)
            is SettingItem.Slider -> SliderSettingView(setting, haptic)
            is SettingItem.Action -> ActionSettingView(setting, haptic)
        }
    }
}

@Composable
private fun ToggleSettingView(
    setting: SettingItem.Toggle,
    haptic: (HapticFeedback.FeedbackType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic(HapticFeedback.FeedbackType.LIGHT)
                setting.onChanged(!setting.value)
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        setting.icon?.let {
            Icon(
                painter = painterResource(it),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Switch(
            checked = setting.value,
            onCheckedChange = {
                haptic(HapticFeedback.FeedbackType.MEDIUM)
                setting.onChanged(it)
            }
        )
    }
}

@Composable
private fun ChoiceSettingView(
    setting: SettingItem.Choice,
    haptic: (HapticFeedback.FeedbackType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic(HapticFeedback.FeedbackType.LIGHT)
                expanded = true
            }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            setting.icon?.let {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = setting.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = setting.subtitle ?: setting.options[setting.selectedIndex],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            setting.options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        haptic(HapticFeedback.FeedbackType.MEDIUM)
                        setting.onSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SliderSettingView(
    setting: SettingItem.Slider,
    haptic: (HapticFeedback.FeedbackType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            setting.icon?.let {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Text(
                text = setting.valueLabel(setting.value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Slider(
            value = setting.value,
            onValueChange = {
                haptic(HapticFeedback.FeedbackType.LIGHT)
                setting.onChanged(it)
            },
            valueRange = setting.range,
            steps = setting.steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ActionSettingView(
    setting: SettingItem.Action,
    haptic: (HapticFeedback.FeedbackType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic(HapticFeedback.FeedbackType.MEDIUM)
                setting.onClick()
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        setting.icon?.let {
            Icon(
                painter = painterResource(it),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Icon(
            painter = painterResource(R.drawable.ic_keyboard_arrow_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
