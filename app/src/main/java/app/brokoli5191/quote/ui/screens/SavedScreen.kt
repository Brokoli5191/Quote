package app.brokoli5191.quote.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brokoli5191.quote.data.CategoryMapper
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.data.QuoteSubmissionStatus
import app.brokoli5191.quote.data.SubmissionError
import app.brokoli5191.quote.data.alternateLanguage
import app.brokoli5191.quote.data.alternateText
import app.brokoli5191.quote.data.hasUnknownAuthor
import app.brokoli5191.quote.ui.QuoteViewModel
import app.brokoli5191.quote.ui.LocalAppLanguage
import app.brokoli5191.quote.ui.categoryText
import app.brokoli5191.quote.ui.formattedQuote
import app.brokoli5191.quote.ui.localizeUi
import app.brokoli5191.quote.ui.uiText
import app.brokoli5191.quote.ui.components.AuthorAvatar
import app.brokoli5191.quote.ui.components.ExpressiveButton
import app.brokoli5191.quote.ui.components.ExpressiveIconButton
import app.brokoli5191.quote.ui.components.TranslationToggle
import app.brokoli5191.quote.ui.components.rememberExpressiveShape
import app.brokoli5191.quote.ui.strings.submissionErrorText
import app.brokoli5191.quote.ui.theme.SerifFontFamily
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

// Same limits as the submission backend (validateSubmission).
private const val MIN_SUBMISSION_TEXT = 3
private const val MAX_SUBMISSION_TEXT = 500
private const val MAX_SUBMISSION_AUTHOR = 100
private const val MAX_SUBMISSION_TAGS = 8
private const val MAX_SUBMISSION_TAG_LENGTH = 30

