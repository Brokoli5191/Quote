package app.brokoli5191.quote.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.icu.text.BreakIterator
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.ceil

/**
 * Draws the quote card: the bitmap of the home-screen widget and the "share as image" picture.
 * Positions come from [WidgetConfig] as fractions of the card. Padding, text size limits and the
 * rules for which elements fit are in dp and converted with the `density` passed to [render], so a
 * config looks the same at every resolution.
 */
object QuoteCardRenderer {
    /** The card's label. The German UI calls quotes "Quote" as well, so it is not translated. */
    const val LABEL = "QUOTE"

    const val SHARE_IMAGE_WIDTH_PX = 1080
    const val SHARE_IMAGE_HEIGHT_PX = 1350

    /** 1080 x 1350 px at density 3 is a 360 x 450 dp card, large enough to show every element. */
    private const val SHARE_IMAGE_DENSITY = 3f

    /**
     * Share image look: no border along the image edge, the quote higher up the tall card, and an
     * author line small enough for long names at this size.
     */
    val SHARE_IMAGE_CONFIG = WidgetConfig(borderEnabled = false, quoteY = 0.24f, authorSizeScale = 0.6f)

    private val DEFAULTS = WidgetConfig()
    private const val LINE_HEIGHT = 1.2f
    private const val MAX_QUOTE_SIZE_DP = 42f
    private const val MIN_QUOTE_SIZE_DP = 8f
    private const val QUOTE_SIZE_STEP_DP = 0.5f
    private const val MIN_TEXT_WIDTH_DP = 24f

    /** Space between the quote's descenders and the author line, relative to the author text size. */
    private const val AUTHOR_GAP = 0.4f

    /** The position after a hyphen or en dash between two letters, as in "Nettes-Mädchen". */
    private val COMPOUND_HYPHEN = Regex("(?<=\\p{L}[-\u2010\u2013])(?=\\p{L})")
    private const val ZERO_WIDTH_SPACE = "\u200B"

