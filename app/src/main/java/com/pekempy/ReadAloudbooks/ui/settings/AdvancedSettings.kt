@file:OptIn(ExperimentalMaterial3Api::class)

package com.pekempy.ReadAloudbooks.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun AdvancedSettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Advanced Settings") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp)
        ) {
            // Sync Settings Section
            item {
                SettingsCategoryHeader("Sync Settings")
            }
            item {
                SwitchPreference(
                    title = "Sync on WiFi Only",
                    subtitle = "Only sync when connected to WiFi",
                    checked = viewModel.syncWifiOnly,
                    onCheckedChange = { viewModel.updateSyncWifiOnly(it) }
                )
            }
            item {
                SwitchPreference(
                    title = "Auto-Sync Progress",
                    subtitle = "Automatically sync reading progress",
                    checked = viewModel.autoSyncProgress,
                    onCheckedChange = { viewModel.updateAutoSyncProgress(it) }
                )
            }
            item {
                SwitchPreference(
                    title = "Background Sync",
                    subtitle = "Allow syncing in background",
                    checked = viewModel.backgroundSyncEnabled,
                    onCheckedChange = { viewModel.updateBackgroundSyncEnabled(it) }
                )
            }

            // Download Settings Section
            item {
                SettingsCategoryHeader("Download Settings")
            }
            item {
                ListPreference(
                    title = "Download Quality",
                    subtitle = "Current: ${viewModel.downloadQuality}",
                    options = listOf("high", "medium", "low"),
                    selectedOption = viewModel.downloadQuality,
                    onOptionSelected = { viewModel.updateDownloadQuality(it) }
                )
            }
            item {
                SwitchPreference(
                    title = "Auto-Download New Series",
                    subtitle = "Automatically download books from followed series",
                    checked = viewModel.autoDownloadNewSeries,
                    onCheckedChange = { viewModel.updateAutoDownloadNewSeries(it) }
                )
            }
            item {
                SliderPreference(
                    title = "Cache Size Limit",
                    subtitle = "${viewModel.cacheLimitMb} MB",
                    value = viewModel.cacheLimitMb.toFloat(),
                    onValueChange = { viewModel.updateCacheLimit(it.toInt()) },
                    valueRange = 100f..5000f,
                    steps = 19
                )
            }

            // Playback Settings Section
            item {
                SettingsCategoryHeader("Playback Settings")
            }
            item {
                SwitchPreference(
                    title = "Auto-Play Next Chapter",
                    subtitle = "Automatically play next chapter when current finishes",
                    checked = viewModel.autoPlayNextChapter,
                    onCheckedChange = { viewModel.updateAutoPlayNextChapter(it) }
                )
            }
            item {
                SliderPreference(
                    title = "Remember Position Threshold",
                    subtitle = "${viewModel.rememberPositionThreshold} seconds",
                    value = viewModel.rememberPositionThreshold.toFloat(),
                    onValueChange = { viewModel.updateRememberPositionThreshold(it.toInt()) },
                    valueRange = 5f..300f,
                    steps = 59
                )
            }
            item {
                SwitchPreference(
                    title = "Skip Silence",
                    subtitle = "Skip silent sections during playback",
                    checked = viewModel.skipSilence,
                    onCheckedChange = { viewModel.updateSkipSilence(it) }
                )
            }

            // Reader Settings Section
            item {
                SettingsCategoryHeader("Reader Settings")
            }
            item {
                SliderPreference(
                    title = "Auto-Scroll Speed",
                    subtitle = "${viewModel.autoScrollSpeed} pixels/sec",
                    value = viewModel.autoScrollSpeed.toFloat(),
                    onValueChange = { viewModel.updateAutoScrollSpeed(it.toInt()) },
                    valueRange = 10f..200f,
                    steps = 38
                )
            }
            item {
                SwitchPreference(
                    title = "Page Turn Animation",
                    subtitle = "Show animation when turning pages",
                    checked = viewModel.pageTurnAnimation,
                    onCheckedChange = { viewModel.updatePageTurnAnimation(it) }
                )
            }
            item {
                SwitchPreference(
                    title = "Brightness Override",
                    subtitle = "Use custom brightness level",
                    checked = viewModel.brightnessOverride,
                    onCheckedChange = { viewModel.updateBrightnessOverride(it) }
                )
            }
            if (viewModel.brightnessOverride) {
                item {
                    SliderPreference(
                        title = "Brightness Level",
                        subtitle = "${(viewModel.brightnessLevel * 100).toInt()}%",
                        value = viewModel.brightnessLevel,
                        onValueChange = { viewModel.updateBrightnessLevel(it) },
                        valueRange = 0.1f..1.0f,
                        steps = 8
                    )
                }
            }

            // Advanced Options Section
            item {
                SettingsCategoryHeader("Advanced Options")
            }
            item {
                SwitchPreference(
                    title = "Developer Mode",
                    subtitle = "Access developer-only features",
                    checked = viewModel.developerMode,
                    onCheckedChange = { viewModel.updateDeveloperMode(it) }
                )
            }
            item {
                SwitchPreference(
                    title = "Export Logs",
                    subtitle = "Allow exporting debug logs",
                    checked = viewModel.exportLogsEnabled,
                    onCheckedChange = { viewModel.updateExportLogsEnabled(it) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    onClick = { viewModel.clearAllCaches() }
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Caches")
                }
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    onClick = { viewModel.resetToDefaults() }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset to Defaults")
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun SwitchPreference(
    title: String,
    subtitle: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = { onCheckedChange(it) }
            )
        }
    }
}

@Composable
fun ListPreference(
    title: String,
    subtitle: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = title) },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOptionSelected(option)
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (option == selectedOption),
                                onClick = {
                                    onOptionSelected(option)
                                    showDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = option, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SliderPreference(
    title: String,
    subtitle: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = { onValueChange(it) },
            valueRange = valueRange,
            steps = steps
        )
    }
}
