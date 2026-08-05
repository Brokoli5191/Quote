package app.brokoli5191.quote.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteSubmissionStatus
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(viewModel: QuoteViewModel) {
    val favorites by viewModel.favorites.collectAsState()
    val userAdded by viewModel.userAdded.collectAsState()
    val submittingQuoteIds by viewModel.submittingQuoteIds.collectAsState()
    
    var activeSubTab by remember { mutableStateOf("Favorites") } // "Favorites" or "My Quotes"
    var showAddDialog by remember { mutableStateOf(false) }
    var quoteToSubmit by remember { mutableStateOf<QuoteEntity?>(null) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val fabInteractionSource = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) { viewModel.refreshSubmissionStatuses() }

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
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 0.dp),
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
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 112.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(activeList, key = { it.id }) { quote ->
                            PremiumCollectionQuoteCard(
                                quote = quote,
                                onToggleFavorite = { viewModel.toggleFavorite(quote) },
                                onDelete = { viewModel.deleteQuote(quote.id) },
                                isSubmitting = quote.id in submittingQuoteIds,
                                onSubmit = { quoteToSubmit = quote },
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
                .padding(bottom = 104.dp, end = 16.dp)
                .size(56.dp),
            shape = rememberExpressiveShape(fabInteractionSource, 16.dp, 8.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            interactionSource = fabInteractionSource
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

        quoteToSubmit?.let { quote ->
            Dialog(
                onDismissRequest = { quoteToSubmit = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .widthIn(max = 420.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    tonalElevation = 0.dp,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.padding(10.dp).size(22.dp)
                            )
                        }
                        Text(
                            "Submit for review?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "This quote, its author, category, and tags will be sent to Quote for review. " +
                                "If approved, it may be included in the public quote collection.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://quote.cowsay.win/privacy"))
                                )
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Privacy & submission policy")
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { quoteToSubmit = null }) {
                                Text("Cancel")
                            }
                            TextButton(
                                onClick = {
                                    quoteToSubmit = null
                                    viewModel.submitQuoteForReview(
                                        quote = quote,
                                        onSuccess = {
                                            Toast.makeText(context, "Submitted for review", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { message ->
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            ) {
                                Text("Submit")
                            }
                        }
                    }
                }
            }
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
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberExpressiveShape(interactionSource, 12.dp, 5.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun PremiumCollectionQuoteCard(
    quote: QuoteEntity,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
    onShare: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // Tick every 30s so relative timestamps stay fresh
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick = System.currentTimeMillis()
        }
    }
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
    } else if (quote.category == "Life") {
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

                    Text(
                        text = formatSavedDate(quote.savedDate, tick),
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
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
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
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.wrapContentWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
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

                        if (quote.isUserAdded) {
                            ExpressiveIconButton(onClick = {
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
                            ExpressiveIconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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

                if (quote.isUserAdded) {
                    when (quote.submissionStatus) {
                        QuoteSubmissionStatus.PENDING -> QuoteReviewStatus(
                            label = "Pending review",
                            icon = Icons.Default.Schedule,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        QuoteSubmissionStatus.APPROVED -> QuoteReviewStatus(
                            label = "Approved for community",
                            icon = Icons.Default.CheckCircle,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        QuoteSubmissionStatus.REJECTED -> QuoteReviewStatus(
                            label = "Not approved",
                            icon = Icons.Default.Cancel,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                        else -> {
                            FilledTonalButton(
                                onClick = onSubmit,
                                enabled = !isSubmitting,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(if (isSubmitting) "Submitting..." else "Submit for review")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuoteReviewStatus(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        color = containerColor.copy(alpha = 0.55f),
        contentColor = contentColor,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun formatSavedDate(savedDate: String?, now: Long = System.currentTimeMillis()): String {
    if (savedDate == null) return ""
    val millis = savedDate.toLongOrNull()
        ?: return savedDate  // backwards compat: old "dd MMM yyyy" strings shown as-is
    val diff = now - millis
    return when {
        diff < 60_000L -> "just now"
        diff < 3_600_000L -> "${diff / 60_000} min ago"
        diff < 86_400_000L -> "${diff / 3_600_000} h ago"
        diff < 172_800_000L -> "yesterday"
        diff < 2_592_000_000L -> "${diff / 86_400_000} days ago"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
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
    var category by remember { mutableStateOf("Inspirational") }
    var tags by remember { mutableStateOf("") }

    val categories = listOf(
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
    val haptic = LocalHapticFeedback.current

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 8.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .nestedScroll(nestedScrollConnection)
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "New Custom Quote",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                ExpressiveIconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Clean Quote Text
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Quote / Affirmation") },
                placeholder = { Text("Write inspiring words...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors()
            )

            // Author Input
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text("Author") },
                placeholder = { Text("e.g. Marcus Aurelius, Self") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors()
            )

            // Tags Input
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags") },
                placeholder = { Text("e.g. wisdom, life (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors()
            )

            // Category Selection (Matches Library's grid style!)
            Text(
                text = "Select Category",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.chunked(2).forEach { rowCategoryList ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowCategoryList.forEach { categoryName ->
                            val isSelected = (category == categoryName)
                            val interactionSource = remember(categoryName) { MutableInteractionSource() }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    category = categoryName
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

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveTextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Cancel")
                }

                ExpressiveButton(
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
                    restingCorner = 12.dp
                ) {
                    Text("Add Quote")
                }
            }
        }
    }
}
