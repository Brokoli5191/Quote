package app.brokoli5191.quote.ui.screens

import androidx.compose.animation.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.text.TextAutoSize
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import app.brokoli5191.quote.data.alternateLanguage
import app.brokoli5191.quote.data.alternateText
import app.brokoli5191.quote.data.hasUnknownAuthor
import app.brokoli5191.quote.data.DailyHistoryEntry
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteSourceMode
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.ui.QuoteViewModel
import app.brokoli5191.quote.ui.SyncUiState
import app.brokoli5191.quote.ui.LocalAppLanguage
import app.brokoli5191.quote.ui.uiText
import app.brokoli5191.quote.ui.components.ExpressiveButton
import app.brokoli5191.quote.ui.components.ExpressiveIconButton
import app.brokoli5191.quote.ui.components.ExpressiveTonalButton
import app.brokoli5191.quote.ui.components.AuthorAvatar
import app.brokoli5191.quote.ui.components.AuthorSheet
import app.brokoli5191.quote.ui.components.AnimatedTranslation
import app.brokoli5191.quote.ui.components.TranslationToggle
import app.brokoli5191.quote.ui.components.FloatingChromeBackdrop
import app.brokoli5191.quote.ui.components.SingleLineFitRow
import app.brokoli5191.quote.ui.components.bottomBarContentPadding
import app.brokoli5191.quote.data.tagLabel
import app.brokoli5191.quote.utils.QuoteSharing
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import app.brokoli5191.quote.ui.theme.SerifFontFamily
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DailyScreen(viewModel: QuoteViewModel) {
    val quoteState by viewModel.dailyQuote.collectAsStateWithLifecycle()
    val lowPerformanceMode by viewModel.lowPerformanceMode.collectAsStateWithLifecycle()
    val blurNavigationSurfaces by viewModel.blurNavigationSurfaces.collectAsStateWithLifecycle()
    val sourceMode by viewModel.quoteSourceMode.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val history by viewModel.dailyHistory.collectAsStateWithLifecycle()
    val authorImagesEnabled by viewModel.authorImagesEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val uiLanguage = LocalAppLanguage.current
    val scrollState = rememberScrollState()
    val dailyHazeState = rememberHazeState(blurEnabled = blurNavigationSurfaces && !lowPerformanceMode)
    val feedbackScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var cycling by remember { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val newQuoteMessage = uiText("New quote for today")
    val undoLabel = uiText("Undo")
    val noOtherQuoteMessage = uiText("No other quote available")
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
                .hazeSource(dailyHazeState)
                .verticalScroll(scrollState)
                .padding(bottom = bottomBarContentPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = uiText("Today"),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (history.isNotEmpty()) {
                        ExpressiveIconButton(onClick = { showHistory = true }) {
                            Icon(Icons.Default.History, contentDescription = uiText("Show earlier quotes"))
                        }
                    }
                }
            }

                if (quoteState == null) {
                    DailyQuoteEmptyState(
                        communityOnly = sourceMode == QuoteSourceMode.COMMUNITY,
                        syncState = syncState,
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
                        val authorInfoFlow = remember(quote.author, unknownAuthor) {
                            if (unknownAuthor) null else viewModel.authorInfo(quote.author)
                        }
                        val authorInfo = authorInfoFlow?.let { it.collectAsStateWithLifecycle().value }
                        val authorImageUrl = authorInfo?.imageUrl?.takeIf { authorImagesEnabled }
                        val authorDescription = authorInfo?.description?.takeIf { it.isNotBlank() }
                        var showAuthorSheet by rememberSaveable(quote.id) { mutableStateOf(false) }
                        val exploreAuthorLabel = uiText("Explore this author")

                        val alternateText = remember(quote) { quote.alternateText() }
                        val alternateLanguage = remember(quote) { quote.alternateLanguage() }
                        // Resets when another quote is shown or the app language changes the display text.
                        var showTranslation by rememberSaveable(quote.id, quote.text) { mutableStateOf(false) }
                        val displayedText =
                            if (showTranslation && alternateText != null) alternateText else quote.text
                        // Picks the quotation marks of copied and shared text.
                        val displayedLanguage = QuoteSharing.textLanguage(quote, showTranslation)
                        var shareMenuOpen by remember(quote.id) { mutableStateOf(false) }
                        var imageShareRunning by remember { mutableStateOf(false) }
                        // Shown in both languages (also for anonymous quotes); the row keeps as many as fit.
                        // Own quotes keep their tags as written; the label is only for the vocabulary.
                        val tags = remember(quote.tags, quote.isUserAdded, uiLanguage) {
                            quote.tags.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .map { raw -> QuoteTag(raw, if (quote.isUserAdded) raw else tagLabel(raw, uiLanguage)) }
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
                                            modifier = Modifier.align(Alignment.TopStart).clearAndSetSemantics {}
                                        )
                                        Text(
                                            text = "”",
                                            fontFamily = SerifFontFamily,
                                            fontSize = 104.sp,
                                            lineHeight = 104.sp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            // The glyph's ink sits high in its line box; shift it down so it
                                            // closes the last line instead of overlapping the lines above it.
                                            modifier = Modifier.align(Alignment.BottomEnd).offset(y = 50.dp).clearAndSetSemantics {}
                                        )
                                        AnimatedTranslation(showTranslation, lowPerformanceMode) { showing ->
                                            DailyQuoteText(if (showing && alternateText != null) alternateText else quote.text)
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
                                    // A known author's row opens the same sheet as "Explore this author".
                                    Row(
                                        modifier = if (unknownAuthor) {
                                            Modifier
                                        } else {
                                            Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable(onClickLabel = exploreAuthorLabel, role = Role.Button) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    showAuthorSheet = true
                                                }
                                        },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
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
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            QuoteTags(tags = tags, onTagClick = { tag ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.openLibrarySearch(tag.raw)
                            })

                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DailyActionButton(
                                    icon = if (quote.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    label = if (quote.isFavorite) uiText("Saved") else uiText("Save"),
                                    iconScale = favoriteScale,
                                    // The visible label already changes; TalkBack gets a fixed name plus the state.
                                    description = uiText("Save"),
                                    stateDescription = uiText(if (quote.isFavorite) "Saved" else "Not saved"),
                                    onClick = {
                                        favoritePulse = true
                                        viewModel.toggleFavorite(quote)
                                    }
                                )
                                DailyActionButton(
                                    if (copyConfirmed) Icons.Default.Check else Icons.Default.ContentCopy,
                                    uiText(if (copyConfirmed) "Copied" else "Copy")
                                ) {
                                    QuoteSharing.copyText(context, displayedText, quote.author, displayedLanguage)
                                    copyConfirmed = true
                                    feedbackScope.launch {
                                        delay(1200)
                                        copyConfirmed = false
                                    }
                                }
                                DailyActionButton(
                                    Icons.Default.Share,
                                    uiText("Share"),
                                    menu = {
                                        DropdownMenu(
                                            expanded = shareMenuOpen,
                                            onDismissRequest = { shareMenuOpen = false },
                                            shape = RoundedCornerShape(18.dp),
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(uiText("Share as text")) },
                                                leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                                                onClick = {
                                                    shareMenuOpen = false
                                                    QuoteSharing.shareText(context, displayedText, quote.author, uiLanguage, displayedLanguage)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(uiText("Share as image")) },
                                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                                enabled = !imageShareRunning,
                                                onClick = {
                                                    shareMenuOpen = false
                                                    imageShareRunning = true
                                                    feedbackScope.launch {
                                                        try {
                                                            QuoteSharing.shareImage(context, displayedText, quote.author, uiLanguage, displayedLanguage)
                                                        } finally {
                                                            imageShareRunning = false
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                ) {
                                    shareMenuOpen = true
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            ExpressiveTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val shownId = quote.id
                                    cycling = true
                                    viewModel.cycleDailyQuote()
                                    feedbackScope.launch {
                                        try {
                                            // The pick is stored asynchronously; no message when nothing changed.
                                            val changed = withTimeoutOrNull(2_000) {
                                                viewModel.dailyQuote.first { it != null && it.id != shownId }
                                            }
                                            if (changed != null) {
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                val undoable = viewModel.canUndoCycle.value
                                                cycling = false
                                                val result = snackbarHostState.showSnackbar(
                                                    message = newQuoteMessage,
                                                    actionLabel = if (undoable) undoLabel else null,
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) viewModel.undoCycleDailyQuote()
                                            } else {
                                                cycling = false
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                snackbarHostState.showSnackbar(
                                                    message = noOtherQuoteMessage,
                                                    duration = SnackbarDuration.Short
                                                )
                                            }
                                        } finally {
                                            cycling = false
                                        }
                                    }
                                },
                                enabled = !cycling,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                restingCorner = 18.dp
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(uiText("Another quote"), maxLines = 2, textAlign = TextAlign.Center)
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
                                Text(exploreAuthorLabel, maxLines = 2, textAlign = TextAlign.Center)
                              }
                            }
                            if (showAuthorSheet && !unknownAuthor) {
                                // Re-read when the quote list finishes loading behind an already open sheet.
                                val allQuotes by viewModel.allQuotes.collectAsStateWithLifecycle()
                                val authorQuoteCount = remember(quote.author, allQuotes) { viewModel.authorQuoteCount(quote.author) }
                                AuthorSheet(
                                    name = authorName,
                                    info = authorInfo,
                                    imageUrl = authorImageUrl,
                                    language = uiLanguage,
                                    onDismiss = { showAuthorSheet = false },
                                    quoteCount = authorQuoteCount,
                                    onShowMoreQuotes = { viewModel.openLibrarySearch(quote.author) }
                                )
                            }
                        }
                    }
                }
            }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = bottomBarContentPadding())
        )

        if (showHistory) {
            DailyHistorySheet(
                history = history,
                language = uiLanguage,
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onDismiss = { showHistory = false }
            )
        }

        // Content scrolling under the status bar fades out instead of running into its icons.
        FloatingChromeBackdrop(
            hazeState = dailyHazeState,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars.add(WindowInsets(top = 16.dp)))
        )
        }
}