    /**
     * The card at [widthPx] x [heightPx]. [label] is drawn when the config shows it and the card
     * is large enough; null leaves it out. [author] is drawn as "— author" unless blank.
     */
    fun render(
        context: Context,
        text: String,
        author: String,
        label: String?,
        config: WidgetConfig,
        widthPx: Int,
        heightPx: Int,
        density: Float
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), layout(text, author, label, config, bitmap.width, bitmap.height, density), config, density)
        return bitmap
    }

    /**
     * A 1080 x 1350 px (4:5) card for sharing. Pass the user's widget config to share in their
     * style; a visual scale below 1 leaves a transparent margin around the card.
     */
    fun renderShareImage(
        context: Context,
        text: String,
        author: String,
        config: WidgetConfig = SHARE_IMAGE_CONFIG
    ): Bitmap = render(
        context,
        text,
        author,
        LABEL,
        config,
        SHARE_IMAGE_WIDTH_PX,
        SHARE_IMAGE_HEIGHT_PX,
        SHARE_IMAGE_DENSITY
    )

    internal fun layout(
        text: String,
        author: String,
        label: String?,
        config: WidgetConfig,
        widthPx: Int,
        heightPx: Int,
        density: Float
    ): CardLayout {
        val scale = config.visualScale.coerceIn(0.65f, 1f)
        val insetX = widthPx * (1f - scale) / 2f
        val insetY = heightPx * (1f - scale) / 2f
        val card = RectF(insetX, insetY, widthPx - insetX, heightPx - insetY)
        val cardWidth = card.width()
        val cardHeight = card.height()
        val visible = widgetElementVisibility(config, widthPx / density, heightPx / density)
        val padX = if (cardWidth < 160 * density) 12f * density else 20f * density
        val padY = if (cardHeight < 100 * density) 8f * density else 16f * density
        val labelSize = (cardHeight * 0.07f).coerceIn(11f * density, 18f * density)
        val labelColor = parseWidgetColor(config.labelColor, DEFAULTS.labelColor)

        val labelRun = if (visible.label && !label.isNullOrBlank()) {
            TextRun(
                label,
                card.left + cardWidth * WidgetElement.LABEL.clampX(config.labelX),
                card.top + cardHeight * WidgetElement.LABEL.clampY(config.labelY) + labelSize,
                textPaint(labelColor, labelSize, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD))
            )
        } else null
        val iconRun = if (visible.icon) {
            TextRun(
                "✦",
                card.left + cardWidth * WidgetElement.ICON.clampX(config.iconX),
                card.top + cardHeight * WidgetElement.ICON.clampY(config.iconY) + labelSize,
                textPaint(labelColor, labelSize * 1.35f, Typeface.create(Typeface.SERIF, Typeface.BOLD)).apply {
                    textAlign = Paint.Align.RIGHT
                }
            )
        } else null
        val authorRun = if (visible.author && author.isNotBlank()) {
            val authorSize = (cardHeight * 0.08f * config.authorSizeScale).coerceIn(9f * density, 28f * density)
            val paint = textPaint(
                parseWidgetColor(config.authorColor, DEFAULTS.authorColor),
                authorSize,
                authorTypeface(config.font)
            )
            val x = card.left + cardWidth * WidgetElement.AUTHOR.clampX(config.authorX)
            val width = (card.right - padX - x).coerceAtLeast(MIN_TEXT_WIDTH_DP * density)
            TextRun(
                TextUtils.ellipsize("— $author", paint, width, TextUtils.TruncateAt.END),
                x,
                card.top + cardHeight * WidgetElement.AUTHOR.clampY(config.authorY),
                paint
            )
        } else null

        val quoteLeft = card.left + cardWidth * WidgetElement.QUOTE.clampX(config.quoteX)
        val quoteTop = card.top + cardHeight * WidgetElement.QUOTE.clampY(config.quoteY)
        val quoteWidth = (card.right - padX - quoteLeft).coerceAtLeast(MIN_TEXT_WIDTH_DP * density)
        // An author line below the quote's start closes the quote box, so the two never overlap.
        val quoteBoxBottom = authorRun
            ?.takeIf { it.baseline > quoteTop }
            ?.let { minOf(card.bottom - padY, it.top - it.paint.textSize * AUTHOR_GAP) }
            ?: (card.bottom - padY)
        val maxQuoteSize = ((if (cardWidth < cardHeight) cardWidth * 0.14f else cardHeight * 0.20f) * config.quoteSizeScale)
            .coerceIn(12f * density, MAX_QUOTE_SIZE_DP * density)
        val quote = fitQuote(
            text,
            textPaint(parseWidgetColor(config.quoteColor, DEFAULTS.quoteColor), maxQuoteSize, quoteTypeface(config.font)),
            quoteWidth.toInt(),
            quoteBoxBottom - quoteTop,
            maxQuoteSize,
            MIN_QUOTE_SIZE_DP * density,
            QUOTE_SIZE_STEP_DP * density
        )

        return CardLayout(
            card = card,
            cornerRadius = config.cornerRadius.coerceIn(0f, 48f) * density * scale,
            label = labelRun,
            icon = iconRun,
            quote = quote,
            quoteLeft = quoteLeft,
            quoteTop = quoteTop,
            quoteBoxBottom = quoteBoxBottom,
            author = authorRun
        )
    }

    /**
     * The largest size (in [step]s from [maxSize] down to [minSize]) at which the whole quote fits
     * the box and every line starts at a line-break opportunity: after a space, a slash or a dash,
     * or after the hyphen of a compound like "Nettes-Mädchen-von-nebenan-itis". If none does, the
     * quote is drawn at [minSize] with hyphenation, so words wider than the line are hyphenated
     * rather than cut anywhere, and the last line that fits ends with "…".
     */
    private fun fitQuote(
        text: String,
        paint: TextPaint,
        width: Int,
        boxHeight: Float,
        maxSize: Float,
        minSize: Float,
        step: Float
    ): StaticLayout {
        // Without hyphenation StaticLayout never breaks after a hyphen (it leaves that to the
        // hyphenator), so a zero-width space after each compound hyphen lets the line break there.
        val layoutText = text.replace(COMPOUND_HYPHEN, ZERO_WIDTH_SPACE)
        // StaticLayout breaks at ICU's line-break opportunities, so a line start that is not one
        // is a word that had to be cut because it is wider than the line.
        val breaks = BreakIterator.getLineInstance(paint.textLocale).apply { setText(layoutText) }
        fun sizeAt(index: Int) = (maxSize - index * step).coerceAtLeast(minSize)
        fun layoutAt(index: Int, hyphenate: Boolean) =
            quoteLayout(layoutText, paint, sizeAt(index), width, boxHeight, hyphenate)
        fun StaticLayout.fitsCompletely(): Boolean =
            getEllipsisCount(lineCount - 1) == 0 &&
                blockHeight() <= boxHeight &&
                (1 until lineCount).all { breaks.isBoundary(getLineStart(it)) }

        var low = 0
        var high = ceil((maxSize - minSize) / step).toInt()
        while (low < high) {
            val middle = (low + high) / 2
            if (layoutAt(middle, hyphenate = false).fitsCompletely()) {
                high = middle
            } else {
                low = middle + 1
            }
        }
        val layout = layoutAt(low, hyphenate = false)
        return if (layout.fitsCompletely()) layout else layoutAt(low, hyphenate = true)
    }

    /**
     * The quote at [size] with as many lines as fit into [boxHeight]; "…" ends a cut-off text.
     * Without [hyphenate] a word wider than the line is cut at an arbitrary letter.
     */
    private fun quoteLayout(
        text: String,
        basePaint: TextPaint,
        size: Float,
        width: Int,
        boxHeight: Float,
        hyphenate: Boolean
    ): StaticLayout {
        val paint = TextPaint(basePaint).apply { textSize = size }
        val lineHeight = size * LINE_HEIGHT
        var maxLines = (((boxHeight - size - paint.descent()) / lineHeight).toInt() + 1).coerceAtLeast(1)
        while (true) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(lineHeight - (paint.descent() - paint.ascent()), 1f)
                .setIncludePad(false)
                .setHyphenationFrequency(
                    if (hyphenate) Layout.HYPHENATION_FREQUENCY_NORMAL else Layout.HYPHENATION_FREQUENCY_NONE
                )
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            // The estimate can be one line too many when the layout rounds line heights up.
            if (maxLines == 1 || layout.blockHeight() <= boxHeight) return layout
            maxLines--
        }
    }

    private fun draw(canvas: Canvas, layout: CardLayout, config: WidgetConfig, density: Float) {
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        val card = layout.card
        val radius = layout.cornerRadius
        val bgStart = parseWidgetColor(config.backgroundStart, DEFAULTS.backgroundStart)
        val bgEnd = parseWidgetColor(config.backgroundEnd, DEFAULTS.backgroundEnd)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.shader = LinearGradient(0f, 0f, width, height, bgStart, bgEnd, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(card, radius, radius, paint)
        paint.shader = null

        if (config.blurBackground) {
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(card, radius, radius, Path.Direction.CW) })
            paint.maskFilter = BlurMaskFilter(WIDGET_BLUR_RADIUS_DP * density, BlurMaskFilter.Blur.NORMAL)
            widgetBlurGlows(bgStart, bgEnd).forEach { glow ->
                paint.color = glow.color
                canvas.drawCircle(width * glow.centerX, height * glow.centerY, width * glow.radiusOfWidth, paint)
            }
            paint.maskFilter = null
            canvas.restore()
        }

        if (config.borderEnabled) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * density
            paint.color = parseWidgetColor(config.borderColor, DEFAULTS.borderColor)
            canvas.drawRoundRect(card, radius, radius, paint)
        }

        layout.label?.draw(canvas)
        layout.icon?.draw(canvas)

        val quote = layout.quote
        canvas.save()
        canvas.translate(layout.quoteLeft, layout.quoteTop + layout.quoteSize - quote.getLineBaseline(0))
        quote.draw(canvas)
        canvas.restore()

        layout.author?.draw(canvas)
    }

    private fun textPaint(color: Int, size: Float, typeface: Typeface) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        this.typeface = typeface
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
}

/** A single line of text drawn at [x] (per the paint's alignment) with its baseline at [baseline]. */
internal class TextRun(val text: CharSequence, val x: Float, val baseline: Float, val paint: TextPaint) {
    val top: Float get() = baseline + paint.ascent()

    fun draw(canvas: Canvas) {
        canvas.drawText(text, 0, text.length, x, baseline, paint)
    }
}

/** Where everything on the card goes; [QuoteCardRenderer.render] draws it. */
internal class CardLayout(
    val card: RectF,
    val cornerRadius: Float,
    val label: TextRun?,
    val icon: TextRun?,
    val quote: StaticLayout,
    val quoteLeft: Float,
    /** Top of the quote box. The first baseline sits one text size below it. */
    val quoteTop: Float,
    val quoteBoxBottom: Float,
    val author: TextRun?
) {
    val quoteSize: Float get() = quote.paint.textSize

    /** Bottom of the last quote line's descenders. */
    val quoteBottom: Float get() = quoteTop + quote.blockHeight()
}

/** Height from the top of the quote box to the bottom of the last line's descenders. */
private fun Layout.blockHeight(): Float =
    paint.textSize + (getLineBaseline(lineCount - 1) - getLineBaseline(0)) + paint.descent()
