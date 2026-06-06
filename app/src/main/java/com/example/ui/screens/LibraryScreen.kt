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
    
    val scrollState = rememberScrollState()
    var showAllCollectionsDialog by remember { mutableStateOf(false) }

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
                    .graphicsLayer { alpha = headerAlpha.value }
                    .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 0.dp),
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

                    // Slider settings icon (Tune) relocated to the top right of the Library screen
                    IconButton(
                        onClick = {
                            val randomCategory = listOf("Stoicism", "Resilience", "Joy", "Focus", "Love").random()
                            selectCategoryWithHaptic(randomCategory)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Random Shuffle",
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
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "LibraryInPlaceTransition",
                modifier = Modifier.fillMaxSize()
            ) { isBrowsing ->
                if (isBrowsing) {
                    CategoryBrowseViewInPlace(
                        category = selectedCategory,
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
                            // 1. Stoicism (Large Hero Card)
                            Card(
                                onClick = { selectCategoryWithHaptic("Stoicism") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = "https://images.unsplash.com/photo-1518156677180-95a2893f3e9f?q=80&w=600",
                                        contentDescription = "Stoicism",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        Color.Black.copy(alpha = 0.3f),
                                                        Color.Black.copy(alpha = 0.85f)
                                                    )
                                                )
                                            )
                                    )

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(20.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Balance,
                                                contentDescription = "Philosophy",
                                                tint = Color(0xFFFFDB9C),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "PHILOSOPHY",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFFFDB9C),
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Stoicism",
                                                    style = MaterialTheme.typography.headlineLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "Daily discipline, logic, and self-mastery",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.8f)
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Default.ArrowForward,
                                                    contentDescription = "Open",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. Resilience and Joy Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Card(
                                    onClick = { selectCategoryWithHaptic("Resilience") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(160.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = "https://images.unsplash.com/photo-1519817650390-64a93db51149?q=80&w=600",
                                            contentDescription = "Resilience",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                                    )
                                                )
                                        )

                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ElectricBolt,
                                                contentDescription = "Resilience",
                                                tint = Color(0xFFA0D2AD),
                                                modifier = Modifier.size(24.dp)
                                            )

                                            Column {
                                                Text(
                                                    text = "Resilience",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Stand firm",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }
                                }

                                Card(
                                    onClick = { selectCategoryWithHaptic("Joy") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(160.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = "https://images.unsplash.com/photo-1490730141103-6cac27aaab94?q=80&w=600",
                                            contentDescription = "Joy",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                                    )
                                                )
                                        )

                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.WbSunny,
                                                contentDescription = "Joy",
                                                tint = Color(0xFFFFDB9C),
                                                modifier = Modifier.size(24.dp)
                                            )

                                            Column {
                                                Text(
                                                    text = "Joy",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Light within",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Focus and Love Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Card(
                                    onClick = { selectCategoryWithHaptic("Focus") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(160.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = "https://images.unsplash.com/photo-1456406644174-8dba4c7f27f2?q=80&w=600",
                                            contentDescription = "Focus",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                                    )
                                                )
                                        )

                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ShortText,
                                                contentDescription = "Focus",
                                                tint = Color(0xFFADC6FF),
                                                modifier = Modifier.size(24.dp)
                                            )

                                            Column {
                                                Text(
                                                    text = "Focus",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Pure mind",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }
                                }

                                Card(
                                    onClick = { selectCategoryWithHaptic("Love") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(160.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = "https://images.unsplash.com/photo-1516589178581-6cd7833ae3b2?q=80&w=600",
                                            contentDescription = "Love",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                                    )
                                                )
                                        )

                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = "Love",
                                                tint = Color(0xFFFFB2C5),
                                                modifier = Modifier.size(24.dp)
                                            )

                                            Column {
                                                Text(
                                                    text = "Love",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Kindness",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(30.dp))

                        // Curated Collections
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = "Curated Collections",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "View All",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showAllCollectionsDialog = true
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CollectionHeroItem(
                                    title = "Morning Stillness",
                                    desc = "Quiet dawn meditations",
                                    badgeText = "Staff Pick",
                                    image = "https://images.unsplash.com/photo-1506126613408-eca07ce68773?q=80&w=600",
                                    badgeBgColor = MaterialTheme.colorScheme.secondary,
                                    badgeTextColor = MaterialTheme.colorScheme.onSecondary,
                                    onClick = { selectCategoryWithHaptic("Love") }
                                )

                                CollectionHeroItem(
                                    title = "Digital Detox",
                                    desc = "Disconnect to find clarity",
                                    badgeText = "Trending",
                                    image = "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?q=80&w=600",
                                    badgeBgColor = MaterialTheme.colorScheme.tertiary,
                                    badgeTextColor = MaterialTheme.colorScheme.onTertiary,
                                    onClick = { selectCategoryWithHaptic("Resilience") }
                                )

                                CollectionHeroItem(
                                    title = "Quiet Confidence",
                                    desc = "Steadfast internal growth",
                                    badgeText = "New",
                                    image = "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?q=80&w=600",
                                    badgeBgColor = MaterialTheme.colorScheme.primary,
                                    badgeTextColor = MaterialTheme.colorScheme.onPrimary,
                                    onClick = { selectCategoryWithHaptic("Focus") }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Curated popup dialog when "View All" is selected
    if (showAllCollectionsDialog) {
        AlertDialog(
            onDismissRequest = { showAllCollectionsDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAllCollectionsDialog = false
                }) {
                    Text("Close")
                }
            },
            title = {
                Text(
                    text = "Aura Collections",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        Triple("Morning Stillness", "Graceful sunrise quotes to begin with focus.", "Love"),
                        Triple("Digital Detox", "Escape modern notifications and return to nature.", "Resilience"),
                        Triple("Quiet Confidence", "Self affirmation cards from Marcus Aurelius.", "Focus"),
                        Triple("Golden Hour Reflections", "Celebrate simple existence with high joy.", "Joy")
                    ).forEach { (title, desc, cat) ->
                        Card(
                            onClick = {
                                selectCategoryWithHaptic(cat)
                                showAllCollectionsDialog = false
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
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
                            tint = if (quote.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer(scaleX = heartScale, scaleY = heartScale)
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

