package com.pekempy.ReadAloudbooks.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.pekempy.ReadAloudbooks.data.Book
import java.io.File
import java.util.zip.ZipInputStream

object LocalScanner {
    fun extractEpubMetadata(context: Context, uri: Uri, fileName: String): Triple<String, String?, String?> {
        var title = fileName.substringBeforeLast(".").replace(" (readaloud)", "")
        var author: String? = "Unknown"
        var series: String? = null
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipInputStream = ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry
                var opfPath: String? = null
                
                while (entry != null) {
                    if (entry.name == "META-INF/container.xml") {
                        val containerXml = zipInputStream.readBytes().toString(Charsets.UTF_8)
                        opfPath = "full-path=\"([^\"]+)\"".toRegex().find(containerXml)?.groupValues?.get(1)
                    } else if (opfPath != null && entry.name == opfPath) {
                        val opfXml = zipInputStream.readBytes().toString(Charsets.UTF_8)
                        title = "<dc:title[^>]*>(.*?)</dc:title>".toRegex(RegexOption.IGNORE_CASE).find(opfXml)?.groupValues?.get(1) ?: title
                        author = "<dc:creator[^>]*>(.*?)</dc:creator>".toRegex(RegexOption.IGNORE_CASE).find(opfXml)?.groupValues?.get(1) ?: author
                        series = "name=\"calibre:series\" content=\"([^\"]+)\"".toRegex(RegexOption.IGNORE_CASE).find(opfXml)?.groupValues?.get(1)
                        break 
                    }
                    entry = zipInputStream.nextEntry
                }
                zipInputStream.close()
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalScanner", "Failed to extract EPUB metadata: ${e.message}")
        }
        return Triple(title, author, series)
    }

    fun extractAudioMetadata(context: Context, uri: Uri, fileName: String): Triple<String, String?, String?> {
        var title = fileName.substringBeforeLast(".").replace(" (readaloud)", "")
        var author: String? = "Unknown"
        var series: String? = null
        
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: title
            author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) 
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: author
            series = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            retriever.release()
        } catch (e: Exception) {
            android.util.Log.e("LocalScanner", "Failed to extract Audio metadata: ${e.message}")
        }
        return Triple(title, author, series)
    }

    fun scanRecursive(context: Context, rootUri: Uri, bookList: MutableList<Book>) {
        val rootDoc = if (rootUri.scheme == "content") {
            DocumentFile.fromTreeUri(context, rootUri)
        } else {
            DocumentFile.fromFile(File(rootUri.path!!))
        } ?: return

        scanDocumentRecursive(context, rootDoc, bookList)
    }

    fun findBookByLocalId(context: Context, bookId: String): Book? {
        val cached = LocalBookMetadataCache.getCachedMetadata(bookId)
        if (cached != null) {
            val coverUri = LocalBookMetadataCache.getCoverUri(context, bookId)?.toString()
            return Book(
                id = bookId,
                title = cached.title,
                author = cached.author,
                series = cached.series,
                coverUrl = coverUri,
                audiobookCoverUrl = coverUri,
                ebookCoverUrl = coverUri,
                hasEbook = cached.hasEbook,
                hasAudiobook = cached.hasAudiobook,
                hasReadAloud = cached.hasReadAloud,
                isDownloaded = cached.hasEbook || cached.hasAudiobook || cached.hasReadAloud,
                isEbookDownloaded = cached.hasEbook,
                isAudiobookDownloaded = cached.hasAudiobook,
                isReadAloudDownloaded = cached.hasReadAloud
            )
        }
        
        val parts = bookId.split("|")
        if (parts.size >= 3) {
            val rootUriStr = parts[0]
            val folderDocId = parts[1]
            val fileName = parts.drop(2).joinToString("|") 
            val rootUri = Uri.parse(rootUriStr)
            
            val title = fileName.substringBeforeLast(".").replace(" (readaloud)", "", ignoreCase = true)
            
            var hasEpub = false
            var hasM4b = false
            var hasReadaloud = false
            
            try {
                val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                    rootUri, folderDocId
                )
                val projection = arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                )
                
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val nameCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                    
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameCol) ?: continue
                        val mimeType = cursor.getString(mimeCol) ?: ""
                        val lowerName = name.lowercase()
                        
                        val isEpubFile = lowerName.endsWith(".epub") || mimeType == "application/epub+zip"
                        val isM4bFile = lowerName.endsWith(".m4b") || mimeType == "audio/x-m4b" || mimeType == "audio/m4b" || (mimeType == "audio/mp4" && lowerName.endsWith(".m4b"))
                        val isReadAloudFile = lowerName.contains("(readaloud)")
                        
                        if (isEpubFile) {
                            if (isReadAloudFile) {
                                hasReadaloud = true
                            } else {
                                hasEpub = true
                            }
                        }
                        if (isM4bFile) {
                            hasM4b = true
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LocalScanner", "Error querying folder contents: ${e.message}")
                val lowerName = fileName.lowercase()
                val isReadAloudFile = lowerName.contains("(readaloud)")
                
                hasEpub = lowerName.endsWith(".epub") && !isReadAloudFile
                hasM4b = lowerName.endsWith(".m4b")
                hasReadaloud = isReadAloudFile
            }
            
            return Book(
                id = bookId,
                title = title,
                author = "Unknown",
                hasEbook = hasEpub,
                hasAudiobook = hasM4b,
                hasReadAloud = hasReadaloud,
                isDownloaded = hasEpub || hasM4b || hasReadaloud,
                isEbookDownloaded = hasEpub,
                isAudiobookDownloaded = hasM4b,
                isReadAloudDownloaded = hasReadaloud,
                coverUrl = null,
                audiobookCoverUrl = null,
                ebookCoverUrl = null
            )
        }
        
        val lastSlash = bookId.lastIndexOf("/")
        if (lastSlash == -1) return null
        val folderUriStr = bookId.substring(0, lastSlash)
        
        val folderUri = Uri.parse(folderUriStr)
        
        if (folderUri.scheme == "content") {
            return findBookByLocalIdFast(context, bookId, folderUri)
        }
        
        val folderDoc = try {
            DocumentFile.fromFile(File(folderUri.path!!))
        } catch (e: Exception) {
            null
        } ?: return null
        
        val files = folderDoc.listFiles()
        
        var epubFile: DocumentFile? = null
        var m4bFile: DocumentFile? = null
        var epubReadAloud: DocumentFile? = null
        
        for (file in files) {
            if (file.isDirectory) continue
            val name = file.name?.lowercase() ?: continue
            when {
                name.endsWith(".epub") && name.contains("(readaloud)") -> epubReadAloud = file
                name.endsWith(".epub") -> epubFile = file
                name.endsWith(".m4b") -> m4bFile = file
            }
        }
        
        val primaryFile = epubFile ?: m4bFile ?: epubReadAloud ?: return null
        val primaryName = primaryFile.name ?: return null
        val title = primaryName.substringBeforeLast(".").replace(" (readaloud)", "", ignoreCase = true)
        
        return Book(
            id = bookId,
            title = title,
            author = "Unknown",
            hasEbook = epubFile != null,
            hasAudiobook = m4bFile != null,
            hasReadAloud = epubReadAloud != null,
            coverUrl = null,
            audiobookCoverUrl = null,
            ebookCoverUrl = null
        )
    }
    
    private fun findBookByLocalIdFast(context: Context, bookId: String, folderUri: Uri): Book? {
        val childrenUri = try {
            android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                folderUri,
                android.provider.DocumentsContract.getDocumentId(folderUri)
            )
        } catch (e: Exception) {
            try {
                android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                    folderUri,
                    android.provider.DocumentsContract.getTreeDocumentId(folderUri)
                )
            } catch (e2: Exception) {
                android.util.Log.e("LocalScanner", "Failed to build children URI: ${e2.message}")
                return null
            }
        }
        
        val projection = arrayOf(
            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        
        var epubName: String? = null
        var m4bName: String? = null
        var readAloudName: String? = null
        
        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val nameCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    val mimeType = cursor.getString(mimeCol) ?: ""
                    
                    if (mimeType == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) continue
                    
                    val lowerName = name.lowercase()
                    val isEpub = lowerName.endsWith(".epub") || mimeType == "application/epub+zip"
                    val isM4b = lowerName.endsWith(".m4b") || mimeType == "audio/x-m4b" || mimeType == "audio/m4b" || (mimeType == "audio/mp4" && lowerName.endsWith(".m4b"))
                    
                    when {
                        isEpub && lowerName.contains("(readaloud)") -> readAloudName = name
                        isEpub -> epubName = name
                        isM4b -> m4bName = name
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalScanner", "ContentResolver query failed: ${e.message}")
            return null
        }
        
        val primaryName = epubName ?: m4bName ?: readAloudName ?: return null
        val title = primaryName.substringBeforeLast(".").replace(" (readaloud)", "", ignoreCase = true)
        
        return Book(
            id = bookId,
            title = title,
            author = "Unknown",
            hasEbook = epubName != null,
            hasAudiobook = m4bName != null,
            hasReadAloud = readAloudName != null,
            coverUrl = null,
            audiobookCoverUrl = null,
            ebookCoverUrl = null
        )
    }

    private data class FileInfo(
        val uri: Uri,
        val name: String,
        val ext: String,
        val title: String,
        val author: String,
        val series: String?,
        val isReadAloud: Boolean,
        val lastModified: Long
    )

    fun scanDocumentRecursive(context: Context, parent: DocumentFile, bookList: MutableList<Book>) {
        val files = parent.listFiles()
        val folderFiles = mutableListOf<FileInfo>()
        
        for (file in files) {
            if (file.isDirectory) {
                scanDocumentRecursive(context, file, bookList)
            } else {
                val name = file.name ?: continue
                val ext = name.substringAfterLast(".", "").lowercase()
                if (ext == "epub" || ext == "m4b") {
                    val isReadAloud = name.contains("(readaloud)", ignoreCase = true)
                    val (title, author, series) = if (ext == "epub") {
                        extractEpubMetadata(context, file.uri, name)
                    } else {
                        extractAudioMetadata(context, file.uri, name)
                    }
                    folderFiles.add(FileInfo(file.uri, name, ext, title, author ?: "Unknown", series, isReadAloud, file.lastModified()))
                }
            }
        }

        if (folderFiles.isEmpty()) return

        val folderBooks = mutableListOf<Book>()
        
        for (info in folderFiles) {
            val baseName = info.title.replace(" (readaloud)", "")
            
            val existing = folderBooks.find { 
                it.title == baseName || 
                (folderFiles.size <= 3 && folderBooks.size == 1) 
            }

            if (existing != null) {
                val updated = existing.copy(
                    author = if (existing.author == "Unknown") info.author else existing.author,
                    series = existing.series ?: info.series,
                    hasEbook = existing.hasEbook || (info.ext == "epub" && !info.isReadAloud),
                    hasAudiobook = existing.hasAudiobook || info.ext == "m4b",
                    hasReadAloud = existing.hasReadAloud || info.isReadAloud,
                    isAudiobookDownloaded = existing.isAudiobookDownloaded || info.ext == "m4b",
                    isEbookDownloaded = existing.isEbookDownloaded || (info.ext == "epub" && !info.isReadAloud),
                    isReadAloudDownloaded = existing.isReadAloudDownloaded || info.isReadAloud
                )
                folderBooks[folderBooks.indexOf(existing)] = updated
            } else {
                folderBooks.add(Book(
                    id = parent.uri.toString() + "/" + baseName,
                    title = baseName,
                    author = info.author,
                    series = info.series,
                    hasEbook = info.ext == "epub" && !info.isReadAloud,
                    hasAudiobook = info.ext == "m4b",
                    hasReadAloud = info.isReadAloud,
                    isDownloaded = true,
                    isAudiobookDownloaded = info.ext == "m4b",
                    isEbookDownloaded = info.ext == "epub" && !info.isReadAloud,
                    isReadAloudDownloaded = info.isReadAloud,
                    addedDate = info.lastModified
                ))
            }
        }

        for (fBook in folderBooks) {
            val globalExisting = bookList.find { it.title == fBook.title && (it.author == fBook.author || fBook.author == "Unknown") }
            if (globalExisting != null) {
                val updated = globalExisting.copy(
                    author = if (globalExisting.author == "Unknown") fBook.author else globalExisting.author,
                    series = globalExisting.series ?: fBook.series,
                    hasEbook = globalExisting.hasEbook || fBook.hasEbook,
                    hasAudiobook = globalExisting.hasAudiobook || fBook.hasAudiobook,
                    hasReadAloud = globalExisting.hasReadAloud || fBook.hasReadAloud,
                    isAudiobookDownloaded = globalExisting.isAudiobookDownloaded || fBook.isAudiobookDownloaded,
                    isEbookDownloaded = globalExisting.isEbookDownloaded || fBook.isEbookDownloaded,
                    isReadAloudDownloaded = globalExisting.isReadAloudDownloaded || fBook.isReadAloudDownloaded
                )
                bookList[bookList.indexOf(globalExisting)] = updated
            } else {
                bookList.add(fBook)
            }
        }
    }

    fun scanRecursiveFast(context: Context, rootUri: Uri, bookList: MutableList<Book>) {
        if (rootUri.scheme != "content") {
            scanRecursive(context, rootUri, bookList)
            return
        }
        
        try {
            val rootDocId = android.provider.DocumentsContract.getTreeDocumentId(rootUri)
            scanWithContentResolver(context, rootUri, rootDocId, bookList)
        } catch (e: Exception) {
            android.util.Log.e("LocalScanner", "Failed to get tree document ID: ${e.message}")
        }
    }
    
    private fun scanWithContentResolver(
        context: Context, 
        rootUri: Uri,
        parentDocId: String, 
        bookList: MutableList<Book>
    ) {
        val parentUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
            rootUri,
            parentDocId
        )

        val projection = arrayOf(
            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
            android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        
        data class FileEntry(
            val docId: String,
            val name: String,
            val mimeType: String,
            val lastModified: Long
        )
        
        val entries = mutableListOf<FileEntry>()
        
        try {
            context.contentResolver.query(parentUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                
                while (cursor.moveToNext()) {
                    entries.add(FileEntry(
                        docId = cursor.getString(idCol),
                        name = cursor.getString(nameCol) ?: "",
                        mimeType = cursor.getString(mimeCol) ?: "",
                        lastModified = cursor.getLong(modCol)
                    ))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalScanner", "ContentResolver query failed: ${e.message}")
            return
        }
        
        val directories = mutableListOf<FileEntry>()
        val mediaFiles = mutableListOf<FileEntry>()
        
        for (entry in entries) {
            if (entry.mimeType == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                directories.add(entry)
            } else {
                val lowerName = entry.name.lowercase()
                val isEpub = lowerName.endsWith(".epub") || entry.mimeType == "application/epub+zip"
                val isM4b = lowerName.endsWith(".m4b") || entry.mimeType == "audio/x-m4b" || entry.mimeType == "audio/m4b" || (entry.mimeType == "audio/mp4" && lowerName.endsWith(".m4b"))

                if (isEpub || isM4b) {
                    mediaFiles.add(entry)
                }
            }
        }
        
        if (mediaFiles.isNotEmpty()) {
            val folderBooks = mutableListOf<Book>()
            
            val folderDocId = parentDocId
            
            for (file in mediaFiles) {
                val lowerName = file.name.lowercase()
                val isEpub = lowerName.endsWith(".epub") || file.mimeType == "application/epub+zip"
                val isM4b = lowerName.endsWith(".m4b") || file.mimeType == "audio/x-m4b" || file.mimeType == "audio/m4b" || (file.mimeType == "audio/mp4" && lowerName.endsWith(".m4b"))
                val isReadAloud = file.name.contains("(readaloud)", ignoreCase = true)
                
                val title = if (lowerName.contains(".")) {
                    file.name.substringBeforeLast(".").replace(" (readaloud)", "", ignoreCase = true)
                } else {
                    file.name.replace(" (readaloud)", "", ignoreCase = true)
                }
                
                val bookId = "${rootUri}|${folderDocId}|${file.name}"
                
                val existing = folderBooks.find { 
                    it.title.equals(title, ignoreCase = true) || 
                    (mediaFiles.size <= 3 && folderBooks.size == 1)
                }
                
                if (existing != null) {
                    val updated = existing.copy(
                        hasEbook = existing.hasEbook || (isEpub && !isReadAloud),
                        hasAudiobook = existing.hasAudiobook || isM4b,
                        hasReadAloud = existing.hasReadAloud || isReadAloud,
                        isAudiobookDownloaded = existing.isAudiobookDownloaded || isM4b,
                        isEbookDownloaded = existing.isEbookDownloaded || (isEpub && !isReadAloud),
                        isReadAloudDownloaded = existing.isReadAloudDownloaded || isReadAloud,
                        isDownloaded = true
                    )
                    folderBooks[folderBooks.indexOf(existing)] = updated
                } else {
                    folderBooks.add(Book(
                        id = bookId,
                        title = title,
                        author = "Unknown", 
                        hasEbook = isEpub && !isReadAloud,
                        hasAudiobook = isM4b,
                        hasReadAloud = isReadAloud,
                        isAudiobookDownloaded = isM4b,
                        isEbookDownloaded = isEpub && !isReadAloud,
                        isReadAloudDownloaded = isReadAloud,
                        isDownloaded = true
                    ))
                }
            }
            
            for (fBook in folderBooks) {
                val globalExisting = bookList.find { it.title.equals(fBook.title, ignoreCase = true) }
                if (globalExisting != null) {
                    val updated = globalExisting.copy(
                        hasEbook = globalExisting.hasEbook || fBook.hasEbook,
                        hasAudiobook = globalExisting.hasAudiobook || fBook.hasAudiobook,
                        hasReadAloud = globalExisting.hasReadAloud || fBook.hasReadAloud,
                        isAudiobookDownloaded = globalExisting.isAudiobookDownloaded || fBook.isAudiobookDownloaded,
                        isEbookDownloaded = globalExisting.isEbookDownloaded || fBook.isEbookDownloaded,
                        isReadAloudDownloaded = globalExisting.isReadAloudDownloaded || fBook.isReadAloudDownloaded,
                        isDownloaded = true
                    )
                    bookList[bookList.indexOf(globalExisting)] = updated
                } else {
                    bookList.add(fBook)
                }
            }
        }
        
        for (dir in directories) {
            scanWithContentResolver(context, rootUri, dir.docId, bookList)
        }
    }
}
