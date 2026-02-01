package com.pekempy.ReadAloudbooks.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.pekempy.ReadAloudbooks.MainActivity
import com.pekempy.ReadAloudbooks.R
import com.pekempy.ReadAloudbooks.data.Book
import com.pekempy.ReadAloudbooks.data.DownloadManager
import java.io.File

class DownloadService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val CHANNEL_ID = "download_channel"
    private val NOTIFICATION_ID = 1

    companion object {
        const val ACTION_START_DOWNLOAD = "START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "CANCEL_DOWNLOAD"
        const val EXTRA_BOOK = "EXTRA_BOOK"
        const val EXTRA_BOOK_ID = "EXTRA_BOOK_ID"
        const val EXTRA_DOWNLOAD_TYPE = "EXTRA_DOWNLOAD_TYPE"

        fun startDownload(context: Context, book: Book, downloadType: DownloadManager.DownloadType) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_BOOK, book)
                putExtra(EXTRA_DOWNLOAD_TYPE, downloadType.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelDownload(context: Context, bookId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_BOOK_ID, bookId)
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val book = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_BOOK, Book::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_BOOK)
                }
                val downloadType = intent.getStringExtra(EXTRA_DOWNLOAD_TYPE)
                    ?.let { DownloadManager.DownloadType.valueOf(it) }
                    ?: DownloadManager.DownloadType.All

                if (book != null) {
                    startForeground(NOTIFICATION_ID, createNotification("Downloading ${book.title}"))
                    DownloadManager.download(book, filesDir, downloadType)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val bookId = intent.getStringExtra(EXTRA_BOOK_ID)
                if (bookId != null) {
                    DownloadManager.cancelDownload(bookId)
                }
                checkAndStopIfNoDownloads()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Book download notifications"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ReadAloud Books")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun acquireWakeLock() {
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ReadAloudBooks::DownloadWakeLock").apply {
                acquire(10*60*1000L /*10 minutes*/)
            }
        }
    }

    private fun checkAndStopIfNoDownloads() {
        if (DownloadManager.activeDownloads.none { !it.isCompleted && !it.isFailed }) {
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        wakeLock?.release()
        super.onDestroy()
    }
}
