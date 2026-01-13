package com.pekempy.ReadAloudbooks.util

import com.pekempy.ReadAloudbooks.data.Book
import com.pekempy.ReadAloudbooks.data.api.ApiClientManager
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object DownloadUtils {
    
    fun sanitize(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    fun getBookDir(filesDir: File, book: Book): File {
        val authorPart = sanitize(book.author)
        val seriesPart = book.series?.let { sanitize(it) }
        
        return if (seriesPart != null) {
            File(File(filesDir, authorPart), seriesPart)
        } else {
            File(filesDir, authorPart)
        }
    }

    fun getBaseFileName(book: Book): String {
        val indexPart = book.seriesIndex?.let { it.padStart(2, '0') }
        return if (indexPart != null) {
            sanitize("$indexPart - ${book.title}")
        } else {
            sanitize(book.title)
        }
    }

    fun isAudiobookDownloaded(filesDir: File, book: Book): Boolean {
        val bookDir = getBookDir(filesDir, book)
        val baseFileName = getBaseFileName(book)
        return File(bookDir, "$baseFileName.m4b").exists()
    }

    fun isEbookDownloaded(filesDir: File, book: Book): Boolean {
        val bookDir = getBookDir(filesDir, book)
        val baseFileName = getBaseFileName(book)
        return File(bookDir, "$baseFileName.epub").exists()
    }

    fun isReadAloudDownloaded(filesDir: File, book: Book): Boolean {
        val bookDir = getBookDir(filesDir, book)
        val baseFileName = getBaseFileName(book)
        return File(bookDir, "$baseFileName (readaloud).epub").exists()
    }

    fun isBookDownloaded(filesDir: File, book: Book): Boolean {
        val aExists = isAudiobookDownloaded(filesDir, book)
        val eExists = isEbookDownloaded(filesDir, book)
        val rExists = isReadAloudDownloaded(filesDir, book)
        
        if (aExists || eExists || rExists) {
            android.util.Log.d("DownloadUtils", "Found downloaded content for ${book.title}: Audio=$aExists, Ebook=$eExists, ReadAloud=$rExists")
            val bookDir = getBookDir(filesDir, book)
            android.util.Log.d("DownloadUtils", "Book directory: ${bookDir.absolutePath}")
        }
        
        return aExists || eExists || rExists
    }

    suspend fun downloadFile(
        downloadClient: okhttp3.OkHttpClient,
        url: String,
        file: File,
        onProgress: (Float) -> Unit
    ) {
        var serverHash: String? = null
        val existingSize = if (file.exists()) file.length() else 0L
        
        val request = Request.Builder()
            .url(url)
            .apply {
                if (existingSize > 0) {
                    addHeader("Range", "bytes=$existingSize-")
                }
            }
            .build()
            
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
                if (response.code == 416) return
                throw Exception("Unexpected code $response")
            }

            serverHash = response.header("X-Storyteller-Hash")
            if (serverHash != null) {
                 android.util.Log.i("DownloadUtils", "X-Storyteller-Hash found: $serverHash")
            }
            
            val body = response.body ?: throw Exception("Empty body")
            val contentLength = body.contentLength()
            val totalSize = if (response.code == 206) contentLength + existingSize else contentLength
            
            val append = response.code == 206
            FileOutputStream(file, append).use { output ->
                val input = body.byteStream()
                val buffer = ByteArray(128 * 1024)
                var bytesRead: Int
                var totalBytesRead = existingSize
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (totalSize > 0) {
                        onProgress(totalBytesRead.toFloat() / totalSize)
                    }
                }
            }
        }

        if (serverHash != null && file.exists()) {
            try {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val computedHash = file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        digest.update(buffer, 0, bytesRead)
                    }
                    digest.digest().joinToString("") { "%02x".format(it) }
                }
                
                if (computedHash.equals(serverHash, ignoreCase = true)) {
                    android.util.Log.i("DownloadUtils", "Hash verification successful for ${file.name}: $computedHash")
                } else {
                    android.util.Log.e("DownloadUtils", "Hash verification FAILED for ${file.name}! Server: $serverHash, Computed: $computedHash")
                }
            } catch (e: Exception) {
                android.util.Log.e("DownloadUtils", "Failed to verify hash", e)
            }
        }
    }
}
