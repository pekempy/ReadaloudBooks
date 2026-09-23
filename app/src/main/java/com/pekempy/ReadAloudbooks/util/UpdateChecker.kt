package com.pekempy.ReadAloudbooks.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pekempy.ReadAloudbooks.BuildConfig
import com.pekempy.ReadAloudbooks.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * GitHub update checker
 * Checks for new releases and prompts user to download
 */

data class UpdateInfo(
    val latestVersion: String,
    val currentVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val isUpdateAvailable: Boolean
)

object UpdateChecker {
    private const val GITHUB_REPO = "pekempy/ReadaloudBooks"
    private const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    
    /**
     * Check for updates from GitHub releases
     */
    suspend fun checkForUpdates(): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val response = URL(GITHUB_API_URL).readText()
                val json = JSONObject(response)
                
                val latestVersion = json.getString("tag_name").removePrefix("v")
                val currentVersion = BuildConfig.VERSION_NAME
                val downloadUrl = json.getJSONArray("assets")
                    .let { assets ->
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.getString("name")
                            if (name.endsWith(".apk")) {
                                return@let asset.getString("browser_download_url")
                            }
                        }
                        json.getString("html_url") // Fallback to release page
                    }
                
                val releaseNotes = json.optString("body", "No release notes available")
                
                UpdateInfo(
                    latestVersion = latestVersion,
                    currentVersion = currentVersion,
                    downloadUrl = downloadUrl,
                    releaseNotes = releaseNotes,
                    isUpdateAvailable = isNewerVersion(latestVersion, currentVersion)
                )
            } catch (e: Exception) {
                null // Silently fail - don't interrupt user experience
            }
        }
    }
    
    /**
     * Compare version strings (semantic versioning)
     */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        return try {
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            
            for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
                val latestPart = latestParts.getOrNull(i) ?: 0
                val currentPart = currentParts.getOrNull(i) ?: 0
                
                when {
                    latestPart > currentPart -> return true
                    latestPart < currentPart -> return false
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}

@Composable
fun UpdatePromptDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onSkip: () -> Unit
) {
    val haptic = rememberHaptic()
    val context = LocalContext.current
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with icon
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Column {
                        Text(
                            text = "Update Available",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "v${updateInfo.latestVersion}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                // Version info
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Current",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "v${updateInfo.currentVersion}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_forward),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Column {
                            Text(
                                text = "Latest",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "v${updateInfo.latestVersion}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                // Release notes
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "What's New",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = updateInfo.releaseNotes.take(300) + 
                                    if (updateInfo.releaseNotes.length > 300) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                                maxLines = 6
                            )
                        }
                    }
                }
                
                HorizontalDivider()
                
                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            haptic(HapticFeedback.FeedbackType.LIGHT)
                            onSkip()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Skip")
                    }
                    
                    Button(
                        onClick = {
                            haptic(HapticFeedback.FeedbackType.MEDIUM)
                            // Open download URL
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl))
                            context.startActivity(intent)
                            onDownload()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Download")
                    }
                }
            }
        }
    }
}

/**
 * Composable to check for updates on launch
 */
@Composable
fun LaunchUpdateChecker(
    onUpdateAvailable: (UpdateInfo) -> Unit = {}
) {
    var hasChecked by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        if (!hasChecked) {
            hasChecked = true
            UpdateChecker.checkForUpdates()?.let { updateInfo ->
                if (updateInfo.isUpdateAvailable) {
                    onUpdateAvailable(updateInfo)
                }
            }
        }
    }
}

/**
 * Example usage in MainActivity:
 * 
 * var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
 * 
 * LaunchUpdateChecker { info ->
 *     updateInfo = info
 * }
 * 
 * updateInfo?.let { info ->
 *     UpdatePromptDialog(
 *         updateInfo = info,
 *         onDismiss = { updateInfo = null },
 *         onDownload = { updateInfo = null },
 *         onSkip = { updateInfo = null }
 *     )
 * }
 */
