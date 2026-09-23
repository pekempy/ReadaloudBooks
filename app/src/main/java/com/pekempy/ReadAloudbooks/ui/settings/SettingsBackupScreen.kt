package com.pekempy.ReadAloudbooks.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pekempy.ReadAloudbooks.data.BackupManager
import com.pekempy.ReadAloudbooks.data.UserPreferencesRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBackupScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { UserPreferencesRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var successExport by remember { mutableStateOf(false) }
    var successImport by remember { mutableStateOf(false) }
    
    // File picker for export (CreateDocument)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    isExporting = true
                    val jsonData = BackupManager.exportSettings(repository)
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonData.toByteArray())
                        outputStream.close()
                        exportMessage = "Settings exported successfully"
                        successExport = true
                    } ?: run {
                        exportMessage = "Failed to open output stream"
                        successExport = false
                    }
                } catch (e: Exception) {
                    exportMessage = "Export failed: ${e.message}"
                    successExport = false
                } finally {
                    isExporting = false
                }
            }
        }
    }
    
    // File picker for import (OpenDocument)
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    isImporting = true
                    val jsonData = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().readText()
                    } ?: run {
                        importMessage = "Failed to read file"
                        successImport = false
                        return@launch
                    }
                    
                    val result = BackupManager.importSettings(jsonData, repository)
                    result.onSuccess {
                        importMessage = "Settings imported successfully"
                        successImport = true
                    }.onFailure { e ->
                        importMessage = "Import failed: ${e.message}"
                        successImport = false
                    }
                } catch (e: Exception) {
                    importMessage = "Import failed: ${e.message}"
                    successImport = false
                } finally {
                    isImporting = false
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Description
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Backup & Restore Settings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Export all your app settings to a JSON file for backup or transfer to another device. Import settings from a previously exported file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Export Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Export Settings",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                "Save your settings to a file",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Button(
                            onClick = {
                                exportLauncher.launch("readaloud-settings-backup.json")
                            },
                            enabled = !isExporting && !isImporting,
                            modifier = Modifier.wrapContentWidth(Alignment.End)
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Export")
                            }
                        }
                    }
                    
                    if (exportMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (successExport) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                exportMessage!!,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (successExport)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            // Import Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Import Settings",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                "Restore from a backup file",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Button(
                            onClick = {
                                importLauncher.launch(arrayOf("application/json"))
                            },
                            enabled = !isImporting && !isExporting,
                            modifier = Modifier.wrapContentWidth(Alignment.End)
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Import")
                            }
                        }
                    }
                    
                    if (importMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (successImport) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                importMessage!!,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (successImport)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            // Info Section
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "What's included:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    BulletPoint("Theme mode and colors")
                    BulletPoint("Reader settings (font, size, theme)")
                    BulletPoint("Playback speed and audio settings")
                    BulletPoint("Sleep timer settings")
                    BulletPoint("Tab visibility preferences")
                    BulletPoint("Sync frequency settings")
                    BulletPoint("Ignored series list")
                }
            }
        }
    }
}

@Composable
private fun BulletPoint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("• ", style = MaterialTheme.typography.bodySmall)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
