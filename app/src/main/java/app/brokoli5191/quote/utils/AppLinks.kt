package app.brokoli5191.quote.utils

/**
 * Intent extras for entry points into MainActivity (widget, notification, notification actions,
 * launcher shortcuts, share target). MainActivity handles them in onCreate and onNewIntent.
 */
object AppLinks {
    /** Which screen to open; one of the DEST_* values. */
    const val EXTRA_DEST = "app.brokoli5191.quote.extra.DEST"

    /** Optional quote id that the destination refers to (e.g. the notified daily quote). */
    const val EXTRA_QUOTE_ID = "app.brokoli5191.quote.extra.QUOTE_ID"

    const val DEST_DAILY = "daily"
    const val DEST_FAVORITES = "favorites"
    const val DEST_MY_QUOTES = "my_quotes"
    const val DEST_NEW_QUOTE = "new_quote"

    /** Broadcast action that asks QuoteWidgetProvider to re-render all widgets. */
    const val ACTION_UPDATE_WIDGET = "app.brokoli5191.quote.UPDATE_WIDGET"
}
