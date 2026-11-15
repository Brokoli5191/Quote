package app.brokoli5191.quote.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle

data class WidgetConfig(
    val showAuthor: Boolean = true,
    val showIcon: Boolean = true,
    val showLabel: Boolean = true,
    val blurBackground: Boolean = false,
    val backgroundStart: String = "#594983",
    val backgroundEnd: String = "#37265E",
    val labelColor: String = "#D0BCFF",
    val quoteColor: String = "#E9DDFF",
    val authorColor: String = "#A0D2AD",
    val font: String = "Serif",
    val quoteSizeScale: Float = 1f,
    val authorSizeScale: Float = 1f,
    val visualScale: Float = 1f,
    val cornerRadius: Float = 0f,
    val borderEnabled: Boolean = true,
    val borderColor: String = "#2DFFFFFF",
    val labelX: Float = 0.06f,
    val labelY: Float = 0.10f,
    val iconX: Float = 0.92f,
    val iconY: Float = 0.10f,
    val quoteX: Float = 0.06f,
    val quoteY: Float = 0.38f,
    val authorX: Float = 0.06f,
    val authorY: Float = 0.88f
) {
    companion object {
        private const val PREFS = "aura_prefs"

        fun read(context: Context, widgetId: Int): WidgetConfig {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return WidgetConfig(
                showAuthor = prefs.getBoolean("widget_show_author_$widgetId", true),
                showIcon = prefs.getBoolean("widget_show_icon_$widgetId", true),
                showLabel = prefs.getBoolean("widget_show_label_$widgetId", true),
                blurBackground = prefs.getBoolean(
                    "widget_blur_$widgetId",
                    prefs.getBoolean("widget_glass_$widgetId", false)
                ),
                backgroundStart = prefs.getString("widget_bg_color_start_$widgetId", "#594983") ?: "#594983",
                backgroundEnd = prefs.getString("widget_bg_color_end_$widgetId", "#37265E") ?: "#37265E",
                labelColor = prefs.getString("widget_header_color_$widgetId", "#D0BCFF") ?: "#D0BCFF",
                quoteColor = prefs.getString("widget_text_color_$widgetId", "#E9DDFF") ?: "#E9DDFF",
                authorColor = prefs.getString("widget_author_color_$widgetId", "#A0D2AD") ?: "#A0D2AD",
                font = prefs.getString("widget_font_$widgetId", "Serif") ?: "Serif",
                quoteSizeScale = prefs.getFloat("widget_quote_size_$widgetId", 1f),
                authorSizeScale = prefs.getFloat("widget_author_size_$widgetId", 1f),
                visualScale = prefs.getFloat("widget_visual_scale_$widgetId", 1f),
                cornerRadius = prefs.getFloat("widget_corner_radius_$widgetId", 0f),
                borderEnabled = prefs.getBoolean("widget_border_enabled_$widgetId", true),
                borderColor = prefs.getString("widget_border_color_$widgetId", "#2DFFFFFF") ?: "#2DFFFFFF",
                labelX = prefs.getFloat("widget_label_x_$widgetId", 0.06f),
                labelY = prefs.getFloat("widget_label_y_$widgetId", 0.10f),
                iconX = prefs.getFloat("widget_icon_x_$widgetId", 0.92f),
                iconY = prefs.getFloat("widget_icon_y_$widgetId", 0.10f),
                quoteX = prefs.getFloat("widget_quote_x_$widgetId", 0.06f),
                quoteY = prefs.getFloat("widget_quote_y_$widgetId", 0.38f),
                authorX = prefs.getFloat("widget_author_x_$widgetId", 0.06f),
                authorY = prefs.getFloat("widget_author_y_$widgetId", 0.88f)
            )
        }

        fun write(context: Context, widgetId: Int, config: WidgetConfig) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("widget_show_author_$widgetId", config.showAuthor)
                .putBoolean("widget_show_icon_$widgetId", config.showIcon)
                .putBoolean("widget_show_label_$widgetId", config.showLabel)
                .putBoolean("widget_blur_$widgetId", config.blurBackground)
                .putString("widget_bg_color_start_$widgetId", config.backgroundStart)
                .putString("widget_bg_color_end_$widgetId", config.backgroundEnd)
                .putString("widget_header_color_$widgetId", config.labelColor)
                .putString("widget_text_color_$widgetId", config.quoteColor)
                .putString("widget_author_color_$widgetId", config.authorColor)
                .putString("widget_font_$widgetId", config.font)
                .putFloat("widget_quote_size_$widgetId", config.quoteSizeScale)
                .putFloat("widget_author_size_$widgetId", config.authorSizeScale)
                .putFloat("widget_visual_scale_$widgetId", config.visualScale)
                .putFloat("widget_corner_radius_$widgetId", config.cornerRadius)
                .putBoolean("widget_border_enabled_$widgetId", config.borderEnabled)
                .putString("widget_border_color_$widgetId", config.borderColor)
                .putFloat("widget_label_x_$widgetId", config.labelX)
                .putFloat("widget_label_y_$widgetId", config.labelY)
                .putFloat("widget_icon_x_$widgetId", config.iconX)
                .putFloat("widget_icon_y_$widgetId", config.iconY)
                .putFloat("widget_quote_x_$widgetId", config.quoteX)
                .putFloat("widget_quote_y_$widgetId", config.quoteY)
                .putFloat("widget_author_x_$widgetId", config.authorX)
                .putFloat("widget_author_y_$widgetId", config.authorY)
                .remove("widget_style_$widgetId")
                .remove("widget_glass_$widgetId")
                .apply()
        }

        fun delete(context: Context, widgetId: Int) {
            val suffix = "_$widgetId"
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            prefs.all.keys.filter { it.startsWith("widget_") && it.endsWith(suffix) }
                .forEach(editor::remove)
            editor.apply()
        }

        /** True once a configuration was saved for [widgetId], i.e. configuring it again is a reconfiguration. */
        fun exists(context: Context, widgetId: Int): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .contains("widget_show_author_$widgetId")

        /**
         * Moves every stored setting of [oldWidgetId] to [newWidgetId] (the launcher hands out new
         * ids when it restores widgets from a backup). The old id's settings are removed unless
         * [keepSource] is set. A no-op when the ids are equal or the old id has no settings.
         */
        fun transfer(context: Context, oldWidgetId: Int, newWidgetId: Int, keepSource: Boolean = false) {
            if (oldWidgetId == newWidgetId) return
            val oldSuffix = "_$oldWidgetId"
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            prefs.all.forEach { (key, value) ->
                if (!key.startsWith("widget_") || !key.endsWith(oldSuffix)) return@forEach
                val target = key.removeSuffix(oldSuffix) + "_$newWidgetId"
                when (value) {
                    is Boolean -> editor.putBoolean(target, value)
                    is String -> editor.putString(target, value)
                    is Float -> editor.putFloat(target, value)
                    is Int -> editor.putInt(target, value)
                    is Long -> editor.putLong(target, value)
                }
                if (!keepSource) editor.remove(key)
            }
            editor.apply()
        }
    }
}

