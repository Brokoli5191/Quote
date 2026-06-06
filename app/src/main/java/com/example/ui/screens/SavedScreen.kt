package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.QuoteEntity
import com.example.ui.AuraViewModel
import com.example.ui.theme.SerifFontFamily
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(viewModel: AuraViewModel) {
    val favorites by viewModel.favorites.collectAsState()
    val userAdded by viewModel.userAdded.collectAsState()
    
    var activeSubTab by remember { mutableStateOf("Favorites") } // "Favorites" or "My Quotes"
    var showAddDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Screen titles shown with comfortable top margin
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 0.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Your Collection",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sub Tab switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TabSelectorButton(
                    label = "Favorites (${favorites.size})",
                    isSelected = activeSubTab == "Favorites",
                    modifier = Modifier.weight(1f),
                    onClick = { activeSubTab = "Favorites" }
                )

                TabSelectorButton(
                    label = "My Quotes (${userAdded.size})",
                    isSelected = activeSubTab == "My Quotes",
                    modifier = Modifier.weight(1f),
                    onClick = { activeSubTab = "My Quotes" }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lists representation with smooth swipe animation
            AnimatedContent(
                targetState = activeSubTab,
                transitionSpec = {
                    if (targetState == "My Quotes") {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = spring(stiffness = 300f, dampingRatio = 0.85f)
                        ) + fadeIn() togetherWith
                        slideOutHorizontally(
                            targetOffsetX = { -it },
                            animationSpec = spring(stiffness = 300f, dampingRatio = 0.85f)
                        ) + fadeOut()
                    } else {
                        slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = spring(stiffness = 300f, dampingRatio = 0.85f)
                        ) + fadeIn() togetherWith
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = spring(stiffness = 300f, dampingRatio = 0.85f)
                        ) + fadeOut()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = "SavedQuotesTabsTransition"
            ) { subTab ->
                val activeList = if (subTab == "Favorites") favorites else userAdded

                if (activeList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = if (subTab == "Favorites") Icons.Default.FavoriteBorder else Icons.Default.NoteAlt,
                                contentDescription = "Empty",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = if (subTab == "Favorites") "No favorites yet" else "No custom quotes yet",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (subTab == "Favorites")
                                    "Explore the daily insights or categories library and heart your favorite affirmations to build your archive here."
                                    else "Click the plus button below to register your own wisdom cards in quote.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(activeList, key = { it.id }) { quote ->
                            PremiumCollectionQuoteCard(
                                quote = quote,
                                onToggleFavorite = { viewModel.toggleFavorite(quote) },
                                onDelete = { viewModel.deleteQuote(quote.id) },
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

        // Floating creator button
        FloatingActionButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showAddDialog = true
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
                imageVector = Icons.Default.Add,
                contentDescription = "Create Quote",
                modifier = Modifier.size(24.dp)
            )
        }

        // Add Custom Quote Dialog Form
        if (showAddDialog) {
            AddCustomQuoteDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { txt, auth, cat, tags ->
                    viewModel.addUserQuote(txt, auth, cat, tags)
                    showAddDialog = false
                    activeSubTab = "My Quotes" // switch to showing user added list
                }
            )
        }
    }
}

@Composable
fun TabSelectorButton(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
            contentColor = if (isSelected)
                MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
        modifier = modifier
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun PremiumCollectionQuoteCard(
    quote: QuoteEntity,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    // Elegant fly-in enter animation from the side
    val offsetX = remember { androidx.compose.animation.core.Animatable(80f) }
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(Unit) {
        offsetX.animateTo(
            targetValue = 0f,
            animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f)
        )
    }
    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 250)
        )
    }

    // Generate styled variations based on quote categories or settings
    val isLightTheme = MaterialTheme.colorScheme.background.red > 0.5f
    val bgBrush = if (isLightTheme) {
        when (quote.id % 4) {
            0 -> Brush.linearGradient(listOf(Color(0xFFF6F2FA), Color(0xFFECE6F0)))
            1 -> Brush.linearGradient(listOf(Color(0xFFFFF7EB), Color(0xFFFFF1D8)))
            2 -> Brush.linearGradient(listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9).copy(alpha = 0.4f)))
            else -> Brush.linearGradient(listOf(Color(0xFFE8EAF6), Color(0xFFD0D4F5).copy(alpha = 0.5f)))
        }
    } else {
        when (quote.id % 4) {
            0 -> Brush.linearGradient(listOf(Color(0xFF2B292D), Color(0xFF1C1B1F)))
            1 -> Brush.linearGradient(listOf(Color(0xFF353438), Color(0xFF201F23)))
            2 -> Brush.linearGradient(listOf(Color(0xFF2E5B3F).copy(alpha = 0.15f), Color(0xFF141317)))
            else -> Brush.linearGradient(listOf(Color(0xFF594983).copy(alpha = 0.1f), Color(0xFF1C1B1F)))
        }
    }

    // Border color based on categories/styles
    val strokeColor = if (isLightTheme) {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    } else if (quote.category == "Resilience") {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    } else {
        Color.White.copy(alpha = 0.04f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = offsetX.value
                this.alpha = alpha.value
            }
            .border(1.dp, strokeColor, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(bgBrush)
                .padding(20.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // If there's an active tags record, display tags and metadata
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val tagsSp = quote.tags.split(",").filter { it.isNotBlank() }
                        tagsSp.take(2).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(100.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Display date or status indicator (e.g. "Saved 2 days ago" mockup)
                    Text(
                        text = quote.savedDate ?: "quote Collection",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                // Quote Content Text in Serif Medium italic
                Text(
                    text = "\"${quote.text}\"",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = SerifFontFamily,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 28.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Author Row with Monogram and action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Initials Monogram Bubble (e.g. "SJ" for Steve Jobs, "TR" etc. in mockup!)
                        val initials = getInitials(quote.author)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Text(
                            text = quote.author,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
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

                        if (quote.isUserAdded) {
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDelete()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                        } else {
                            var isLikedAnim by remember { mutableStateOf(false) }
                            val heartScale by androidx.compose.animation.core.animateFloatAsState(
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
    }
}

private fun getInitials(name: String): String {
    val clean = name.trim().replace(Regex("[^a-zA-Z\\s]"), "")
    val parts = clean.split(" ").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "U"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomQuoteDialog(
    onDismiss: () -> Unit,
    onAdd: (text: String, author: String, category: String, tags: String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Love") } // Stoicism, Resilience, Joy, Focus, Love, Custom
    var tags by remember { mutableStateOf("") }

    val categories = listOf("Stoicism", "Resilience", "Joy", "Focus", "Love")
    var expandedDropdown by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Custom Affirmation",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Quote text input
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Affirmation Wisdom Text") },
                    placeholder = { Text("What inspiring words would you like to add?") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // Author input
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author / Sage Name") },
                    placeholder = { Text("Who said this? (e.g. Ancient Proverb, Self)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // Category selector dropdown list representing M3 selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        label = { Text("Card Category Theme") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                expandedDropdown = !expandedDropdown
                            }) {
                                Icon(
                                    imageVector = if (expandedDropdown) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown"
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    DropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat, color = Color.White) },
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    category = cat
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }

                // Tags CSV input
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Hashtags / Labels (comma-separated)") },
                    placeholder = { Text("e.g. Identity, SelfLove, Wisdom") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (text.isNotBlank()) {
                        onAdd(text, author, category, tags)
                    }
                },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add to Collection")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
