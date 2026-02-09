package com.pekempy.ReadAloudbooks.ui.settings

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

object BackupHelper {
    suspend fun backupFiles(
        context: Context,
        destinationUri: Uri,
        sourceDir: File,
        onProgress: (Float, String) -> Unit
    ): Result<String> {
        return try {
            val destRoot = DocumentFile.fromTreeUri(context, destinationUri)
            if (destRoot == null) {
                return Result.failure(Exception("Invalid destination"))
            }
            
            // Collect all files to copy
            val allFiles = mutableListOf<File>()
            fun collectFiles(dir: File) {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        collectFiles(file)
                    } else if (file.name.endsWith(".epub") || file.name.endsWith(".m4b")) {
                        allFiles.add(file)
                    }
                }
            }
            collectFiles(sourceDir)
            
            if (allFiles.isEmpty()) {
                return Result.success("No files to backup")
            }
            
            val totalFiles = allFiles.size
            var copiedFiles = 0
            var totalBytes = 0L
            
            allFiles.forEachIndexed { index, file ->
                onProgress((index + 1).toFloat() / totalFiles, "Backing up ${file.name} (${index + 1}/$totalFiles)")
                
                try {
                    // Recreate directory structure
                    val relativePath = file.absolutePath.removePrefix(sourceDir.absolutePath).removePrefix("/")
                    val pathParts = relativePath.split("/")
                    
                    var currentDir = destRoot
                    for (i in 0 until pathParts.size - 1) {
                        val dirName = pathParts[i]
                        val existing = currentDir?.findFile(dirName)
                        currentDir = if (existing != null && existing.isDirectory) {
                            existing
                        } else {
                            currentDir?.createDirectory(dirName) ?: throw Exception("Failed to create directory: $dirName")
                        }
                    }
                    
                    // Copy the file
                    val fileName = pathParts.last()
                    val mimeType = when {
                        fileName.endsWith(".epub") -> "application/epub+zip"
                        fileName.endsWith(".m4b") -> "audio/mp4"
                        else -> "application/octet-stream"
                    }
                    
                    val destFile = currentDir?.findFile(fileName)?.also { it.delete() } 
                        ?: currentDir?.createFile(mimeType, fileName)
                    
                    if (destFile != null) {
                        context.contentResolver.openOutputStream(destFile.uri)?.use { output ->
                            file.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        copiedFiles++
                        totalBytes += file.length()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BackupHelper", "Failed to backup ${file.name}", e)
                }
            }
            
            val totalMB = totalBytes / (1024 * 1024)
            Result.success("Backup complete: $copiedFiles files ($totalMB MB)")
            
        } catch (e: Exception) {
            android.util.Log.e("BackupHelper", "Backup failed", e)
            Result.failure(e)
        }
    }
}