/**
 * A background gradient together with the text colours that stay readable on it. Dark presets
 * keep the default light text; light presets (Paper) bring dark text and border.
 */
data class WidgetBackgroundPreset(
    val name: String,
    val start: String,
    val end: String,
    val labelColor: String = WidgetConfig().labelColor,
    val quoteColor: String = WidgetConfig().quoteColor,
    val authorColor: String = WidgetConfig().authorColor,
    val borderColor: String = WidgetConfig().borderColor
)

val widgetBackgroundPresets = listOf(
    WidgetBackgroundPreset("Violet", "#594983", "#37265E"),
    WidgetBackgroundPreset("Midnight", "#111827", "#020617"),
    WidgetBackgroundPreset("Ocean", "#0F4C5C", "#082F49"),
    WidgetBackgroundPreset("Emerald", "#14532D", "#052E16"),
    WidgetBackgroundPreset("Rose", "#881337", "#4C0519"),
    WidgetBackgroundPreset("Sunset", "#9A3412", "#431407"),
    WidgetBackgroundPreset("Gold", "#713F12", "#291804"),
    WidgetBackgroundPreset("Slate", "#334155", "#0F172A"),
    WidgetBackgroundPreset(
        "Paper", "#F5F1E8", "#D8D0C2",
        labelColor = "#594983",
        quoteColor = "#1C1B1F",
        authorColor = "#2E5E3A",
        borderColor = "#33000000"
    ),
    WidgetBackgroundPreset("Pure Black", "#000000", "#000000")
)

