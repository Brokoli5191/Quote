package com.example.ui.screens

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
import com.example.data.QuoteEntity
import com.example.ui.AuraViewModel
import com.example.ui.theme.SerifFontFamily
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: AuraViewModel) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val filteredQuotes by viewModel.filteredQuotes.collectAsState()
    val allQuotes by viewModel.allQuotes.collectAsState()
    
    val selectedCategories by viewModel.selectedCategories.collectAsState()
    
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
                        tint = MaterialTheme.colorScheme.primary
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
                targetState = (selectedCategory != null || searchQuery.isNotBlank()),
                transitionSpec = {
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
                },
                label = "LibraryInPlaceTransition",
                modifier = Modifier.fillMaxSize()
            ) { isBrowsing ->
                if (isBrowsing) {
                    val categoryLabel = when {
                        selectedCategories.size > 1 -> "${selectedCategories.size} categories"
                        selectedCategories.size == 1 -> selectedCategories.first()
                        else -> selectedCategory
                    }
                    CategoryBrowseViewInPlace(
                        category = categoryLabel,
                        searchQuery = searchQuery,
                        quotes = filteredQuotes,
                        onBack = {
                            selectCategoryWithHaptic(null)
                            viewModel.setSearchQuery("")
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(it) }
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
                                    CategoryTileData(
                                        name = "Inspirational",
                                        description = "Ignite your inner fire, passion, and thrive",
                                        imageUrl = "https://images.unsplash.com/photo-1499209974431-9dddcdce7f88?q=80&w=600",
                                        icon = Icons.Default.Lightbulb,
                                        tintColor = Color(0xFFFFF7EB),
                                        isFullWidth = true
                                    ),
                                    CategoryTileData(
                                        name = "Life",
                                        description = "Existential reflections & daily journeys",
                                        imageUrl = "https://images.unsplash.com/photo-1502082553048-f009c37129b9?q=80&w=600",
                                        icon = Icons.Default.Nature,
                                        tintColor = Color(0xFFA0D2AD)
                                    ),
                                    CategoryTileData(
                                        name = "Humor",
                                        description = "Laughter, wit, and cheeky observations",
                                        imageUrl = "https://images.unsplash.com/photo-1531746020798-e6953c6e8e04?q=80&w=600",
                                        icon = Icons.Default.WbSunny,
                                        tintColor = Color(0xFFFFDB9C)
                                    ),
                                    CategoryTileData(
                                        name = "Love",
                                        description = "Compassion, human bonds, and high affection",
                                        imageUrl = "https://images.unsplash.com/photo-1516589178581-6cd7833ae3b2?q=80&w=600",
                                        icon = Icons.Default.Favorite,
                                        tintColor = Color(0xFFFFB2C5)
                                    ),
                                    CategoryTileData(
                                        name = "Books",
                                        description = "A portable magic of printed pages",
                                        imageUrl = "https://images.unsplash.com/photo-1524995997946-a1c2e315a42f?q=80&w=600",
                                        icon = Icons.Default.MenuBook,
                                        tintColor = Color(0xFFFFDB9C)
                                    ),
                                    CategoryTileData(
                                        name = "Truth",
                                        description = "Honesty and direct paths without compromise",
                                        imageUrl = "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?q=80&w=600",
                                        icon = Icons.Default.CheckCircle,
                                        tintColor = Color(0xFFADC6FF)
                                    ),
                                    CategoryTileData(
                                        name = "Reading",
                                        description = "The quiet art of continuous literature",
                                        imageUrl = "https://images.unsplash.com/photo-1544947950-fa07a98d237f?q=80&w=600",
                                        icon = Icons.Default.AutoStories,
                                        tintColor = Color(0xFFFFDB9C)
                                    ),
                                    CategoryTileData(
                                        name = "Wisdom",
                                        description = "Centuries of knowledge and deep philosophy",
                                        imageUrl = "https://images.unsplash.com/photo-1457369804613-52c61a468e7d?q=80&w=600",
                                        icon = Icons.Default.Psychology,
                                        tintColor = Color(0xFFFFDB9C)
                                    ),
                                    CategoryTileData(
                                        name = "Happiness",
                                        description = "Peace of mind and pure contentments",
                                        imageUrl = "https://images.unsplash.com/photo-1490730141103-6cac27aaab94?q=80&w=600",
                                        icon = Icons.Default.SentimentVerySatisfied,
                                        tintColor = Color(0xFFFFDB9C)
                                    ),
                                    CategoryTileData(
                                        name = "Writing",
                                        description = "Sit down at typewriters and bleed",
                                        imageUrl = "https://images.unsplash.com/photo-1455390582262-044cdead277a?q=80&w=600",
                                        icon = Icons.Default.Edit,
                                        tintColor = Color(0xFFADC6FF)
                                    ),
                                    CategoryTileData(
                                        name = "Inspiration",
                                        description = "Sudden bright bursts of creative idea",
                                        imageUrl = "https://images.unsplash.com/photo-1456406644174-8dba4c7f27f2?q=80&w=600",
                                        icon = Icons.Default.FlashOn,
                                        tintColor = Color(0xFFFFF7EB)
                                    ),
                                    CategoryTileData(
                                        name = "Philosophy",
                                        description = "Socrates, Stoics, and search of meaning",
                                        imageUrl = "https://images.unsplash.com/photo-1507679799987-c73779587ccf?q=80&w=600",
                                        icon = Icons.Default.AccountBalance,
                                        tintColor = Color(0xFFADC6FF)
                                    ),
                                    CategoryTileData(
                                        name = "Death",
                                        description = "The next great adventure and transition",
                                        imageUrl = "https://images.unsplash.com/photo-1509114397022-ed747cca3f65?q=80&w=600",
                                        icon = Icons.Default.RemoveCircle,
                                        tintColor = Color(0xFFADC6FF)
                                    ),
                                    CategoryTileData(
                                        name = "Poetry",
                                        description = "Rhythms, lines, and feelings of the heart",
                                        imageUrl = "https://images.unsplash.com/photo-1473186578172-c141e6798cf4?q=80&w=600",
                                        icon = Icons.Default.Brush,
                                        tintColor = Color(0xFFFFB2C5)
                                    ),
                                    CategoryTileData(
                                        name = "Optimism",
                                        description = "Gutter-born gazing toward bright stars",
                                        imageUrl = "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?q=80&w=600",
                                        icon = Icons.Default.Star,
                                        tintColor = Color(0xFFFFDB9C)
                                    ),
                                    CategoryTileData(
                                        name = "Hope",
                                        description = "Quiet tomorrow with no mistakes in yet",
                                        imageUrl = "https://images.unsplash.com/photo-1488330890490-c291ec66277b?q=80&w=600",
                                        icon = Icons.Default.BrightnessLow,
                                        tintColor = Color(0xFFFFF7EB)
                                    ),
                                    CategoryTileData(
                                        name = "Friendship",
                                        description = "Loyal companions and chocolate moments",
                                        imageUrl = "https://images.unsplash.com/photo-1461532242715-57f47ee568ad?q=80&w=600",
                                        icon = Icons.Default.Group,
                                        tintColor = Color(0xFFADC6FF)
                                    ),
                                    CategoryTileData(
                                        name = "Education",
                                        description = "Continuous school versus self learning",
                                        imageUrl = "https://images.unsplash.com/photo-1497633762265-9d179a990aa6?q=80&w=600",
                                        icon = Icons.Default.School,
                                        tintColor = Color(0xFFFFDB9C)
                                    ),
                                    CategoryTileData(
                                        name = "Music",
                                        description = "When it hits you, you feel no pain",
                                        imageUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=600",
                                        icon = Icons.Default.MusicNote,
                                        tintColor = Color(0xFFA0D2AD)
                                    ),
                                    CategoryTileData(
                                        name = "Women",
                                        description = "Seldom well-behaved historic giants",
                                        imageUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=600",
                                        icon = Icons.Default.Face,
                                        tintColor = Color(0xFFFFB2C5),
                                        isFullWidth = true
                                    )
                                )
                            }

                            // Render Inspirational (First large card)
                            val inspirational = bentoCategories[0]
                            CategoryBentoCard(
                                name = inspirational.name,
                                imageUrl = inspirational.imageUrl,
                                icon = inspirational.icon,
                                tintColor = inspirational.tintColor,
                                height = 180.dp,
                                onClick = { selectCategoryWithHaptic(inspirational.name) }
                            )

                            // Render rows of 2 for middle categories (1 to 18)
                            val midCategories = bentoCategories.subList(1, 19)
                            midCategories.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    pair.forEach { cat ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            CategoryBentoCard(
                                                name = cat.name,
                                                imageUrl = cat.imageUrl,
                                                icon = cat.icon,
                                                tintColor = cat.tintColor,
                                                height = 160.dp,
                                                onClick = { selectCategoryWithHaptic(cat.name) }
                                            )
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }

                            // Render Women (Last large card)
                            val women = bentoCategories[19]
                            CategoryBentoCard(
                                name = women.name,
                                imageUrl = women.imageUrl,
                                icon = women.icon,
                                tintColor = women.tintColor,
                                height = 180.dp,
                                onClick = { selectCategoryWithHaptic(women.name) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Category Filter Picker Dialog (allows selecting multiple categories at once)
    if (showCategoryFilterDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryFilterDialog = false },
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(28.dp)),
            title = {
                Text(
                    text = "Select Categories",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Filter search findings by multiple selected categories below:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
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
                            "Optimism",
                            "Hope",
                            "Friendship",
                            "Education",
                            "Music",
                            "Women"
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
                }
            },
            confirmButton = {
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
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.clearCategorySelection()
                        showCategoryFilterDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Clear All")
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color(0xFF111015),
            tonalElevation = 8.dp
        )
    }
}

