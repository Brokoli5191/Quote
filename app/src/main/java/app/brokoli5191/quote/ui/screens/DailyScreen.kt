package app.brokoli5191.quote.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.brokoli5191.quote.data.AuthorInfo
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.alternateLanguage
import app.brokoli5191.quote.data.alternateText
import app.brokoli5191.quote.data.hasUnknownAuthor
import app.brokoli5191.quote.data.QuoteSourceMode
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.ui.QuoteViewModel
import app.brokoli5191.quote.ui.LocalAppLanguage
import app.brokoli5191.quote.ui.formattedQuote
import app.brokoli5191.quote.ui.uiText
import app.brokoli5191.quote.ui.components.ExpressiveButton
import app.brokoli5191.quote.ui.components.ExpressiveTonalButton
import app.brokoli5191.quote.ui.components.AuthorAvatar
import app.brokoli5191.quote.ui.components.TranslationToggle
import app.brokoli5191.quote.ui.theme.SerifFontFamily
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DailyScreen(viewModel: QuoteViewModel) {
    val quoteState by viewModel.dailyQuote.collectAsStateWithLifecycle()
    val lowPerformanceMode by viewModel.lowPerformanceMode.collectAsStateWithLifecycle()
    val sourceMode by viewModel.quoteSourceMode.collectAsStateWithLifecycle()
    val communitySyncFinished by viewModel.communitySyncFinished.collectAsStateWithLifecycle()
    val authorImagesEnabled by viewModel.authorImagesEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val uiLanguage = LocalAppLanguage.current
    val scrollState = rememberScrollState()
    val feedbackScope = rememberCoroutineScope()
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

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    text = uiText("Today"),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = uiText("A thought worth keeping"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

                if (quoteState == null) {
                    DailyQuoteEmptyState(
                        communityOnly = sourceMode == QuoteSourceMode.COMMUNITY,
                        loading = !communitySyncFinished,
                        onRetry = { viewModel.syncCommunityQuotes() }
                    )
                } else AnimatedContent(
                    targetState = quoteState?.id,
                    transitionSpec = {
                        EnterTransition.None togetherWith ExitTransition.None
                    },
                    label = "DailyQuoteTransition",
                    modifier = Modifier.fillMaxWidth()
                ) { quoteId ->
                    quoteState?.takeIf { it.id == quoteId }?.let { quote ->
                        var copyConfirmed by remember(quote.id) { mutableStateOf(false) }
                        var favoritePulse by remember(quote.id) { mutableStateOf(false) }
                        val favoriteScale by animateFloatAsState(
                            targetValue = if (!lowPerformanceMode && favoritePulse) 1.3f else 1f,
                            animationSpec = spring(stiffness = 500f, dampingRatio = 0.52f),
                            finishedListener = { favoritePulse = false },
                            label = "DailyFavoritePulse"
                        )
                        val unknownAuthor = quote.hasUnknownAuthor
                        val authorName = if (unknownAuthor) uiText("Unknown") else quote.author
                        // Copy/share keep a blank author blank, like before.
                        val shareAuthor = if (quote.author.isBlank()) "" else authorName
                        val authorInfoFlow = remember(quote.author, unknownAuthor) {
                            if (unknownAuthor) null else viewModel.authorInfo(quote.author)
                        }
                        val authorInfo = authorInfoFlow?.let { it.collectAsStateWithLifecycle().value }
                        val authorImageUrl = authorInfo?.imageUrl?.takeIf { authorImagesEnabled }
                        val authorDescription = authorInfo?.description?.takeIf { it.isNotBlank() }
                        var showAuthorSheet by rememberSaveable(quote.id) { mutableStateOf(false) }

                        val alternateText = remember(quote) { quote.alternateText() }
                        val alternateLanguage = remember(quote) { quote.alternateLanguage() }
                        // Resets when another quote is shown or the app language changes the display text.
                        var showTranslation by remember(quote.id, quote.text) { mutableStateOf(false) }
                        val displayedText =
                            if (showTranslation && alternateText != null) alternateText else quote.text
                        val tags = remember(quote.tags) {
                            quote.tags.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .take(4)
                        }
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
                                        if (lowPerformanceMode) {
                                            DailyQuoteText(displayedText)
                                        } else {
                                            AnimatedContent(
                                                targetState = displayedText,
                                                transitionSpec = {
                                                    fadeIn(tween(220)) togetherWith fadeOut(tween(150)) using
                                                        SizeTransform(clip = false)
                                                },
                                                label = "DailyTranslationTransition"
                                            ) { text ->
                                                DailyQuoteText(text)
                                            }
                                        }
                                    }
                                    if (alternateText != null && alternateLanguage != null) {
                                        val originalLanguage =
                                            if (alternateLanguage == QuoteLanguage.GERMAN) QuoteLanguage.ENGLISH
                                            else QuoteLanguage.GERMAN
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TranslationToggle(
                                                showingTranslation = showTranslation,
                                                targetLanguage = if (showTranslation) originalLanguage else alternateLanguage,
                                                onToggle = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    showTranslation = !showTranslation
                                                }
                                            )
                                        }
                                        Spacer(Modifier.height(12.dp))
                                    } else {
                                        Spacer(Modifier.height(24.dp))
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AuthorAvatar(
                                            name = authorName,
                                            imageUrl = authorImageUrl,
                                            unknown = unknownAuthor
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                authorName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (authorDescription != null) {
                                                Text(
                                                    authorDescription,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            if (!quote.isAnonymous && uiLanguage == QuoteLanguage.ENGLISH) {
                                QuoteTags(tags = tags)
                            }

                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DailyActionButton(
                                    icon = if (quote.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    label = if (quote.isFavorite) uiText("Saved") else uiText("Save"),
                                    iconScale = favoriteScale,
                                    onClick = {
                                        favoritePulse = true
                                        viewModel.toggleFavorite(quote)
                                    }
                                )
                                DailyActionButton(
                                    if (copyConfirmed) Icons.Default.Check else Icons.Default.ContentCopy,
                                    uiText(if (copyConfirmed) "Copied" else "Copy")
                                ) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("quote", formattedQuote(displayedText, shareAuthor)))
                                    copyConfirmed = true
                                    feedbackScope.launch {
                                        delay(1200)
                                        copyConfirmed = false
                                    }
                                }
                                DailyActionButton(Icons.Default.Share, uiText("Share")) {
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, formattedQuote(displayedText, shareAuthor))
                                    }, null))
                                }
                            }
                            if (!unknownAuthor) {
                              Spacer(Modifier.height(10.dp))
                              ExpressiveButton(
                                onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showAuthorSheet = true
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                restingCorner = 18.dp
                            ) {
                                Icon(Icons.Default.Language, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(uiText("Explore this author"), maxLines = 2, textAlign = TextAlign.Center)
                              }
                            }
                            if (showAuthorSheet && !unknownAuthor) {
                                AuthorSheet(
                                    name = authorName,
                                    info = authorInfo,
                                    imageUrl = authorImageUrl,
                                    language = uiLanguage,
                                    onDismiss = { showAuthorSheet = false }
                                )
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun DailyQuoteEmptyState(
    communityOnly: Boolean,
    loading: Boolean,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            Text(uiText("Syncing quotes..."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                if (communityOnly) uiText("No community quotes yet") else uiText("No quotes available"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (communityOnly) uiText("Approved community quotes will appear here after the next sync.")
                else uiText("Try syncing again in a moment."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ExpressiveTonalButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(uiText("Try again"))
            }
        }
    }
}

@Composable
private fun QuoteTags(tags: List<String>) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = {
                tags.forEach { tag ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(tag, maxLines = 1) }
                    )
                }
            }
        ) { measurables, constraints ->
            val spacing = 8.dp.roundToPx()
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
            val positions = ArrayList<Pair<Int, Int>>(placeables.size)
            var x = 0
            var y = 0
            var rowHeight = 0

            placeables.forEach { placeable ->
                if (x > 0 && x + placeable.width > constraints.maxWidth) {
                    x = 0
                    y += rowHeight + spacing
                    rowHeight = 0
                }
                positions += x to y
                x += placeable.width + spacing
                rowHeight = maxOf(rowHeight, placeable.height)
            }

            val height = (y + rowHeight).coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(constraints.maxWidth, height) {
                placeables.forEachIndexed { index, placeable ->
                    val (placeX, placeY) = positions[index]
                    placeable.placeRelative(placeX, placeY)
                }
            }
        }
    }
}

