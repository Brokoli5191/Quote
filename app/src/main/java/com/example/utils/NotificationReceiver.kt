package com.example.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule notification alarm on boot if it was enabled
            val prefs = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("daily_reminder_enabled", false)
            if (enabled) {
                val hour = prefs.getInt("daily_reminder_hour", 8)
                val minute = prefs.getInt("daily_reminder_minute", 0)
                NotificationScheduler.scheduleDailyNotification(context, hour, minute)
            }
            return
        }

        // Fetch a daily quote asynchronously using coroutines
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val db = AppDatabase.getInstance(context)
                val quotes = db.quoteDao().getAllQuotesSync()
                val quote = if (quotes.isNotEmpty()) {
                    quotes.random()
                } else {
                    null
                }

                val quoteText = quote?.text ?: "You have power over your mind, not outside events. Realize this, and you will find strength."
                val quoteAuthor = quote?.author ?: "Marcus Aurelius"

                showNotification(context, quoteText, quoteAuthor)
            } catch (e: Exception) {
                e.printStackTrace()
                // Beautiful static fallback in case of db errors
                showNotification(
                    context,
                    "You have power over your mind, not outside events. Realize this, and you will find strength.",
                    "Marcus Aurelius"
                )
            }
        }
    }

    private fun showNotification(context: Context, text: String, author: String) {
        val channelId = "aura_daily_quotes"
        val notificationId = 1001

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Daily Quote of the Day",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Sends daily quotes of wisdom at your preferred time"
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Stable system fallback icon
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentTitle("Your Daily Quote")
            .setContentText("\"$text\" — $author")
            .setStyle(NotificationCompat.BigTextStyle().bigText("\"$text\"\n\n— $author"))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
