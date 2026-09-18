package app.brokoli5191.quote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brokoli5191.quote.BuildConfig
import app.brokoli5191.quote.data.QuoteOrigin
import app.brokoli5191.quote.data.QuoteSubmissionStatus
import app.brokoli5191.quote.ui.QuoteViewModel
import app.brokoli5191.quote.ui.categoryText
import app.brokoli5191.quote.ui.uiText
import app.brokoli5191.quote.ui.components.ExpressiveButton
import app.brokoli5191.quote.ui.components.ExpressiveOutlinedButton

@Composable
fun DeveloperScreen(viewModel: QuoteViewModel, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current

    val dailyQuote by viewModel.dailyQuote.collectAsStateWithLifecycle()
    val allQuotes by viewModel.allQuotes.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val themeAccent by viewModel.themeAccent.collectAsStateWithLifecycle()
    val lowPerf by viewModel.lowPerformanceMode.collectAsStateWithLifecycle()
    val reminderEnabled by viewModel.dailyReminderEnabled.collectAsStateWithLifecycle()
    val reminderHour by viewModel.dailyReminderHour.collectAsStateWithLifecycle()
    val reminderMinute by viewModel.dailyReminderMinute.collectAsStateWithLifecycle()
    val quoteSourceMode by viewModel.quoteSourceMode.collectAsStateWithLifecycle()
    val communitySyncFinished by viewModel.communitySyncFinished.collectAsStateWithLifecycle()
    val verificationResult by viewModel.verificationResult.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = uiText("Developer Options"),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiText("Debug & testing tools"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.closeDevScreen()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = uiText("Close Developer Options"),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            DebugSection(title = "App Info") {
                DebugRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                DebugRow("Build Type", BuildConfig.BUILD_TYPE)
                DebugRow("DB Quotes", "${allQuotes.size}")
                DebugRow(
                    "Theme",
                    "${uiText(themeMode.lowercase().replaceFirstChar { it.uppercase() })} / ${uiText(themeAccent)}"
                )
                DebugRow("Low Perf Mode", if (lowPerf) uiText("On") else uiText("Off"))
                DebugRow(
                    "Notification",
                    if (reminderEnabled) "${uiText("On")} • ${String.format("%02d", reminderHour)}:${String.format("%02d", reminderMinute)}"
                    else uiText("Off")
                )
            }

            DebugSection(title = "Daily Quote") {
                dailyQuote?.let { quote ->
                    DebugRow("ID", "${quote.id}")
                    DebugRow("Author", quote.author)
                    DebugRow("Category", categoryText(quote.category))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "\"${quote.text.take(160)}${if (quote.text.length > 160) "…" else ""}\"",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } ?: Text(
                    text = uiText("No daily quote loaded"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            DebugSection(title = "Data Health") {
                DebugRow("Bundled", allQuotes.count { it.origin == QuoteOrigin.BUNDLED }.toString())
                DebugRow("Community", allQuotes.count { it.origin == QuoteOrigin.COMMUNITY }.toString())
                DebugRow("Personal", allQuotes.count { it.origin == QuoteOrigin.PERSONAL }.toString())
                DebugRow("Favorites", allQuotes.count { it.isFavorite }.toString())
                DebugRow(
                    "Pending reviews",
                    allQuotes.count { it.submissionStatus == QuoteSubmissionStatus.PENDING }.toString()
                )
                DebugRow("Source mode", uiText(quoteSourceMode.replaceFirstChar { it.uppercase() }))
                DebugRow("Initial sync", if (communitySyncFinished) uiText("Finished") else uiText("Waiting"))
                verificationResult?.let { result ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    DebugRow("Verification", if (result.isValid) uiText("Passed") else uiText("Needs attention"))
                    DebugRow("Empty categories", result.emptyCategories.size.toString())
                }
            }

            DebugSection(title = "Actions") {
                ExpressiveButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.cycleDailyQuote()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    restingCorner = 12.dp
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(uiText("Next Quote"), fontWeight = FontWeight.Bold)
                }

                ExpressiveOutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.triggerTestNotification()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    restingCorner = 12.dp
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(uiText("Send Test Notification"))
                }

                ExpressiveOutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.syncCommunityQuotes()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    restingCorner = 12.dp
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(uiText("Sync Community Quotes"))
                }

                ExpressiveOutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.refreshSubmissionStatuses()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    restingCorner = 12.dp
                ) {
                    Icon(Icons.Default.Rule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(uiText("Refresh Review Statuses"))
                }

                ExpressiveOutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.runVerification()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    restingCorner = 12.dp
                ) {
                    Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(uiText("Verify Quote Database"))
                }
            }
        }
    }
}

@Composable
private fun DebugSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = uiText(title),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            content()
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = uiText(label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
