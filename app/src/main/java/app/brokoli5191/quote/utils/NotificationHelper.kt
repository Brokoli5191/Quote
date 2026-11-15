package app.brokoli5191.quote.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.brokoli5191.quote.MainActivity
import app.brokoli5191.quote.R
import app.brokoli5191.quote.data.QuoteSubmissionStatus
import app.brokoli5191.quote.data.SubmissionUpdate
import app.brokoli5191.quote.ui.formattedQuote

object NotificationHelper {
    const val CHANNEL_ID = "aura_daily_quotes"
    const val SUBMISSION_CHANNEL_ID = "submission_updates"

    const val NO_QUOTE_ID = -1
    private const val SUBMISSION_NOTIFICATION_ID = 1003
    private const val PREFS = "aura_prefs"
    private const val SNIPPET_LENGTH = 60

    /**
     * Creates both channels (or updates their names and descriptions) in the current app
     * language. Safe to call repeatedly; call it after a language change so the system
     * notification settings follow the app.
     */
    fun refreshChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val german = isGerman(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                if (german) "Tägliches Quote" else "Daily Quote of the Day",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = if (german) "Sendet täglich ein Quote zu deiner gewählten Zeit" else "Sends daily quotes of wisdom at your preferred time"
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                SUBMISSION_CHANNEL_ID,
                if (german) "Einreichungen" else "Submission updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = if (german) "Meldet, wenn über ein eingereichtes Quote entschieden wurde" else "Tells you when a quote you submitted has been reviewed"
            }
        )
    }

    /** False when the user or system blocks notifications (permission on API 33+, or switched off). */
    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Posts one notification summarizing review decisions; tapping it opens My Quotes. */
    fun showSubmissionUpdates(context: Context, updates: List<SubmissionUpdate>) {
        if (updates.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("submission_notifications_enabled", true)) return
        if (!canPostNotifications(context)) return

        refreshChannels(context)
        val german = isGerman(context)
        val lines = updates.map { submissionLine(it, german) }
        val title = if (updates.size == 1) {
            submissionTitle(updates.first().newStatus, german)
        } else if (german) {
            "${updates.size} Neuigkeiten zu deinen Quotes"
        } else {
            "${updates.size} updates on your quotes"
        }

        val builder = NotificationCompat.Builder(context, SUBMISSION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_quote)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openAppIntent(context, AppLinks.DEST_MY_QUOTES, NO_QUOTE_ID, 1))
            .setAutoCancel(true)
        builder.setStyle(
            if (updates.size == 1) NotificationCompat.BigTextStyle().bigText(lines.first())
            else NotificationCompat.InboxStyle().also { style -> lines.forEach(style::addLine) }
        )
        notifySafely(context, SUBMISSION_NOTIFICATION_ID, builder.build())
    }

    fun showQuoteNotification(
        context: Context,
        text: String,
        author: String,
        notificationId: Int = 1001,
        quoteId: Int = NO_QUOTE_ID,
        saved: Boolean = false
    ) {
        if (!canPostNotifications(context)) return
        refreshChannels(context)
        val german = isGerman(context)
        val language = if (german) "de" else "en"
        val quoteText = formattedQuote(text, author, language)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_quote)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentTitle(if (german) "Dein tägliches Quote" else "Your Daily Quote")
            .setContentText(quoteText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(quoteText))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent(context, AppLinks.DEST_DAILY, quoteId, 0))
            // Only the re-post after "Save" stays silent; a new day's reminder that replaces one still
            // showing must alert again.
            .setOnlyAlertOnce(saved)
            .setAutoCancel(true)

        if (quoteId != NO_QUOTE_ID && !saved) {
            val saveIntent = Intent(context, QuoteActionReceiver::class.java).apply {
                action = QuoteActionReceiver.ACTION_SAVE
                putExtra(QuoteActionReceiver.EXTRA_QUOTE_ID, quoteId)
                putExtra(QuoteActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(QuoteActionReceiver.EXTRA_TEXT, text)
                putExtra(QuoteActionReceiver.EXTRA_AUTHOR, author)
            }
            builder.addAction(
                0,
                if (german) "Speichern" else "Save",
                PendingIntent.getBroadcast(context, notificationId, saveIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
        }
        val shareIntent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, quoteText)
            },
            null
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        builder.addAction(
            0,
            if (german) "Teilen" else "Share",
            PendingIntent.getActivity(context, notificationId, shareIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )

        notifySafely(context, notificationId, builder.build())
    }

    private fun openAppIntent(context: Context, destination: String, quoteId: Int, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AppLinks.EXTRA_DEST, destination)
            if (quoteId != NO_QUOTE_ID) putExtra(AppLinks.EXTRA_QUOTE_ID, quoteId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notifySafely(context: Context, id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    private fun isGerman(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("app_language", "en") == "de"

    private fun submissionTitle(status: String, german: Boolean): String = when (status) {
        QuoteSubmissionStatus.APPROVED -> if (german) "Dein Quote wurde freigegeben" else "Your quote was approved"
        QuoteSubmissionStatus.REJECTED -> if (german) "Dein Quote wurde nicht freigegeben" else "Your quote was not approved"
        else -> if (german) "Dein Quote ist nicht mehr veröffentlicht" else "Your quote is no longer published"
    }

    private fun submissionLine(update: SubmissionUpdate, german: Boolean): String {
        val snippet = update.quoteText.trim().let { if (it.length > SNIPPET_LENGTH) it.take(SNIPPET_LENGTH).trimEnd() + "…" else it }
        val quoted = if (german) "„$snippet“" else "“$snippet”"
        val outcome = when (update.newStatus) {
            QuoteSubmissionStatus.APPROVED ->
                if (german) "ist jetzt in der Community-Sammlung." else "is now in the community collection."
            QuoteSubmissionStatus.REJECTED ->
                if (german) "wurde nicht freigegeben." else "was not approved."
            else ->
                if (german) "ist nicht mehr veröffentlicht." else "is no longer published."
        }
        val note = update.reviewNote?.trim().orEmpty()
        val noteText = if (note.isEmpty() || update.newStatus == QuoteSubmissionStatus.APPROVED) ""
        else (if (german) " Hinweis: " else " Note: ") + note
        return "$quoted $outcome$noteText"
    }
}
