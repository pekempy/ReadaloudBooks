package com.pekempy.ReadAloudbooks.util

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AudioCodecConverter(private val context: Context) {
    
    private val TAG = "AudioCodecConverter"
    private val convertedDir = File(context.cacheDir, "converted_audio")
    
    init {
        if (!convertedDir.exists()) {
            convertedDir.mkdirs()
        }
    }
    
    suspend fun checkAndConvertIfNeeded(filePath: String): String = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext filePath
        
        Log.d(TAG, "Checking codec for: ${file.name}")
        
        if (filePath.lowercase().contains("atmos") || filePath.lowercase().contains("eac3")) {
            Log.i(TAG, "Probable EAC3-Atmos file detected by name")
        } else {
            val metadata = getAudioMetadata(filePath)
            val codec = metadata.codec
            Log.d(TAG, "Detected codec: $codec")
            if (!(codec == "eac3" || codec.contains("atmos") || codec.contains("joc"))) {
                return@withContext filePath
            }
        }

        val convertedFile = File(convertedDir, "${file.nameWithoutExtension}_aac.m4a")
        if (convertedFile.exists()) {
            if (convertedFile.length() > 65536 && isAudioFileValid(convertedFile.absolutePath)) { 
                Log.d(TAG, "Using cached converted file")
                return@withContext convertedFile.absolutePath
            } else {
                Log.w(TAG, "Existing cached file is invalid or too small, deleting it")
                convertedFile.delete()
            }
        }

        Log.i(TAG, "Converting unsupported audio to AAC...")
        
        val tempFile = File(convertedDir, "${file.nameWithoutExtension}_temp.m4a")
        if (tempFile.exists()) tempFile.delete()

        var success = convertWithTransformer(filePath, tempFile.absolutePath)
        
        if (!success) {
            Log.w(TAG, "Transformer failed, falling back to FFmpegKit...")
            if (tempFile.exists()) tempFile.delete()
            success = convertWithFFmpegKit(filePath, tempFile.absolutePath)
        }
        
        if (success && tempFile.exists() && tempFile.length() > 65536) {
            if (convertedFile.exists()) convertedFile.delete()
            tempFile.renameTo(convertedFile)
            return@withContext convertedFile.absolutePath
        } else {
            if (tempFile.exists()) tempFile.delete()
            Log.e(TAG, "Conversion failed or produced empty file")
            return@withContext filePath
        }
    }
    
    private fun isAudioFileValid(path: String): Boolean {
        return try {
            val session = FFprobeKit.execute("-v error -show_streams -select_streams a:0 \"$path\"")
            val output = session.allLogsAsString
            ReturnCode.isSuccess(session.returnCode) && output.contains("codec_type=audio")
        } catch (e: Exception) {
            false
        }
    }

    fun getCachedPathIfValid(filePath: String): String? {
        val file = File(filePath)
        val convertedFile = File(convertedDir, "${file.nameWithoutExtension}_aac.m4a")
        return if (convertedFile.exists() && convertedFile.length() > 65536 && isAudioFileValid(convertedFile.absolutePath)) {
            convertedFile.absolutePath
        } else {
            null
        }
    }

    data class ProbedChapter(val title: String, val startMs: Long, val durationMs: Long)
    data class AudioMetadata(val codec: String, val durationMs: Long, val chapters: List<ProbedChapter> = emptyList())

    fun getAudioMetadata(filePath: String): AudioMetadata {
        Log.d(TAG, "Probing metadata with FFprobeKit for: $filePath")
        
        val token = com.pekempy.ReadAloudbooks.data.api.AppContainer.apiClientManager.token
        val isRemote = filePath.startsWith("http")
        val headers = if (isRemote && token != null) "-headers \"Authorization: Bearer $token\r\n\" " else ""
        
        val session = FFprobeKit.execute("$headers-v error -show_format -show_streams -show_chapters -select_streams a:0 \"$filePath\"")
        val output = session.allLogsAsString
        
        val codecRegex = "codec_name=([^\\s]+)".toRegex()
        val codecMatch = codecRegex.find(output)
        val codecName = codecMatch?.groupValues?.get(1)?.lowercase() ?: "unknown"
        
        val isAtmos = output.contains("atmos", ignoreCase = true) || 
                     output.contains("joc", ignoreCase = true) || 
                     output.contains("Side, LFE", ignoreCase = true)
                     
        val codec = when {
            codecName == "eac3" || codecName == "ac3" || isAtmos -> "eac3"
            else -> codecName
        }
        
        val durationRegex = "duration=([0-9.]+)".toRegex()
        val matches = durationRegex.findAll(output)
        val durationMs = matches.map { it.groupValues[1].toDoubleOrNull() ?: 0.0 }
            .maxOrNull()?.let { (it * 1000).toLong() } ?: 0L
            
        val chapters = mutableListOf<ProbedChapter>()
        val chapterBlocks = output.split("[CHAPTER]")
        if (chapterBlocks.size > 1) {
            chapterBlocks.drop(1).forEach { block ->
                val cleanBlock = block.substringBefore("[/CHAPTER]")
                val startTime = "start_time=([0-9.]+)".toRegex().find(cleanBlock)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val endTime = "end_time=([0-9.]+)".toRegex().find(cleanBlock)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val titleMatch = "TAG:title=(.*)".toRegex().find(cleanBlock)
                val title = titleMatch?.groupValues?.get(1) ?: "Chapter ${chapters.size + 1}"
                
                chapters.add(ProbedChapter(
                    title = StringUtils.decodeHtml(title).trim(),
                    startMs = (startTime * 1000).toLong(),
                    durationMs = ((endTime - startTime) * 1000).toLong()
                ))
            }
        }
        
        Log.d(TAG, "Probed codec: $codec (raw: $codecName), atmos: $isAtmos, duration: ${durationMs}ms")
        return AudioMetadata(codec, durationMs, chapters)
    }


    
    private fun convertWithTransformer(inputPath: String, outputPath: String): Boolean {
        val latch = CountDownLatch(1)
        var success = false
        
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val transformer = Transformer.Builder(context)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .build()

                val mediaItem = MediaItem.fromUri(android.net.Uri.fromFile(File(inputPath)))
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        success = true
                        latch.countDown()
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        Log.e(TAG, "Transformer error: ${exportException.message}")
                        latch.countDown()
                    }
                }

                transformer.addListener(listener)
                transformer.start(mediaItem, outputPath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize or start Transformer: ${e.message}")
                latch.countDown()
            }
        }

        try {
            latch.await(5, TimeUnit.MINUTES)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Transformer wait interrupted")
        }
        
        return success
    }
    
    private fun convertWithFFmpegKit(inputPath: String, outputPath: String): Boolean {
        Log.d(TAG, "Converting with FFmpegKit...")
        val session = FFmpegKit.execute("-i \"$inputPath\" -vn -c:a aac -b:a 192k -ac 2 -y \"$outputPath\"")
        val returnCode = session.returnCode
        
        return if (ReturnCode.isSuccess(returnCode)) {
            Log.i(TAG, "FFmpegKit conversion finished successfully")
            true
        } else {
            Log.e(TAG, "FFmpegKit conversion failed with code $returnCode: ${session.allLogsAsString}")
            false
        }
    }
}
