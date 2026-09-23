package com.pekempy.ReadAloudbooks.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pekempy.ReadAloudbooks.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Settings backup and restore functionality
 */

object SettingsBackup {
    
    suspend fun exportSettings(
        context: Context,
        settingsManager: SettingsManager
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val settings = settingsManager.exportSettings()
            val json = JSONObject(settings)
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "readaloud_settings_$timestamp.json"
            val file = File(context.filesDir, fileName)
            
            file.writeText(json.toString(2))
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun importSettings(
        file: File,
        settingsManager: SettingsManager
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(file.readText())
            val settings = json.keys().asSequence().associateWith { key ->
                json.get(key)
            }
            
            settingsManager.importSettings(settings)
            Result.success(settings.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Composable
fun SettingsBackupDialog(
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings Backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Export your settings to backup or transfer to another device.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Divider()
                
                BackupOption(
                    title = "Export Settings",
                    subtitle = "Save all settings to a file",
                    icon = R.drawable.ic_download,
                    onClick = {
                        onExport()
                        onDismiss()
                    }
                )
                
                BackupOption(
                    title = "Import Settings",
                    subtitle = "Restore from a backup file",
                    icon = R.drawable.ic_folder,
                    onClick = {
                        onImport()
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun BackupOption(
    title: String,
    subtitle: String,
    icon: Int,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