private val HEX_COLOR = Regex("^#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

/** "#RRGGBB" / "#AARRGGBB" (upper case, leading # optional on input), or null when [value] is no such colour. */
fun normalizeWidgetColor(value: String): String? =
    HEX_COLOR.matchEntire(value.trim())?.let { "#" + it.groupValues[1].uppercase() }

/**
 * Colour of a stored widget colour string. Invalid input falls back to [fallbackHex], so the
 * configure preview and the widget renderer always agree.
 */
fun parseWidgetColor(value: String, fallbackHex: String): Int =
    Color.parseColor(normalizeWidgetColor(value) ?: fallbackHex)

/** Lighter variant of a background colour used for the blur glows. */
fun lightenWidgetColor(color: Int): Int = Color.rgb(
    (Color.red(color) + 75).coerceAtMost(255),
    (Color.green(color) + 75).coerceAtMost(255),
    (Color.blue(color) + 75).coerceAtMost(255)
)

/** Soft glow of the Background Blur option; centre and radius are fractions of the card width/height. */
data class WidgetGlow(val centerX: Float, val centerY: Float, val radiusOfWidth: Float, val color: Int)

/** Blur radius of [widgetBlurGlows] in dp (scaled with the card in the preview). */
const val WIDGET_BLUR_RADIUS_DP = 28f

fun widgetBlurGlows(backgroundStart: Int, backgroundEnd: Int): List<WidgetGlow> = listOf(
    WidgetGlow(0.18f, 0.20f, 0.24f, withAlpha(lightenWidgetColor(backgroundStart), 115)),
    WidgetGlow(0.85f, 0.80f, 0.32f, withAlpha(lightenWidgetColor(backgroundEnd), 105))
)

private fun withAlpha(color: Int, alpha: Int) =
    Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

/** The draggable elements with the position limits the renderer applies to their stored fractions. */
enum class WidgetElement(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float) {
    LABEL(0f, 0.9f, 0.08f, 0.9f),
    ICON(0.1f, 0.98f, 0.08f, 0.9f),
    QUOTE(0f, 0.85f, 0.12f, 0.9f),
    AUTHOR(0f, 0.85f, 0.2f, 0.98f);

    fun clampX(x: Float): Float = x.coerceIn(minX, maxX)
    fun clampY(y: Float): Float = y.coerceIn(minY, maxY)
}

/** Which optional elements are actually drawn; the single source of the size rules. */
data class WidgetElementVisibility(val label: Boolean, val icon: Boolean, val author: Boolean)

/** Size of the visible card in dp: the widget area shrunk by the Visual Widget Size setting. */
fun widgetCardSizeDp(widgetWidthDp: Float, widgetHeightDp: Float, visualScale: Float): Pair<Float, Float> {
    val scale = visualScale.coerceIn(0.65f, 1f)
    return widgetWidthDp * scale to widgetHeightDp * scale
}

/**
 * The renderer hides the label below 90dp card height / 130dp width, the icon below 80dp / 120dp
 * and the author below 65dp height. [widgetWidthDp]/[widgetHeightDp] are the full widget area
 * (before the visual scale is applied).
 */
fun widgetElementVisibility(config: WidgetConfig, widgetWidthDp: Float, widgetHeightDp: Float): WidgetElementVisibility {
    val (cardWidth, cardHeight) = widgetCardSizeDp(widgetWidthDp, widgetHeightDp, config.visualScale)
    return WidgetElementVisibility(
        label = config.showLabel && cardHeight >= 90f && cardWidth >= 130f,
        icon = config.showIcon && cardHeight >= 80f && cardWidth >= 120f,
        author = config.showAuthor && cardHeight >= 65f
    )
}

/** Widget area in dp as the provider computes it from the launcher's size options (280x140 when unknown). */
fun widgetSizeDpFromOptions(options: Bundle, portrait: Boolean): Pair<Int, Int> {
    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).let { min ->
        if (portrait) min else options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
    }
    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).let { max ->
        if (portrait) max else options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
    }
    return (width.takeIf { it > 0 } ?: 280) to (height.takeIf { it > 0 } ?: 140)
}