@Composable
fun CategoryBrowseViewInPlace(
    category: String?,
    searchQuery: String,
    quotes: List<QuoteEntity>,
    onBack: () -> Unit,
    onToggleFavorite: (QuoteEntity) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val onBackWithHaptic = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onBack()
    }

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
            SuggestionChip(
                onClick = onBackWithHaptic,
                label = {
                    Text(
                        text = if (category != null) "$category ✕" else "Search results ✕",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            )

            TextButton(onClick = onBackWithHaptic) {
                Text(
                    text = "Clear Filter",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        if (quotes.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SearchOff,
                    contentDescription = "Empty",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No wisdom found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Try adjusting tags or search keywords.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
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
fun CollectionHeroItem(
    title: String,
    desc: String,
    badgeText: String,
    image: String,
    badgeBgColor: Color,
    badgeTextColor: Color,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier
            .width(260.dp)
            .height(200.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = image,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .background(badgeBgColor, shape = RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = badgeTextColor
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
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

@Composable
fun SimulatedExpressiveWidget(text: String, author: String) {
    Box(
        modifier = Modifier
            .width(280.dp)
            .height(140.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF594983), Color(0xFF37265E))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        // Star decoration top right
        Text(
            text = "✦",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFFD0BCFF).copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.TopEnd)
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Text(
                text = "EXPRESSIVE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = Color(0xFFD0BCFF)
            )

            // Quote text
            Text(
                text = "\"$text\"",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = SerifFontFamily,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 18.sp
                ),
                color = Color(0xFFE9DDFF),
                maxLines = 3,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Author
            Text(
                text = "— ${author.uppercase()}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFA0D2AD)
            )
        }
    }
}

data class CategoryTileData(
    val name: String,
    val description: String,
    val imageUrl: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tintColor: Color,
    val isFullWidth: Boolean = false
)

@Composable
fun CategoryBentoCard(
    name: String,
    imageUrl: String,
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
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // First show a beautiful default background as a placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                tintColor.copy(alpha = 0.35f),
                                Color(0xFF1B1A21)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tintColor.copy(alpha = 0.25f),
                    modifier = Modifier.size(56.dp)
                )
            }

            coil.compose.SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = tintColor,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        tintColor.copy(alpha = 0.45f),
                                        Color(0xFF1B1A21)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tintColor.copy(alpha = 0.55f),
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.70f)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = name,
                    style = if (height >= 180.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

