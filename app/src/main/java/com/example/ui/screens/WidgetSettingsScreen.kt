package com.example.ui.screens

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
import com.example.ui.AuraViewModel
import com.example.widget.AuraWidgetProvider
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

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
                    .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 0.dp),
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
        }
    }
}
