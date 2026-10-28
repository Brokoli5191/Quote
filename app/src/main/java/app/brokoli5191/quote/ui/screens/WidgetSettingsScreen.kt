package app.brokoli5191.quote.ui.screens

import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brokoli5191.quote.BuildConfig
import app.brokoli5191.quote.data.QuoteSourceMode
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.ui.QuoteViewModel
import app.brokoli5191.quote.ui.LocalAppLanguage
import app.brokoli5191.quote.ui.localizeUi
import app.brokoli5191.quote.ui.uiText
import app.brokoli5191.quote.ui.components.ExpressiveButton
import app.brokoli5191.quote.ui.components.ExpressiveOutlinedButton
import app.brokoli5191.quote.ui.components.ExpressiveTextButton
import app.brokoli5191.quote.ui.components.QuoteTimePickerDialog
import app.brokoli5191.quote.utils.UpdateStatus
import java.util.Calendar

@Composable
fun WidgetSettingsScreen(viewModel: QuoteViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current
    val uiLanguage = LocalAppLanguage.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                viewModel.setDailyReminderEnabled(true)
                Toast.makeText(
                    context,
                    localizeUi("Daily Reminder scheduled! ✦", uiLanguage),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    localizeUi("Notifications permission is required to receive daily quotes.", uiLanguage),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 112.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Elegant Settings Page Title (matches Library / Saved headers)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 0.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = uiText("Settings"),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Section 2: App Theme Selection Configurations (AMOLED Black, Dark, Light, Dynamic)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = uiText("App Theme"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                )

                val activeThemeMode by viewModel.themeMode.collectAsStateWithLifecycle()
                val modes = listOf("LIGHT", "DARK", "AMOLED", "DYNAMIC")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.chunked(2).forEach { rowModes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowModes.forEach { mode ->
                                val isSelected = activeThemeMode == mode
                                val label = when (mode) {
                                    "DYNAMIC" -> "System"
                                    "AMOLED" -> "AMOLED"
                                    else -> mode.lowercase().replaceFirstChar { it.uppercase() }
                                }
                                ExpressiveButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.setThemeMode(mode)
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    restingCorner = 12.dp,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        contentColor = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    SettingsButtonText(uiText(label))
                                }
                            }
                        }
                    }
                }
                Text(
                    text = when (activeThemeMode) {
                        "LIGHT" -> uiText("Light surfaces for bright environments.")
                        "DARK" -> uiText("Comfortable dark gray surfaces with clear elevation.")
                        "AMOLED" -> uiText("True-black backgrounds for OLED displays.")
                        else -> uiText("Follows the system appearance and uses system colors when available.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Section 3: Accent Palette Settings
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = uiText("Accent Palette"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                )

                val activeThemeMode by viewModel.themeMode.collectAsStateWithLifecycle()
                val isDynamic = activeThemeMode == "DYNAMIC" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeThemeAccent by viewModel.themeAccent.collectAsStateWithLifecycle()
                    val colors = listOf("Violet", "Amber", "Green", "Blue", "Rose")
                    colors.forEach { color ->
                        val isSelected = !isDynamic && activeThemeAccent == color
                        val colorLabel = uiText(color)
                        val displayColor = when (color) {
                            "Violet" -> Color(0xFFD0BCFF)
                            "Amber" -> Color(0xFFFFDB9C)
                            "Green" -> Color(0xFFA0D2AD)
                            "Blue" -> Color(0xFFADC6FF)
                            else -> Color(0xFFFFB2C5) // Rose
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isDynamic) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) displayColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .semantics {
                                    contentDescription = colorLabel
                                }
                                .selectable(
                                    selected = isSelected,
                                    enabled = !isDynamic,
                                    role = Role.RadioButton
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.setThemeAccent(color)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Circular indicator (only concentric color swatch representation, no text labels)
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 20.dp else 14.dp)
                                    .background(
                                        if (isDynamic) displayColor.copy(alpha = 0.4f) else displayColor,
                                        CircleShape
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }

                if (isDynamic) {
                    Text(
                        text = uiText("Accent colors come from your system in System mode."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = uiText("Quote Sources"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                val sourceMode by viewModel.quoteSourceMode.collectAsStateWithLifecycle()
                val options = listOf(
                    QuoteSourceMode.ALL to "All",
                    QuoteSourceMode.CURATED to "Curated",
                    QuoteSourceMode.COMMUNITY to "Community"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { (mode, title) ->
                        val selected = sourceMode == mode
                        ExpressiveButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setQuoteSourceMode(mode)
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            restingCorner = 12.dp,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = if (selected) BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            ) else null,
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            SettingsButtonText(
                                uiText(title),
                                MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
                Text(
                    text = uiText("All combines curated and community quotes. Personal quotes stay in My Quotes."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = uiText("Language"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        QuoteLanguage.ENGLISH to "English",
                        QuoteLanguage.GERMAN to "German"
                    ).forEach { (language, title) ->
                        val selected = appLanguage == language
                        ExpressiveButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setAppLanguage(language)
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            restingCorner = 12.dp,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            SettingsButtonText(
                                localizeUi(title, appLanguage),
                                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                val showAnonymous by viewModel.showAnonymousQuotes.collectAsStateWithLifecycle()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(uiText("Anonymous reflections"), fontWeight = FontWeight.SemiBold)
                        Text(
                            uiText("Show original poetic quotes without an author"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showAnonymous,
                        onCheckedChange = viewModel::setShowAnonymousQuotes
                    )
                }

                val authorImagesEnabled by viewModel.authorImagesEnabled.collectAsStateWithLifecycle()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(uiText("Author pictures"), fontWeight = FontWeight.SemiBold)
                        Text(
                            uiText("Portraits are loaded from Wikipedia (Wikimedia) when enabled."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = authorImagesEnabled,
                        onCheckedChange = { isChecked ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.setAuthorImagesEnabled(isChecked)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Section 4: Daily Quote Reminders
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = uiText("Daily quote reminder"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                )

                val reminderEnabled by viewModel.dailyReminderEnabled.collectAsStateWithLifecycle()
                val reminderHour by viewModel.dailyReminderHour.collectAsStateWithLifecycle()
                val reminderMinute by viewModel.dailyReminderMinute.collectAsStateWithLifecycle()
                val formattedReminderTime = formatReminderTime(context, reminderHour, reminderMinute)
                var showTimePicker by rememberSaveable { mutableStateOf(false) }
                val reduceMotion by viewModel.lowPerformanceMode.collectAsStateWithLifecycle()

                if (showTimePicker) {
                    QuoteTimePickerDialog(
                        initialHour = reminderHour,
                        initialMinute = reminderMinute,
                        onConfirm = { hour, minute ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.updateDailyReminderTime(hour, minute)
                            showTimePicker = false
                        },
                        onDismiss = { showTimePicker = false }
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(modifier = Modifier.padding(end = 8.dp)) {
                                    Text(
                                        text = uiText("Receive one quote at your chosen time."),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Switch(
                                checked = reminderEnabled,
                                onCheckedChange = { isChecked ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (isChecked) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            val hasPermission = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) == PackageManager.PERMISSION_GRANTED

                                            if (hasPermission) {
                                                viewModel.setDailyReminderEnabled(true)
                                                Toast.makeText(context, localizeUi("Daily Reminder scheduled! ✦", uiLanguage), Toast.LENGTH_SHORT).show()
                                            } else {
                                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        } else {
                                            viewModel.setDailyReminderEnabled(true)
                                            Toast.makeText(context, localizeUi("Daily Reminder scheduled! ✦", uiLanguage), Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        viewModel.setDailyReminderEnabled(false)
                                        Toast.makeText(context, localizeUi("Daily Reminder disabled", uiLanguage), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        AnimatedVisibility(
                            visible = reminderEnabled,
                            enter = if (reduceMotion) EnterTransition.None else expandVertically() + fadeIn(),
                            exit = if (reduceMotion) ExitTransition.None else shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = uiText("Choose Notification Time"),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )

                                val changeTimeLabel = uiText("Change time")
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                        .clickable(onClickLabel = changeTimeLabel, role = Role.Button) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            showTimePicker = true
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = formattedReminderTime,
                                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = uiText("Tap to change"),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                Text(
                                    text = uiText("Scheduled daily at {time}").replace("{time}", formattedReminderTime),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Section 4b: Performance Settings
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = uiText("Performance"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                )

                val isLowPerf by viewModel.lowPerformanceMode.collectAsStateWithLifecycle()
                val blurNavigationSurfaces by viewModel.blurNavigationSurfaces.collectAsStateWithLifecycle()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = uiText("Blur Navigation Surfaces"),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = uiText("Adds a translucent effect to navigation areas. Disable it if scrolling feels slow."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = blurNavigationSurfaces,
                            onCheckedChange = viewModel::setBlurNavigationSurfaces
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = uiText("Low Performance Mode"),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = uiText("Reduces motion and visual effects to improve performance and battery life."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = isLowPerf,
                            onCheckedChange = { isChecked ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setLowPerformanceMode(isChecked)
                                Toast.makeText(
                                    context,
                                    localizeUi(if (isChecked) "Low Performance Mode enabled" else "Standard performance mode active", uiLanguage),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Section 5: In-App Updates
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = uiText("Updates"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                )

                val autoUpdateEnabled by viewModel.autoUpdateEnabled.collectAsStateWithLifecycle()
                val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = uiText("Automatically check for updates"),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = uiText("Checks once a day. Downloads and installs only start when you choose them."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoUpdateEnabled,
                                onCheckedChange = { isChecked ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.setAutoUpdateEnabled(isChecked)
                                }
                            )
                        }

                        // Status row
                        val statusText = when (val s = updateStatus) {
                            is UpdateStatus.Idle -> uiText("Check for app and community quote updates.")
                            is UpdateStatus.Checking -> uiText("Checking for updates…")
                            is UpdateStatus.UpToDate -> uiText("You're on the latest version.")
                            is UpdateStatus.UpdateAvailable -> uiText("Update available: v{version}").replace("{version}", s.version)
                            is UpdateStatus.Downloading -> uiText("Downloading… {progress}%").replace("{progress}", s.progress.toString())
                            is UpdateStatus.ReadyToInstall -> uiText("v{version} ready to install.").replace("{version}", s.version)
                            is UpdateStatus.Error -> localizeUi(s.message, uiLanguage)
                        }
                        val statusColor = when (updateStatus) {
                            is UpdateStatus.UpdateAvailable, is UpdateStatus.ReadyToInstall ->
                                MaterialTheme.colorScheme.primary
                            is UpdateStatus.Error ->
                                MaterialTheme.colorScheme.error
                            else ->
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor
                        )

                        val downloading = updateStatus as? UpdateStatus.Downloading
                        if (downloading != null) {
                            LinearProgressIndicator(
                                progress = { downloading.progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isDownloading = updateStatus is UpdateStatus.Downloading
                            val isChecking = updateStatus is UpdateStatus.Checking

                            ExpressiveOutlinedButton(
                                onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.checkForUpdatesManually()
                                },
                                enabled = !isChecking && !isDownloading,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                restingCorner = 12.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                SettingsButtonText(uiText("Check"))
                            }

                            when (val s = updateStatus) {
                                is UpdateStatus.UpdateAvailable -> {
                                    ExpressiveButton(
                                        onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.downloadUpdate(s.downloadUrl, s.version)
                                        },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        restingCorner = 12.dp,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        SettingsButtonText(uiText("Download"))
                                    }
                                }
                                is UpdateStatus.ReadyToInstall -> {
                                    ExpressiveButton(
                                        onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.installUpdate(context, s.filePath)
                                        },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        restingCorner = 12.dp,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        SettingsButtonText(uiText("Install"))
                                    }
                                }
                                else -> Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Section 6: Backup & Restore
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = uiText("Backup & Restore"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                )

                val exportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json"),
                    onResult = { uri ->
                        if (uri != null) {
                            viewModel.exportBackup(
                                uri = uri,
                                onSuccess = {
                                    Toast.makeText(context, localizeUi("Backup exported successfully! ✦", uiLanguage), Toast.LENGTH_LONG).show()
                                },
                                onError = { error ->
                                    Toast.makeText(
                                        context,
                                        localizeUi("Export failed: {error}", uiLanguage).replace("{error}", localizeUi(error, uiLanguage)),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                    }
                )

                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri ->
                        if (uri != null) {
                            viewModel.importBackup(
                                uri = uri,
                                onSuccess = { customCount, favCount, settingsRestored ->
                                    Toast.makeText(
                                        context,
                                        localizeUi(
                                            if (settingsRestored) {
                                                "Restore complete! Imported {custom} custom quotes, updated {favorites} favorites, and restored settings."
                                            } else {
                                                "Restore complete! Imported {custom} custom quotes and updated {favorites} favorites."
                                            },
                                            uiLanguage
                                        )
                                            .replace("{custom}", customCount.toString())
                                            .replace("{favorites}", favCount.toString()),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    if (
                                        settingsRestored &&
                                        viewModel.dailyReminderEnabled.value &&
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        // Runtime permissions are device-specific and cannot be part of a backup.
                                        viewModel.setDailyReminderEnabled(false)
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    if (!settingsRestored) viewModel.loadDailyQuote()
                                    viewModel.runVerification()
                                },
                                onError = { error ->
                                    Toast.makeText(
                                        context,
                                        localizeUi("Restore failed: {error}", uiLanguage).replace("{error}", localizeUi(error, uiLanguage)),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                    }
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = uiText("Backups include favorites, custom quotes, and app settings."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Export Button
                            ExpressiveButton(
                                onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    exportLauncher.launch("quote_backup.json")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                restingCorner = 12.dp,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                SettingsButtonText(uiText("Export"))
                            }

                            // Import Button
                            ExpressiveOutlinedButton(
                                onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    importLauncher.launch(arrayOf("application/json"))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                restingCorner = 12.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                SettingsButtonText(uiText("Restore"))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Version number — tap 5× to unlock Developer Mode
            val devUnlocked by viewModel.devModeUnlocked.collectAsStateWithLifecycle()
            var versionTapCount by remember(devUnlocked) { mutableIntStateOf(0) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${uiText("Version")} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (devUnlocked) 0.6f else 0.35f),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!devUnlocked) {
                            versionTapCount++
                            if (versionTapCount >= 5) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.unlockDevMode()
                                Toast.makeText(context, localizeUi("Developer mode enabled", uiLanguage), Toast.LENGTH_SHORT).show()
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                )

                if (devUnlocked) {
                    ExpressiveTextButton(
                        onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.openDevScreen()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = uiText("Developer Options"),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Single-line button label that shrinks instead of wrapping or clipping, so long German labels
 * (e.g. "Wiederherstellen") still fit narrow side-by-side buttons.
 */
@Composable
private fun SettingsButtonText(
    text: String,
    style: TextStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
) {
    val maxSize = style.fontSize.takeIf { it.isSp } ?: 14.sp
    Text(
        text = text,
        style = style,
        maxLines = 1,
        textAlign = TextAlign.Center,
        autoSize = TextAutoSize.StepBased(
            minFontSize = minOf(9f, maxSize.value).sp,
            maxFontSize = maxSize,
            stepSize = 0.5.sp
        )
    )
}

/** Reminder time in the system's 12/24-hour format and locale (e.g. "08:00" or "8:00 AM"). */
internal fun formatReminderTime(context: Context, hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
        set(Calendar.MINUTE, minute.coerceIn(0, 59))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}
