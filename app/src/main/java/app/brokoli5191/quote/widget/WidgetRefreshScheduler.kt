package app.brokoli5191.quote.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import app.brokoli5191.quote.utils.AppLinks

/**
 * Central place for widget refreshes. [requestUpdate] re-renders all widgets now;
 * [scheduleMidnightRefresh] arms a refresh at the next local midnight so the widget follows the
 * new day; [cancel] removes it when the last widget is removed.
 */
object WidgetRefreshScheduler {
    fun requestUpdate(context: Context) {
        context.sendBroadcast(Intent(AppLinks.ACTION_UPDATE_WIDGET).apply {
            component = ComponentName(context, QuoteWidgetProvider::class.java)
        })
    }

    /** OWNER: widget-render package. Arms the next-midnight refresh (idempotent). */
    fun scheduleMidnightRefresh(context: Context) {
    }

    /** OWNER: widget-render package. Cancels the midnight refresh. */
    fun cancel(context: Context) {
    }
}
