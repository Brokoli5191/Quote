package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.QuoteEntity
import com.example.data.QuoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AuraWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val database = AppDatabase.getInstance(context)
        val repository = QuoteRepository(database.quoteDao())
        val pref = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        val style = pref.getString("widget_style", "Expressive") ?: "Expressive"

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
                updateAppWidget(context, appWidgetManager, appWidgetId, quote, style)
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
        val pref = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        val style = pref.getString("widget_style", "Expressive") ?: "Expressive"

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
            updateAppWidget(context, appWidgetManager, appWidgetId, quote, style)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.example.UPDATE_WIDGET") {
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
            quote: QuoteEntity,
            style: String
        ) {
            val views = RemoteViews(context.packageName, R.layout.aura_widget_layout)
            
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            
            val widthDp = if (minWidthDp > 0) minWidthDp else 280
            val heightDp = if (minHeightDp > 0) minHeightDp else 140
            
            // Scaled dynamically according to the homescreen's current widget size (high-density crisp text representation)
            val scaleFactor = 3.0f
            val widthPx = (widthDp * scaleFactor).toInt().coerceAtLeast(120)
            val heightPx = (heightDp * scaleFactor).toInt().coerceAtLeast(100)
            
            // Draw premium image representation of layout
            val bitmap = drawWidgetBitmap(context, quote, style, widthPx, heightPx)
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

        private fun drawWidgetBitmap(context: Context, quote: QuoteEntity, style: String, width: Int, height: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())

            // Dynamically calculate paddings and sizes based on final image proportions
            val paddingX = width * 0.08f
            val headerY = height * 0.16f
            val authorY = height - (height * 0.14f)
            
            val isPortrait = width < height
            
            val headerTextSize = (height * 0.08f).coerceIn(16f, 36f)
            val authorTextSize = (height * 0.09f).coerceIn(18f, 38f)
            val ornamentSize = (height * 0.40f).coerceIn(60f, 180f)
            
            var quoteTextSize = if (isPortrait) {
                (width * 0.16f).coerceIn(24f, 80f)
            } else {
                (height * 0.22f).coerceIn(24f, 90f)
            }
            var quoteLineHeight = quoteTextSize * 1.22f

            val textPaintForCheck = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            }

            // Margin and width bounds depending on style
            val paddingLeft = paddingX
            val paddingRight = if (style == "Compact" && width >= 300) {
                val bubbleWidth = 70f
                paddingX + bubbleWidth + 30f
            } else {
                paddingX
            }
            val maxWidth = (width - paddingLeft - paddingRight).toInt().coerceAtLeast(100)

            while (quoteTextSize > 10f) {
                textPaintForCheck.textSize = quoteTextSize
                val textStartY = headerY + (quoteTextSize * 1.5f)
                val maxTextHeight = authorY - textStartY - (authorTextSize * 1.5f)
                val lineCount = getWrappedLineCount("\"${quote.text}\"", maxWidth, textPaintForCheck)
                val totalTextHeight = lineCount * quoteLineHeight
                
                if (totalTextHeight <= maxTextHeight && textStartY < authorY - authorTextSize * 1.5f) {
                    break
                }
                quoteTextSize -= 0.5f
                quoteLineHeight = quoteTextSize * 1.22f
            }

            when (style) {
                "Expressive" -> {
                    // Draw Expressive Gradient background (594983 to 37265e)
                    val gradient = LinearGradient(
                        0f, 0f, width.toFloat(), height.toFloat(),
                        Color.parseColor("#594983"), Color.parseColor("#37265E"),
                        Shader.TileMode.CLAMP
                    )
                    paint.shader = gradient
                    canvas.drawRoundRect(rect, 48f, 48f, paint)
                    paint.shader = null

                    // Draw quote ornament (decorative quote icon in background) with 10% opacity
                    paint.color = Color.parseColor("#FFFFFF")
                    paint.alpha = 20
                    paint.textSize = ornamentSize
                    paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
                    canvas.drawText("“", paddingX, height * 0.5f, paint)
                    paint.alpha = 255 // Restore opacity

                    // Draw Widget category header label
                    paint.textSize = headerTextSize
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    paint.color = Color.parseColor("#D0BCFF")
                    canvas.drawText("EXPRESSIVE", paddingX, headerY, paint)

                    // Star decoration top right
                    paint.color = Color.parseColor("#D0BCFF")
                    paint.alpha = 150
                    paint.textSize = headerTextSize * 1.2f
                    canvas.drawText("✦", width - paddingX - (headerTextSize * 0.5f), headerY, paint)
                    paint.alpha = 255

                    // Draw Quote Text (Serif Italic)
                    paint.color = Color.parseColor("#E9DDFF")
                    paint.textSize = quoteTextSize
                    paint.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                    
                    val textStartY = headerY + (quoteTextSize * 1.5f)
                    val maxTextHeight = authorY - textStartY - (authorTextSize * 1.5f)

                    drawWrappedText(
                        canvas = canvas,
                        text = "\"${quote.text}\"",
                        x = paddingX,
                        y = textStartY,
                        maxWidth = (width - paddingX * 2).toInt(),
                        paint = paint,
                        lineHeight = quoteLineHeight,
                        maxHeight = maxTextHeight
                    )

                    // Draw Author Name
                    paint.color = Color.parseColor("#A0D2AD") // Soft Tertiary Green
                    paint.textSize = authorTextSize
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    canvas.drawText("— ${quote.author.uppercase()}", paddingX, authorY, paint)
                }
                "Minimal" -> {
                    // Draw Dark Translucent Glassmorphic Card
                    paint.color = Color.parseColor("#1C1B1F")
                    canvas.drawRoundRect(rect, 40f, 40f, paint)

                    // Draw subtle light surface border
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3f
                    paint.color = Color.parseColor("#353438")
                    canvas.drawRoundRect(rect, 40f, 40f, paint)
                    paint.style = Paint.Style.FILL // Reset

                    // Draw Minimal design elements
                    paint.color = Color.parseColor("#CAC4D0") // Neutral Variant
                    paint.textSize = headerTextSize
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    canvas.drawText("❝ Minimal", paddingX, headerY, paint)

                    // Draw Quote Text (Serif Regular bold)
                    paint.color = Color.parseColor("#E5E1E7")
                    paint.textSize = quoteTextSize
                    paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                    
                    val textStartY = headerY + (quoteTextSize * 1.5f)
                    val maxTextHeight = authorY - textStartY - (authorTextSize * 1.5f)

                    drawWrappedText(
                        canvas = canvas,
                        text = "\"${quote.text}\"",
                        x = paddingX,
                        y = textStartY,
                        maxWidth = (width - paddingX * 2).toInt(),
                        paint = paint,
                        lineHeight = quoteLineHeight,
                        maxHeight = maxTextHeight
                    )

                    // Draw Author Name
                    paint.color = Color.parseColor("#948F9A")
                    paint.textSize = authorTextSize
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
                    canvas.drawText(quote.author, paddingX, authorY, paint)
                }
                else -> { // Compact
                    paint.color = Color.parseColor("#201F23")
                    canvas.drawRoundRect(rect, 30f, 30f, paint)

                    // Outline stroke
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    paint.color = Color.parseColor("#49454F")
                    canvas.drawRoundRect(rect, 30f, 30f, paint)
                    paint.style = Paint.Style.FILL // Reset

                    val isVeryNarrow = width < 300
                    if (isVeryNarrow) {
                        paint.color = Color.parseColor("#CAC4D0")
                        paint.textSize = headerTextSize
                        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        canvas.drawText("DAILY", paddingX, headerY, paint)

                        // Draw Quote Text (Serif Italic)
                        paint.color = Color.parseColor("#E5E1E7")
                        paint.textSize = quoteTextSize
                        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                        
                        val textStartY = headerY + (quoteTextSize * 1.5f)
                        val maxTextHeight = authorY - textStartY - (authorTextSize * 1.2f)

                        drawWrappedText(
                            canvas = canvas,
                            text = "\"${quote.text}\"",
                            x = paddingX,
                            y = textStartY,
                            maxWidth = (width - paddingX * 2).toInt(),
                            paint = paint,
                            lineHeight = quoteLineHeight,
                            maxHeight = maxTextHeight
                        )

                        // Draw author
                        paint.color = Color.parseColor("#CAC4D0")
                        paint.textSize = authorTextSize
                        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                        canvas.drawText("— ${quote.author}", paddingX, authorY, paint)
                    } else {
                        val bubbleWidth = 70f
                        paint.color = Color.parseColor("#FDB700") // Secondary Amber Accent
                        canvas.drawRoundRect(paddingX, headerY - 10f, paddingX + bubbleWidth, authorY + 10f, 20f, 20f, paint)
                        
                        paint.color = Color.parseColor("#412D00")
                        paint.textSize = authorTextSize * 1.2f
                        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        canvas.drawText("✦", paddingX + 22f, height / 2f + 11f, paint)

                        val contentX = paddingX + bubbleWidth + 30f
                        val contentMaxWidth = width - contentX - paddingX

                        // Header
                        paint.color = Color.parseColor("#CAC4D0")
                        paint.textSize = headerTextSize
                        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        canvas.drawText("DAILY INSPIRATION", contentX, headerY, paint)

                        // Quote Text
                        paint.color = Color.parseColor("#E5E1E7")
                        paint.textSize = quoteTextSize
                        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                        
                        val textStartY = headerY + (quoteTextSize * 1.5f)
                        val maxTextHeight = authorY - textStartY - (authorTextSize * 1.2f)

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
                        paint.color = Color.parseColor("#CAC4D0")
                        paint.textSize = authorTextSize
                        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                        canvas.drawText("— ${quote.author}", contentX, authorY, paint)
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
