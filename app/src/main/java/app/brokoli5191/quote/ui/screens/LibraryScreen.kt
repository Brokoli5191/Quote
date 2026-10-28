package app.brokoli5191.quote.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.data.QuoteSourceMode
import app.brokoli5191.quote.data.alternateLanguage
import app.brokoli5191.quote.data.alternateText
import app.brokoli5191.quote.data.hasUnknownAuthor
import app.brokoli5191.quote.ui.QuoteViewModel
import app.brokoli5191.quote.ui.LocalAppLanguage
import app.brokoli5191.quote.ui.categoryText
import app.brokoli5191.quote.ui.formattedQuote
import app.brokoli5191.quote.ui.uiText
import app.brokoli5191.quote.ui.components.ExpressiveButton
import app.brokoli5191.quote.ui.components.ExpressiveIconButton
import app.brokoli5191.quote.ui.components.ExpressiveTextButton
import app.brokoli5191.quote.ui.components.TranslationToggle
import app.brokoli5191.quote.ui.components.rememberExpressiveShape
import app.brokoli5191.quote.ui.theme.SerifFontFamily
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: QuoteViewModel) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filteredQuotes by viewModel.filteredQuotes.collectAsStateWithLifecycle()
    val sourceMode by viewModel.quoteSourceMode.collectAsStateWithLifecycle()
    val communitySyncFinished by viewModel.communitySyncFinished.collectAsStateWithLifecycle()
    val showAnonymousQuotes by viewModel.showAnonymousQuotes.collectAsStateWithLifecycle()
    val language = LocalAppLanguage.current
    val visibleCategoryCatalog = remember(showAnonymousQuotes) {
        categoryCatalog.filter { showAnonymousQuotes || it.name != "Reflections" }
    }
    val visibleFilterCategories = remember(showAnonymousQuotes) {
        filterCategoryNames.filter { showAnonymousQuotes || it != "Reflections" }
    }

    val selectedCategories by viewModel.selectedCategories.collectAsStateWithLifecycle()
    val lowPerformanceMode by viewModel.lowPerformanceMode.collectAsStateWithLifecycle()
    val blurNavigationSurfaces by viewModel.blurNavigationSurfaces.collectAsStateWithLifecycle()
    val isBrowsing = selectedCategories.isNotEmpty() || searchQuery.isNotBlank()
    val categoryLabel = when {
        selectedCategories.size > 1 -> if (language == "de") "${selectedCategories.size} Kategorien" else "${selectedCategories.size} categories"
        selectedCategories.size == 1 -> categoryText(selectedCategories.first())
        else -> null
    }
    
    val scrollState = rememberScrollState()
    val libraryBlurEnabled = blurNavigationSurfaces && !lowPerformanceMode
    val libraryHazeState = rememberHazeState(blurEnabled = libraryBlurEnabled)
    // The header (title + search field) and the filter row are measured, so the content below
    // them starts at the real chrome height for every language and font scale.
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    var filterRowHeightPx by remember { mutableIntStateOf(0) }
    val headerHeight = if (headerHeightPx > 0) with(density) { headerHeightPx.toDp() } else statusBarTop + LibraryHeaderFallbackHeight
    val filterRowHeight = if (filterRowHeightPx > 0) with(density) { filterRowHeightPx.toDp() } else LibraryFilterRowFallbackHeight
    val filterRowTop = headerHeight + LibraryFilterRowGap
    val browseContentTop = filterRowTop + filterRowHeight + LibraryContentGap
    val libraryChromeHeight = (if (isBrowsing) filterRowTop + filterRowHeight else headerHeight) + LibraryContentGap
    var showCategoryFilterDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val haptic = LocalHapticFeedback.current
    val selectCategoryWithHaptic: (String?) -> Unit = { category ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        viewModel.selectCategory(category)
    }

    // Entry animations for headers
    val headerAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        headerAlpha.animateTo(1f, animationSpec = tween(500, easing = LinearOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // 3. Main sliding bento content vs list view in same composition
            AnimatedContent(
                targetState = isBrowsing,
                transitionSpec = {
                    if (lowPerformanceMode) {
                        ContentTransform(
                            targetContentEnter = EnterTransition.None,
                            initialContentExit = ExitTransition.None
                        )
                    } else {
                        val exprSpring = spring<Float>(dampingRatio = 0.52f, stiffness = 220f)
                        val exprOffsetSpring = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.52f, stiffness = 220f)
                        if (targetState) {
                            // Sliding bento collapses, category content slides up with organic bounce
                            (slideInVertically(animationSpec = exprOffsetSpring) { height -> (height * 0.15f).toInt() } +
                             fadeIn(animationSpec = exprSpring) +
                             scaleIn(initialScale = 0.88f, animationSpec = exprSpring))
                            .togetherWith(
                             fadeOut(animationSpec = exprSpring) +
                             scaleOut(targetScale = 0.88f, animationSpec = exprSpring))
                        } else {
                            // Category back: slide out downwards cleanly, bento content fades in
                            (fadeIn(animationSpec = exprSpring) +
                             scaleIn(initialScale = 0.88f, animationSpec = exprSpring))
                            .togetherWith(
                             slideOutVertically(animationSpec = exprOffsetSpring) { height -> (height * 0.12f).toInt() } +
                             fadeOut(animationSpec = exprSpring) +
                             scaleOut(targetScale = 0.95f, animationSpec = exprSpring))
                        }
                    }
                },
                label = "LibraryInPlaceTransition",
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
                    .hazeSource(libraryHazeState, zIndex = 0f)
            ) { isBrowsing ->
                if (isBrowsing) {
                    CategoryBrowseViewInPlace(
                        searchQuery = searchQuery,
                        quotes = filteredQuotes,
                        onBack = {
                            selectCategoryWithHaptic(null)
                            viewModel.setSearchQuery("")
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        communityOnly = sourceMode == QuoteSourceMode.COMMUNITY,
                        communitySyncFinished = communitySyncFinished,
                        onRetrySync = { viewModel.syncCommunityQuotes() },
                        lowPerformanceMode = lowPerformanceMode,
                        topContentPadding = browseContentTop
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(bottom = 112.dp)
                    ) {
                        Spacer(modifier = Modifier.height(headerHeight + LibraryContentGap))

                        // Beautiful Adaptive Bento Grid with Stunning Stocks loaded via Coil
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Single source of truth: see CategoryCatalog.kt
                            val bentoCategories = visibleCategoryCatalog

                            // Render Inspirational (first large card)
                            val inspirational = bentoCategories[0]
                            CategoryBentoCard(
                                name = inspirational.name,
                                icon = inspirational.icon,
                                tintColor = inspirational.tintColor,
                                height = 120.dp,
                                onClick = { selectCategoryWithHaptic(inspirational.name) }
                            )

                            // Render the remaining categories in rows of 2
                            val midCategories = bentoCategories.drop(1)
                            midCategories.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    pair.forEach { cat ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            CategoryBentoCard(
                                                name = cat.name,
                                                icon = cat.icon,
                                                tintColor = cat.tintColor,
                                                height = 110.dp,
                                                onClick = { selectCategoryWithHaptic(cat.name) },
                                                modifier = Modifier.fillMaxHeight()
                                            )
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Compose the source first, then sample it from this single blur layer.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(libraryChromeHeight)
                    .zIndex(1f)
                    .hazeEffect(libraryHazeState) {
                        blurRadius = 32.dp
                        tints = emptyList()
                        noiseFactor = 0f
                        mask = Brush.verticalGradient(
                            0f to Color.Black,
                            0.72f to Color.Black,
                            1f to Color.Transparent
                        )
                    }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(libraryChromeHeight)
                    .zIndex(1.5f)
                    .background(
                        if (isBrowsing) {
                            Brush.verticalGradient(
                                0f to MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                                0.58f to MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
                                0.84f to MaterialTheme.colorScheme.background.copy(alpha = 0.34f),
                                1f to Color.Transparent
                            )
                        } else {
                            Brush.verticalGradient(
                                0f to MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                0.55f to MaterialTheme.colorScheme.background.copy(alpha = 0.68f),
                                0.82f to MaterialTheme.colorScheme.background.copy(alpha = 0.22f),
                                1f to Color.Transparent
                            )
                        }
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(2f)
                    .onSizeChanged { headerHeightPx = it.height }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .graphicsLayer { alpha = headerAlpha.value }
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiText("Library"),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        )
                        ExpressiveIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showCategoryFilterDialog = true
                            },
                            modifier = Modifier.size(48.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = uiText("Filter Categories"),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = {
                        Text(
                            if (language == "de") "Quotes durchsuchen …" else "Search wisdom…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(32.dp)),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = uiText("Search"), tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            ExpressiveIconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = uiText("Clear Search"))
                            }
                        }
                    },
                    shape = RoundedCornerShape(32.dp),
                    singleLine = true
                )
            }

            if (isBrowsing) {
                LibraryFilterRow(
                    category = categoryLabel,
                    onBack = {
                        selectCategoryWithHaptic(null)
                        viewModel.setSearchQuery("")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = filterRowTop)
                        .zIndex(3f)
                        .padding(horizontal = 20.dp)
                        .onSizeChanged { filterRowHeightPx = it.height }
                )
            }
        }
    }

    // Category Filter Picker Sheet (allows selecting multiple categories at once)
    if (showCategoryFilterDialog) {
        var sheetDragOffset by remember { mutableFloatStateOf(0f) }
        var pendingCategories by remember { mutableStateOf(selectedCategories) }
        Dialog(
            onDismissRequest = { showCategoryFilterDialog = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable { showCategoryFilterDialog = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationY = sheetDragOffset }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {},
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragStart = { sheetDragOffset = 0f },
                                        onDragCancel = { sheetDragOffset = 0f },
                                        onDragEnd = {
                                            if (sheetDragOffset > 120f) {
                                                showCategoryFilterDialog = false
                                            } else {
                                                sheetDragOffset = 0f
                                            }
                                        }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        sheetDragOffset = (sheetDragOffset + dragAmount).coerceAtLeast(0f)
                                    }
                                },
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, end = 24.dp, top = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BottomSheetDefaults.DragHandle(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }

                            Text(
                                text = uiText("Select Categories"),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            visibleFilterCategories.chunked(2).forEach { rowCategoryList ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowCategoryList.forEach { categoryName ->
                                        val isSelected = pendingCategories.contains(categoryName)
                                        val interactionSource = remember(categoryName) { MutableInteractionSource() }
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                pendingCategories = if (isSelected) {
                                                    pendingCategories - categoryName
                                                } else {
                                                    pendingCategories + categoryName
                                                }
                                            },
                                            label = {
                                                // Long German names wrap onto a second line instead of being cut off.
                                                Text(
                                                    text = categoryText(categoryName),
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    ),
                                                    maxLines = 2,
                                                    modifier = Modifier.padding(vertical = 6.dp)
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .heightIn(min = 48.dp),
                                            shape = rememberExpressiveShape(interactionSource, 23.dp, 10.dp),
                                            interactionSource = interactionSource,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = isSelected,
                                                selectedBorderColor = MaterialTheme.colorScheme.primary,
                                                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                            )
                                        )
                                    }
                                    if (rowCategoryList.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExpressiveTextButton(
                                onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    pendingCategories = emptySet()
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(end = 8.dp)
                            ) {
                                Text(uiText("Clear All"), textAlign = TextAlign.Center, maxLines = 2)
                            }

                            ExpressiveButton(
                                onClick = {
                                    viewModel.setSelectedCategories(pendingCategories)
                                    showCategoryFilterDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                restingCorner = 12.dp,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(uiText("Apply Filter"), textAlign = TextAlign.Center, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryBrowseViewInPlace(
    searchQuery: String,
    quotes: List<QuoteEntity>,
    onBack: () -> Unit,
    onToggleFavorite: (QuoteEntity) -> Unit,
    communityOnly: Boolean = false,
    communitySyncFinished: Boolean = true,
    onRetrySync: () -> Unit = {},
    lowPerformanceMode: Boolean = false,
    topContentPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val shareTitle = uiText("Share Wisdom")
    val haptic = LocalHapticFeedback.current
    val onBackWithHaptic = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onBack()
    }
    // Ids whose entry animation already ran: a card slides in only the first time it appears,
    // not again when it is scrolled back into view.
    val animatedQuoteIds = remember { HashSet<Int>() }
    val onShare = remember(context, shareTitle) {
        { text: String ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, shareTitle))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        if (quotes.isEmpty()) {
            val infiniteTransition = rememberInfiniteTransition(label = "EmptyStateAnimation")
            
            // Smoother floating micro-animations
            val floatAnim by if (lowPerformanceMode) {
                remember { mutableFloatStateOf(0f) }
            } else {
                infiniteTransition.animateFloat(
                    initialValue = -8f,
                    targetValue = 8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "FloatingAnimation"
                )
            }
            
            val scaleAnim by if (lowPerformanceMode) {
                remember { mutableFloatStateOf(1f) }
            } else {
                infiniteTransition.animateFloat(
                    initialValue = 0.95f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(3000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "ScalingAnimation"
                )
            }

            val alphaPulse by if (lowPerformanceMode) {
                remember { mutableFloatStateOf(1f) }
            } else {
                infiniteTransition.animateFloat(
                    initialValue = 0.65f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2500, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "AlphaPulseAnimation"
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topContentPadding, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Beautiful Multi-layer Material Illustration Frame
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .graphicsLayer {
                            translationY = floatAnim
                            scaleX = scaleAnim
                            scaleY = scaleAnim
                            alpha = alphaPulse
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Soft glowing background circle
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                shape = CircleShape
                            )
                    )
                    
                    // Stacked decorative card representing missing quotes
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .graphicsLayer { rotationZ = -12f }
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            )
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .graphicsLayer { rotationZ = 8f }
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(
                                1.5.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                RoundedCornerShape(16.dp)
                            )
                    )
                    
                    // Main illustration icon
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = uiText("Search Off Icon"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    
                    // Micro-interaction star on upper right
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.TopEnd)
                            .graphicsLayer {
                                translationX = -20f
                                translationY = 15f
                            }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = if (communityOnly) uiText("No Community Quotes Yet") else uiText("No Wisdom Matches"),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = SerifFontFamily
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                
                if (communityOnly) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text =
                        if (communitySyncFinished) uiText("Approved community quotes will appear here after the next sync.")
                        else uiText("Checking for newly approved community quotes..."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .padding(bottom = 24.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Beautiful interactive button to clear filters
                ExpressiveButton(
                    onClick = if (communityOnly) onRetrySync else onBackWithHaptic,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    restingCorner = 24.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (communityOnly) uiText("Sync again") else uiText("Reset All Filters"),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = topContentPadding, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(quotes, key = { it.id }, contentType = { BrowseQuoteContentType }) { quote ->
                    val animateEntry = remember(quote.id) {
                        !lowPerformanceMode && animatedQuoteIds.add(quote.id)
                    }
                    QuoteBrowseItemCard(
                        quote = quote,
                        lowPerformanceMode = lowPerformanceMode,
                        animateEntry = animateEntry,
                        onToggleFavorite = onToggleFavorite,
                        onShare = onShare
                    )
                }
            }
        }

    }
}

@Composable
private fun LibraryFilterRow(
    category: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // The pill wraps its label and may grow to two lines; it never pushes the button away.
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Box(
                modifier = Modifier
                    .clip(FilterPillShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category?.let { categoryText(it) } ?: uiText("Search results"),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        ExpressiveTextButton(
            onClick = onBack,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .widthIn(max = 200.dp),
            restingCorner = 32.dp,
            colors = ButtonDefaults.textButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = uiText("Clear Filter"),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}


@Composable
fun QuoteBrowseItemCard(
    quote: QuoteEntity,
    onToggleFavorite: (QuoteEntity) -> Unit,
    onShare: (String) -> Unit,
    lowPerformanceMode: Boolean = false,
    animateEntry: Boolean = !lowPerformanceMode
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val cardScope = rememberCoroutineScope()
    var copyConfirmed by remember { mutableStateOf(false) }
    // Animatables only exist for cards that actually slide in.
    val offsetX = if (animateEntry) remember { Animatable(80f) } else null
    val alphaAnim = if (animateEntry) remember { Animatable(0f) } else null

    if (offsetX != null && alphaAnim != null) {
        LaunchedEffect(Unit) {
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f)
            )
        }
        LaunchedEffect(Unit) {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 250)
            )
        }
    }

    val alternateText = quote.alternateText()
    val alternateLanguage = quote.alternateLanguage()
    var showTranslation by rememberSaveable(quote.id) { mutableStateOf(false) }
    val displayedText = if (showTranslation && alternateText != null) alternateText else quote.text
    val appLanguage = LocalAppLanguage.current
    // Quotation marks follow the language of the text on screen, not the app language. An own quote
    // without a translation has no known text language, so it keeps neutral English marks.
    val displayedLanguage = when {
        alternateLanguage == null && quote.isUserAdded -> QuoteLanguage.ENGLISH
        alternateLanguage == null -> appLanguage
        showTranslation -> alternateLanguage
        alternateLanguage == QuoteLanguage.GERMAN -> QuoteLanguage.ENGLISH
        else -> QuoteLanguage.GERMAN
    }
    val displayedAuthor = if (quote.hasUnknownAuthor) uiText("Unknown") else quote.author

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (offsetX != null && alphaAnim != null) {
                    Modifier.graphicsLayer {
                        translationX = offsetX.value
                        alpha = alphaAnim.value
                    }
                } else {
                    Modifier
                }
            )
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), BrowseCardShape),
        shape = BrowseCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (displayedLanguage == QuoteLanguage.GERMAN) "„$displayedText“" else "“$displayedText”",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = SerifFontFamily,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 26.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (alternateText != null && alternateLanguage != null) {
                // The chip names the language the quote switches to.
                val targetLanguage = when {
                    !showTranslation -> alternateLanguage
                    alternateLanguage == QuoteLanguage.GERMAN -> QuoteLanguage.ENGLISH
                    else -> QuoteLanguage.GERMAN
                }
                TranslationToggle(
                    showingTranslation = showTranslation,
                    targetLanguage = targetLanguage,
                    onToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showTranslation = !showTranslation
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (displayedAuthor.isNotBlank()) Text(
                    text = "— $displayedAuthor",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                    // Two lines so names like "Marcus Aurelius" stay readable next to the actions.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveIconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("quote", formattedQuote(displayedText, displayedAuthor)))
                        copyConfirmed = true
                        cardScope.launch {
                            delay(1200)
                            copyConfirmed = false
                        }
                    }) {
                        Icon(
                            imageVector = if (copyConfirmed) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = uiText(if (copyConfirmed) "Copied" else "Copy"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    ExpressiveIconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onShare(formattedQuote(displayedText, displayedAuthor))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = uiText("Share"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    var isLikedAnim by remember { mutableStateOf(false) }
                    val heartScale by animateFloatAsState(
                        targetValue = if (!lowPerformanceMode && isLikedAnim) 1.35f else 1.0f,
                        animationSpec = spring(stiffness = 500f, dampingRatio = 0.5f),
                        finishedListener = { isLikedAnim = false }
                    )

                    ExpressiveIconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isLikedAnim = true
                        onToggleFavorite(quote)
                    }) {
                        Icon(
                            imageVector = if (quote.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = uiText("Favorite"),
                            tint = if (quote.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer {
                                scaleX = heartScale
                                scaleY = heartScale
                            }
                        )
                    }
                }
            }
        }
    }
}

private val BrowseCardShape = RoundedCornerShape(20.dp)
private const val BrowseQuoteContentType = "browse_quote"

// Chrome layout: gap between search field and filter row, gap above the first card, and
// estimates used for the first frame until the header and filter row have been measured.
private val LibraryFilterRowGap = 12.dp
private val LibraryContentGap = 16.dp
private val LibraryHeaderFallbackHeight = 136.dp
private val LibraryFilterRowFallbackHeight = 48.dp

private val FilterPillShape = RoundedCornerShape(32.dp)


@Composable
fun CategoryBentoCard(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tintColor: Color,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val expressiveShape = rememberExpressiveShape(interactionSource, 20.dp, 10.dp)
    val tint = categoryTint(tintColor, lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f)
    Card(
        onClick = onClick,
        // A minimum height instead of a fixed one, so names still fit at large font scales.
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .border(
                width = 1.dp,
                color = tint.copy(alpha = 0.15f),
                shape = expressiveShape
            ),
        shape = expressiveShape,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            tint.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            // Elegant large top-right or center-right glowing background icon
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-10).dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint.copy(alpha = 0.12f),
                    modifier = Modifier.size(72.dp)
                )
            }

            // Icon on top, label at the bottom; the card grows instead of overlapping them.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = height - 32.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Primary visual crisp icon in top-left
                Box(
                    modifier = Modifier
                        .background(
                            color = tint.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = categoryText(name),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
