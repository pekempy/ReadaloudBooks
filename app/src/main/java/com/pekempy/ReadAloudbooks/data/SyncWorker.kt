package com.pekempy.ReadAloudbooks.data

import android.content.Context
import androidx.work.*
import com.pekempy.ReadAloudbooks.data.api.AppContainer
import com.pekempy.ReadAloudbooks.data.db.BookRepository
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val repository = UserPreferencesRepository(applicationContext)
        val bookRepository = BookRepository(applicationContext, repository)
        
        return try {
            android.util.Log.d("SyncWorker", "Starting background sync...")
            val success = bookRepository.syncWithServer(force = true)
            if (success) {
                repository.updateLastSyncTime(System.currentTimeMillis())
                android.util.Log.d("SyncWorker", "Background sync successful")
                Result.success()
            } else {
                android.util.Log.w("SyncWorker", "Background sync failed")
                Result.retry()
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Error in background sync: ${e.message}")
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "PeriodicSyncWork"

        fun schedule(context: Context, intervalMinutes: Int) {
            val workManager = WorkManager.getInstance(context)
            
            if (intervalMinutes <= 0) {
                workManager.cancelUniqueWork(WORK_NAME)
                android.util.Log.d("SyncWorker", "Background sync cancelled (interval=0)")
                return
            }

            // WorkManager has a minimum interval of 15 minutes
            val interval = intervalMinutes.toLong().coerceAtLeast(15)
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(interval, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            android.util.Log.d("SyncWorker", "Background sync scheduled every $interval minutes")
        }
    }
}
