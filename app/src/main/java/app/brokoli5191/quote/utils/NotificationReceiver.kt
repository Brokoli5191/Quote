package app.brokoli5191.quote.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.FallbackQuote
import app.brokoli5191.quote.data.InstallationSeed
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.data.QuoteRepository
import app.brokoli5191.quote.data.QuoteSourceMode
import app.brokoli5191.quote.data.dailyDateKey
import app.brokoli5191.quote.data.hasUnknownAuthor
import app.brokoli5191.quote.widget.WidgetRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("daily_reminder_enabled", false)) return
        // The alarm is one-shot, so arm tomorrow's first.
        NotificationScheduler.ensureScheduled(context)

        val language = prefs.getString("app_language", QuoteLanguage.ENGLISH) ?: QuoteLanguage.ENGLISH
        // goAsync() tells Android we're still working after onReceive returns
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = QuoteRepository(AppDatabase.getInstance(context).quoteDao(), InstallationSeed.get(context))
                val quote = repository.getDailyQuote(
                    dailyDateKey(),
                    prefs.getString("quote_source_mode", QuoteSourceMode.ALL) ?: QuoteSourceMode.ALL,
                    language,
                    prefs.getBoolean("show_anonymous_quotes", false)
                )
                if (quote != null) {
                    NotificationHelper.showQuoteNotification(
                        context,
                        quote.text,
                        if (quote.hasUnknownAuthor) "" else quote.author,
                        quoteId = quote.id
                    )
                } else {
                    showFallback(context, language)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showFallback(context, language)
            }
            try {
                // The day's quote was just picked; bring the widget along.
                WidgetRefreshScheduler.requestUpdate(context)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showFallback(context: Context, language: String) {
        NotificationHelper.showQuoteNotification(context, FallbackQuote.text(language), FallbackQuote.AUTHOR)
    }
}
