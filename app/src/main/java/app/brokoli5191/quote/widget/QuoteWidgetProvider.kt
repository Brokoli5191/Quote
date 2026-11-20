package app.brokoli5191.quote.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import androidx.core.os.BundleCompat
import app.brokoli5191.quote.MainActivity
import app.brokoli5191.quote.R
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.FallbackQuote
import app.brokoli5191.quote.data.InstallationSeed
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.data.QuoteRepository
import app.brokoli5191.quote.data.QuoteSourceMode
import app.brokoli5191.quote.data.dailyDateKey
import app.brokoli5191.quote.data.hasUnknownAuthor
import app.brokoli5191.quote.ui.formattedQuote
import app.brokoli5191.quote.ui.localizeUi
import app.brokoli5191.quote.utils.AppLinks
import app.brokoli5191.quote.utils.NotificationScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class QuoteWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        WidgetRefreshScheduler.scheduleMidnightRefresh(context)
    }

    override fun onDisabled(context: Context) {
        WidgetRefreshScheduler.cancel(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgets(context, manager, ids)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetConfig.delete(context, it) }
    }

    // AppWidgetProvider calls onUpdate for the new ids right after this, which renders them.
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        restoreWidgetConfigs(context, oldWidgetIds, newWidgetIds, placedWidgetIds(context).toSet())
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            AppLinks.ACTION_UPDATE_WIDGET ->
                updateWidgets(context, AppWidgetManager.getInstance(context), placedWidgetIds(context))
            WidgetRefreshScheduler.ACTION_MIDNIGHT_REFRESH -> {
                // Wakes the app once a day: a reminder the system dropped (exact-alarm permission
                // revoked) is put back here too.
                NotificationScheduler.ensureScheduled(context)
                updateWidgets(context, AppWidgetManager.getInstance(context), placedWidgetIds(context))
            }
        }
    }

    private fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Without a placed widget nothing may pick (and store) today's quote for it.
        if (ids.isEmpty()) {
            WidgetRefreshScheduler.cancel(context)
            return
        }
        WidgetRefreshScheduler.scheduleMidnightRefresh(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val quote = widgetQuote(context)
                ids.forEach { updateAppWidget(context, manager, it, quote) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "QuoteWidget"
        private const val PREFS = "aura_prefs"

        /** Action of the widget's tap intent; keeps its PendingIntent apart from the notifications' ones. */
        const val ACTION_OPEN_DAILY = "app.brokoli5191.quote.widget.OPEN_DAILY"

        /** RemoteViews(Map) takes at most 16 sizes. */
        private const val MAX_SIZED_LAYOUTS = 16

        /**
         * Today's quote in the app's language and source settings, or [FallbackQuote] when the
         * database is empty (before the first app start seeds it), nothing matches, or it fails.
         */
        suspend fun widgetQuote(context: Context): QuoteEntity {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val language = appLanguage(context)
            val quote = try {
                QuoteRepository(AppDatabase.getInstance(context).quoteDao(), InstallationSeed.get(context))
                    .getDailyQuote(
                        dailyDateKey(),
                        prefs.getString("quote_source_mode", QuoteSourceMode.ALL) ?: QuoteSourceMode.ALL,
                        language,
                        prefs.getBoolean("show_anonymous_quotes", false)
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Daily quote could not be loaded", e)
                null
            }
            return quote ?: QuoteEntity(text = FallbackQuote.text(language), author = FallbackQuote.AUTHOR, category = "Life")
        }

        /**
         * Renders [quote] into widget [appWidgetId] at every size the launcher may show it. Never
         * throws: a failed update is logged and leaves the widget as it was.
         */
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            quote: QuoteEntity
        ) {
            try {
                val language = appLanguage(context)
                val author = if (quote.hasUnknownAuthor) "" else quote.author
                val config = WidgetConfig.read(context, appWidgetId)
                val description = widgetContentDescription(quote.text, author, language)
                val openDaily = openDailyIntent(context, appWidgetId)
                val views = sizedViews(appWidgetManager.getAppWidgetOptions(appWidgetId), context.resources.displayMetrics) { size ->
                    RemoteViews(context.packageName, R.layout.quote_widget_layout).apply {
                        setImageViewBitmap(
                            R.id.widget_image,
                            QuoteCardRenderer.render(
                                context,
                                quote.text,
                                author,
                                QuoteCardRenderer.LABEL,
                                config,
                                size.widthPx,
                                size.heightPx,
                                size.density
                            )
                        )
                        setContentDescription(R.id.widget_image, description)
                        setOnClickPendingIntent(R.id.widget_image, openDaily)
                    }
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.w(TAG, "Widget $appWidgetId could not be updated", e)
            }
        }

        /**
         * One layout per size the launcher may show: the size list on Android 12+, otherwise the
         * portrait (min width x max height) and landscape (max width x min height) pair, of which
         * the launcher picks the one for its own orientation.
         */
        private fun sizedViews(
            options: Bundle,
            metrics: DisplayMetrics,
            cardViews: (WidgetBitmapSize) -> RemoteViews
        ): RemoteViews {
            fun bitmapSizes(sizesDp: List<Pair<Float, Float>>) =
                widgetBitmapSizes(sizesDp, metrics.density, metrics.widthPixels, metrics.heightPixels)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val sizes = BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
                    .orEmpty()
                    .distinct()
                    .take(MAX_SIZED_LAYOUTS)
                if (sizes.isNotEmpty()) {
                    val bitmaps = bitmapSizes(sizes.map { it.width to it.height })
                    return if (sizes.size == 1) {
                        cardViews(bitmaps.single())
                    } else {
                        RemoteViews(sizes.zip(bitmaps).associate { (size, bitmap) -> size to cardViews(bitmap) })
                    }
                }
            }
            val portrait = widgetSizeDpFromOptions(options, portrait = true).let { (w, h) -> w.toFloat() to h.toFloat() }
            val landscape = widgetSizeDpFromOptions(options, portrait = false).let { (w, h) -> w.toFloat() to h.toFloat() }
            if (portrait == landscape) return cardViews(bitmapSizes(listOf(portrait)).single())
            val (landscapeBitmap, portraitBitmap) = bitmapSizes(listOf(landscape, portrait))
            return RemoteViews(cardViews(landscapeBitmap), cardViews(portraitBitmap))
        }

        private fun openDailyIntent(context: Context, appWidgetId: Int): PendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_DAILY
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(AppLinks.EXTRA_DEST, AppLinks.DEST_DAILY)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun placedWidgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, QuoteWidgetProvider::class.java))

        private fun appLanguage(context: Context): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("app_language", QuoteLanguage.ENGLISH)
                ?: QuoteLanguage.ENGLISH
    }
}

