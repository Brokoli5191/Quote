package app.brokoli5191.quote.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.InstallationSeed
import app.brokoli5191.quote.data.QuoteRepository
import app.brokoli5191.quote.widget.WidgetRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the "Save" button of the daily quote notification. */
class QuoteActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SAVE) return
        val quoteId = intent.getIntExtra(EXTRA_QUOTE_ID, NotificationHelper.NO_QUOTE_ID)
        if (quoteId == NotificationHelper.NO_QUOTE_ID) return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 1001)
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val author = intent.getStringExtra(EXTRA_AUTHOR).orEmpty()
        val german = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
            .getString("app_language", "en") == "de"

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = QuoteRepository(AppDatabase.getInstance(context).quoteDao(), InstallationSeed.get(context))
                val quote = repository.getQuoteById(quoteId)
                if (quote != null) {
                    if (!quote.isFavorite) repository.toggleFavorite(quoteId, true)
                    // Re-post without the Save button so the notification reflects the new state.
                    NotificationHelper.showQuoteNotification(context, text, author, notificationId, quoteId, saved = true)
                    WidgetRefreshScheduler.requestUpdate(context)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, if (german) "Zu Favoriten hinzugefügt" else "Saved to favorites", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SAVE = "app.brokoli5191.quote.action.SAVE_QUOTE"
        const val EXTRA_QUOTE_ID = "app.brokoli5191.quote.extra.ACTION_QUOTE_ID"
        const val EXTRA_NOTIFICATION_ID = "app.brokoli5191.quote.extra.NOTIFICATION_ID"
        const val EXTRA_TEXT = "app.brokoli5191.quote.extra.QUOTE_TEXT"
        const val EXTRA_AUTHOR = "app.brokoli5191.quote.extra.QUOTE_AUTHOR"
    }
}
