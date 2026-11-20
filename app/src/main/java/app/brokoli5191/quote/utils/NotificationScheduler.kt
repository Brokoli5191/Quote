package app.brokoli5191.quote.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import app.brokoli5191.quote.data.dailyDateKey
import java.util.Calendar

object NotificationScheduler {
    private const val ALARM_REQ_CODE = 4242

    /** aura_prefs: dailyDateKey() of the day the reminder last fired. */
    const val KEY_LAST_REMINDER_DATE = "daily_reminder_last_date"

    /**
     * Whether the reminder can fire on the minute. Without the exact-alarm permission (the default on
     * Android 14+) Android may deliver it up to about an hour late.
     */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return true
        return alarmManager.canScheduleExactAlarms()
    }

    /**
     * Re-arms the daily reminder from the stored prefs if it is enabled. Safe to call on every
     * app start: Auto Backup restores, force-stops and device transfers keep the pref but lose
     * the alarm.
     */
    fun ensureScheduled(context: Context) {
        val prefs = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("daily_reminder_enabled", false)) return
        scheduleDailyNotification(
            context,
            prefs.getInt("daily_reminder_hour", 8),
            prefs.getInt("daily_reminder_minute", 0),
            skipToday = prefs.getString(KEY_LAST_REMINDER_DATE, null) == dailyDateKey()
        )
    }

    /**
     * The user set the reminder (time or switch): a time later today may fire today even if an
     * earlier one already did.
     */
    fun scheduleForUserChange(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE).edit()
            .remove(KEY_LAST_REMINDER_DATE).apply()
        scheduleDailyNotification(context, hour, minute)
    }

    /**
     * Arms the one-shot alarm for the next [hour]:[minute], or for tomorrow's when [skipToday] (today's
     * reminder already fired and the clock or time zone was set back since).
     */
    fun scheduleDailyNotification(context: Context, hour: Int, minute: Int, skipToday: Boolean = false) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, NotificationReceiver::class.java)
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel any existing matching alarms first
        alarmManager.cancel(pendingIntent)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If the time is in the past, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis() || skipToday) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val isExactPossible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.canScheduleExactAlarms()
                } else {
                    true
                }

                if (isExactPossible) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d("NotificationScheduler", "Scheduled daily alarm at $hour:$minute")
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback immediately to standard non-exact allow-while-idle if exact fails due to standby/security restrictions
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun cancelDailyNotification(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("NotificationScheduler", "Canceled daily quote alarm")
        }
    }
}
