package app.brokoli5191.quote.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteRepository
import app.brokoli5191.quote.ui.theme.MyApplicationTheme
import app.brokoli5191.quote.ui.theme.SerifFontFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class WidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve widget ID
        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // If invalid, abort
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Set result to CANCELED by default so that if the user backs out, the widget is not placed
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, resultValue)

        setContent {
            MyApplicationTheme(themeMode = "AMOLED", themeAccent = "Violet") {
                ConfigureScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ConfigureScreen() {
        val pref = getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        val initialStyle = pref.getString("widget_style_$appWidgetId", "Quote") ?: "Quote"
        val initialGlass = pref.getBoolean("widget_glass_$appWidgetId", false)
        val initialBgStart = pref.getString("widget_bg_color_start_$appWidgetId", "#594983") ?: "#594983"
        val initialBgEnd = pref.getString("widget_bg_color_end_$appWidgetId", "#37265E") ?: "#37265E"
        val initialHeader = pref.getString("widget_header_color_$appWidgetId", "#D0BCFF") ?: "#D0BCFF"
        val initialText = pref.getString("widget_text_color_$appWidgetId", "#E9DDFF") ?: "#E9DDFF"
        val initialAuthor = pref.getString("widget_author_color_$appWidgetId", "#A0D2AD") ?: "#A0D2AD"

        var style by remember { mutableStateOf(initialStyle) }
        var glassmorphism by remember { mutableStateOf(initialGlass) }

        var bgColorStart by remember { mutableStateOf(initialBgStart) }
        var bgColorEnd by remember { mutableStateOf(initialBgEnd) }
        var headerColor by remember { mutableStateOf(initialHeader) }
        var textColor by remember { mutableStateOf(initialText) }
        var authorColor by remember { mutableStateOf(initialAuthor) }

        val scrollState = rememberScrollState()

        // Background presets
        val bgPresets = listOf(
            Triple("Quote (Purple)", "#594983", "#37265E"),
            Triple("Quote Gold", "#412D00", "#201600"),
            Triple("Emerald", "#0A2F1D", "#03170D"),
            Triple("Navy", "#0D2240", "#050F20"),
            Triple("Ruby", "#380E1B", "#1B050B"),
            Triple("Obsidian", "#1C1B1F", "#111015")
        )

        // Component presets
        val componentPresets = listOf(
            "#D0BCFF", "#E9DDFF", "#A0D2AD", "#ADC6FF", "#FFFFB2C5", "#FFDB9C", "#FFFFFF", "#CAC4D0", "#948F9A"
        )

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Customize Widget", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                Surface(
                    tonalElevation = 8.dp,
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(16.dp).navigationBarsPadding()) {
                        Button(
                            onClick = { saveAndFinish(style, glassmorphism, bgColorStart, bgColorEnd, headerColor, textColor, authorColor) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Create Widget ✦", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Widget Preview Card
                Text(
                    text = "Live Preview",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SimulatedWidgetPreview(
                        style = style,
                        glassmorphism = glassmorphism,
                        bgColorStart = bgColorStart,
                        bgColorEnd = bgColorEnd,
                        headerColor = headerColor,
                        textColor = textColor,
                        authorColor = authorColor
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                // Selection for Style
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Widget Design Style",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Quote", "Minimal", "Compact").forEach { s ->
                            val isSelected = style == s
                            Button(
                                onClick = {
                                    style = s
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(s, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                }

                // Glassmorphism toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                        .clickable { glassmorphism = !glassmorphism }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Glassmorphism Look",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Translucent blurred backdrop with glass edge glow.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Switch(
                        checked = glassmorphism,
                        onCheckedChange = { glassmorphism = it }
                    )
                }

                // Background gradient start and end customization
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Background Presets",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bgPresets.forEach { (name, start, end) ->
                            val isSelected = bgColorStart.equals(start, ignoreCase = true) && bgColorEnd.equals(end, ignoreCase = true)
                            val gradientBrush = Brush.linearGradient(
                                listOf(safeParseColor(start), safeParseColor(end))
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = 90.dp, height = 46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(gradientBrush)
                                    .border(
                                        width = if (isSelected) 2.dp else 0.5.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        bgColorStart = start
                                        bgColorEnd = end
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = bgColorStart,
                            onValueChange = { if (it.length <= 9) bgColorStart = it },
                            label = { Text("BG Gradient Start") },
                            placeholder = { Text("#594983") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = { Icon(Icons.Default.Colorize, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                        OutlinedTextField(
                            value = bgColorEnd,
                            onValueChange = { if (it.length <= 9) bgColorEnd = it },
                            label = { Text("BG Gradient End") },
                            placeholder = { Text("#37265E") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = { Icon(Icons.Default.Colorize, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                    }
                }

                // Component Colors Customization
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Component Colors",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Header Text Color
                    ColorPickerComponent(
                        label = "Header Text Color",
                        currentColor = headerColor,
                        presets = componentPresets,
                        onColorSelected = { headerColor = it }
                    )

                    // Quote Text Color
                    ColorPickerComponent(
                        label = "Quote Text Color",
                        currentColor = textColor,
                        presets = componentPresets,
                        onColorSelected = { textColor = it }
                    )

                    // Author Color
                    ColorPickerComponent(
                        label = "Author Text Color",
                        currentColor = authorColor,
                        presets = componentPresets,
                        onColorSelected = { authorColor = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    @Composable
    fun ColorPickerComponent(
        label: String,
        currentColor: String,
        presets: List<String>,
        onColorSelected: (String) -> Unit
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = currentColor.equals(preset, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(safeParseColor(preset))
                            .border(
                                width = if (isSelected) 2.dp else 0.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(preset) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = if (preset.equals("#FFFFFF", ignoreCase = true) || preset.equals("#E9DDFF", ignoreCase = true)) Color.Black else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = currentColor,
                onValueChange = { if (it.length <= 9) onColorSelected(it) },
                placeholder = { Text("#FFFFFF") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = { Icon(Icons.Default.Colorize, contentDescription = null, tint = safeParseColor(currentColor)) }
            )
        }
    }

    @Composable
    fun SimulatedWidgetPreview(
        style: String,
        glassmorphism: Boolean,
        bgColorStart: String,
        bgColorEnd: String,
        headerColor: String,
        textColor: String,
        authorColor: String
    ) {
        val parsedBgStart = safeParseColor(bgColorStart)
        val parsedBgEnd = safeParseColor(bgColorEnd)
        val parsedHeader = safeParseColor(headerColor)
        val parsedText = safeParseColor(textColor)
        val parsedAuthor = safeParseColor(authorColor)

        val text = "The happiness of your life depends upon the quality of your thoughts."
        val authorName = "Marcus Aurelius"

        val widgetBackground = if (glassmorphism) {
            val baseColor = if (bgColorStart.isNotBlank()) parsedBgStart else Color(0xFF1C1B1F)
            Brush.linearGradient(listOf(baseColor.copy(alpha = 0.45f), baseColor.copy(alpha = 0.45f)))
        } else {
            Brush.linearGradient(listOf(parsedBgStart, parsedBgEnd))
        }

        val shape = RoundedCornerShape(24.dp)

        Box(
            modifier = Modifier
                .width(280.dp)
                .height(140.dp)
                .clip(shape)
                .background(widgetBackground)
                .border(
                    BorderStroke(
                        width = 1.dp,
                        color = if (glassmorphism) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f)
                    ),
                    shape = shape
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            when (style) {
                "Quote" -> {
                    // Star decoration top right
                    Text(
                        text = "✦",
                        style = MaterialTheme.typography.labelLarge,
                        color = parsedHeader.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.TopEnd)
                    )

                    // Glow quotation mark in background
                    Text(
                        text = "“",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 72.sp,
                            fontFamily = SerifFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic
                        ),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.align(Alignment.BottomStart).offset(x = 4.dp, y = 14.dp)
                    )

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "QUOTE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = parsedHeader
                        )

                        Text(
                            text = "\"$text\"",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = SerifFontFamily,
                                fontStyle = FontStyle.Italic,
                                lineHeight = 18.sp
                            ),
                            color = parsedText,
                            maxLines = 3,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Text(
                            text = "— ${authorName.uppercase()}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = parsedAuthor
                        )
                    }
                }
                "Minimal" -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "QUOTE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = parsedHeader
                        )

                        Text(
                            text = "\"$text\"",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = SerifFontFamily,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 18.sp
                            ),
                            color = parsedText,
                            maxLines = 3,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Text(
                            text = authorName,
                            style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                            color = parsedAuthor
                        )
                    }
                }
                else -> { // Compact
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Amber vertical accent stripe on the left
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFFFDB700))
                        )

                        Column(
                            modifier = Modifier.fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "QUOTE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = parsedHeader
                            )

                            Text(
                                text = "\"$text\"",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = SerifFontFamily,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp
                                ),
                                color = parsedText,
                                maxLines = 4,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )

                            Text(
                                text = "— $authorName",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = parsedAuthor
                            )
                        }
                    }
                }
            }
        }
    }

    private fun safeParseColor(hex: String): Color {
        return try {
            if (hex.isBlank()) return Color.White
            val cleaned = if (hex.startsWith("#")) hex else "#$hex"
            Color(android.graphics.Color.parseColor(cleaned))
        } catch (e: Exception) {
            Color.White
        }
    }

    private fun saveAndFinish(
        style: String,
        glassmorphism: Boolean,
        bgColorStart: String,
        bgColorEnd: String,
        headerColor: String,
        textColor: String,
        authorColor: String
    ) {
        val pref = getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        pref.edit().apply {
            putString("widget_style_$appWidgetId", style)
            putBoolean("widget_glass_$appWidgetId", glassmorphism)
            putString("widget_bg_color_start_$appWidgetId", bgColorStart)
            putString("widget_bg_color_end_$appWidgetId", bgColorEnd)
            putString("widget_header_color_$appWidgetId", headerColor)
            putString("widget_text_color_$appWidgetId", textColor)
            putString("widget_author_color_$appWidgetId", authorColor)
            apply()
        }

        // Trigger widget update
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val context = this

        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getInstance(context)
            val repository = QuoteRepository(database.quoteDao())
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = dateFormat.format(Date())
            
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

            withContext(Dispatchers.Main) {
                QuoteWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId, quote)
                
                // Set result OK and finish
                val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                setResult(RESULT_OK, resultValue)
                
                Toast.makeText(context, "Widget created successfully! ✦", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
