package com.pekempy.ReadAloudbooks.util

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateChecker {
    private val client = OkHttpClient()
    private val gson = Gson()
    
    data class GitHubRelease(
        val tag_name: String,
        val html_url: String,
        val prerelease: Boolean
    )

    suspend fun checkForUpdate(currentVersion: String): GitHubRelease? {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("UpdateChecker", "Checking for update. Current version: $currentVersion")
                val request = Request.Builder()
                    .url("https://api.github.com/repos/pekempy/ReadaloudBooks/releases/latest")
                    .header("User-Agent", "ReadAloudBooks-Android")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        android.util.Log.w("UpdateChecker", "GitHub API request failed: ${response.code}")
                        return@withContext null
                    }
                    val body = response.body?.string() ?: return@withContext null
                    
                    val release = gson.fromJson(body, GitHubRelease::class.java)
                    android.util.Log.d("UpdateChecker", "Latest remote version: ${release.tag_name}")
                    
                    if (isNewer(release.tag_name, currentVersion)) {
                        android.util.Log.i("UpdateChecker", "New version available: ${release.tag_name}")
                        release
                    } else {
                        android.util.Log.d("UpdateChecker", "App is up to date")
                        null
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UpdateChecker", "Error checking for update", e)
                null
            }
        }
    }

    fun isNewer(remoteTag: String, localVersion: String): Boolean {
        if (remoteTag == localVersion) return false
        
        val remote = remoteTag.removePrefix("v").trim()
        val local = localVersion.removePrefix("v").trim()

        val remoteBase = remote.split("-")[0]
        val localBase = local.split("-")[0]

        val remoteParts = remoteBase.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = localBase.split(".").mapNotNull { it.toIntOrNull() }
        
        val length = maxOf(remoteParts.size, localParts.size)
        
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        
        return remote != local && remoteBase == localBase
    }

    fun isInstalledFromPlayStore(context: android.content.Context): Boolean {
        return try {
            val packageName = context.packageName
            val packageManager = context.packageManager
            val installerPackageName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            }
            installerPackageName == "com.android.vending"
        } catch (e: Exception) {
            false
        }
    }
}
