package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Language
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
import com.example.data.QuoteEntity
import com.example.ui.AuraViewModel
import com.example.ui.theme.SerifFontFamily
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DailyScreen(viewModel: AuraViewModel) {
    val quoteState by viewModel.dailyQuote.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ElasticPullDownContainer(
            onTriggerRefresh = {
                viewModel.cycleDailyQuote(context)
            },
            scrollState = scrollState,
            modifier = Modifier.fillMaxSize()
        ) { offsetY ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .graphicsLayer {
                        translationY = offsetY
                    }
                    .padding(bottom = 16.dp)
            ) {
            // Unified structural Header layout (matches Your Collection layout style)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 0.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daily Quote",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.cycleDailyQuote(context)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Cycle Daily Quote",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Big Centerpiece Quote with Fade In Reveal
            quoteState?.let { quote ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = spring(dampingRatio = 0.8f)) + slideInVertically(initialOffsetY = { 40 }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 30.dp, end = 30.dp, top = 2.dp, bottom = 12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        // Giant Opening Quote Mark in a compact-height Box so it does not push the content down
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                        ) {
                            Text(
                                text = "“",
                                fontFamily = SerifFontFamily,
                                fontSize = 110.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                modifier = Modifier.offset(y = (-45).dp)
                            )
                        }

                        Text(
                            text = quote.text,
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            lineHeight = 38.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "— ${quote.author.uppercase()}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 2.sp
                        )
                    }
                }

                // Daily Insight Card
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = spring(stiffness = 100f)) + expandVertically(),
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Author Image (Using custom abstract visual representation or Coil Placeholder)
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = quote.author.take(1).uppercase(),
                                        fontSize = 36.sp,
                                        fontFamily = SerifFontFamily,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "✦",
                                            color = MaterialTheme.colorScheme.tertiary,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "DAILY INSIGHT",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "About the Sage",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = if (quote.aboutAuthor.isBlank())
                                            "A wisdom practitioner with deep teachings of truth and insight."
                                            else quote.aboutAuthor,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Tags row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val splitTags = quote.tags.split(",").filter { it.isNotBlank() }
                                if (splitTags.isNotEmpty()) {
                                    splitTags.forEach { tag ->
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(100.dp)
                                                )
                                                .border(
                                                    0.5.dp,
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(100.dp)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "#$tag",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    // Fallback tags
                                    listOf("Wisdom", "Insight").forEach { tag ->
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(100.dp)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "#$tag",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Interactive Row
                            var isLikedAnim by remember { mutableStateOf(false) }
                            val heartScale by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (isLikedAnim) 1.35f else 1.0f,
                                animationSpec = spring(stiffness = 500f, dampingRatio = 0.5f),
                                finishedListener = { isLikedAnim = false }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isLikedAnim = true
                                        viewModel.toggleFavorite(quote)
                                    },
                                    modifier = Modifier
                                        .size(56.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (quote.isFavorite)
                                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        contentColor = if (quote.isFavorite)
                                            MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = if (quote.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        modifier = Modifier
                                            .size(24.dp)
                                    )
                                }

                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val url = "https://en.wikipedia.org/wiki/${quote.author.replace(" ", "_")}"
                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    shape = RoundedCornerShape(28.dp),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = "Learn More",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Learn More",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }

        // Floating share FAB in bottom corner (higher visual hierarchy)
        quoteState?.let { quote ->
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "\"${quote.text}\" — ${quote.author} (via quote)")
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 16.dp)
                    .size(56.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share Quote",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun ElasticPullDownContainer(
    onTriggerRefresh: () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
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
                            
                            scope.launch {
                                dragOffset.snapTo(elasticOffset)
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
