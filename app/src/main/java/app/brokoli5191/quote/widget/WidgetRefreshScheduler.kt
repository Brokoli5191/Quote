package app.brokoli5191.quote.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import app.brokoli5191.quote.utils.AppLinks
import java.util.Calendar
import java.util.TimeZone

/**
 * Central place for widget refreshes. [requestUpdate] re-renders all widgets now;
 * [scheduleMidnightRefresh] arms a refresh at the next local midnight so the widget follows the
 * new day; [cancel] removes it when the last widget is removed.
 */
object WidgetRefreshScheduler {
    /** Action of the midnight alarm's explicit broadcast to [QuoteWidgetProvider]. */
    const val ACTION_MIDNIGHT_REFRESH = "app.brokoli5191.quote.widget.MIDNIGHT_REFRESH"

    private const val MIDNIGHT_REQUEST_CODE = 5191

    /** The refresh runs a few seconds after midnight, when the new day's date key is in effect. */
    private const val AFTER_MIDNIGHT_MS = 5_000L

    /** Inexact window (the platform minimum on Android 12+). Plain set() may slip by hours. */
    private const val WINDOW_MS = 10 * 60_000L

    fun requestUpdate(context: Context) {
        context.sendBroadcast(Intent(AppLinks.ACTION_UPDATE_WIDGET).apply {
            component = ComponentName(context, QuoteWidgetProvider::class.java)
        })
    }

    /**
     * Arms the refresh after the next local midnight, replacing an armed one. Without a placed
     * widget it cancels instead, so callers need no check. The alarm does not wake the device: a
     * sleeping device delivers it when it wakes up, before the home screen is seen. Clock and
     * time zone changes need a new call (BootReceiver), the provider re-arms on every refresh.
     */
    fun scheduleMidnightRefresh(context: Context) {
        if (!hasWidgets(context)) {
            cancel(context)
            return
        }
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = midnightIntent(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        alarmManager.setWindow(
            AlarmManager.RTC,
            nextMidnightRefreshAt(System.currentTimeMillis(), TimeZone.getDefault()),
            WINDOW_MS,
            pendingIntent
        )
    }

    fun cancel(context: Context) {
        val pendingIntent = midnightIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /** Start of the alarm window after [nowMillis]: the next local midnight in [timeZone], plus a few seconds. */
    internal fun nextMidnightRefreshAt(nowMillis: Long, timeZone: TimeZone): Long {
        val midnight = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, 1)
            // A midnight skipped by a DST change resolves to the first valid time of that day.
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        // When clocks fall back onto midnight (Cuba), midnight occurs twice; Calendar picks the later one.
        val repeated = timeZone.getOffset(midnight - 1) - timeZone.getOffset(midnight)
        val dayStart = if (repeated > 0 && midnight - repeated > nowMillis) midnight - repeated else midnight
        return dayStart + AFTER_MIDNIGHT_MS
    }

    private fun hasWidgets(context: Context): Boolean =
        AppWidgetManager.getInstance(context)
            ?.getAppWidgetIds(ComponentName(context, QuoteWidgetProvider::class.java))
            ?.isNotEmpty() == true

    private fun midnightIntent(context: Context, flag: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        MIDNIGHT_REQUEST_CODE,
        Intent(context, QuoteWidgetProvider::class.java).setAction(ACTION_MIDNIGHT_REFRESH),
        flag or PendingIntent.FLAG_IMMUTABLE
    )
}
