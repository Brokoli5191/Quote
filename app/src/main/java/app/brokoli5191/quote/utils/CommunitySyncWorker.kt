package app.brokoli5191.quote.utils

import android.content.Context
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
        // Shares its lock with the in-app sync and updates the widget itself when quotes changed.
        val result = CommunitySyncManager(applicationContext, repository).syncWithResult()
        // Review decisions found here are posted even when the feed failed: the stored state has already
        // moved on, so a retry would not report them again. A sync in the app shows them in the app instead.
        val updates = result.submissions.updates
        val notify = applicationContext.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
            .getBoolean(KEY_SUBMISSION_NOTIFICATIONS, true)
        if (updates.isNotEmpty() && notify) NotificationHelper.showSubmissionUpdates(applicationContext, updates)
        return if (result.feedSynced) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "community_quote_sync"

        /** Pref (aura_prefs, default true): post a notification when a review decision arrives in the background. */
        const val KEY_SUBMISSION_NOTIFICATIONS = "submission_notifications_enabled"

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