@Composable
private fun DailyQuoteEmptyState(
    communityOnly: Boolean,
    syncState: SyncUiState,
    onRetry: () -> Unit
) {
    // The daily quote is read right after start; give it a moment before calling the list empty.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(600)
        settled = true
    }
    val failed = syncState is SyncUiState.Failed
    val loading = syncState is SyncUiState.Running || (!settled && !failed)
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
                when {
                    failed -> uiText("Couldn't reach the community service")
                    communityOnly -> uiText("No community quotes yet")
                    else -> uiText("No quotes available")
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                when {
                    failed -> uiText("Check your connection and try again.")
                    communityOnly -> uiText("Approved community quotes will appear here after the next sync.")
                    else -> uiText("Try syncing again in a moment.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            ExpressiveTonalButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(uiText("Try again"))
            }
        }
    }
}

/** A tag as stored ([raw], what the Library search matches) and as shown ([label]). */
private data class QuoteTag(val raw: String, val label: String)

// A single line of chips: the tags that fit completely, in order, never wrapped or cut off.
@Composable
private fun QuoteTags(tags: List<QuoteTag>, onTagClick: (QuoteTag) -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        SingleLineFitRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
            tags.forEach { tag ->
                SuggestionChip(
                    onClick = { onTagClick(tag) },
                    label = { Text(tag.label, maxLines = 1, softWrap = false) }
                )
            }
        }
    }
}

