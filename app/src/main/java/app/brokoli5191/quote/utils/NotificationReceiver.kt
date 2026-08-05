package app.brokoli5191.quote.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.QuoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("daily_reminder_enabled", false)
        if (enabled) {
            val hour = prefs.getInt("daily_reminder_hour", 8)
            val minute = prefs.getInt("daily_reminder_minute", 0)
            NotificationScheduler.scheduleDailyNotification(context, hour, minute)
        }

        // goAsync() tells Android we're still working after onReceive returns
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val repository = QuoteRepository(db.quoteDao(), app.brokoli5191.quote.data.InstallationSeed.get(context))
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val quote = repository.getDailyQuote(todayStr)
                NotificationHelper.showQuoteNotification(context, quote.text, quote.author)
            } catch (e: Exception) {
                e.printStackTrace()
                NotificationHelper.showQuoteNotification(
                    context,
                    "You have power over your mind, not outside events. Realize this, and you will find strength.",
                    "Marcus Aurelius"
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
