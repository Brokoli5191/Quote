package app.brokoli5191.quote.widget

import android.content.Context

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
    }
}

data class WidgetBackgroundPreset(val name: String, val start: String, val end: String)

val widgetBackgroundPresets = listOf(
    WidgetBackgroundPreset("Violet", "#594983", "#37265E"),
    WidgetBackgroundPreset("Midnight", "#111827", "#020617"),
    WidgetBackgroundPreset("Ocean", "#0F4C5C", "#082F49"),
    WidgetBackgroundPreset("Emerald", "#14532D", "#052E16"),
    WidgetBackgroundPreset("Rose", "#881337", "#4C0519"),
    WidgetBackgroundPreset("Sunset", "#9A3412", "#431407"),
    WidgetBackgroundPreset("Gold", "#713F12", "#291804"),
    WidgetBackgroundPreset("Slate", "#334155", "#0F172A"),
    WidgetBackgroundPreset("Paper", "#F5F1E8", "#D8D0C2"),
    WidgetBackgroundPreset("Pure Black", "#000000", "#000000")
)
