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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.ui.QuoteViewModel
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
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDailyQuoteIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ElasticPullDownContainer(
            onTriggerRefresh = viewModel::cycleDailyQuote,
            scrollState = scrollState,
            lowPerformanceMode = lowPerformanceMode,
            modifier = Modifier.fillMaxSize()
        ) { offsetY ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .graphicsLayer { translationY = offsetY }
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
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
                    IconButton(onClick = viewModel::cycleDailyQuote) {
                        Icon(Icons.Default.Refresh, contentDescription = "Show another quote", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                AnimatedContent(
                    targetState = quoteState,
                    transitionSpec = {
                        if (lowPerformanceMode) EnterTransition.None togetherWith ExitTransition.None
                        else fadeIn(tween(250)) togetherWith fadeOut(tween(250))
                    },
                    label = "DailyQuoteTransition",
                    modifier = Modifier.fillMaxWidth()
                ) { quote ->
                    quote?.let {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth().border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    RoundedCornerShape(30.dp)
                                ),
                                shape = RoundedCornerShape(30.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp)) {
                                    Text(
                                        text = "“",
                                        fontFamily = SerifFontFamily,
                                        fontSize = 68.sp,
                                        lineHeight = 48.sp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    )
                                    Text(
                                        text = quote.text,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontFamily = SerifFontFamily,
                                        lineHeight = 35.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
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
                                            Text(
                                                quote.category,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quote.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(4).forEach { tag ->
                                    SuggestionChip(onClick = {}, label = { Text(tag) })
                                }
                            }

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
                            Button(
                                onClick = {
                                    val url = "https://en.wikipedia.org/wiki/${quote.author.replace(" ", "_")}"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(18.dp)
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
}

@Composable
private fun RowScope.DailyActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(52.dp),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
}

@Composable
fun ElasticPullDownContainer(
    onTriggerRefresh: () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    lowPerformanceMode: Boolean = false,
    content: @Composable (offsetY: Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dragOffset = remember { Animatable(0f) }
    var rawDragY by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    var hasTriggeredLimitHaptic by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .pointerInput(scrollState.value) {
                detectVerticalDragGestures(
                    onDragStart = {
                        rawDragY = 0f
                        hasTriggeredLimitHaptic = false
                    },
                    onDragEnd = {
                        scope.launch {
                            if (rawDragY > 600f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTriggerRefresh()
                            }
                            dragOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.65f, // Custom elastic rubber-band damping
                                    stiffness = 300f     // Satisfying snap-back speed
                                )
                            )
                        }
                        rawDragY = 0f
                        hasTriggeredLimitHaptic = false
                    },
                    onDragCancel = {
                        scope.launch {
                            dragOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.65f,
                                    stiffness = 300f
                                )
                            )
                        }
                        rawDragY = 0f
                        hasTriggeredLimitHaptic = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        // Only intercept drag gestures if at the top of the scroll list
                        if (scrollState.value == 0 && (dragAmount > 0 || rawDragY > 0f)) {
                            change.consume()
                            rawDragY = (rawDragY + dragAmount).coerceAtLeast(0f)
                            
                            // Exponential rubber-banding math: offset = maxOffset * (1 - e^-x)
                            val maxOffset = 1200f
                            val resistanceFactor = 1200f
                            val elasticOffset = maxOffset * (1f - kotlin.math.exp(-rawDragY / resistanceFactor))

                            // Low-performance mode: keep the refresh trigger but no rubber-band visual.
                            if (!lowPerformanceMode) {
                                scope.launch {
                                    dragOffset.snapTo(elasticOffset)
                                }
                            }

                            // Trigger tactile feedback ticks
                            if (rawDragY > 600f && !hasTriggeredLimitHaptic) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                hasTriggeredLimitHaptic = true
                            } else if (rawDragY <= 600f && hasTriggeredLimitHaptic) {
                                hasTriggeredLimitHaptic = false
                            }
                        }
                    }
                )
            }
    ) {
        // Visual Pull-To-Refresh instruction pill at the top
        if (dragOffset.value > 5f) {
            val progress = (rawDragY / 600f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .graphicsLayer {
                        alpha = progress
                        scaleX = 0.7f + progress * 0.3f
                        scaleY = 0.7f + progress * 0.3f
                        translationY = dragOffset.value * 0.38f
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .border(
                            0.5.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Pull To Refresh Indicator",
                        tint = if (rawDragY > 600f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer {
                                rotationZ = rawDragY * 0.8f
                            }
                    )
                    Text(
                        text = if (rawDragY > 600f) "Release for wisdom ✦" else "Pull for wisdom",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (rawDragY > 600f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        content(dragOffset.value)
    }
}
