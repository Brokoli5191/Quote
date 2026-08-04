package app.brokoli5191.quote.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.ui.AuraViewModel
import app.brokoli5191.quote.ui.theme.SerifFontFamily
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: AuraViewModel) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredQuotes by viewModel.filteredQuotes.collectAsState()
    val allQuotes by viewModel.allQuotes.collectAsState()

    val selectedCategories by viewModel.selectedCategories.collectAsState()
    val lowPerformanceMode by viewModel.lowPerformanceMode.collectAsState()
    
    val scrollState = rememberScrollState()
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
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // 1. Sleek unifed Header (matches "Your Collection" in Saved tab)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .graphicsLayer { alpha = headerAlpha.value }
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 0.dp),
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

                    // Category Filter list icon on the top right
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showCategoryFilterDialog = true
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter Categories",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Persistent Search bar in composition to prevent keyboard loss
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search wisdom...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(32.dp)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(32.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Main sliding bento content vs list view in same composition
            AnimatedContent(
                targetState = (selectedCategories.isNotEmpty() || searchQuery.isNotBlank()),
                transitionSpec = {
                    if (lowPerformanceMode) {
                        fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
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
                modifier = Modifier.fillMaxSize()
            ) { isBrowsing ->
                if (isBrowsing) {
                    val categoryLabel = when {
                        selectedCategories.size > 1 -> "${selectedCategories.size} categories"
                        selectedCategories.size == 1 -> selectedCategories.first()
                        else -> null
                    }
                    CategoryBrowseViewInPlace(
                        category = categoryLabel,
                        searchQuery = searchQuery,
                        quotes = filteredQuotes,
                        onBack = {
                            selectCategoryWithHaptic(null)
                            viewModel.setSearchQuery("")
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        lowPerformanceMode = lowPerformanceMode
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(bottom = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Category Section Header to spacing bento grid correctly down
                        Text(
                            text = "Browse Categories",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                        )

                        // Beautiful Adaptive Bento Grid with Stunning Stocks loaded via Coil
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val bentoCategories = remember {
                                listOf(
                                    CategoryTileData(name = "Inspirational", icon = Icons.Default.EmojiObjects, tintColor = Color(0xFFFFF7EB), isFullWidth = true),
                                    CategoryTileData(name = "Life", icon = Icons.Default.Spa, tintColor = Color(0xFFA0D2AD)),
                                    CategoryTileData(name = "Humor", icon = Icons.Default.TheaterComedy, tintColor = Color(0xFFFFDB9C)),
                                    CategoryTileData(name = "Love", icon = Icons.Default.Favorite, tintColor = Color(0xFFFFB2C5)),
                                    CategoryTileData(name = "Books", icon = Icons.Default.LibraryBooks, tintColor = Color(0xFFFFDB9C)),
                                    CategoryTileData(name = "Truth", icon = Icons.Default.Balance, tintColor = Color(0xFFADC6FF)),
                                    CategoryTileData(name = "Reading", icon = Icons.Default.AutoStories, tintColor = Color(0xFFFFDB9C)),
                                    CategoryTileData(name = "Wisdom", icon = Icons.Default.SelfImprovement, tintColor = Color(0xFFFFDB9C)),
                                    CategoryTileData(name = "Happiness", icon = Icons.Default.SentimentVerySatisfied, tintColor = Color(0xFFFFDB9C)),
                                    CategoryTileData(name = "Writing", icon = Icons.Default.DriveFileRenameOutline, tintColor = Color(0xFFADC6FF)),
                                    CategoryTileData(name = "Inspiration", icon = Icons.Default.AutoAwesome, tintColor = Color(0xFFFFF7EB)),
                                    CategoryTileData(name = "Philosophy", icon = Icons.Default.HistoryEdu, tintColor = Color(0xFFADC6FF)),
                                    CategoryTileData(name = "Death", icon = Icons.Default.HourglassEmpty, tintColor = Color(0xFFADC6FF)),
                                    CategoryTileData(name = "Poetry", icon = Icons.Default.Create, tintColor = Color(0xFFFFB2C5)),
                                    CategoryTileData(name = "Optimism", icon = Icons.Default.WbSunny, tintColor = Color(0xFFFFDB9C))
                                )
                            }

                            // Render Inspirational (First large card)
                            val inspirational = bentoCategories[0]
                            CategoryBentoCard(
                                name = inspirational.name,
                                icon = inspirational.icon,
                                tintColor = inspirational.tintColor,
                                height = 120.dp,
                                onClick = { selectCategoryWithHaptic(inspirational.name) }
                            )

                            // Render rows of 2 for middle categories (1 to 14)
                            val midCategories = bentoCategories.subList(1, 15)
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
        }
    }

    // Category Filter Picker Sheet (allows selecting multiple categories at once)
    if (showCategoryFilterDialog) {
        ModalBottomSheet(
            onDismissRequest = { showCategoryFilterDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF111015),
            tonalElevation = 8.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
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
                    IconButton(onClick = { showCategoryFilterDialog = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Text(
                    text = "Filter search findings by multiple selected categories below:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )

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
                    val categoriesList = listOf(
                        "Inspirational",
                        "Life",
                        "Humor",
                        "Love",
                        "Books",
                        "Truth",
                        "Reading",
                        "Wisdom",
                        "Happiness",
                        "Writing",
                        "Inspiration",
                        "Philosophy",
                        "Death",
                        "Poetry",
                        "Optimism"
                    )

                    categoriesList.chunked(2).forEach { rowCategoryList ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowCategoryList.forEach { categoryName ->
                                val isSelected = selectedCategories.contains(categoryName)
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
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        containerColor = Color(0xFF1B1A21),
                                        labelColor = Color.White.copy(alpha = 0.6f)
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        selectedBorderColor = MaterialTheme.colorScheme.primary,
                                        borderColor = Color.White.copy(alpha = 0.08f)
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
                    TextButton(
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

                    Button(
                        onClick = { showCategoryFilterDialog = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
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
    category: String?,
    searchQuery: String,
    quotes: List<QuoteEntity>,
    onBack: () -> Unit,
    onToggleFavorite: (QuoteEntity) -> Unit,
    lowPerformanceMode: Boolean = false
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // Beautiful elegant breadcrumb row to manage selection
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (category != null) category else "Search results",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            TextButton(onClick = onBackWithHaptic) {
                Text(
                    text = "Clear Filter",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

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
                    .padding(vertical = 32.dp),
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
                    // Soft glowing background aura circle
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
                Button(
                    onClick = onBackWithHaptic,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(24.dp)
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
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(quotes, key = { it.id }) { quote ->
                    QuoteBrowseItemCard(
                        quote = quote,
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
fun QuoteBrowseItemCard(
    quote: QuoteEntity,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val offsetX = remember { Animatable(80f) }
    val alphaAnim = remember { Animatable(0f) }

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
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
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

                    IconButton(onClick = {
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

                    IconButton(onClick = {
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


data class CategoryTileData(
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tintColor: Color,
    val isFullWidth: Boolean = false
)

@Composable
fun CategoryBentoCard(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tintColor: Color,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .border(
                width = 1.dp,
                color = tintColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF131217)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            tintColor.copy(alpha = 0.08f),
                            Color(0xFF111015)
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
                    color = Color.White
                )
            }
        }
    }
}

