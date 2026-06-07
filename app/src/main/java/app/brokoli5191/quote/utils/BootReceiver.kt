package app.brokoli5191.quote.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d("BootReceiver", "Device boot completed, rescheduling alarms...")
            val prefs = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("daily_reminder_enabled", false)
            if (enabled) {
                val hour = prefs.getInt("daily_reminder_hour", 8)
                val minute = prefs.getInt("daily_reminder_minute", 0)
                NotificationScheduler.scheduleDailyNotification(context, hour, minute)
                Log.d("BootReceiver", "Rescheduled daily reminder alarm on device boot.")
            }
        }
    }
}
