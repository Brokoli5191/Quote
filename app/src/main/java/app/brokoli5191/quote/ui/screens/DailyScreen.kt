package app.brokoli5191.quote.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.ui.QuoteViewModel
import app.brokoli5191.quote.ui.components.ExpressiveButton
import app.brokoli5191.quote.ui.components.ExpressiveTonalButton
import app.brokoli5191.quote.ui.theme.SerifFontFamily
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

import androidx.compose.foundation.layout.ExperimentalLayoutApi

@OptIn(ExperimentalAnimationApi::class, ExperimentalLayoutApi::class)
@Composable
fun DailyScreen(viewModel: QuoteViewModel) {
    val quoteState by viewModel.dailyQuote.collectAsState()
    val lowPerformanceMode by viewModel.lowPerformanceMode.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var pageVisible by remember { mutableStateOf(false) }
    val pageAlpha by animateFloatAsState(
        targetValue = if (pageVisible) 1f else 0f,
        animationSpec = if (lowPerformanceMode) snap() else tween(600),
        label = "DailyPageFadeIn"
    )

    LaunchedEffect(Unit) { pageVisible = true }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDailyQuoteIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .graphicsLayer { alpha = pageAlpha }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 112.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp)
            ) {
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "A thought worth keeping",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

                AnimatedContent(
                    targetState = quoteState?.id,
                    transitionSpec = {
                        if (lowPerformanceMode) EnterTransition.None togetherWith ExitTransition.None
                        else fadeIn(tween(250)) togetherWith fadeOut(tween(250))
                    },
                    label = "DailyQuoteTransition",
                    modifier = Modifier.fillMaxWidth()
                ) { quoteId ->
                    quoteState?.takeIf { it.id == quoteId }?.let { quote ->
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth().border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    RoundedCornerShape(30.dp)
                                ),
                                shape = RoundedCornerShape(30.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
                                )
                            ) {
                                Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                                    Box(Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "“",
                                            fontFamily = SerifFontFamily,
                                            fontSize = 104.sp,
                                            lineHeight = 104.sp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            modifier = Modifier.align(Alignment.TopStart)
                                        )
                                        Text(
                                            text = "”",
                                            fontFamily = SerifFontFamily,
                                            fontSize = 104.sp,
                                            lineHeight = 104.sp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        )
                                        Text(
                                            text = quote.text,
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontFamily = SerifFontFamily,
                                            lineHeight = 36.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 30.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(24.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(42.dp).background(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                CircleShape
                                            ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                quote.author.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                quote.author,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                quote.tags.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .take(4)
                                    .chunked(2)
                                    .forEach { tags ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            tags.forEach { tag ->
                                                SuggestionChip(
                                                    onClick = {},
                                                    label = { Text(tag, maxLines = 1) }
                                                )
                                            }
                                        }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DailyActionButton(
                                    icon = if (quote.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    label = if (quote.isFavorite) "Saved" else "Save",
                                    onClick = { viewModel.toggleFavorite(quote) }
                                )
                                DailyActionButton(Icons.Default.ContentCopy, "Copy") {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("quote", "\"${quote.text}\" — ${quote.author}"))
                                }
                                DailyActionButton(Icons.Default.Share, "Share") {
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "\"${quote.text}\" — ${quote.author}")
                                    }, null))
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            ExpressiveButton(
                                onClick = {
                                    val url = "https://en.wikipedia.org/wiki/${quote.author.replace(" ", "_")}"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                restingCorner = 18.dp
                            ) {
                                Icon(Icons.Default.Language, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Explore this author")
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun RowScope.DailyActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    ExpressiveTonalButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(52.dp),
        restingCorner = 18.dp,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
}
