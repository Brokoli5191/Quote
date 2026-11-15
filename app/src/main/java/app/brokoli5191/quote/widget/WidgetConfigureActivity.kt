package app.brokoli5191.quote.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.InstallationSeed
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteRepository
import app.brokoli5191.quote.data.QuoteSourceMode
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.data.FallbackQuote
import app.brokoli5191.quote.data.dailyDateKey
import app.brokoli5191.quote.ui.LocalAppLanguage
import app.brokoli5191.quote.ui.uiText
import app.brokoli5191.quote.ui.theme.MyApplicationTheme
import app.brokoli5191.quote.ui.components.ExpressiveButton
import app.brokoli5191.quote.ui.components.FloatingChromeBackdrop
import app.brokoli5191.quote.ui.components.bleedHorizontally
import app.brokoli5191.quote.ui.components.rememberExpressiveShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

private val DEFAULT_CONFIG = WidgetConfig()

// Same scrims enableEdgeToEdge() uses by default (only visible on API < 29 with button navigation).
private val LightNavigationScrim = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkNavigationScrim = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class WidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var reconfiguring = false
    private var saving by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        reconfiguring = WidgetConfig.exists(this, appWidgetId)
        setResult(
            RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )

        val prefs = getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("theme_mode", "DARK") ?: "DARK"
        val accent = prefs.getString("theme_accent", "Violet") ?: "Violet"
        val amoled = prefs.getBoolean("amoled_black", false)
        val language = prefs.getString("app_language", QuoteLanguage.ENGLISH) ?: QuoteLanguage.ENGLISH
        // System bar icons follow the app theme, not the system one, like in MainActivity.
        val effectiveMode = if (amoled && mode == "DARK") "AMOLED" else mode
        val appDark = when (effectiveMode) {
            "LIGHT" -> false
            "DYNAMIC" -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            else -> true
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ) { appDark },
            navigationBarStyle = SystemBarStyle.auto(LightNavigationScrim, DarkNavigationScrim) { appDark }
        )
        setContent {
            MyApplicationTheme(themeMode = mode, themeAccent = accent, amoledBlack = amoled) {
                CompositionLocalProvider(LocalAppLanguage provides language) { ConfigureScreen() }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ConfigureScreen() {
        val initial = remember { WidgetConfig.read(this, appWidgetId) }
        var showAuthor by rememberSaveable { mutableStateOf(initial.showAuthor) }
        var showIcon by rememberSaveable { mutableStateOf(initial.showIcon) }
        var showLabel by rememberSaveable { mutableStateOf(initial.showLabel) }
        var blurBackground by rememberSaveable { mutableStateOf(initial.blurBackground) }
        var bgStart by rememberSaveable { mutableStateOf(initial.backgroundStart) }
        var bgEnd by rememberSaveable { mutableStateOf(initial.backgroundEnd) }
        var labelColor by rememberSaveable { mutableStateOf(initial.labelColor) }
        var quoteColor by rememberSaveable { mutableStateOf(initial.quoteColor) }
        var authorColor by rememberSaveable { mutableStateOf(initial.authorColor) }
        var font by rememberSaveable { mutableStateOf(initial.font) }
        var quoteScale by rememberSaveable { mutableFloatStateOf(initial.quoteSizeScale) }
        var authorScale by rememberSaveable { mutableFloatStateOf(initial.authorSizeScale) }
        var visualScale by rememberSaveable { mutableFloatStateOf(initial.visualScale) }
        var cornerRadius by rememberSaveable { mutableFloatStateOf(initial.cornerRadius) }
        var borderEnabled by rememberSaveable { mutableStateOf(initial.borderEnabled) }
        var borderColor by rememberSaveable { mutableStateOf(initial.borderColor) }
        var labelX by rememberSaveable { mutableFloatStateOf(initial.labelX) }
        var labelY by rememberSaveable { mutableFloatStateOf(initial.labelY) }
        var iconX by rememberSaveable { mutableFloatStateOf(initial.iconX) }
        var iconY by rememberSaveable { mutableFloatStateOf(initial.iconY) }
        var quoteX by rememberSaveable { mutableFloatStateOf(initial.quoteX) }
        var quoteY by rememberSaveable { mutableFloatStateOf(initial.quoteY) }
        var authorX by rememberSaveable { mutableFloatStateOf(initial.authorX) }
        var authorY by rememberSaveable { mutableFloatStateOf(initial.authorY) }

        val config = WidgetConfig(
            showAuthor = showAuthor,
            showIcon = showIcon,
            showLabel = showLabel,
            blurBackground = blurBackground,
            backgroundStart = bgStart,
            backgroundEnd = bgEnd,
            labelColor = labelColor,
            quoteColor = quoteColor,
            authorColor = authorColor,
            font = font,
            quoteSizeScale = quoteScale,
            authorSizeScale = authorScale,
            visualScale = visualScale,
            cornerRadius = cornerRadius,
            borderEnabled = borderEnabled,
            borderColor = borderColor,
            labelX = labelX,
            labelY = labelY,
            iconX = iconX,
            iconY = iconY,
            quoteX = quoteX,
            quoteY = quoteY,
            authorX = authorX,
            authorY = authorY
        )

        val colorsValid = listOf(bgStart, bgEnd, labelColor, quoteColor, authorColor)
            .all { normalizeWidgetColor(it) != null } &&
            (!borderEnabled || normalizeWidgetColor(borderColor) != null)
        val portrait = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        val widgetSizeDp = remember {
            widgetSizeDpFromOptions(AppWidgetManager.getInstance(this).getAppWidgetOptions(appWidgetId), portrait)
        }
        BackHandler(enabled = saving) {}

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(uiText("Customize Widget"), fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            // The settings scroll under the preview and the floating Create button and fade out
            // there instead of ending at a hard edge.
            val layoutDirection = LocalLayoutDirection.current
            var createBarHeightPx by remember { mutableIntStateOf(0) }
            val createBarHeight = with(LocalDensity.current) { createBarHeightPx.toDp() }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = padding.calculateStartPadding(layoutDirection),
                        top = padding.calculateTopPadding(),
                        end = padding.calculateEndPadding(layoutDirection)
                    )
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionTitle("Live Preview")
                            Text(
                                uiText("Drag elements to move"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(start = 12.dp)
                            )
                        }
                        WidgetPreview(
                            config = config,
                            widgetWidthDp = widgetSizeDp.first,
                            widgetHeightDp = widgetSizeDp.second,
                            onLabelPosition = { labelX = it.first; labelY = it.second },
                            onIconPosition = { iconX = it.first; iconY = it.second },
                            onQuotePosition = { quoteX = it.first; quoteY = it.second },
                            onAuthorPosition = { authorX = it.first; authorY = it.second }
                        )
                        val hidden = hiddenElements(config, widgetSizeDp)
                        if (hidden.isNotEmpty()) {
                            Text(
                                widgetText("Hidden at this widget size: ", "Bei dieser Widget-Größe ausgeblendet: ") + hidden.joinToString(", "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box(Modifier.weight(1f)) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp + createBarHeight),
                            verticalArrangement = Arrangement.spacedBy(22.dp)
                        ) {
                            SectionTitle("Visible Elements")
                            WidgetToggle("Author", showAuthor) { showAuthor = it }
                            WidgetToggle("Decorative Icon", showIcon) { showIcon = it }
                            WidgetToggle("Quote Label", showLabel) { showLabel = it }
                            WidgetToggle("Background Blur", blurBackground) { blurBackground = it }

                            ExpressiveValueSlider("Visual Widget Size", visualScale, 0.65f..1f, "${(visualScale * 100).toInt()}%") { visualScale = it }
                            ExpressiveValueSlider("Corner Radius", cornerRadius, 0f..48f, "${cornerRadius.toInt()} dp") { cornerRadius = it }
                            WidgetToggle("Border", borderEnabled) { borderEnabled = it }
                            if (borderEnabled) ColorPicker("Border", borderColor) { borderColor = it }

                            SectionTitle("Background Presets")
                            Row(
                            Modifier
                                .fillMaxWidth()
                                .bleedHorizontally(20.dp)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                            widgetBackgroundPresets.forEach { preset ->
                                val selected = bgStart == preset.start && bgEnd == preset.end
                                Box(
                                    Modifier
                                        .width(104.dp)
                                        .height(58.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(widgetColor(preset.start, preset.start), widgetColor(preset.end, preset.end))
                                            )
                                        )
                                        .border(
                                            if (selected) 3.dp else 1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            bgStart = preset.start
                                            bgEnd = preset.end
                                            labelColor = preset.labelColor
                                            quoteColor = preset.quoteColor
                                            authorColor = preset.authorColor
                                            borderColor = preset.borderColor
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        uiText(preset.name),
                                        color = if (preset.name == "Paper") Color.Black else Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                }
                            }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ColorTextField("Start", bgStart, { bgStart = it }, Modifier.weight(1f))
                            ColorTextField("End", bgEnd, { bgEnd = it }, Modifier.weight(1f))
                            }

                            SectionTitle("Widget Font")
                            Row(
                            Modifier
                                .fillMaxWidth()
                                .bleedHorizontally(20.dp)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                            listOf("Serif", "Serif Bold", "Sans", "Monospace").forEach { option ->
                                val interactionSource = remember(option) { MutableInteractionSource() }
                                FilterChip(
                                    selected = font == option,
                                    onClick = { font = option },
                                    label = { Text(uiText(option)) },
                                    shape = rememberExpressiveShape(interactionSource, 20.dp, 9.dp),
                                    interactionSource = interactionSource,
                                    leadingIcon = if (font == option) {
                                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                            }

                            ExpressiveValueSlider("Quote Font Size", quoteScale, 0.7f..1.4f, "${(quoteScale * 100).toInt()}%") { quoteScale = it }
                            ExpressiveValueSlider("Author Font Size", authorScale, 0.7f..1.4f, "${(authorScale * 100).toInt()}%") { authorScale = it }

                            SectionTitle("Colors")
                            ColorPicker("Quote Label", labelColor) { labelColor = it }
                            ColorPicker("Quote Text", quoteColor) { quoteColor = it }
                            ColorPicker("Author", authorColor) { authorColor = it }
                            Spacer(Modifier.height(12.dp))
                        }
                        FloatingChromeBackdrop(
                            hazeState = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                        )
                    }
                }

                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { createBarHeightPx = it.height }
                ) {
                    FloatingChromeBackdrop(
                        hazeState = null,
                        modifier = Modifier.matchParentSize(),
                        fadeDown = false
                    )
                    ExpressiveButton(
                        onClick = { saveAndFinish(config) },
                        enabled = colorsValid && !saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 16.dp, top = 28.dp, end = 16.dp, bottom = 16.dp)
                            .heightIn(min = 54.dp),
                        restingCorner = 18.dp
                    ) {
                        Text(
                            if (reconfiguring) widgetText("Save", "Speichern") else uiText("Create Widget"),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun widgetText(english: String, german: String): String =
        if (LocalAppLanguage.current == QuoteLanguage.GERMAN) german else english

    @Composable
    private fun hiddenElements(config: WidgetConfig, widgetSizeDp: Pair<Int, Int>): List<String> {
        val visible = widgetElementVisibility(config, widgetSizeDp.first.toFloat(), widgetSizeDp.second.toFloat())
        return buildList {
            if (config.showLabel && !visible.label) add(uiText("Quote Label"))
            if (config.showIcon && !visible.icon) add(uiText("Decorative Icon"))
            if (config.showAuthor && !visible.author) add(uiText("Author"))
        }
    }

    @Composable
    private fun SectionTitle(text: String) {
        Text(uiText(text), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }

    @Composable
    private fun WidgetToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .clickable { onChecked(!checked) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                uiText(label),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            )
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }

    @Composable
    private fun ExpressiveValueSlider(
        label: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        valueText: String,
        onValue: (Float) -> Unit
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    uiText(label),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                )
                Text(valueText, color = MaterialTheme.colorScheme.primary)
            }
            Slider(value = value, onValueChange = onValue, valueRange = range)
        }
    }

    @Composable
    private fun ColorPicker(label: String, value: String, onValue: (String) -> Unit) {
        val colors = listOf("#FFFFFF", "#E9DDFF", "#D0BCFF", "#A0D2AD", "#ADC6FF", "#FFB2C5", "#FFDB9C", "#CAC4D0", "#1C1B1F")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(uiText(label), style = MaterialTheme.typography.labelLarge)
            // Scrolls out to the screen edges instead of being cut off at the settings padding.
            Row(
                Modifier
                    .fillMaxWidth()
                    .bleedHorizontally(20.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                colors.forEach { color ->
                    val selected = color.equals(value, true)
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(widgetColor(color, color))
                            .border(
                                if (selected) 3.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                CircleShape
                            )
                            .clickable { onValue(color) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Icon(Icons.Default.Check, null, tint = if (color == "#1C1B1F") Color.White else Color.Black)
                    }
                }
            }
            ColorTextField(label, value, onValue, Modifier.fillMaxWidth())
        }
    }

    @Composable
    private fun ColorTextField(label: String, value: String, onValue: (String) -> Unit, modifier: Modifier) {
        val invalid = normalizeWidgetColor(value) == null
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= 9) onValue(it) },
            modifier = modifier,
            label = { Text(uiText(label)) },
            singleLine = true,
            isError = invalid,
            supportingText = if (invalid) {
                { Text(widgetText("Use #RRGGBB or #AARRGGBB", "Nutze #RRGGBB oder #AARRGGBB")) }
            } else null,
            shape = RoundedCornerShape(14.dp),
            trailingIcon = {
                Icon(Icons.Default.Colorize, null, tint = if (invalid) MaterialTheme.colorScheme.error else widgetColor(value, "#FFFFFF"))
            }
        )
    }

    /**
     * Mirrors the renderer: same widget proportions (from the launcher's size options), same
     * size rules for label/icon/author, same text sizes, position limits and anchors. Everything
     * is the real size in dp times [fit], the factor that fits the widget area into the preview.
     */
    @Composable
    private fun WidgetPreview(
        config: WidgetConfig,
        widgetWidthDp: Int,
        widgetHeightDp: Int,
        onLabelPosition: (Pair<Float, Float>) -> Unit,
        onIconPosition: (Pair<Float, Float>) -> Unit,
        onQuotePosition: (Pair<Float, Float>) -> Unit,
        onAuthorPosition: (Pair<Float, Float>) -> Unit
    ) {
        val language = LocalAppLanguage.current
        val density = LocalDensity.current
        val visibility = widgetElementVisibility(config, widgetWidthDp.toFloat(), widgetHeightDp.toFloat())
        val quoteFont = when (config.font) {
            "Sans" -> FontFamily.SansSerif
            "Monospace" -> FontFamily.Monospace
            else -> FontFamily.Serif
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            val fit = min(maxWidth.value / widgetWidthDp, maxHeight.value / widgetHeightDp)
            val (cardWidthDp, cardHeightDp) = widgetCardSizeDp(widgetWidthDp.toFloat(), widgetHeightDp.toFloat(), config.visualScale)
            fun sp(valueDp: Float) = with(density) { (valueDp * fit).dp.toSp() }
            val shape = RoundedCornerShape((config.cornerRadius.coerceIn(0f, 48f) * config.visualScale.coerceIn(0.65f, 1f) * fit).dp)
            val bgStart = widgetColor(config.backgroundStart, DEFAULT_CONFIG.backgroundStart)
            val bgEnd = widgetColor(config.backgroundEnd, DEFAULT_CONFIG.backgroundEnd)

            val labelSize = (cardHeightDp * 0.07f).coerceIn(11f, 18f)
            val authorSize = (cardHeightDp * 0.08f * config.authorSizeScale).coerceIn(9f, 28f)
            val padX = if (cardWidthDp < 160f) 12f else 20f
            val padY = if (cardHeightDp < 100f) 8f else 16f
            val quoteX = WidgetElement.QUOTE.clampX(config.quoteX)
            val quoteY = WidgetElement.QUOTE.clampY(config.quoteY)
            val quoteWidthDp = (cardWidthDp * (1f - quoteX) - padX).coerceAtLeast(80f)
            val quoteMaxHeightDp = cardHeightDp * (1f - quoteY) - padY
            val quoteText = FallbackQuote.text(language)
            val quoteSizeDp = fitQuoteSize(
                quoteText.length,
                quoteWidthDp,
                quoteMaxHeightDp,
                ((if (cardWidthDp < cardHeightDp) cardWidthDp * 0.14f else cardHeightDp * 0.20f) * config.quoteSizeScale).coerceIn(12f, 42f)
            )
            val authorWidthDp = (cardWidthDp * (1f - WidgetElement.AUTHOR.clampX(config.authorX)) - padX).coerceAtLeast(80f)

            Box(
                Modifier
                    .size((cardWidthDp * fit).dp, (cardHeightDp * fit).dp)
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(bgStart, bgEnd)))
                    .then(
                        if (config.borderEnabled) {
                            Modifier.border((1.5f * fit).dp, widgetColor(config.borderColor, DEFAULT_CONFIG.borderColor), shape)
                        } else Modifier
                    )
            ) {
                if (config.blurBackground) BlurGlows(bgStart, bgEnd, fit, config.visualScale.coerceIn(0.65f, 1f))
                if (visibility.label) {
                    DraggablePreviewElement(WidgetElement.LABEL, config.labelX, config.labelY, onLabelPosition) {
                        Text(
                            QuoteCardRenderer.LABEL,
                            color = widgetColor(config.labelColor, DEFAULT_CONFIG.labelColor),
                            fontSize = sp(labelSize),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
                if (visibility.icon) {
                    DraggablePreviewElement(WidgetElement.ICON, config.iconX, config.iconY, onIconPosition, anchorEnd = true) {
                        Text(
                            "✦",
                            color = widgetColor(config.labelColor, DEFAULT_CONFIG.labelColor),
                            fontSize = sp(labelSize * 1.35f),
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                DraggablePreviewElement(WidgetElement.QUOTE, config.quoteX, config.quoteY, onQuotePosition) {
                    Text(
                        quoteText,
                        color = widgetColor(config.quoteColor, DEFAULT_CONFIG.quoteColor),
                        fontFamily = quoteFont,
                        fontStyle = if (config.font == "Serif") FontStyle.Italic else FontStyle.Normal,
                        fontWeight = if (config.font == "Serif Bold") FontWeight.Bold else FontWeight.Normal,
                        fontSize = sp(quoteSizeDp),
                        lineHeight = sp(quoteSizeDp * 1.2f),
                        maxLines = (quoteMaxHeightDp / (quoteSizeDp * 1.2f)).toInt().coerceAtLeast(1),
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width((quoteWidthDp * fit).dp)
                    )
                }
                if (visibility.author) {
                    DraggablePreviewElement(WidgetElement.AUTHOR, config.authorX, config.authorY, onAuthorPosition, anchorBottom = true) {
                        Text(
                            "— ${FallbackQuote.AUTHOR}",
                            color = widgetColor(config.authorColor, DEFAULT_CONFIG.authorColor),
                            fontFamily = quoteFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = sp(authorSize),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width((authorWidthDp * fit).dp)
                        )
                    }
                }
            }
        }
    }

    /**
     * Rough line-count estimate (about half an em per character) standing in for the renderer's
     * measured shrink-to-fit loop.
     */
    private fun fitQuoteSize(textLength: Int, widthDp: Float, maxHeightDp: Float, startDp: Float): Float {
        var size = startDp
        val lowest = minOf(8f, startDp)
        while (size > lowest) {
            val lines = ceil(textLength * size * 0.5f / widthDp).coerceAtLeast(1f)
            if (lines * size * 1.2f <= maxHeightDp) break
            size -= 0.5f
        }
        return size
    }

    /** The widget's glows. Compose only blurs from API 31, so older versions get soft radial fades instead. */
    @Composable
    private fun BlurGlows(bgStart: Color, bgEnd: Color, fit: Float, visualScale: Float) {
        val nativeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val glows = widgetBlurGlows(bgStart.toArgb(), bgEnd.toArgb())
        Canvas(
            Modifier
                .fillMaxSize()
                .then(
                    if (nativeBlur) Modifier.blur((WIDGET_BLUR_RADIUS_DP * fit).dp, BlurredEdgeTreatment.Unbounded)
                    else Modifier
                )
        ) {
            glows.forEach { glow ->
                // Glow geometry is relative to the whole widget area, the card is inset by the visual scale.
                val areaWidth = size.width / visualScale
                val areaHeight = size.height / visualScale
                val inset = (1f - visualScale) / 2f
                val center = Offset((glow.centerX - inset) * areaWidth, (glow.centerY - inset) * areaHeight)
                val radius = glow.radiusOfWidth * areaWidth
                val color = Color(glow.color)
                if (nativeBlur) {
                    drawCircle(color, radius, center)
                } else {
                    val reach = radius + WIDGET_BLUR_RADIUS_DP * fit * density
                    drawCircle(
                        Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, reach),
                        reach,
                        center
                    )
                }
            }
        }
    }

    @Composable
    private fun DraggablePreviewElement(
        element: WidgetElement,
        x: Float,
        y: Float,
        onPosition: (Pair<Float, Float>) -> Unit,
        anchorEnd: Boolean = false,
        anchorBottom: Boolean = false,
        content: @Composable () -> Unit
    ) {
        var containerSize by remember { mutableStateOf(IntSize.Zero) }
        var elementSize by remember { mutableStateOf(IntSize.Zero) }
        val latestX by rememberUpdatedState(x)
        val latestY by rememberUpdatedState(y)
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
        ) {
            Box(
                Modifier
                    .offset {
                        // The renderer anchors the icon by its right edge and the author by its baseline.
                        IntOffset(
                            (element.clampX(x) * containerSize.width).roundToInt() - if (anchorEnd) elementSize.width else 0,
                            (element.clampY(y) * containerSize.height).roundToInt() - if (anchorBottom) elementSize.height else 0
                        )
                    }
                    .onSizeChanged { elementSize = it }
                    .pointerInput(containerSize) {
                        var currentX = latestX
                        var currentY = latestY
                        detectDragGestures(
                            onDragStart = {
                                currentX = element.clampX(latestX)
                                currentY = element.clampY(latestY)
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            if (containerSize.width == 0 || containerSize.height == 0) return@detectDragGestures
                            currentX = element.clampX(currentX + dragAmount.x / containerSize.width)
                            currentY = element.clampY(currentY + dragAmount.y / containerSize.height)
                            onPosition(currentX to currentY)
                        }
                    }
            ) {
                content()
            }
        }
    }

    private fun saveAndFinish(config: WidgetConfig) {
        if (saving) return
        saving = true
        val normalized = config.copy(
            backgroundStart = normalizeWidgetColor(config.backgroundStart) ?: DEFAULT_CONFIG.backgroundStart,
            backgroundEnd = normalizeWidgetColor(config.backgroundEnd) ?: DEFAULT_CONFIG.backgroundEnd,
            labelColor = normalizeWidgetColor(config.labelColor) ?: DEFAULT_CONFIG.labelColor,
            quoteColor = normalizeWidgetColor(config.quoteColor) ?: DEFAULT_CONFIG.quoteColor,
            authorColor = normalizeWidgetColor(config.authorColor) ?: DEFAULT_CONFIG.authorColor,
            borderColor = normalizeWidgetColor(config.borderColor) ?: DEFAULT_CONFIG.borderColor
        )
        val prefs = getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        val language = prefs.getString("app_language", QuoteLanguage.ENGLISH) ?: QuoteLanguage.ENGLISH
        lifecycleScope.launch {
            try {
                val quote = withContext(Dispatchers.IO) {
                    val repository = QuoteRepository(
                        AppDatabase.getInstance(this@WidgetConfigureActivity).quoteDao(),
                        InstallationSeed.get(this@WidgetConfigureActivity)
                    )
                    val sourceMode = prefs.getString("quote_source_mode", QuoteSourceMode.ALL) ?: QuoteSourceMode.ALL
                    runCatching {
                        repository.getDailyQuote(dailyDateKey(), sourceMode, language, prefs.getBoolean("show_anonymous_quotes", false))
                    }.getOrNull()
                        ?: QuoteEntity(text = FallbackQuote.text(language), author = FallbackQuote.AUTHOR, category = "Life")
                }
                // Written right before the first render so a cancelled save leaves no config behind.
                withContext(Dispatchers.IO) { WidgetConfig.write(this@WidgetConfigureActivity, appWidgetId, normalized) }
                QuoteWidgetProvider.updateAppWidget(
                    this@WidgetConfigureActivity,
                    AppWidgetManager.getInstance(this@WidgetConfigureActivity),
                    appWidgetId,
                    quote
                )
                setResult(
                    RESULT_OK,
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                )
                val message = if (reconfiguring) "Widget updated" else "Widget created"
                val messageDe = if (reconfiguring) "Widget aktualisiert" else "Widget erstellt"
                Toast.makeText(
                    this@WidgetConfigureActivity,
                    if (language == QuoteLanguage.GERMAN) messageDe else message,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saving = false
            }
        }
    }
}

private fun widgetColor(value: String, fallbackHex: String): Color = Color(parseWidgetColor(value, fallbackHex))
