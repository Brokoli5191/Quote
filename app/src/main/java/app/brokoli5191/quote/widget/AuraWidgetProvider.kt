package app.brokoli5191.quote.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import app.brokoli5191.quote.MainActivity
import app.brokoli5191.quote.R
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AuraWidgetProvider : AppWidgetProvider() {
 
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val database = AppDatabase.getInstance(context)
        val repository = QuoteRepository(database.quoteDao())
 
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date())
 
        CoroutineScope(Dispatchers.Default).launch {
            val quote = try {
                repository.getDailyQuote(todayStr)
            } catch (e: Exception) {
                QuoteEntity(
                    text = "Stay hungry. Stay foolish.",
                    author = "Steve Jobs",
                    category = "Love",
                    aboutAuthor = "Steve Jobs was co-founder of Apple Inc.",
                    tags = "Focus"
                )
            }
            
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, quote)
            }
        }
    }
 
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val database = AppDatabase.getInstance(context)
        val repository = QuoteRepository(database.quoteDao())
 
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date())
 
        CoroutineScope(Dispatchers.Default).launch {
            val quote = try {
                repository.getDailyQuote(todayStr)
            } catch (e: Exception) {
                QuoteEntity(
                    text = "Stay hungry. Stay foolish.",
                    author = "Steve Jobs",
                    category = "Love",
                    aboutAuthor = "Steve Jobs was co-founder of Apple Inc.",
                    tags = "Focus"
                )
            }
            updateAppWidget(context, appWidgetManager, appWidgetId, quote)
        }
    }
 
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "app.brokoli5191.quote.UPDATE_WIDGET") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, AuraWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }
 
    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            quote: QuoteEntity
        ) {
            val views = RemoteViews(context.packageName, R.layout.aura_widget_layout)
            
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val isPortrait = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            
            val widthDp = if (isPortrait) {
                if (minWidthDp > 0) minWidthDp else 280
            } else {
                if (maxWidthDp > 0) maxWidthDp else 280
            }
            
            val heightDp = if (isPortrait) {
                if (maxHeightDp > 0) maxHeightDp else 140
            } else {
                if (minHeightDp > 0) minHeightDp else 140
            }
            
            // Scaled dynamically according to the homescreen's current widget size (high-density crisp text representation)
            val scaleFactor = 3.0f
            val widthPx = (widthDp * scaleFactor).toInt().coerceAtLeast(120)
            val heightPx = (heightDp * scaleFactor).toInt().coerceAtLeast(100)
            
            // Draw premium image representation of layout
            val bitmap = drawWidgetBitmap(context, quote, appWidgetId, widthPx, heightPx)
            views.setImageViewBitmap(R.id.widget_image, bitmap)
 
            // Setup click intent to open main App
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            // Use immutable/mutable flag as required by S+
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_image, pendingIntent)
 
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
 
        private fun drawWidgetBitmap(context: Context, quote: QuoteEntity, appWidgetId: Int, width: Int, height: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
 
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val density = 3.0f
            
            // Scale the border padding dynamically for larger widgets to prevent corner clipping by the system launcher mask
            val borderPadding = if (width > 400 || height > 300) {
                5f * density
            } else {
                2.5f * density
            }
            val rect = RectF(borderPadding, borderPadding, width.toFloat() - borderPadding, height.toFloat() - borderPadding)
            val pref = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
            val style = pref.getString("widget_style_$appWidgetId", "Quote") ?: "Quote"
            val isGlass = pref.getBoolean("widget_glass_$appWidgetId", false)
            val bgColorStart = pref.getString("widget_bg_color_start_$appWidgetId", "") ?: ""
            val bgColorEnd = pref.getString("widget_bg_color_end_$appWidgetId", "") ?: ""
            val headerColor = pref.getString("widget_header_color_$appWidgetId", "") ?: ""
            val textColor = pref.getString("widget_text_color_$appWidgetId", "") ?: ""
            val authorColor = pref.getString("widget_author_color_$appWidgetId", "") ?: ""

            // Component default values mapping
            val defaultBgStart = when (style) {
                "Quote" -> "#594983"
                "Minimal" -> "#1C1B1F"
                else -> "#201F23"
            }
            val defaultBgEnd = when (style) {
                "Quote" -> "#37265E"
                "Minimal" -> "#1C1B1F"
                else -> "#201F23"
            }
            val defaultHeader = when (style) {
                "Quote" -> "#D0BCFF"
                else -> "#CAC4D0"
            }
            val defaultText = when (style) {
                "Quote" -> "#E9DDFF"
                else -> "#E5E1E7"
            }
            val defaultAuthor = when (style) {
                "Quote" -> "#A0D2AD"
                "Minimal" -> "#948F9A"
                else -> "#CAC4D0"
            }

            val finalBgStart = if (bgColorStart.isNotBlank()) bgColorStart else defaultBgStart
            val finalBgEnd = if (bgColorEnd.isNotBlank()) bgColorEnd else defaultBgEnd
            val finalHeaderColor = if (headerColor.isNotBlank()) headerColor else defaultHeader
            val finalTextColor = if (textColor.isNotBlank()) textColor else defaultText
            val finalAuthorColor = if (authorColor.isNotBlank()) authorColor else defaultAuthor

            // 1. Dynamic padding based on size
            val padX = if (width < 160 * density) 12f * density else 20f * density
            val padY = if (height < 100 * density) 8f * density else 16f * density
 
            // 2. Determine visibility of elements
            val showHeader = height >= 100 * density && width >= 140 * density
            val showAuthor = height >= 70 * density
 
            // 3. Text sizes (scale dynamically with height, but keep readable limits)
            val headerTextSize = (height * 0.07f).coerceIn(11f * density, 18f * density)
            val authorTextSize = (height * 0.08f).coerceIn(12f * density, 20f * density)
            val ornamentSize = (height * 0.35f).coerceIn(20f * density, 60f * density)
 
            // 4. Heights of elements
            val headerHeight = if (showHeader) headerTextSize + 4f * density else 0f
            val authorHeight = if (showAuthor) authorTextSize + 4f * density else 0f
 
            // 5. Layout Y coordinates
            val headerY = padY + headerTextSize
            val authorY = height - padY
 
            // 6. Max text height for quote wrapping
            val textTop = if (showHeader) headerY + 8f * density else padY
            val textBottom = if (showAuthor) authorY - authorHeight else height - padY
            val maxTextHeight = textBottom - textTop
 
            var quoteTextSize = if (width < height) {
                (width * 0.14f).coerceIn(18f * density, 30f * density)
            } else {
                (height * 0.20f).coerceIn(18f * density, 32f * density)
            }
            if (style == "Compact") {
                quoteTextSize = (quoteTextSize * 0.82f).coerceIn(14f * density, 26f * density)
            }
            var quoteLineHeight = quoteTextSize * 1.22f
 
            val textPaintForCheck = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            }
 
            // Margin and width bounds depending on style
            val paddingLeft = padX
            val paddingRight = if (style == "Compact" && width >= 300) {
                val bubbleWidth = 22f * density
                padX + bubbleWidth + 10f * density
            } else {
                padX
            }
            val maxWidth = (width - paddingLeft - paddingRight).toInt().coerceAtLeast(100)
 
            while (quoteTextSize > 8f * density) {
                textPaintForCheck.textSize = quoteTextSize
                val lineCount = getWrappedLineCount("\"${quote.text}\"", maxWidth, textPaintForCheck)
                val totalTextHeight = lineCount * quoteLineHeight
                
                if (totalTextHeight <= maxTextHeight) {
                    break
                }
                quoteTextSize -= 0.5f * density
                quoteLineHeight = quoteTextSize * 1.22f
            }

            val textStartY = textTop + quoteTextSize

            // Draw background
            val baseRadiusDp = when (style) {
                "Quote" -> 20f
                "Minimal" -> 18f
                else -> 16f
            }
            val borderRadius = (baseRadiusDp * density) - borderPadding

            if (isGlass) {
                // Parse start color and apply 45% alpha for glassmorphism
                val baseColor = try {
                    Color.parseColor(finalBgStart)
                } catch (e: Exception) {
                    Color.parseColor("#1C1B1F")
                }
                val glassFillColor = Color.argb(
                    (255 * 0.45f).toInt(),
                    Color.red(baseColor),
                    Color.green(baseColor),
                    Color.blue(baseColor)
                )

                paint.color = glassFillColor
                canvas.drawRoundRect(rect, borderRadius, borderRadius, paint)

                // Crisp translucent white border stroke
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f * density
                paint.color = Color.parseColor("#40FFFFFF")
                canvas.drawRoundRect(rect, borderRadius, borderRadius, paint)
                paint.style = Paint.Style.FILL // Reset
            } else {
                // Draw normal style background
                when (style) {
                    "Quote" -> {
                        val gradient = LinearGradient(
                            0f, 0f, width.toFloat(), height.toFloat(),
                            Color.parseColor(finalBgStart), Color.parseColor(finalBgEnd),
                            Shader.TileMode.CLAMP
                        )
                        paint.shader = gradient
                        canvas.drawRoundRect(rect, borderRadius, borderRadius, paint)
                        paint.shader = null
                    }
                    "Minimal" -> {
                        paint.color = Color.parseColor(finalBgStart)
                        canvas.drawRoundRect(rect, borderRadius, borderRadius, paint)

                        // Outline stroke
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 3f * density
                        paint.color = Color.parseColor("#353438")
                        canvas.drawRoundRect(rect, borderRadius, borderRadius, paint)
                        paint.style = Paint.Style.FILL // Reset
                    }
                    else -> { // Compact
                        paint.color = Color.parseColor(finalBgStart)
                        canvas.drawRoundRect(rect, borderRadius, borderRadius, paint)

                        // Outline stroke
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 2f * density
                        paint.color = Color.parseColor("#49454F")
                        canvas.drawRoundRect(rect, borderRadius, borderRadius, paint)
                        paint.style = Paint.Style.FILL // Reset
                    }
                }
            }
 
            when (style) {
                "Quote" -> {
                    // Draw quote ornament (decorative quote icon in background) with 10% opacity
                    paint.color = Color.parseColor("#FFFFFF")
                    paint.alpha = 20
                    paint.textSize = ornamentSize
                    paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
                    canvas.drawText("“", padX, height * 0.5f, paint)
                    paint.alpha = 255 // Restore opacity
 
                    // Draw Widget category header label
                    if (showHeader) {
                        paint.textSize = headerTextSize
                        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        paint.color = Color.parseColor(finalHeaderColor)
                        canvas.drawText("QUOTE", padX, headerY, paint)
 
                        // Star decoration top right
                        paint.color = Color.parseColor(finalHeaderColor)
                        paint.alpha = 150
                        paint.textSize = headerTextSize * 1.2f
                        canvas.drawText("✦", width - padX - (headerTextSize * 0.5f), headerY, paint)
                        paint.alpha = 255
                    }
 
                    // Draw Quote Text (Serif Italic)
                    paint.color = Color.parseColor(finalTextColor)
                    paint.textSize = quoteTextSize
                    paint.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                    
                    drawWrappedText(
                        canvas = canvas,
                        text = "\"${quote.text}\"",
                        x = padX,
                        y = textStartY,
                        maxWidth = (width - padX - padX).toInt(),
                        paint = paint,
                        lineHeight = quoteLineHeight,
                        maxHeight = maxTextHeight
                    )
 
                    // Draw Author Name
                    if (showAuthor) {
                        paint.color = Color.parseColor(finalAuthorColor)
                        paint.textSize = authorTextSize
                        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        canvas.drawText("— ${quote.author.uppercase()}", padX, authorY - 4f * density, paint)
                    }
                }
                "Minimal" -> {
                    // Draw Minimal design elements
                    if (showHeader) {
                        paint.color = Color.parseColor(finalHeaderColor)
                        paint.textSize = headerTextSize
                        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        canvas.drawText("QUOTE", padX, headerY, paint)
                    }
 
                    // Draw Quote Text (Serif Regular bold)
                    paint.color = Color.parseColor(finalTextColor)
                    paint.textSize = quoteTextSize
                    paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                    
                    drawWrappedText(
                        canvas = canvas,
                        text = "\"${quote.text}\"",
                        x = padX,
                        y = textStartY,
                        maxWidth = (width - padX - padX).toInt(),
                        paint = paint,
                        lineHeight = quoteLineHeight,
                        maxHeight = maxTextHeight
                    )
 
                    // Draw Author Name
                    if (showAuthor) {
                        paint.color = Color.parseColor(finalAuthorColor)
                        paint.textSize = authorTextSize
                        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
                        canvas.drawText(quote.author, padX, authorY - 4f * density, paint)
                    }
                }
                else -> { // Compact
                    val isVeryNarrow = width < 300
                    if (isVeryNarrow) {
                        if (showHeader) {
                            paint.color = Color.parseColor(finalHeaderColor)
                            paint.textSize = headerTextSize
                            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                            canvas.drawText("QUOTE", padX, headerY, paint)
                        }
 
                        // Draw Quote Text (Serif Italic)
                        paint.color = Color.parseColor(finalTextColor)
                        paint.textSize = quoteTextSize
                        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                        
                        drawWrappedText(
                            canvas = canvas,
                            text = "\"${quote.text}\"",
                            x = padX,
                            y = textStartY,
                            maxWidth = (width - padX - padX).toInt(),
                            paint = paint,
                            lineHeight = quoteLineHeight,
                            maxHeight = maxTextHeight
                        )
 
                        // Draw author
                        if (showAuthor) {
                            paint.color = Color.parseColor(finalAuthorColor)
                            paint.textSize = authorTextSize
                            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                            canvas.drawText("— ${quote.author}", padX, authorY - 4f * density, paint)
                        }
                    } else {
                        val accentLineWidth = 4f * density
                        val contentX = padX + accentLineWidth + 8f * density
                        val contentMaxWidth = width - contentX - padX
 
                        // Draw thin vertical accent stripe on the left
                        paint.color = Color.parseColor("#FDB700") // Secondary Amber Accent
                        val accentRect = RectF(padX, padY, padX + accentLineWidth, authorY)
                        canvas.drawRoundRect(accentRect, 2f * density, 2f * density, paint)
 
                        // Header
                        if (showHeader) {
                            paint.color = Color.parseColor(finalHeaderColor)
                            paint.textSize = headerTextSize
                            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                            canvas.drawText("QUOTE", contentX, headerY, paint)
                        }
 
                        // Quote Text
                        paint.color = Color.parseColor(finalTextColor)
                        paint.textSize = quoteTextSize
                        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                        
                        drawWrappedText(
                            canvas = canvas,
                            text = "\"${quote.text}\"",
                            x = contentX,
                            y = textStartY,
                            maxWidth = contentMaxWidth.toInt(),
                            paint = paint,
                            lineHeight = quoteLineHeight,
                            maxHeight = maxTextHeight
                        )
 
                        // Author
                        if (showAuthor) {
                            paint.color = Color.parseColor(finalAuthorColor)
                            paint.textSize = authorTextSize
                            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                            canvas.drawText("— ${quote.author}", contentX, authorY - 4f * density, paint)
                        }
                    }
                }
            }
 
            return bitmap
        }

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
            val startY = y

            for (word in words) {
                val testLine = if (line.isEmpty()) word else "$line $word"
                val textWidth = paint.measureText(testLine)
                if (textWidth > maxWidth) {
                    if (currentY - startY + lineHeight > maxHeight) {
                        val truncatedLine = if (line.length > 3) line.dropLast(3) + "..." else "..."
                        canvas.drawText(truncatedLine, x, currentY, paint)
                        return
                    }
                    canvas.drawText(line, x, currentY, paint)
                    line = word
                    currentY += lineHeight
                } else {
                    line = testLine
                }
            }
            if (line.isNotEmpty()) {
                if (currentY - startY + lineHeight <= maxHeight || currentY == startY) {
                    canvas.drawText(line, x, currentY, paint)
                } else {
                    val truncatedLine = if (line.length > 3) line.dropLast(3) + "..." else "..."
                    canvas.drawText(truncatedLine, x, currentY, paint)
                }
            }
        }

        private fun getWrappedLineCount(text: String, maxWidth: Int, paint: Paint): Int {
            val words = text.split(" ")
            var line = ""
            var count = 0
            for (word in words) {
                val testLine = if (line.isEmpty()) word else "$line $word"
                val textWidth = paint.measureText(testLine)
                if (textWidth > maxWidth) {
                    count++
                    line = word
                } else {
                    line = testLine
                }
            }
            if (line.isNotEmpty()) {
                count++
            }
            return count.coerceAtLeast(1)
        }

        private fun doesTextFit(
            text: String,
            maxWidth: Int,
            maxHeight: Float,
            paint: Paint,
            textSize: Float,
            lineHeight: Float
        ): Boolean {
            val testPaint = Paint(paint).apply { this.textSize = textSize }
            val words = text.split(" ")
            var line = ""
            var textHeight = lineHeight
            
            for (word in words) {
                val testLine = if (line.isEmpty()) word else "$line $word"
                val textWidth = testPaint.measureText(testLine)
                if (textWidth > maxWidth) {
                    textHeight += lineHeight
                    line = word
                } else {
                    line = testLine
                }
            }
            return textHeight <= maxHeight
        }
    }
}