@Composable
private fun RowScope.DailyActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconScale: Float = 1f,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    ExpressiveTonalButton(
        onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = Modifier.weight(1f).heightIn(min = 64.dp),
        restingCorner = 18.dp,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
    ) {
        // Icon on top gives the label the full button width; German labels ("Gespeichert")
        // shrink slightly instead of being cut off on narrow screens or with large fonts.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp).graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
            )
            val labelStyle = MaterialTheme.typography.labelLarge
            val labelSize = labelStyle.fontSize.takeIf { it.isSp } ?: 14.sp
            Text(
                label,
                style = labelStyle,
                maxLines = 1,
                textAlign = TextAlign.Center,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = minOf(10f, labelSize.value).sp,
                    maxFontSize = labelSize,
                    stepSize = 0.5.sp
                )
            )
        }
    }
}

@Composable
private fun DailyQuoteText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontFamily = SerifFontFamily,
        lineHeight = 36.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 30.dp)
    )
}

private fun wikipediaSearchUrl(author: String, language: String): String {
    val wikipediaLanguage = if (language == QuoteLanguage.GERMAN) "de" else "en"
    return "https://$wikipediaLanguage.wikipedia.org/w/index.php?search=" +
        java.net.URLEncoder.encode(author, "UTF-8")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthorSheet(
    name: String,
    info: AuthorInfo?,
    imageUrl: String?,
    language: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val description = info?.description?.takeIf { it.isNotBlank() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AuthorAvatar(name = name, imageUrl = imageUrl, unknown = false, size = 112.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (description != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(24.dp))
            ExpressiveButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val url = info?.wikiUrl ?: wikipediaSearchUrl(name, language)
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                    }
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                restingCorner = 18.dp
            ) {
                Icon(Icons.Default.Language, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(uiText("Read on Wikipedia"), maxLines = 2, textAlign = TextAlign.Center)
            }
        }
    }
}
