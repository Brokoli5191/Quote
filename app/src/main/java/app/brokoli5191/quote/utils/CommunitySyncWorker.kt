package app.brokoli5191.quote.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.CommunitySyncManager
import app.brokoli5191.quote.data.InstallationSeed
import app.brokoli5191.quote.data.QuoteRepository
import java.util.concurrent.TimeUnit

class CommunitySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = QuoteRepository(
            AppDatabase.getInstance(applicationContext).quoteDao(),
            InstallationSeed.get(applicationContext)
        )
        val synced = CommunitySyncManager(applicationContext, repository).sync()
        if (!synced) return Result.retry()

        applicationContext.sendBroadcast(Intent("app.brokoli5191.quote.UPDATE_WIDGET").apply {
            component = ComponentName(applicationContext, "app.brokoli5191.quote.widget.QuoteWidgetProvider")
        })
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "community_quote_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CommunitySyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