@Composable
private fun RowScope.DailyActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconScale: Float = 1f,
    // Replaces the visible label for screen readers when the label itself changes with the state.
    description: String? = null,
    stateDescription: String? = null,
    // Drawn next to the button so a DropdownMenu opens anchored to it.
    menu: @Composable () -> Unit = {},
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Box(Modifier.weight(1f)) {
        ExpressiveTonalButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics {
                if (description != null) contentDescription = description
                if (stateDescription != null) this.stateDescription = stateDescription
            },
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
        menu()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyHistorySheet(
    history: List<DailyHistoryEntry>,
    language: String,
    onToggleFavorite: (QuoteEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shareScope = rememberCoroutineScope()
    var copiedId by remember { mutableStateOf<Int?>(null) }
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                uiText("Earlier quotes"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            history.forEach { entry ->
                val quote = entry.quote
                val textLanguage = QuoteSharing.textLanguage(quote, false)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        historyDateLabel(entry.date, language),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        quote.text,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = SerifFontFamily,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!quote.hasUnknownAuthor) {
                        Text(
                            quote.author,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ExpressiveIconButton(
                            onClick = { onToggleFavorite(quote) },
                            stateDescription = uiText(if (quote.isFavorite) "Saved" else "Not saved")
                        ) {
                            Icon(
                                if (quote.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = uiText("Save")
                            )
                        }
                        ExpressiveIconButton(onClick = {
                            QuoteSharing.copyText(context, quote.text, quote.author, textLanguage)
                            copiedId = quote.id
                            shareScope.launch {
                                delay(1200)
                                if (copiedId == quote.id) copiedId = null
                            }
                        }) {
                            val copied = copiedId == quote.id
                            Icon(
                                if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = uiText(if (copied) "Copied" else "Copy")
                            )
                        }
                        ExpressiveIconButton(onClick = {
                            QuoteSharing.shareText(context, quote.text, quote.author, language, textLanguage)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = uiText("Share"))
                        }
                    }
                }
            }
        }
    }
}

/** "yyyy-MM-dd" as a long date in the app language; the key itself if it cannot be parsed. */
private fun historyDateLabel(date: String, language: String): String {
    val parsed = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) }.getOrNull() ?: return date
    return DateFormat.getDateInstance(DateFormat.LONG, Locale.forLanguageTag(language)).format(parsed)
}
