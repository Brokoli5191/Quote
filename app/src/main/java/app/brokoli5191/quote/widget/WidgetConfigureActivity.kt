package app.brokoli5191.quote.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.InstallationSeed
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteRepository
import app.brokoli5191.quote.data.QuoteSourceMode
import app.brokoli5191.quote.ui.theme.MyApplicationTheme
import app.brokoli5191.quote.ui.components.ExpressiveButton
import app.brokoli5191.quote.ui.components.rememberExpressiveShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class WidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

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
        setResult(
            RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )

        val prefs = getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("theme_mode", "DARK") ?: "DARK"
        val accent = prefs.getString("theme_accent", "Violet") ?: "Violet"
        val amoled = prefs.getBoolean("amoled_black", false)
        setContent {
            MyApplicationTheme(themeMode = mode, themeAccent = accent, amoledBlack = amoled) {
                ConfigureScreen()
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

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Customize Widget", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 8.dp) {
                    ExpressiveButton(
                        onClick = { saveAndFinish(config) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp)
                            .height(54.dp),
                        restingCorner = 18.dp
                    ) {
                        Text("Create Widget", fontWeight = FontWeight.Bold)
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionTitle("Live Preview")
                        Text("Drag elements to move", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    WidgetPreview(
                        config = config,
                        onLabelPosition = { labelX = it.first; labelY = it.second },
                        onIconPosition = { iconX = it.first; iconY = it.second },
                        onQuotePosition = { quoteX = it.first; quoteY = it.second },
                        onAuthorPosition = { authorX = it.first; authorY = it.second }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                }

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
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
                    Modifier.horizontalScroll(rememberScrollState()),
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
                                        listOf(parseColor(preset.start), parseColor(preset.end))
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
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                preset.name,
                                color = if (preset.name == "Paper") Color.Black else Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
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
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                    listOf("Serif", "Serif Bold", "Sans", "Monospace").forEach { option ->
                        val interactionSource = remember(option) { MutableInteractionSource() }
                        FilterChip(
                            selected = font == option,
                            onClick = { font = option },
                            label = { Text(option) },
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
            }
        }
    }

    @Composable
    private fun SectionTitle(text: String) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
            Text(label, fontWeight = FontWeight.SemiBold)
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
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(valueText, color = MaterialTheme.colorScheme.primary)
            }
            Slider(value = value, onValueChange = onValue, valueRange = range)
        }
    }

    @Composable
    private fun ColorPicker(label: String, value: String, onValue: (String) -> Unit) {
        val colors = listOf("#FFFFFF", "#E9DDFF", "#D0BCFF", "#A0D2AD", "#ADC6FF", "#FFB2C5", "#FFDB9C", "#CAC4D0", "#1C1B1F")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                colors.forEach { color ->
                    val selected = color.equals(value, true)
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(parseColor(color))
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
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= 9) onValue(it) },
            modifier = modifier,
            label = { Text(label) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            trailingIcon = { Icon(Icons.Default.Colorize, null, tint = parseColor(value)) }
        )
    }

    @Composable
    private fun WidgetPreview(
        config: WidgetConfig,
        onLabelPosition: (Pair<Float, Float>) -> Unit,
        onIconPosition: (Pair<Float, Float>) -> Unit,
        onQuotePosition: (Pair<Float, Float>) -> Unit,
        onAuthorPosition: (Pair<Float, Float>) -> Unit
    ) {
        val shape = RoundedCornerShape(config.cornerRadius.dp)
        val quoteFont = when (config.font) {
            "Sans" -> FontFamily.SansSerif
            "Monospace" -> FontFamily.Monospace
            else -> FontFamily.Serif
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .fillMaxWidth(config.visualScale)
                    .fillMaxHeight(config.visualScale)
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(parseColor(config.backgroundStart), parseColor(config.backgroundEnd))))
                    .then(
                        if (config.borderEnabled) Modifier.border(1.dp, parseColor(config.borderColor), shape)
                        else Modifier
                    )
            ) {
                if (config.blurBackground) {
                    Box(
                        Modifier
                            .size(150.dp)
                            .offset(x = (-35).dp, y = (-45).dp)
                            .blur(28.dp)
                            .background(parseColor(config.labelColor).copy(alpha = 0.45f), CircleShape)
                    )
                    Box(
                        Modifier
                            .size(170.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 55.dp, y = 70.dp)
                            .blur(32.dp)
                            .background(parseColor(config.authorColor).copy(alpha = 0.35f), CircleShape)
                    )
                }
                if (config.showLabel) {
                    DraggablePreviewElement(config.labelX, config.labelY, onLabelPosition) {
                        Text("QUOTE", color = parseColor(config.labelColor), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                if (config.showIcon) {
                    DraggablePreviewElement(config.iconX, config.iconY, onIconPosition) {
                        Text("✦", color = parseColor(config.labelColor), fontSize = 20.sp)
                    }
                }
                DraggablePreviewElement(config.quoteX, config.quoteY, onQuotePosition) {
                    Text(
                        "The happiness of your life depends upon the quality of your thoughts.",
                        color = parseColor(config.quoteColor),
                        fontFamily = quoteFont,
                        fontStyle = if (config.font == "Serif") FontStyle.Italic else FontStyle.Normal,
                        fontWeight = if (config.font == "Serif Bold") FontWeight.Bold else FontWeight.Normal,
                        fontSize = (18 * config.quoteSizeScale).sp,
                        lineHeight = (23 * config.quoteSizeScale).sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.82f)
                    )
                }
                if (config.showAuthor) {
                    DraggablePreviewElement(config.authorX, config.authorY, onAuthorPosition) {
                        Text(
                            "— Marcus Aurelius",
                            color = parseColor(config.authorColor),
                            fontFamily = quoteFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = (12 * config.authorSizeScale).sp
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun DraggablePreviewElement(
        x: Float,
        y: Float,
        onPosition: (Pair<Float, Float>) -> Unit,
        content: @Composable () -> Unit
    ) {
        var containerSize by remember { mutableStateOf(IntSize.Zero) }
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
                        IntOffset(
                            (x * containerSize.width).roundToInt(),
                            (y * containerSize.height).roundToInt()
                        )
                    }
                    .pointerInput(containerSize) {
                        var currentX = latestX
                        var currentY = latestY
                        detectDragGestures(
                            onDragStart = {
                                currentX = latestX
                                currentY = latestY
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            if (containerSize.width == 0 || containerSize.height == 0) return@detectDragGestures
                            currentX = (currentX + dragAmount.x / containerSize.width).coerceIn(0f, 0.95f)
                            currentY = (currentY + dragAmount.y / containerSize.height).coerceIn(0f, 0.95f)
                            onPosition(currentX to currentY)
                        }
                    }
            ) {
                content()
            }
        }
    }

    private fun parseColor(value: String): Color = runCatching {
        Color(android.graphics.Color.parseColor(if (value.startsWith("#")) value else "#$value"))
    }.getOrElse { Color.White }

    private fun saveAndFinish(config: WidgetConfig) {
        WidgetConfig.write(this, appWidgetId, config)
        val manager = AppWidgetManager.getInstance(this)
        CoroutineScope(Dispatchers.IO).launch {
            val repository = QuoteRepository(
                AppDatabase.getInstance(this@WidgetConfigureActivity).quoteDao(),
                InstallationSeed.get(this@WidgetConfigureActivity)
            )
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val sourceMode = getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
                .getString("quote_source_mode", QuoteSourceMode.ALL) ?: QuoteSourceMode.ALL
            val quote = runCatching { repository.getDailyQuote(date, sourceMode) }.getOrNull()
                ?: QuoteEntity(
                    text = if (sourceMode == QuoteSourceMode.COMMUNITY) "No community quotes available yet." else "Stay hungry. Stay foolish.",
                    author = if (sourceMode == QuoteSourceMode.COMMUNITY) "Open Quote to sync" else "Steve Jobs",
                    category = "Life"
                )
            withContext(Dispatchers.Main) {
                QuoteWidgetProvider.updateAppWidget(this@WidgetConfigureActivity, manager, appWidgetId, quote)
                setResult(
                    RESULT_OK,
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                )
                Toast.makeText(this@WidgetConfigureActivity, "Widget created", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
