package com.pekempy.ReadAloudbooks.util

import android.content.Context
import android.net.Uri
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object LogExporter {
    fun getLogcatText(): String {
        return try {
            val pid = android.os.Process.myPid()
            val process = Runtime.getRuntime().exec("logcat -d --pid=$pid")
            val reader = InputStreamReader(process.inputStream)
            reader.readText()
        } catch (e: Exception) {
            "Failed to capture logcat: ${e.message}"
        }
    }

    fun saveLogToFile(context: Context, uri: Uri, text: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(text)
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("LogExporter", "Failed to save log to file", e)
            false
        }
    }
}
