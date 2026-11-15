package app.brokoli5191.quote.utils

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import app.brokoli5191.quote.widget.WidgetRefreshScheduler

/**
 * Re-arms everything AlarmManager forgets: alarms are cleared on reboot and app update, the
 * daily reminder is computed in local wall-clock time (so it must follow clock and time zone
 * changes), and its exact/inexact choice depends on the exact-alarm permission.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return
        Log.d("BootReceiver", "Received $action, rescheduling alarms...")
        NotificationScheduler.ensureScheduled(context)
        WidgetRefreshScheduler.scheduleMidnightRefresh(context)
        WidgetRefreshScheduler.requestUpdate(context)
    }

    private companion object {
        val HANDLED_ACTIONS: Set<String> = buildSet {
            add(Intent.ACTION_BOOT_COMPLETED)
            add(Intent.ACTION_MY_PACKAGE_REPLACED)
            add(Intent.ACTION_TIME_CHANGED)
            add(Intent.ACTION_TIMEZONE_CHANGED)
            add("android.intent.action.QUICKBOOT_POWERON")
            add("com.htc.intent.action.QUICKBOOT_POWERON")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
            }
        }
    }
}
