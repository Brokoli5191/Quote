package app.brokoli5191.quote.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.brokoli5191.quote.MainActivity

object NotificationHelper {
    const val CHANNEL_ID = "aura_daily_quotes"

    fun showQuoteNotification(context: Context, text: String, author: String, notificationId: Int = 1001) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(app.brokoli5191.quote.R.drawable.ic_notification_quote)
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