/** What TalkBack reads for the widget: "Daily Quote: "text" — author" in the app language. */
internal fun widgetContentDescription(text: String, author: String, language: String): String =
    "${localizeUi("Daily Quote", language)}: ${formattedQuote(text, author, language)}"

/** One bitmap of a widget update: its size in px and the density the card is drawn with. */
internal data class WidgetBitmapSize(val widthPx: Int, val heightPx: Int, val density: Float)

/** Share of AppWidgetService's bitmap memory limit that one widget update may use. */
private const val BITMAP_MEMORY_SHARE = 0.5

/**
 * Bitmap sizes for one widget update that shows the card at each of [sizesDp] (width to height).
 * AppWidgetService rejects an update whose bitmaps together need more than 6 bytes per screen
 * pixel (4 bytes per ARGB_8888 pixel here). All bitmaps are therefore scaled down together to
 * [BITMAP_MEMORY_SHARE] of that limit; the scaled density keeps the card's dp layout unchanged.
 */
internal fun widgetBitmapSizes(
    sizesDp: List<Pair<Float, Float>>,
    density: Float,
    screenWidthPx: Int,
    screenHeightPx: Int
): List<WidgetBitmapSize> {
    val budgetBytes = 6.0 * screenWidthPx * screenHeightPx * BITMAP_MEMORY_SHARE
    val neededBytes = sizesDp.sumOf { (width, height) -> 4.0 * width * density * height * density }
    val scale = if (budgetBytes > 0 && neededBytes > budgetBytes) sqrt(budgetBytes / neededBytes).toFloat() else 1f
    val renderDensity = density * scale
    return sizesDp.map { (width, height) ->
        WidgetBitmapSize(
            (width * renderDensity).toInt().coerceAtLeast(1),
            (height * renderDensity).toInt().coerceAtLeast(1),
            renderDensity
        )
    }
}

/**
 * Moves the settings of widgets restored from a backup from their old ids to the new ones. Every
 * old setting is read before anything is deleted or written, because an old id can be the new id
 * of another restored widget. Old ids of widgets placed on this device ([placedIds]) keep theirs.
 */
internal fun restoreWidgetConfigs(context: Context, oldIds: IntArray, newIds: IntArray, placedIds: Set<Int>) {
    val restored = oldIds.zip(newIds).map { (oldId, newId) ->
        newId to oldId.takeIf { WidgetConfig.exists(context, it) }?.let { WidgetConfig.read(context, it) }
    }
    (oldIds.filter { it !in placedIds } + newIds.toList()).forEach { WidgetConfig.delete(context, it) }
    restored.forEach { (newId, config) -> if (config != null) WidgetConfig.write(context, newId, config) }
}
