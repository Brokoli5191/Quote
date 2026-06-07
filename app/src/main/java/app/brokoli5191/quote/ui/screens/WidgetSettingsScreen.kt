package app.brokoli5191.quote.ui.screens

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brokoli5191.quote.ui.AuraViewModel
import app.brokoli5191.quote.widget.AuraWidgetProvider
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Add

@Composable
fun WidgetSettingsScreen(viewModel: AuraViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp),
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
                    text = "Settings",
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
                    text = "App Theme",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val activeThemeMode by viewModel.themeMode.collectAsState()
                    val modes = listOf("AMOLED", "DARK", "LIGHT", "DYNAMIC")
                    modes.forEach { mode ->
                        val isSelected = activeThemeMode == mode
                        val label = when (mode) {
                            "AMOLED" -> "Amoled"
                            "DYNAMIC" -> "System"
                            else -> mode.lowercase().replaceFirstChar { it.uppercase() }
                        }
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.setThemeMode(context, mode)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                contentColor = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
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
                    text = "Accent Palette",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                )

                val activeThemeMode by viewModel.themeMode.collectAsState()
                val isDynamic = activeThemeMode == "DYNAMIC"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeThemeAccent by viewModel.themeAccent.collectAsState()
                    val colors = listOf("Violet", "Amber", "Green", "Blue", "Rose")
                    colors.forEach { color ->
                        val isSelected = !isDynamic && activeThemeAccent == color
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
                                .height(46.dp)
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
                                .clickable(enabled = !isDynamic) { 
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setThemeAccent(context, color) 
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
                        text = "Accent colors are automatically determined by your device's live system/wallpaper palette.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
                    text = "Daily Reflection Reminders",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                )

                val reminderEnabled by viewModel.dailyReminderEnabled.collectAsState()
                val reminderHour by viewModel.dailyReminderHour.collectAsState()
                val reminderMinute by viewModel.dailyReminderMinute.collectAsState()

                // Permission launcher
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        if (isGranted) {
                            viewModel.setDailyReminderEnabled(context, true)
                            Toast.makeText(context, "Daily Reminder scheduled! ✦", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Notifications permission is required to receive daily quotes.", Toast.LENGTH_LONG).show()
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
                                        contentDescription = "Daily Reminders",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(modifier = Modifier.padding(end = 8.dp)) {
                                    Text(
                                        text = "Inspirational Mornings",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Start your day with a focused quote of the day notification.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            Switch(
                                checked = reminderEnabled,
                                onCheckedChange = { isChecked ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isChecked) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            val hasPermission = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) == PackageManager.PERMISSION_GRANTED

                                            if (hasPermission) {
                                                viewModel.setDailyReminderEnabled(context, true)
                                                Toast.makeText(context, "Daily Reminder scheduled! ✦", Toast.LENGTH_SHORT).show()
                                            } else {
                                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        } else {
                                            viewModel.setDailyReminderEnabled(context, true)
                                            Toast.makeText(context, "Daily Reminder scheduled! ✦", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        viewModel.setDailyReminderEnabled(context, false)
                                        Toast.makeText(context, "Daily Reminder disabled", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        AnimatedVisibility(
                            visible = reminderEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
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
                                    text = "Choose Notification Time",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Hour Column
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(60.dp)
                                    ) {
                                        Text(
                                            text = "▲",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clickable {
                                                    val h = (reminderHour + 1) % 24
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.updateDailyReminderTime(context, h, reminderMinute)
                                                }
                                                .padding(6.dp)
                                        )

                                        Text(
                                            text = String.format("%02d", reminderHour),
                                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Text(
                                            text = "▼",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clickable {
                                                    var h = reminderHour - 1
                                                    if (h < 0) h = 23
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.updateDailyReminderTime(context, h, reminderMinute)
                                                }
                                                .padding(6.dp)
                                        )
                                    }

                                    Text(
                                        text = ":",
                                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )

                                    // Minute Column
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(60.dp)
                                    ) {
                                        Text(
                                            text = "▲",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clickable {
                                                    val m = (reminderMinute + 5) % 60
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.updateDailyReminderTime(context, reminderHour, m)
                                                }
                                                .padding(6.dp)
                                        )

                                        Text(
                                            text = String.format("%02d", reminderMinute),
                                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Text(
                                            text = "▼",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clickable {
                                                    var m = reminderMinute - 5
                                                    if (m < 0) m = 55
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.updateDailyReminderTime(context, reminderHour, m)
                                                }
                                                .padding(6.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    // AM/PM Display Badge
                                    val isAm = reminderHour < 12
                                    val amPmLabel = if (isAm) "AM" else "PM"

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = amPmLabel,
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Text(
                                    text = "Scheduled daily at ${if (reminderHour == 0) 12 else if (reminderHour > 12) reminderHour - 12 else reminderHour}:${String.format("%02d", reminderMinute)} ${if (reminderHour < 12) "AM" else "PM"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
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
                    text = "Performance",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                )

                val isLowPerf by viewModel.lowPerformanceMode.collectAsState()

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
                                text = "Low Performance Mode",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Simplify screen transitions and empty matches animations to enhance responsiveness.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }

                        Switch(
                            checked = isLowPerf,
                            onCheckedChange = { isChecked ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.setLowPerformanceMode(context, isChecked)
                                Toast.makeText(
                                    context,
                                    if (isChecked) "Low Performance Mode enabled" else "Standard performance mode active",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Section 5: Backup & Restore of User Custom & Saved Quotes
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Backup & Restore",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                )

                val exportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json"),
                    onResult = { uri ->
                        if (uri != null) {
                            viewModel.exportBackup(
                                context = context,
                                uri = uri,
                                onSuccess = {
                                    Toast.makeText(context, "Backup exported successfully! ✦", Toast.LENGTH_LONG).show()
                                },
                                onError = { error ->
                                    Toast.makeText(context, "Export failed: $error", Toast.LENGTH_LONG).show()
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
                                context = context,
                                uri = uri,
                                onSuccess = { customCount, favCount ->
                                    Toast.makeText(
                                        context,
                                        "Restore complete! Imported $customCount custom quotes & updated $favCount favorites.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    viewModel.loadDailyQuote()
                                    viewModel.runVerification()
                                },
                                onError = { error ->
                                    Toast.makeText(context, "Restore failed: $error", Toast.LENGTH_LONG).show()
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
                            text = "Backup custom quotes and saved reflections to a JSON file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Export Button
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    exportLauncher.launch("aura_backup.json")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Export Backup",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Export",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            // Import Button
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    importLauncher.launch(arrayOf("application/json"))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Import Backup",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Restore",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
