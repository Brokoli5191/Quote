package app.brokoli5191.quote.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.ui.QuoteViewModel
import app.brokoli5191.quote.ui.components.ExpressiveButton
import app.brokoli5191.quote.ui.components.ExpressiveIconButton
import app.brokoli5191.quote.ui.components.ExpressiveTextButton
import app.brokoli5191.quote.ui.components.rememberExpressiveShape
import app.brokoli5191.quote.ui.theme.SerifFontFamily
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: QuoteViewModel) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredQuotes by viewModel.filteredQuotes.collectAsState()
    val allQuotes by viewModel.allQuotes.collectAsState()

    val selectedCategories by viewModel.selectedCategories.collectAsState()
    val lowPerformanceMode by viewModel.lowPerformanceMode.collectAsState()
    val blurNavigationSurfaces by viewModel.blurNavigationSurfaces.collectAsState()
    val isBrowsing = selectedCategories.isNotEmpty() || searchQuery.isNotBlank()
    val categoryLabel = when {
        selectedCategories.size > 1 -> "${selectedCategories.size} categories"
        selectedCategories.size == 1 -> selectedCategories.first()
        else -> null
    }
    
    val scrollState = rememberScrollState()
    val libraryBlurEnabled = blurNavigationSurfaces && !lowPerformanceMode
    val libraryHazeState = rememberHazeState(blurEnabled = libraryBlurEnabled)
    val libraryChromeHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 128.dp
    var showCategoryFilterDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val haptic = LocalHapticFeedback.current
    val selectCategoryWithHaptic: (String?) -> Unit = { category ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                        lowPerformanceMode = lowPerformanceMode,
                        topContentPadding = libraryChromeHeight
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(bottom = 112.dp)
                    ) {
                        Spacer(modifier = Modifier.height(libraryChromeHeight + 12.dp))

                        // Beautiful Adaptive Bento Grid with Stunning Stocks loaded via Coil
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Single source of truth: see CategoryCatalog.kt
                            val bentoCategories = categoryCatalog

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
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    pair.forEach { cat ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            CategoryBentoCard(
                                                name = cat.name,
                                                icon = cat.icon,
                                                tintColor = cat.tintColor,
                                                height = 110.dp,
                                                onClick = { selectCategoryWithHaptic(cat.name) }
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
                    .height(libraryChromeHeight + if (isBrowsing) 64.dp else 0.dp)
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
                    .height(libraryChromeHeight + if (isBrowsing) 64.dp else 0.dp)
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
                            text = "Library",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        ExpressiveIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showCategoryFilterDialog = true
                            },
                            modifier = Modifier.size(40.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter Categories",
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
                            "Search wisdom...",
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
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            ExpressiveIconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Search")
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
                        .offset(y = libraryChromeHeight)
                        .zIndex(3f)
                        .padding(horizontal = 20.dp)
                )
            }
        }
    }

    // Category Filter Picker Sheet (allows selecting multiple categories at once)
    if (showCategoryFilterDialog) {
        ModalBottomSheet(
            onDismissRequest = { showCategoryFilterDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Categories",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    ExpressiveIconButton(onClick = { showCategoryFilterDialog = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val nestedScrollConnection = remember {
                    object : NestedScrollConnection {
                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            return if (available.y > 0f) {
                                Offset(0f, available.y)
                            } else {
                                Offset.Zero
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .nestedScroll(nestedScrollConnection)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val categoriesList = filterCategoryNames

                    categoriesList.chunked(2).forEach { rowCategoryList ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowCategoryList.forEach { categoryName ->
                                val isSelected = selectedCategories.contains(categoryName)
                                val interactionSource = remember(categoryName) { MutableInteractionSource() }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.toggleCategorySelected(categoryName)
                                    },
                                    label = {
                                        Text(
                                            text = categoryName,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = rememberExpressiveShape(interactionSource, 23.dp, 10.dp),
                                    interactionSource = interactionSource,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
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
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveTextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.clearCategorySelection()
                            showCategoryFilterDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Clear All")
                    }

                    ExpressiveButton(
                        onClick = { showCategoryFilterDialog = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        restingCorner = 12.dp
                    ) {
                        Text("Apply Filter")
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
    lowPerformanceMode: Boolean = false,
    topContentPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val onBackWithHaptic = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onBack()
    }

    // Don't flash empty-state on first frame before filteredQuotes arrives
    var hasReceivedQuotes by remember { mutableStateOf(quotes.isNotEmpty()) }
    LaunchedEffect(quotes) { if (quotes.isNotEmpty()) hasReceivedQuotes = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        if (hasReceivedQuotes && quotes.isEmpty()) {
            val infiniteTransition = rememberInfiniteTransition(label = "EmptyStateAnimation")
            
            // Smoother floating micro-animations
            val floatAnim by if (lowPerformanceMode) {
                remember { mutableStateOf(0f) }
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
                remember { mutableStateOf(1f) }
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
                remember { mutableStateOf(1f) }
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
                    .padding(top = topContentPadding + 56.dp, bottom = 32.dp),
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
                        contentDescription = "Search Off Icon",
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
                    text = "No Wisdom Matches",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = SerifFontFamily
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "We couldn't find any quotes matching your interest. Reset the filters to explore other profound areas of wisdom.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(bottom = 24.dp)
                )

                // Beautiful interactive button to clear filters
                ExpressiveButton(
                    onClick = onBackWithHaptic,
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
                        text = "Reset All Filters",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = topContentPadding + 56.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(quotes, key = { it.id }) { quote ->
                    QuoteBrowseItemCard(
                        quote = quote,
                        lowPerformanceMode = lowPerformanceMode,
                        onToggleFavorite = { onToggleFavorite(quote) },
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "\"${quote.text}\" — ${quote.author}")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Wisdom"))
                        }
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
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .clip(FilterPillShape)
                .background(MaterialTheme.colorScheme.primary)
                .height(40.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = category ?: "Search results",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        ExpressiveTextButton(
            onClick = onBack,
            modifier = Modifier.height(40.dp),
            restingCorner = 32.dp,
            colors = ButtonDefaults.textButtonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF1C1B1F)
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
        ) {
            Text(
                text = "Clear Filter",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}


@Composable
fun QuoteBrowseItemCard(
    quote: QuoteEntity,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    lowPerformanceMode: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val offsetX = remember { Animatable(if (lowPerformanceMode) 0f else 80f) }
    val alphaAnim = remember { Animatable(if (lowPerformanceMode) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!lowPerformanceMode) offsetX.animateTo(
            targetValue = 0f,
            animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f)
        )
    }
    LaunchedEffect(Unit) {
        if (!lowPerformanceMode) alphaAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 250)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = offsetX.value
                alpha = alphaAnim.value
            }
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "\"${quote.text}\"",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = SerifFontFamily,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 26.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "— ${quote.author}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveIconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("quote", "\"${quote.text}\" — ${quote.author}"))
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    ExpressiveIconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onShare()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    var isLikedAnim by remember { mutableStateOf(false) }
                    val heartScale by animateFloatAsState(
                        targetValue = if (isLikedAnim) 1.35f else 1.0f,
                        animationSpec = spring(stiffness = 500f, dampingRatio = 0.5f),
                        finishedListener = { isLikedAnim = false }
                    )

                    ExpressiveIconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isLikedAnim = true
                        onToggleFavorite()
                    }) {
                        Icon(
                            imageVector = if (quote.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (quote.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private val FilterPillShape = RoundedCornerShape(32.dp)


@Composable
fun CategoryBentoCard(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tintColor: Color,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val expressiveShape = rememberExpressiveShape(interactionSource, 20.dp, 10.dp)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .border(
                width = 1.dp,
                color = tintColor.copy(alpha = 0.15f),
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
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            tintColor.copy(alpha = 0.08f),
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
                    tint = tintColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(72.dp)
                )
            }

            // Primary visual crisp icon in top-left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(
                        color = tintColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tintColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Bottom descriptive and label texts
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
