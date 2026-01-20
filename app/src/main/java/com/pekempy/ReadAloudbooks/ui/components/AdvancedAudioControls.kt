package com.pekempy.ReadAloudbooks.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pekempy.ReadAloudbooks.data.UserSettings

/**
 * Advanced Audio Playback Controls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedAudioControlsSheet(
    userSettings: UserSettings,
    onSkipBackSecondsChange: (Int) -> Unit,
    onSkipForwardSecondsChange: (Int) -> Unit,
    onVolumeBoostChange: (Boolean, Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                "Audio Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Skip Back Interval
            AudioSettingSection(title = "Skip Back Interval") {
                val skipBackOptions = listOf(5, 10, 15, 30)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    skipBackOptions.forEach { seconds ->
                        IntervalButton(
                            label = "${seconds}s",
                            isSelected = userSettings.skipBackSeconds == seconds,
                            onClick = { onSkipBackSecondsChange(seconds) }
                        )
                    }
                }
            }

            // Skip Forward Interval
            AudioSettingSection(title = "Skip Forward Interval") {
                val skipForwardOptions = listOf(15, 30, 45, 60)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    skipForwardOptions.forEach { seconds ->
                        IntervalButton(
                            label = "${seconds}s",
                            isSelected = userSettings.skipForwardSeconds == seconds,
                            onClick = { onSkipForwardSecondsChange(seconds) }
                        )
                    }
                }
            }

            // Volume Boost
            AudioSettingSection(title = "Volume Boost") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Volume Boost")
                    Switch(
                        checked = userSettings.enableVolumeBoost,
                        onCheckedChange = { enabled ->
                            onVolumeBoostChange(enabled, userSettings.volumeBoostLevel)
                        }
                    )
                }

                if (userSettings.enableVolumeBoost) {
                    Slider(
                        value = userSettings.volumeBoostLevel,
                        onValueChange = { level ->
                            onVolumeBoostChange(true, level)
                        },
                        valueRange = 1.0f..2.0f,
                        steps = 9
                    )
                    Text(
                        "Boost Level: ${String.format("%.1f", userSettings.volumeBoostLevel)}x",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun AudioSettingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        content()
    }
}

@Composable
fun IntervalButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = if (isSelected) {
            ButtonDefaults.buttonColors()
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
        modifier = Modifier.width(70.dp),
        contentPadding = PaddingValues(8.dp)
    ) {
        Text(label)
    }
}

/**
 * Audio Bookmark Components
 */
@Composable
fun AudioBookmarkButton(
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Text("🎵", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun AudioBookmarkDialog(
    currentTimestamp: String,
    onSave: (label: String?, note: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Audio Bookmark") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Bookmark at: $currentTimestamp")

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (Optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    label.ifBlank { null },
                    note.ifBlank { null }
                )
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Enhanced Sleep Timer with presets
 */
@Composable
fun EnhancedSleepTimerDialog(
    currentMinutes: Int,
    finishChapter: Boolean,
    onSet: (minutes: Int, finishChapter: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMinutes by remember { mutableStateOf(currentMinutes) }
    var selectedFinishChapter by remember { mutableStateOf(finishChapter) }

    val presets = listOf(0, 5, 10, 15, 30, 45, 60, 90, 120)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Quick Presets", style = MaterialTheme.typography.labelLarge)

                // Preset buttons in 3 columns
                presets.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { minutes ->
                            Button(
                                onClick = { selectedMinutes = minutes },
                                modifier = Modifier.weight(1f),
                                colors = if (selectedMinutes == minutes) {
                                    ButtonDefaults.buttonColors()
                                } else {
                                    ButtonDefaults.outlinedButtonColors()
                                }
                            ) {
                                Text(
                                    if (minutes == 0) "Off" else "${minutes}m"
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Finish chapter option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Finish Chapter", fontWeight = FontWeight.Medium)
                        Text(
                            "Wait until chapter ends",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = selectedFinishChapter,
                        onCheckedChange = { selectedFinishChapter = it },
                        enabled = selectedMinutes > 0
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSet(selectedMinutes, selectedFinishChapter)
                onDismiss()
            }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Library View Mode Selector
 */
@Composable
fun LibraryViewModeSelector(
    currentMode: String,
    onModeChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ViewModeButton(
            icon = "▦",
            label = "Grid",
            isSelected = currentMode == "grid",
            onClick = { onModeChange("grid") }
        )
        ViewModeButton(
            icon = "☰",
            label = "List",
            isSelected = currentMode == "list",
            onClick = { onModeChange("list") }
        )
        ViewModeButton(
            icon = "▦",
            label = "Compact",
            isSelected = currentMode == "compact",
            onClick = { onModeChange("compact") }
        )
        ViewModeButton(
            icon = "▤",
            label = "Table",
            isSelected = currentMode == "table",
            onClick = { onModeChange("table") }
        )
    }
}

@Composable
fun ViewModeButton(
    icon: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.headlineSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
