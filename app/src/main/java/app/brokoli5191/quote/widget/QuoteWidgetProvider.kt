package app.brokoli5191.quote.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.widget.RemoteViews
import app.brokoli5191.quote.MainActivity
import app.brokoli5191.quote.R
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.InstallationSeed
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteRepository
import app.brokoli5191.quote.data.QuoteSourceMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QuoteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgets(context, manager, ids)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetConfig.delete(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "app.brokoli5191.quote.UPDATE_WIDGET") {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, QuoteWidgetProvider::class.java))
            updateWidgets(context, manager, ids)
        }
    }

    private fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pendingResult = goAsync()
        val repository = QuoteRepository(
            AppDatabase.getInstance(context).quoteDao(),
            InstallationSeed.get(context)
        )
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val sourceMode = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
                    .getString("quote_source_mode", QuoteSourceMode.ALL) ?: QuoteSourceMode.ALL
                val quote = runCatching { repository.getDailyQuote(date, sourceMode) }.getOrNull()
                    ?: QuoteEntity(
                        text = if (sourceMode == QuoteSourceMode.COMMUNITY) "No community quotes available yet." else "Stay hungry. Stay foolish.",
                        author = if (sourceMode == QuoteSourceMode.COMMUNITY) "Open Quote to sync" else "Steve Jobs",
                        category = "Life"
                    )
                ids.forEach { updateAppWidget(context, manager, it, quote) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            quote: QuoteEntity
        ) {
            val views = RemoteViews(context.packageName, R.layout.quote_widget_layout)
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val portrait = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            val widthDp = (if (portrait) minWidth else maxWidth).takeIf { it > 0 } ?: 280
            val heightDp = (if (portrait) maxHeight else minHeight).takeIf { it > 0 } ?: 140
            val scale = 3f
            val bitmap = drawWidgetBitmap(
                quote,
                WidgetConfig.read(context, appWidgetId),
                (widthDp * scale).toInt().coerceAtLeast(120),
                (heightDp * scale).toInt().coerceAtLeast(100),
                scale
            )
            views.setImageViewBitmap(R.id.widget_image, bitmap)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            views.setOnClickPendingIntent(
                R.id.widget_image,
                PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun drawWidgetBitmap(
            quote: QuoteEntity,
            config: WidgetConfig,
            width: Int,
            height: Int,
            density: Float
        ): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val scale = config.visualScale.coerceIn(0.65f, 1f)
            val insetX = width * (1f - scale) / 2f
            val insetY = height * (1f - scale) / 2f
            val rect = RectF(
                insetX,
                insetY,
                width - insetX,
                height - insetY
            )
            val radius = config.cornerRadius.coerceIn(0f, 48f) * density * scale
            val bgStart = parseColor(config.backgroundStart, "#594983")
            val bgEnd = parseColor(config.backgroundEnd, "#37265E")

            paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), bgStart, bgEnd, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.shader = null

            if (config.blurBackground) {
                val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
                canvas.save()
                canvas.clipPath(clip)
                paint.maskFilter = BlurMaskFilter(28f * density, BlurMaskFilter.Blur.NORMAL)
                paint.color = withAlpha(lighten(bgStart), 115)
                canvas.drawCircle(width * 0.18f, height * 0.2f, width * 0.24f, paint)
                paint.color = withAlpha(lighten(bgEnd), 105)
                canvas.drawCircle(width * 0.85f, height * 0.8f, width * 0.32f, paint)
                paint.maskFilter = null
                canvas.restore()
            }

            if (config.borderEnabled) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f * density
                paint.color = parseColor(config.borderColor, "#2DFFFFFF")
                canvas.drawRoundRect(rect, radius, radius, paint)
                paint.style = Paint.Style.FILL
            }

            val cardWidth = rect.width()
            val cardHeight = rect.height()
            val padX = if (cardWidth < 160 * density) 12f * density else 20f * density
            val padY = if (cardHeight < 100 * density) 8f * density else 16f * density
            val canShowLabel = config.showLabel && cardHeight >= 90 * density && cardWidth >= 130 * density
            val canShowIcon = config.showIcon && cardHeight >= 80 * density && cardWidth >= 120 * density
            val canShowAuthor = config.showAuthor && cardHeight >= 65 * density
            val labelSize = (cardHeight * 0.07f).coerceIn(11f * density, 18f * density)
            val authorSize = ((cardHeight * 0.08f) * config.authorSizeScale).coerceIn(9f * density, 28f * density)
            val labelX = rect.left + cardWidth * config.labelX.coerceIn(0f, 0.9f)
            val labelY = rect.top + cardHeight * config.labelY.coerceIn(0.08f, 0.9f) + labelSize
            val iconX = rect.left + cardWidth * config.iconX.coerceIn(0.1f, 0.98f)
            val iconY = rect.top + cardHeight * config.iconY.coerceIn(0.08f, 0.9f) + labelSize
            val quoteX = rect.left + cardWidth * config.quoteX.coerceIn(0f, 0.85f)
            val quoteY = rect.top + cardHeight * config.quoteY.coerceIn(0.12f, 0.9f)
            val authorX = rect.left + cardWidth * config.authorX.coerceIn(0f, 0.85f)
            val authorY = rect.top + cardHeight * config.authorY.coerceIn(0.2f, 0.98f)
            val quoteTypeface = quoteTypeface(config.font)
            val authorTypeface = authorTypeface(config.font)
            var quoteSize = ((if (cardWidth < cardHeight) cardWidth * 0.14f else cardHeight * 0.20f) * config.quoteSizeScale)
                .coerceIn(12f * density, 42f * density)
            var lineHeight = quoteSize * 1.2f
            val availableWidth = (rect.right - padX - quoteX).toInt().coerceAtLeast(80)
            val maxHeight = (rect.bottom - padY - quoteY).coerceAtLeast(quoteSize)
            val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = quoteTypeface }
            while (quoteSize > 8f * density) {
                quotePaint.textSize = quoteSize
                if (wrappedLineCount(quote.text, availableWidth, quotePaint) * lineHeight <= maxHeight) break
                quoteSize -= 0.5f * density
                lineHeight = quoteSize * 1.2f
            }

            if (canShowLabel) {
                paint.color = parseColor(config.labelColor, "#D0BCFF")
                paint.textSize = labelSize
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                canvas.drawText("QUOTE", labelX, labelY, paint)
            }
            if (canShowIcon) {
                paint.color = parseColor(config.labelColor, "#D0BCFF")
                paint.textSize = labelSize * 1.35f
                paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("✦", iconX, iconY, paint)
                paint.textAlign = Paint.Align.LEFT
            }

            paint.color = parseColor(config.quoteColor, "#E9DDFF")
            paint.textSize = quoteSize
            paint.typeface = quoteTypeface
            drawWrappedText(canvas, quote.text, quoteX, quoteY + quoteSize, availableWidth, paint, lineHeight, maxHeight)

            if (canShowAuthor) {
                paint.color = parseColor(config.authorColor, "#A0D2AD")
                paint.textSize = authorSize
                paint.typeface = authorTypeface
                val authorWidth = (rect.right - padX - authorX).toInt().coerceAtLeast(80)
                drawSingleLineEllipsized(canvas, "— ${quote.author}", authorX, authorY, authorWidth, paint)
            }
            return bitmap
        }

        private fun quoteTypeface(font: String): Typeface = when (font) {
            "Sans" -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            "Monospace" -> Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            "Serif Bold" -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            else -> Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }

        private fun authorTypeface(font: String): Typeface = when (font) {
            "Monospace" -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            "Serif", "Serif Bold" -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            else -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        private fun parseColor(value: String, fallback: String): Int = runCatching {
            Color.parseColor(if (value.startsWith("#")) value else "#$value")
        }.getOrElse { Color.parseColor(fallback) }

        private fun lighten(color: Int): Int = Color.rgb(
            (Color.red(color) + 75).coerceAtMost(255),
            (Color.green(color) + 75).coerceAtMost(255),
            (Color.blue(color) + 75).coerceAtMost(255)
        )

        private fun withAlpha(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

        private fun drawWrappedText(
            canvas: Canvas,
            text: String,
            x: Float,
            y: Float,
            maxWidth: Int,
            paint: Paint,
            lineHeight: Float,
            maxHeight: Float
        ) {
            val words = text.split(" ")
            var line = ""
            var currentY = y
            for (word in words) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                    if (currentY - y + lineHeight > maxHeight) {
                        canvas.drawText(ellipsize(line, maxWidth, paint), x, currentY, paint)
                        return
                    }
                    canvas.drawText(line, x, currentY, paint)
                    line = word
                    currentY += lineHeight
                } else line = candidate
            }
            if (line.isNotEmpty() && currentY - y + lineHeight <= maxHeight) canvas.drawText(line, x, currentY, paint)
        }

        private fun drawSingleLineEllipsized(canvas: Canvas, text: String, x: Float, y: Float, width: Int, paint: Paint) {
            canvas.drawText(ellipsize(text, width, paint), x, y, paint)
        }

        private fun ellipsize(text: String, width: Int, paint: Paint): String {
            if (paint.measureText(text) <= width) return text
            var shortened = text
            while (shortened.isNotEmpty() && paint.measureText("$shortened…") > width) shortened = shortened.dropLast(1)
            return "$shortened…"
        }

        private fun wrappedLineCount(text: String, maxWidth: Int, paint: Paint): Int {
            var count = 1
            var line = ""
            text.split(" ").forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                    count++
                    line = word
                } else line = candidate
            }
            return count
        }
    }
}