private data class EditRequest(val quote: QuoteEntity, val resubmit: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(viewModel: QuoteViewModel) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val userAdded by viewModel.userAdded.collectAsStateWithLifecycle()
    val submittingQuoteIds by viewModel.submittingQuoteIds.collectAsStateWithLifecycle()
    val activeSubTab by viewModel.savedSubTab.collectAsStateWithLifecycle()
    val lowPerformanceMode by viewModel.lowPerformanceMode.collectAsStateWithLifecycle()
    val authorImagesEnabled by viewModel.authorImagesEnabled.collectAsStateWithLifecycle()

    var quoteToSubmit by remember { mutableStateOf<QuoteEntity?>(null) }
    var quoteToDelete by remember { mutableStateOf<QuoteEntity?>(null) }
    var quoteToWithdraw by remember { mutableStateOf<QuoteEntity?>(null) }
    var editRequest by remember { mutableStateOf<EditRequest?>(null) }

    val context = LocalContext.current
    val shareTitle = uiText("Share Wisdom")
    val uiLanguage = LocalAppLanguage.current
    val haptic = LocalHapticFeedback.current
    val fabInteractionSource = remember { MutableInteractionSource() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = uiText("Quote deleted")
    val undoLabel = uiText("Undo")
    var timestampTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            timestampTick = System.currentTimeMillis()
        }
    }

    // The ViewModel re-reads the stored row by id, so Undo restores the raw entity (including its
    // submission state), never this localized display copy.
    val deleteWithUndo: (QuoteEntity) -> Unit = { quote ->
        viewModel.deleteQuote(quote.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreDeletedQuote(quote)
            }
        }
    }

    val requestSubmit: (QuoteEntity) -> Unit = { quote ->
        val problem = quote.submissionProblem()
        if (problem != null) {
            Toast.makeText(context, localizeUi(problem, uiLanguage), Toast.LENGTH_LONG).show()
        } else {
            quoteToSubmit = quote
        }
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
            // Screen titles shown with comfortable top margin
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 0.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = uiText("Your Collection"),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sub Tab switcher
            SavedTabSwitcher(
                activeTab = activeSubTab,
                favoritesCount = favorites.size,
                myQuotesCount = userAdded.size,
                lowPerformanceMode = lowPerformanceMode,
                onSelect = viewModel::selectSavedSubTab
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Lists representation with smooth swipe animation
            AnimatedContent(
                targetState = activeSubTab,
                transitionSpec = {
                    if (lowPerformanceMode) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else if (targetState == "My Quotes") {
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
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = if (subTab == "Favorites") uiText("No favorites yet") else uiText("No custom quotes yet"),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (subTab == "Favorites") {
                                    uiText("Tap the heart on a quote to keep it here.")
                                } else {
                                    uiText("Create a personal quote from the add button.")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // The bottom padding clears the bottom bar and the FAB above it (104dp + 56dp), so the
                    // last card's buttons can scroll out from under the FAB.
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 176.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(activeList, key = { it.id }) { quote ->
                            // Own quotes are stored locally only, so their author is never looked up
                            // online; bundled names still resolve from the offline map elsewhere.
                            val lookUpAuthor = authorImagesEnabled && !quote.isUserAdded && !quote.hasUnknownAuthor
                            val authorInfoFlow = remember(quote.author, lookUpAuthor) {
                                if (lookUpAuthor) viewModel.authorInfo(quote.author) else null
                            }
                            val authorImageUrl = authorInfoFlow?.let { it.collectAsStateWithLifecycle().value }?.imageUrl
                            val isSubmitting = quote.id in submittingQuoteIds

                            PremiumCollectionQuoteCard(
                                quote = quote,
                                nowMillis = timestampTick,
                                authorImageUrl = authorImageUrl,
                                onToggleFavorite = { viewModel.toggleFavorite(quote) },
                                onDelete = {
                                    // Pending quotes are withdrawn on delete, approved ones stay published:
                                    // both get a dialog that says so.
                                    val needsConfirmation = quote.isUserAdded && (
                                        quote.submissionStatus == QuoteSubmissionStatus.PENDING ||
                                            quote.submissionStatus == QuoteSubmissionStatus.APPROVED
                                        )
                                    when {
                                        isSubmitting -> Unit
                                        needsConfirmation -> quoteToDelete = quote
                                        else -> deleteWithUndo(quote)
                                    }
                                },
                                isSubmitting = isSubmitting,
                                lowPerformanceMode = lowPerformanceMode,
                                onSubmit = { requestSubmit(quote) },
                                onEdit = { resubmit -> editRequest = EditRequest(quote, resubmit) },
                                onWithdraw = { quoteToWithdraw = quote },
                                onShare = { shareText ->
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    context.startActivity(Intent.createChooser(intent, shareTitle))
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
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.openNewQuoteScreen()
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
                contentDescription = uiText("Create Quote"),
                modifier = Modifier.size(24.dp)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 176.dp)
        )

        quoteToSubmit?.let { quote ->
            val resubmit = quote.submissionStatus == QuoteSubmissionStatus.REJECTED ||
                quote.submissionStatus == QuoteSubmissionStatus.REMOVED
            SubmissionConsentDialog(
                resubmit = resubmit,
                onDismiss = { quoteToSubmit = null },
                onOpenPolicy = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://quote.cowsay.win/privacy"))
                    )
                },
                onConfirm = {
                    quoteToSubmit = null
                    val onSuccess = {
                        Toast.makeText(context, localizeUi("Submitted for review", uiLanguage), Toast.LENGTH_SHORT).show()
                    }
                    val onError = { code: String ->
                        Toast.makeText(context, submitErrorMessage(code, uiLanguage), Toast.LENGTH_LONG).show()
                    }
                    if (resubmit) {
                        viewModel.resubmitQuote(quote, onSuccess, onError)
                    } else {
                        viewModel.submitQuoteForReview(quote, onSuccess, onError)
                    }
                }
            )
        }

        quoteToDelete?.let { quote ->
            val pending = quote.submissionStatus == QuoteSubmissionStatus.PENDING
            AlertDialog(
                onDismissRequest = { quoteToDelete = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                title = { Text(uiText(if (pending) "Delete and withdraw?" else "Delete quote?")) },
                text = {
                    Text(
                        uiText(
                            if (pending) {
                                "This quote is still waiting for review. Deleting it also withdraws the submission, so it won't be published. Without an internet connection, the submission can't be withdrawn and stays in review."
                            } else {
                                "This only deletes your own copy. The published quote stays in the community collection."
                            }
                        )
                    )
                },
                dismissButton = {
                    TextButton(onClick = { quoteToDelete = null }) {
                        Text(uiText("Cancel"))
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        quoteToDelete = null
                        deleteWithUndo(quote)
                    }) {
                        Text(uiText("Delete"), color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }

        quoteToWithdraw?.let { quote ->
            AlertDialog(
                onDismissRequest = { quoteToWithdraw = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                title = { Text(uiText("Withdraw submission?")) },
                text = {
                    Text(uiText("The quote leaves the review queue and stays in My Quotes. You can submit it again later."))
                },
                dismissButton = {
                    TextButton(onClick = { quoteToWithdraw = null }) {
                        Text(uiText("Cancel"))
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        quoteToWithdraw = null
                        viewModel.withdrawSubmission(
                            quote = quote,
                            onSuccess = {
                                Toast.makeText(context, localizeUi("Submission withdrawn", uiLanguage), Toast.LENGTH_SHORT).show()
                            },
                            onError = { code ->
                                Toast.makeText(context, submissionErrorText(code, uiLanguage), Toast.LENGTH_LONG).show()
                            }
                        )
                    }) {
                        Text(uiText("Withdraw"))
                    }
                }
            )
        }

        editRequest?.let { request ->
            EditQuoteSheet(
                quote = request.quote,
                resubmit = request.resubmit,
                onDismiss = { editRequest = null },
                onSave = { text, author, category, tags ->
                    viewModel.updateUserQuote(request.quote, text, author, category, tags) { saved ->
                        if (!saved) {
                            Toast.makeText(context, localizeUi("Changes could not be saved.", uiLanguage), Toast.LENGTH_LONG).show()
                        } else if (request.resubmit) {
                            // The edit cleared the old review; the consent dialog sends the stored row.
                            quoteToSubmit = request.quote.copy(submissionStatus = QuoteSubmissionStatus.NOT_SUBMITTED)
                        }
                    }
                }
            )
        }
    }
}

private fun submitErrorMessage(code: String, language: String): String =
    if (code == SubmissionError.ALREADY_REVIEWED) {
        // On submit this means the server already decided on exactly this text.
        localizeUi("This exact quote was already reviewed. Edit it before submitting it again.", language)
    } else {
        submissionErrorText(code, language)
    }

@Composable
private fun SubmissionConsentDialog(
    resubmit: Boolean,
    onDismiss: () -> Unit,
    onOpenPolicy: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
            )
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
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
                    uiText(if (resubmit) "Submit again?" else "Submit for review?"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    uiText("A moderator reviews the quote. If approved, it is published in the community collection under the author's name and may be slightly corrected."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        uiText("What is sent"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    listOf(
                        "Quote text",
                        "Author",
                        "Category",
                        "Tags",
                        "Language of the quote",
                        "App version",
                        "A random installation ID (used for the review status and spam limits)"
                    ).forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                uiText(item),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Text(
                    uiText("Your IP address is processed for spam protection. Its hash is deleted after 30 days. Nothing else from your device is sent."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onOpenPolicy,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(uiText("Privacy & submission policy"), modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(uiText("Cancel"), textAlign = TextAlign.Center, maxLines = 2)
                    }
                    TextButton(onClick = onConfirm) {
                        Text(
                            uiText(if (resubmit) "Submit again" else "Submit"),
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedTabSwitcher(
    activeTab: String,
    favoritesCount: Int,
    myQuotesCount: Int,
    lowPerformanceMode: Boolean,
    onSelect: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp)
            )
            .padding(4.dp)
    ) {
        val segmentWidth = maxWidth / 2
        val indicatorOffset by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (activeTab == "Favorites") 0.dp else segmentWidth,
            animationSpec = if (lowPerformanceMode) snap() else spring(dampingRatio = 0.82f, stiffness = 420f),
            label = "SavedTabIndicator"
        )

        // Matches the row height, which grows when a label needs two lines at large font scales.
        Box(
            modifier = Modifier
                .matchParentSize()
                .wrapContentWidth(Alignment.Start)
                .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                .width(segmentWidth)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
        )

        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            listOf(
                "Favorites" to "${uiText("Favorites")} ($favoritesCount)",
                "My Quotes" to "${uiText("My Quotes")} ($myQuotesCount)"
            ).forEach { (tab, label) ->
                val selected = activeTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            interactionSource = remember(tab) { MutableInteractionSource() },
                            indication = null
                        ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(tab)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PremiumCollectionQuoteCard(
    quote: QuoteEntity,
    nowMillis: Long,
    authorImageUrl: String?,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    isSubmitting: Boolean,
    lowPerformanceMode: Boolean,
    onSubmit: () -> Unit,
    onEdit: (resubmit: Boolean) -> Unit,
    onWithdraw: () -> Unit,
    onShare: (String) -> Unit
) {
    val language = LocalAppLanguage.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // Elegant fly-in enter animation from the side
    val offsetX = remember { androidx.compose.animation.core.Animatable(if (lowPerformanceMode) 0f else 80f) }
    val alpha = remember { androidx.compose.animation.core.Animatable(if (lowPerformanceMode) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!lowPerformanceMode) offsetX.animateTo(
            targetValue = 0f,
            animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f)
        )
    }
    LaunchedEffect(Unit) {
        if (!lowPerformanceMode) alpha.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 250)
        )
    }

    val bgBrush = when (quote.id % 4) {
        0 -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainer))
        1 -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f), MaterialTheme.colorScheme.surfaceContainer))
        2 -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.16f), MaterialTheme.colorScheme.surfaceContainerLow))
        else -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f), MaterialTheme.colorScheme.surfaceContainer))
    }
    val strokeColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
    val cardScope = rememberCoroutineScope()
    var copyConfirmed by remember { mutableStateOf(false) }
    var favoritePulse by remember { mutableStateOf(false) }
    val favoriteScale by animateFloatAsState(
        targetValue = if (!lowPerformanceMode && favoritePulse) 1.3f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.52f),
        finishedListener = { favoritePulse = false },
        label = "SavedFavoritePulse"
    )

    val alternateText = remember(quote) { quote.alternateText() }
    val alternateLanguage = remember(quote) { quote.alternateLanguage() }
    // Resets when the app language changes the display text.
    var showTranslation by rememberSaveable(quote.id, quote.text) { mutableStateOf(false) }
    val displayedText = if (showTranslation && alternateText != null) alternateText else quote.text
    // Quotation marks follow the language of the text on screen, not the app language. An own quote
    // without a translation stores the app language it was written in, not its own, so it keeps
    // neutral English marks.
    val displayedLanguage = when {
        alternateLanguage == null && quote.isUserAdded -> QuoteLanguage.ENGLISH
        alternateLanguage == null -> quote.language
        showTranslation -> alternateLanguage
        alternateLanguage == QuoteLanguage.GERMAN -> QuoteLanguage.ENGLISH
        else -> QuoteLanguage.GERMAN
    }
    val unknownAuthor = quote.hasUnknownAuthor
    val authorName = if (unknownAuthor) uiText("Unknown") else quote.author
    // Copy/share keep a blank author blank, like before.
    val shareText = formattedQuote(displayedText, if (quote.author.isBlank()) "" else authorName)

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
                // Tags and saved date; skipped when both are empty so no blank row is left behind.
                // Catalogue tags are English only, so like on Daily they're hidden in German; own tags stay.
                val tagsSp = remember(quote.tags, quote.isAnonymous, quote.isUserAdded, language) {
                    if (quote.isAnonymous || (!quote.isUserAdded && language == QuoteLanguage.GERMAN)) emptyList()
                    else quote.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                }
                val savedDateText = formatSavedDate(quote.savedDate, nowMillis, language)
                if (tagsSp.isNotEmpty() || savedDateText.isNotEmpty()) Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        text = savedDateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                // Quote Content Text in Serif Medium italic
                Text(
                    text = if (displayedLanguage == QuoteLanguage.GERMAN) "„$displayedText“" else "“$displayedText”",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = SerifFontFamily,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 28.sp
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

                // Author with avatar, then the actions. When the name can't fit in two lines beside the
                // three actions (long names, large font scales), the actions move below the name.
                val authorBlock: @Composable (Modifier) -> Unit = { rowModifier ->
                    Row(
                        modifier = rowModifier.padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AuthorAvatar(
                            name = authorName,
                            imageUrl = authorImageUrl,
                            unknown = unknownAuthor,
                            size = 36.dp
                        )

                        Text(
                            text = authorName,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (unknownAuthor) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                val actionButtons: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier.wrapContentWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpressiveIconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("quote", shareText))
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
                            onShare(shareText)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = uiText("Share"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (quote.isUserAdded) {
                            // A running submission or withdrawal must finish before the row can go.
                            ExpressiveIconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDelete()
                                },
                                enabled = !isSubmitting
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = uiText("Delete"),
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = if (isSubmitting) 0.3f else 0.8f)
                                )
                            }
                        } else {
                            ExpressiveIconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                favoritePulse = true
                                onToggleFavorite()
                            }) {
                                Icon(
                                    imageVector = if (quote.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = uiText("Favorite"),
                                    tint = if (quote.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = favoriteScale
                                        scaleY = favoriteScale
                                    }
                                )
                            }
                        }
                    }
                }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val textMeasurer = rememberTextMeasurer()
                    val nameStyle = MaterialTheme.typography.labelLarge
                    // Avatar 36 + gap 10 + end padding 8, three 48dp actions with 4dp gaps.
                    val nameRoom = with(LocalDensity.current) { (maxWidth - 206.dp).roundToPx() }.coerceAtLeast(0)
                    val stackActions = remember(authorName, nameStyle, textMeasurer, nameRoom) {
                        val longestWordWidth = authorName.split(' ').maxOfOrNull { word ->
                            textMeasurer.measure(word, nameStyle, maxLines = 1).size.width
                        } ?: 0
                        // A word wider than the column would break mid-word; a name needing more
                        // than two lines would be cut.
                        longestWordWidth > nameRoom || textMeasurer.measure(
                            authorName,
                            nameStyle,
                            maxLines = 2,
                            constraints = Constraints(maxWidth = nameRoom)
                        ).hasVisualOverflow
                    }
                    if (stackActions) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            authorBlock(Modifier.fillMaxWidth())
                            Box(modifier = Modifier.align(Alignment.End)) { actionButtons() }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            authorBlock(Modifier.weight(1f))
                            actionButtons()
                        }
                    }
                }

                if (quote.isUserAdded) {
                    SubmissionSection(
                        quote = quote,
                        isSubmitting = isSubmitting,
                        onSubmit = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSubmit()
                        },
                        onEdit = { resubmit ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onEdit(resubmit)
                        },
                        onWithdraw = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onWithdraw()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SubmissionSection(
    quote: QuoteEntity,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
    onEdit: (resubmit: Boolean) -> Unit,
    onWithdraw: () -> Unit
) {
    val problem = remember(quote) { quote.submissionProblem() }
    val reviewNote = quote.reviewNote?.trim()?.takeIf { it.isNotEmpty() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (quote.submissionStatus) {
            QuoteSubmissionStatus.PENDING -> QuoteReviewStatus(
                label = uiText("Pending review"),
                icon = Icons.Default.Schedule,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                } else {
                    TextButton(
                        onClick = onWithdraw,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        Text(uiText("Withdraw"), textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }

            QuoteSubmissionStatus.APPROVED -> QuoteReviewStatus(
                label = uiText("Approved for community"),
                icon = Icons.Default.CheckCircle,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )

            QuoteSubmissionStatus.REJECTED -> {
                QuoteReviewStatus(
                    label = uiText("Not approved"),
                    icon = Icons.Default.Cancel,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
                if (reviewNote != null) ReviewNote(reviewNote)
                // The server keeps its decision for this exact text, so a rejected quote is revised first.
                SubmissionActions(
                    label = uiText("Edit and resubmit"),
                    icon = Icons.Default.Edit,
                    isSubmitting = isSubmitting,
                    submitEnabled = true,
                    showEditButton = false,
                    onSubmit = { onEdit(true) },
                    onEdit = {}
                )
            }

            QuoteSubmissionStatus.REMOVED -> {
                QuoteReviewStatus(
                    label = uiText("No longer published"),
                    icon = Icons.Default.CloudOff,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    uiText("This quote was removed from the community collection. To submit it again, change its text or author."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (reviewNote != null) ReviewNote(reviewNote)
                // Like a rejected quote: the server does not accept the same text and author again.
                SubmissionActions(
                    label = uiText("Edit and resubmit"),
                    icon = Icons.Default.Edit,
                    isSubmitting = isSubmitting,
                    submitEnabled = true,
                    showEditButton = false,
                    onSubmit = { onEdit(true) },
                    onEdit = {}
                )
            }

            else -> {
                SubmissionProblem(problem)
                SubmissionActions(
                    label = uiText(if (isSubmitting) "Submitting..." else "Submit for review"),
                    icon = Icons.Default.CloudUpload,
                    isSubmitting = isSubmitting,
                    submitEnabled = problem == null,
                    showEditButton = true,
                    onSubmit = onSubmit,
                    onEdit = { onEdit(false) }
                )
            }
        }
    }
}

@Composable
private fun SubmissionActions(
    label: String,
    icon: ImageVector,
    isSubmitting: Boolean,
    submitEnabled: Boolean,
    showEditButton: Boolean,
    onSubmit: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showEditButton) {
            FilledTonalIconButton(
                onClick = onEdit,
                enabled = !isSubmitting,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = uiText("Edit"))
            }
        }
        FilledTonalButton(
            onClick = onSubmit,
            enabled = submitEnabled && !isSubmitting,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(icon, contentDescription = null)
            }
            Spacer(Modifier.width(8.dp))
            Text(label, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun SubmissionProblem(problem: String?) {
    if (problem == null) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
        )
        Text(
            "${uiText(problem)} ${uiText("Edit the quote to fix this.")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ReviewNote(note: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                uiText("Note from the reviewer"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(note, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuoteReviewStatus(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    action: (@Composable () -> Unit)? = null
) {
    Surface(
        color = containerColor.copy(alpha = 0.55f),
        contentColor = contentColor,
        shape = RoundedCornerShape(14.dp),
        modifier = if (action != null) Modifier.fillMaxWidth() else Modifier
    ) {
        if (action == null) {
            Row(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            // The action moves below the label when both don't fit, so long labels never break mid-word.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .heightIn(min = 40.dp)
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    action()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditQuoteSheet(
    quote: QuoteEntity,
    resubmit: Boolean,
    onDismiss: () -> Unit,
    onSave: (text: String, author: String, category: String, tags: String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val originalText = remember(quote.id) { quote.originalText() }
    val originalAuthor = remember(quote.id) { quote.editableAuthor() }
    var text by rememberSaveable(quote.id) { mutableStateOf(originalText) }
    var author by rememberSaveable(quote.id) { mutableStateOf(originalAuthor) }
    var category by rememberSaveable(quote.id) { mutableStateOf(quote.category) }
    var tags by rememberSaveable(quote.id) { mutableStateOf(quote.tags) }

    val textLength = text.trim().length
    val textTooShort = textLength < MIN_SUBMISSION_TEXT
    val textTooLong = textLength > MAX_SUBMISSION_TEXT
    val authorTooLong = author.trim().length > MAX_SUBMISSION_AUTHOR
    val parsedTags = tags.split(',').map(String::trim).filter(String::isNotEmpty)
    val tagsInvalid = parsedTags.size > MAX_SUBMISSION_TAGS || parsedTags.any { it.length > MAX_SUBMISSION_TAG_LENGTH }
    // The server identifies a submission by its text and author, so a resubmission must change one of them.
    val contentChanged = comparisonKey(text) != comparisonKey(originalText) ||
        comparisonKey(author.ifBlank { "Unknown" }) != comparisonKey(originalAuthor.ifBlank { "Unknown" })
    val needsChange = resubmit && !contentChanged
    val canSave = !textTooShort && !textTooLong && !authorTooLong && !tagsInvalid && !needsChange
    val resetsReview = quote.submissionStatus == QuoteSubmissionStatus.REJECTED ||
        quote.submissionStatus == QuoteSubmissionStatus.REMOVED

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
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    uiText("Edit quote"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (resubmit) {
                    Text(
                        uiText("Revise the quote, then submit it again."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (resetsReview) {
                    Text(
                        uiText("Saving resets the review status so you can submit the quote again."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            quote.reviewNote?.trim()?.takeIf { resubmit && it.isNotEmpty() }?.let { ReviewNote(it) }

            OutlinedTextField(
                value = text,
                // Longer imported text can still be shortened, but never grow past the limit.
                onValueChange = { if (it.length <= MAX_SUBMISSION_TEXT || it.length < text.length) text = it },
                label = { Text(uiText("Quote or affirmation")) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = SerifFontFamily,
                    lineHeight = 27.sp
                ),
                minLines = 1,
                maxLines = 6,
                isError = textTooShort || textTooLong,
                supportingText = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = when {
                                textTooShort -> uiText("Enter at least 3 characters.")
                                textTooLong -> uiText("At most 500 characters.")
                                else -> ""
                            },
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Text("${text.length}/$MAX_SUBMISSION_TEXT")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            )

            OutlinedTextField(
                value = author,
                onValueChange = { if (it.length <= MAX_SUBMISSION_AUTHOR || it.length < author.length) author = it },
                label = { Text(uiText("Author")) },
                placeholder = { Text(uiText("Unknown")) },
                isError = authorTooLong,
                supportingText = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = if (authorTooLong) uiText("At most 100 characters.") else "",
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Text("${author.length}/$MAX_SUBMISSION_AUTHOR")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = uiText("Shelf"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryMapper.categories.forEach { categoryName ->
                        val interactionSource = remember(categoryName) { MutableInteractionSource() }
                        FilterChip(
                            selected = category == categoryName,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                category = categoryName
                            },
                            label = { Text(categoryText(categoryName)) },
                            shape = rememberExpressiveShape(interactionSource, 18.dp, 8.dp),
                            interactionSource = interactionSource,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        )
                    }
                }
            }

            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text(uiText("Tags")) },
                supportingText = {
                    Text(
                        if (tagsInvalid) uiText("Maximum 8 tags, 30 characters each.")
                        else uiText("Separate tags with commas")
                    )
                },
                isError = tagsInvalid,
                placeholder = { Text(uiText("wisdom, courage, morning")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            if (needsChange) {
                Text(
                    uiText("Change the text or author to submit it again."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ExpressiveButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSave(text.trim(), author.trim(), category, tags.trim())
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                restingCorner = 18.dp,
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                Icon(if (resubmit) Icons.Default.CloudUpload else Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    uiText(if (resubmit) "Save and submit" else "Save"),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}

// The quote in the language it was written in; a personal row's display copy may show a translation.
private fun QuoteEntity.originalText(): String =
    if (alternateLanguage() == language) alternateText() ?: text else text

// A blank field stands for "Unknown"; the localized copy may say "Unbekannt".
private fun QuoteEntity.editableAuthor(): String = if (hasUnknownAuthor) "" else author

private fun comparisonKey(value: String): String = value.trim().replace(Regex("\\s+"), " ").lowercase()

/**
 * Why the backend would reject this own quote, as an English UI key, or null when it can be sent.
 * Mirrors the server's limits so the user sees the reason before the consent dialog.
 */
private fun QuoteEntity.submissionProblem(): String? {
    val textLength = originalText().trim().length
    val tagList = tags.split(',').map(String::trim).filter(String::isNotEmpty)
    return when {
        textLength < MIN_SUBMISSION_TEXT -> "Too short to submit: a quote needs at least 3 characters."
        textLength > MAX_SUBMISSION_TEXT -> "Too long to submit: a quote can have at most 500 characters."
        editableAuthor().trim().length > MAX_SUBMISSION_AUTHOR -> "The author name is too long to submit (max. 100 characters)."
        tagList.distinctBy { it.lowercase() }.size > MAX_SUBMISSION_TAGS -> "Too many tags to submit (max. 8)."
        tagList.any { it.length > MAX_SUBMISSION_TAG_LENGTH } -> "A tag is too long to submit (max. 30 characters each)."
        else -> null
    }
}

private fun formatSavedDate(savedDate: String?, now: Long = System.currentTimeMillis(), language: String = "en"): String {
    if (savedDate == null) return ""
    val millis = savedDate.toLongOrNull()
        ?: return savedDate  // backwards compat: old "dd MMM yyyy" strings shown as-is
    val diff = now - millis
    return when {
        diff < 60_000L -> if (language == "de") "gerade eben" else "just now"
        diff < 3_600_000L -> if (language == "de") "vor ${diff / 60_000} Min." else "${diff / 60_000} min ago"
        diff < 86_400_000L -> if (language == "de") "vor ${diff / 3_600_000} Std." else "${diff / 3_600_000} h ago"
        diff < 172_800_000L -> if (language == "de") "gestern" else "yesterday"
        diff < 2_592_000_000L -> if (language == "de") "vor ${diff / 86_400_000} Tagen" else "${diff / 86_400_000} days ago"
        else -> SimpleDateFormat(
            "d MMM yyyy",
            if (language == "de") Locale.GERMAN else Locale.ENGLISH
        ).format(Date(millis))
    }
}
