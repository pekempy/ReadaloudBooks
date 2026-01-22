package com.pekempy.ReadAloudbooks.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.pekempy.ReadAloudbooks.data.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Caches metadata extracted from local EPUB/M4B files to avoid repeated parsing
 */
object LocalBookMetadataCache {
    
    data class CachedMetadata(
        val title: String,
        val author: String,
        val series: String?,
        val seriesIndex: Float?,
        val coverPath: String?,
        val lastModified: Long,
        val hasEbook: Boolean = false,
        val hasAudiobook: Boolean = false,
        val hasReadAloud: Boolean = false
    )
    
    private const val CACHE_DIR = "local_book_metadata"
    private const val METADATA_FILE = "metadata.json"
    private const val COVER_SUBDIR = "covers"
    
    private var memoryCache = mutableMapOf<String, CachedMetadata>()
    
    private fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, CACHE_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    private fun getCoverDir(context: Context): File {
        val dir = File(getCacheDir(context), COVER_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    private fun getMetadataFile(context: Context): File {
        return File(getCacheDir(context), METADATA_FILE)
    }
    
    /**
     * Load the metadata cache from disk
     */
    suspend fun loadCache(context: Context) = withContext(Dispatchers.IO) {
        val file = getMetadataFile(context)
        if (!file.exists()) return@withContext
        
        try {
            val json = JSONObject(file.readText())
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = json.getJSONObject(key)
                memoryCache[key] = CachedMetadata(
                    title = obj.getString("title"),
                    author = obj.getString("author"),
                    series = obj.optString("series").takeIf { it.isNotEmpty() },
                    seriesIndex = if (obj.has("seriesIndex")) obj.getDouble("seriesIndex").toFloat() else null,
                    coverPath = obj.optString("coverPath").takeIf { it.isNotEmpty() },
                    lastModified = obj.getLong("lastModified"),
                    hasEbook = obj.optBoolean("hasEbook", false),
                    hasAudiobook = obj.optBoolean("hasAudiobook", false),
                    hasReadAloud = obj.optBoolean("hasReadAloud", false)
                )
            }
            android.util.Log.d("MetadataCache", "Loaded ${memoryCache.size} cached metadata entries")
        } catch (e: Exception) {
            android.util.Log.e("MetadataCache", "Failed to load cache: ${e.message}")
        }
    }
    
    /**
     * Save the metadata cache to disk
     */
    private suspend fun saveCache(context: Context) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject()
            for ((key, value) in memoryCache) {
                val obj = JSONObject()
                obj.put("title", value.title)
                obj.put("author", value.author)
                obj.put("series", value.series ?: "")
                if (value.seriesIndex != null) {
                    obj.put("seriesIndex", value.seriesIndex.toDouble())
                }
                obj.put("coverPath", value.coverPath ?: "")
                obj.put("lastModified", value.lastModified)
                obj.put("hasEbook", value.hasEbook)
                obj.put("hasAudiobook", value.hasAudiobook)
                obj.put("hasReadAloud", value.hasReadAloud)
                json.put(key, obj)
            }
            getMetadataFile(context).writeText(json.toString())
        } catch (e: Exception) {
            android.util.Log.e("MetadataCache", "Failed to save cache: ${e.message}")
        }
    }
    
    /**
     * Get cached metadata for a book, or null if not cached/stale
     */
    fun getCachedMetadata(bookId: String): CachedMetadata? {
        return memoryCache[bookId]
    }
    
    /**
     * Get the cover file path for a book
     */
    fun getCoverPath(context: Context, bookId: String): String? {
        val cached = memoryCache[bookId] ?: return null
        val coverPath = cached.coverPath ?: return null
        val coverFile = File(coverPath)
        return if (coverFile.exists()) coverPath else null
    }
    
    fun getCoverUri(context: Context, bookId: String): Uri? {
        val path = getCoverPath(context, bookId) ?: return null
        return Uri.fromFile(File(path))
    }
    
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    fun clearMetadata(context: Context, bookId: String) {
        memoryCache.remove(bookId)
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            saveCache(context)
        }
    }
    
    suspend fun extractAndCacheMetadata(
        context: Context,
        bookId: String
    ): CachedMetadata? = withContext(Dispatchers.IO) {
        try {
            val parts = bookId.split("|")
            if (parts.size < 3) {
                android.util.Log.e("MetadataCache", "Invalid bookId format (parts=${parts.size}): $bookId")
                return@withContext null
            }
            
            val rootUriStr = parts[0]
            val folderDocId = parts[1]
            val fileName = parts.drop(2).joinToString("|")
            val rootUri = Uri.parse(rootUriStr)
            
            android.util.Log.d("MetadataCache", "Extracting: rootUri=$rootUriStr, folderDocId=$folderDocId, fileName=$fileName")
            
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                rootUri,
                folderDocId
            )
            
            android.util.Log.d("MetadataCache", "Children URI: $childrenUri")
            
            val projection = arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            
            var epubDocId: String? = null
            var epubName: String? = null
            var m4bDocId: String? = null
            var m4bName: String? = null
            var readaloudEpubDocId: String? = null  
            var readaloudEpubName: String? = null
            var lastModified: Long = 0
            var fileCount = 0
            
            var hasEpub = false
            var hasM4b = false
            var hasReadAloud = false
            
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val docIdCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val modCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                
                while (cursor.moveToNext()) {
                    fileCount++
                    val name = cursor.getString(nameCol) ?: continue
                    val mimeType = cursor.getString(mimeCol) ?: ""
                    val lowerName = name.lowercase()
                    
                    val isEpub = lowerName.endsWith(".epub") || mimeType == "application/epub+zip"
                    val isM4b = lowerName.endsWith(".m4b") || mimeType == "audio/x-m4b" || mimeType == "audio/m4b" || (mimeType == "audio/mp4" && lowerName.endsWith(".m4b"))
                    
                    if (isEpub) {
                        if (lowerName.contains("(readaloud)")) {
                            hasReadAloud = true
                            if (readaloudEpubDocId == null) {
                                readaloudEpubDocId = cursor.getString(docIdCol)
                                readaloudEpubName = name
                                lastModified = maxOf(lastModified, cursor.getLong(modCol))
                            }
                        } else {
                            hasEpub = true
                            epubDocId = cursor.getString(docIdCol)
                            epubName = name
                            lastModified = maxOf(lastModified, cursor.getLong(modCol))
                        }
                    } else if (isM4b) {
                        hasM4b = true
                        m4bDocId = cursor.getString(docIdCol)
                        m4bName = name
                        lastModified = maxOf(lastModified, cursor.getLong(modCol))
                    }
                }
            }
            
            if (epubDocId == null && readaloudEpubDocId != null) {
                epubDocId = readaloudEpubDocId
                epubName = readaloudEpubName
            }
            
            android.util.Log.d("MetadataCache", "Found $fileCount files. epub=$epubName, m4b=$m4bName")
            
            val existing = memoryCache[bookId]
            if (existing != null && existing.lastModified >= lastModified) {
                return@withContext existing
            }
            
            val primaryDocId = epubDocId ?: m4bDocId ?: return@withContext null
            val primaryName = epubName ?: m4bName ?: return@withContext null
            val isEpub = epubDocId != null
            
            val fileUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(rootUri, primaryDocId)
            
            val extracted = if (isEpub) {
                extractEpubFullMetadata(context, fileUri, primaryName)
            } else {
                extractM4bFullMetadata(context, fileUri, primaryName)
            }
            
            var coverPath: String? = null
            if (extracted.coverBytes != null) {
                val coverFile = File(getCoverDir(context), "${bookId.hashCode()}.jpg")
                try {
                    coverFile.writeBytes(extracted.coverBytes)
                    coverPath = coverFile.absolutePath
                    android.util.Log.d("MetadataCache", "Saved cover to: $coverPath")
                } catch (e: Exception) {
                    android.util.Log.e("MetadataCache", "Failed to save cover: ${e.message}")
                }
            }
            
            val metadata = CachedMetadata(
                title = extracted.title,
                author = extracted.author,
                series = extracted.series,
                seriesIndex = extracted.seriesIndex,
                coverPath = coverPath,
                lastModified = lastModified,
                hasEbook = hasEpub,
                hasAudiobook = hasM4b,
                hasReadAloud = hasReadAloud
            )
            
            memoryCache[bookId] = metadata
            saveCache(context)
            
            android.util.Log.d("MetadataCache", "Cached metadata for: ${metadata.title} by ${metadata.author} (cover: ${coverPath != null})")
            metadata

        } catch (e: Exception) {
            android.util.Log.e("MetadataCache", "Failed to extract metadata for $bookId: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    private data class ExtractedMetadata(
        val title: String,
        val author: String,
        val series: String?,
        val seriesIndex: Float?,
        val coverBytes: ByteArray?
    )
    
    private fun extractEpubFullMetadata(
        context: Context,
        uri: Uri,
        fileName: String
    ): ExtractedMetadata {
        var title = fileName.substringBeforeLast(".").replace(" (readaloud)", "", ignoreCase = true)
        var author = "Unknown"
        var series: String? = null
        var seriesIndex: Float? = null
        var coverBytes: ByteArray? = null
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipInputStream = ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry
                var opfPath: String? = null
                var coverHref: String? = null
                val opfDir: String
                
                while (entry != null) {
                    if (entry.name == "META-INF/container.xml") {
                        val containerXml = zipInputStream.readBytes().toString(Charsets.UTF_8)
                        opfPath = "full-path=\"([^\"]+)\"".toRegex().find(containerXml)?.groupValues?.get(1)
                    }
                    entry = zipInputStream.nextEntry
                }
                zipInputStream.close()
                
                if (opfPath == null) return ExtractedMetadata(title, author, series, seriesIndex, null)
                
                opfDir = opfPath.substringBeforeLast("/", "")
                
                context.contentResolver.openInputStream(uri)?.use { stream2 ->
                    val zip2 = ZipInputStream(stream2)
                    var entry2 = zip2.nextEntry
                    
                    while (entry2 != null) {
                        if (entry2.name == opfPath) {
                            val opfXml = zip2.readBytes().toString(Charsets.UTF_8)
                            
                            title = "<dc:title[^>]*>([^<]+)</dc:title>".toRegex(RegexOption.IGNORE_CASE)
                                .find(opfXml)?.groupValues?.get(1)?.trim() ?: title
                            author = "<dc:creator[^>]*>([^<]+)</dc:creator>".toRegex(RegexOption.IGNORE_CASE)
                                .find(opfXml)?.groupValues?.get(1)?.trim() ?: author
                            series = """name=["']calibre:series["']\s+content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
                                .find(opfXml)?.groupValues?.get(1)
                            
                            val seriesIndexStr = """name=["']calibre:series_index["']\s+content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
                                .find(opfXml)?.groupValues?.get(1)
                            if (seriesIndexStr == null) {
                                val altSeriesIndex = """content=["']([^"']+)["']\s+name=["']calibre:series_index["']""".toRegex(RegexOption.IGNORE_CASE)
                                    .find(opfXml)?.groupValues?.get(1)
                                seriesIndex = altSeriesIndex?.toFloatOrNull()
                            } else {
                                seriesIndex = seriesIndexStr.toFloatOrNull()
                            }
                            
                            var coverId = """<meta[^>]*name=["']cover["'][^>]*content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
                                .find(opfXml)?.groupValues?.get(1)
                            if (coverId == null) {
                                coverId = """<meta[^>]*content=["']([^"']+)["'][^>]*name=["']cover["']""".toRegex(RegexOption.IGNORE_CASE)
                                    .find(opfXml)?.groupValues?.get(1)
                            }
                            
                            if (coverId != null) {
                                val itemRegex = """<item[^>]*id=["']${Regex.escape(coverId)}["'][^>]*href=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
                                coverHref = itemRegex.find(opfXml)?.groupValues?.get(1)
                                if (coverHref == null) {
                                    val itemRegex2 = """<item[^>]*href=["']([^"']+)["'][^>]*id=["']${Regex.escape(coverId)}["']""".toRegex(RegexOption.IGNORE_CASE)
                                    coverHref = itemRegex2.find(opfXml)?.groupValues?.get(1)
                                }
                            }
                            
                            if (coverHref == null) {
                                val propsRegex = """<item[^>]*properties=["'][^"']*cover-image[^"']*["'][^>]*href=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
                                coverHref = propsRegex.find(opfXml)?.groupValues?.get(1)
                                if (coverHref == null) {
                                    val propsRegex2 = """<item[^>]*href=["']([^"']+)["'][^>]*properties=["'][^"']*cover-image[^"']*["']""".toRegex(RegexOption.IGNORE_CASE)
                                    coverHref = propsRegex2.find(opfXml)?.groupValues?.get(1)
                                }
                            }
                            
                            if (coverHref == null) {
                                val coverIdRegex = """<item[^>]*id=["'][^"']*cover[^"']*["'][^>]*href=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
                                coverHref = coverIdRegex.find(opfXml)?.groupValues?.get(1)
                                if (coverHref == null) {
                                    val coverIdRegex2 = """<item[^>]*href=["']([^"']+)["'][^>]*id=["'][^"']*cover[^"']*["']""".toRegex(RegexOption.IGNORE_CASE)
                                    coverHref = coverIdRegex2.find(opfXml)?.groupValues?.get(1)
                                }
                            }
                            
                            if (coverHref == null) {
                                val imgItemRegex = """<item[^>]*href=["']([^"']*cover[^"']*\.(jpg|jpeg|png))["'][^>]*media-type=["']image/""".toRegex(RegexOption.IGNORE_CASE)
                                coverHref = imgItemRegex.find(opfXml)?.groupValues?.get(1)
                            }
                        }
                        entry2 = zip2.nextEntry
                    }
                    zip2.close()
                }
                
                if (coverHref != null) {
                    val coverPath = if (opfDir.isEmpty()) coverHref else "$opfDir/$coverHref"
                    
                    context.contentResolver.openInputStream(uri)?.use { stream3 ->
                        val zip3 = ZipInputStream(stream3)
                        var entry3 = zip3.nextEntry
                        
                        while (entry3 != null) {
                            if (entry3.name == coverPath || entry3.name.endsWith(coverHref!!)) {
                                coverBytes = zip3.readBytes()
                                break
                            }
                            entry3 = zip3.nextEntry
                        }
                        zip3.close()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MetadataCache", "EPUB extraction failed: ${e.message}")
        }
        
        return ExtractedMetadata(title, author, series, seriesIndex, coverBytes)
    }
    
    private fun extractM4bFullMetadata(
        context: Context,
        uri: Uri,
        fileName: String
    ): ExtractedMetadata {
        var title = fileName.substringBeforeLast(".")
        var author = "Unknown"
        var series: String? = null
        var coverBytes: ByteArray? = null
        
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: title
            author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) 
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: author
            series = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            
            coverBytes = retriever.embeddedPicture
            
            retriever.release()
        } catch (e: Exception) {
            android.util.Log.e("MetadataCache", "M4B extraction failed: ${e.message}")
        }
        
        return ExtractedMetadata(title, author, series, null, coverBytes)
    }
    
    fun enrichBook(context: Context, book: Book): Book {
        val cached = memoryCache[book.id] ?: return book
        val coverUri = getCoverUri(context, book.id)?.toString()
        
        val hasEbook = book.hasEbook || cached.hasEbook
        val hasAudiobook = book.hasAudiobook || cached.hasAudiobook
        val hasReadAloud = book.hasReadAloud || cached.hasReadAloud
        
        return book.copy(
            title = cached.title,
            author = cached.author,
            series = cached.series ?: book.series,
            seriesIndex = cached.seriesIndex?.toString() ?: book.seriesIndex,
            coverUrl = coverUri ?: book.coverUrl,
            audiobookCoverUrl = coverUri ?: book.audiobookCoverUrl,
            ebookCoverUrl = coverUri ?: book.ebookCoverUrl,
            
            hasEbook = hasEbook,
            hasAudiobook = hasAudiobook,
            hasReadAloud = hasReadAloud,
            
            isDownloaded = book.isDownloaded || hasEbook || hasAudiobook || hasReadAloud,
            isAudiobookDownloaded = book.isAudiobookDownloaded || hasAudiobook,
            isEbookDownloaded = book.isEbookDownloaded || hasEbook,
            isReadAloudDownloaded = book.isReadAloudDownloaded || hasReadAloud
        )
    }
    
    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        memoryCache.clear()
        getCacheDir(context).deleteRecursively()
    }
}
