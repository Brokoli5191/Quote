package app.brokoli5191.quote.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.reflect.KProperty
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.brokoli5191.quote.MainActivity
import app.brokoli5191.quote.data.CategoryMapper
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.data.QuoteSubmissionStatus
import app.brokoli5191.quote.data.SubmissionError
import app.brokoli5191.quote.data.SubmissionLimits
import app.brokoli5191.quote.data.SubmissionRefreshResult
import app.brokoli5191.quote.data.alternateLanguage
import app.brokoli5191.quote.data.alternateText
import app.brokoli5191.quote.data.hasUnknownAuthor
import app.brokoli5191.quote.data.storedQuoteTextLength
import app.brokoli5191.quote.data.parseQuoteTags
import app.brokoli5191.quote.data.submissionLength
import app.brokoli5191.quote.data.tagLabel
import app.brokoli5191.quote.ui.LocalAppLanguage
import app.brokoli5191.quote.ui.QuoteViewModel
import app.brokoli5191.quote.ui.SavedSort
import app.brokoli5191.quote.ui.SavedStatusFilter
import app.brokoli5191.quote.ui.categoryText
import app.brokoli5191.quote.ui.editKeepsReview
import app.brokoli5191.quote.ui.localizeUi
import app.brokoli5191.quote.ui.uiText
import app.brokoli5191.quote.ui.components.CenteredEmptyState
import app.brokoli5191.quote.ui.components.AnimatedTranslation
import app.brokoli5191.quote.ui.components.AuthorAvatar
import app.brokoli5191.quote.ui.components.AuthorSheet
import app.brokoli5191.quote.ui.components.ExpressiveButton
import app.brokoli5191.quote.ui.components.ExpressiveIconButton
import app.brokoli5191.quote.ui.components.FloatingChromeBackdrop
import app.brokoli5191.quote.ui.components.SingleLineFitRow
import app.brokoli5191.quote.ui.components.SnackbarMessage
import app.brokoli5191.quote.ui.components.TranslationToggle
import app.brokoli5191.quote.ui.components.UndoKind
import app.brokoli5191.quote.ui.components.UndoRequest
import app.brokoli5191.quote.ui.components.bleedHorizontally
import app.brokoli5191.quote.ui.components.countText
import app.brokoli5191.quote.ui.components.floatingBottomBarHeight
import app.brokoli5191.quote.ui.components.quoteSnackbarController
import app.brokoli5191.quote.ui.components.rememberExpressiveShape
import app.brokoli5191.quote.ui.strings.submissionErrorText
import app.brokoli5191.quote.ui.theme.SerifFontFamily
import app.brokoli5191.quote.utils.CommunitySyncWorker
import app.brokoli5191.quote.utils.QuoteSharing
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private val SavedHeaderFallbackHeight = 140.dp
private val SavedContentGap = 16.dp

private const val SUB_TAB_FAVORITES = "Favorites"
private const val SUB_TAB_MY_QUOTES = "My Quotes"

// Until the database answers for the first time the lists are empty; the empty state waits this long.
private const val FIRST_LOAD_GRACE_MS = 400L
private const val THIRTY_DAYS_MS = 2_592_000_000L

private const val PRIVACY_POLICY_URL = "https://quote.cowsay.win/privacy"

// aura_prefs: Quote asked for the notification permission after a submission (once per install).
private const val KEY_SUBMISSION_PERMISSION_ASKED = "submission_notification_permission_asked"

private val SortOptions = listOf(
    SavedSort.RECENT to "Newest first",
    SavedSort.OLDEST to "Oldest first",
    SavedSort.AUTHOR to "Author A–Z",
    SavedSort.TEXT to "Quote A–Z"
)

private val StatusFilters = listOf(
    SavedStatusFilter.ALL to "All",
    SavedStatusFilter.NOT_SUBMITTED to "Not submitted",
    SavedStatusFilter.PENDING to "Pending",
    SavedStatusFilter.APPROVED to "Approved",
    SavedStatusFilter.NOT_APPROVED to "Not approved"
)

/** Saved's UI state that outlives the screen while another tab is open. */
internal class SavedScreenMemory : ViewModel() {
    // Cards whose entry animation already ran: a card slides in only the first time it appears.
    val animatedQuoteIds = HashSet<Int>()

    // Both lists have answered once; before that an empty list only means "not loaded yet".
    var listsLoaded = false

    // First visible item and its offset, per sub-tab.
    val scrollPositions = HashMap<String, Pair<Int, Int>>()
}

@Composable
private fun savedScreenMemory(): SavedScreenMemory = viewModel()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(viewModel: QuoteViewModel) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val userAdded by viewModel.userAdded.collectAsStateWithLifecycle()
    val favoritesTotal by viewModel.favoritesTotal.collectAsStateWithLifecycle()
    val userAddedTotal by viewModel.userAddedTotal.collectAsStateWithLifecycle()
    val sort by viewModel.favoritesSort.collectAsStateWithLifecycle()
    val searchQuery by viewModel.savedSearchQuery.collectAsStateWithLifecycle()
    val statusFilter by viewModel.savedStatusFilter.collectAsStateWithLifecycle()
    val submittingQuoteIds by viewModel.submittingQuoteIds.collectAsStateWithLifecycle()
    val refreshRunning by viewModel.submissionRefreshRunning.collectAsStateWithLifecycle()
    val activeSubTab by viewModel.savedSubTab.collectAsStateWithLifecycle()
    val lowPerformanceMode by viewModel.lowPerformanceMode.collectAsStateWithLifecycle()
    val authorImagesEnabled by viewModel.authorImagesEnabled.collectAsStateWithLifecycle()
    val blurNavigationSurfaces by viewModel.blurNavigationSurfaces.collectAsStateWithLifecycle()
    val newQuoteOpen by viewModel.showNewQuoteScreen.collectAsStateWithLifecycle()
    val devScreenOpen by viewModel.showDevScreen.collectAsStateWithLifecycle()

    val memory = savedScreenMemory()
    val snackbars = quoteSnackbarController()
    val context = LocalContext.current
    val uiLanguage = LocalAppLanguage.current
    val haptic = LocalHapticFeedback.current
    val fabInteractionSource = remember { MutableInteractionSource() }
    // The header (title, tab switcher, search, filters) floats over the list; it is measured so the
    // list starts below it for every language and font scale and scrolls underneath it.
    val savedHazeState = rememberHazeState(blurEnabled = blurNavigationSurfaces && !lowPerformanceMode)
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeight = if (headerHeightPx > 0) with(density) { headerHeightPx.toDp() } else statusBarTop + SavedHeaderFallbackHeight
    // FAB sits above the floating bottom bar; the list clears both.
    val fabBottom = floatingBottomBarHeight() + 23.dp
    val listBottom = fabBottom + 56.dp + 16.dp
    var timestampTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            timestampTick = System.currentTimeMillis()
        }
    }

    // The lists start empty until the database answers, and an empty list must not show the empty
    // state then. A total above zero means the list has loaded; otherwise the first visit waits briefly.
    var firstLoadWaited by remember { mutableStateOf(memory.listsLoaded) }
    LaunchedEffect(Unit) {
        if (!firstLoadWaited) {
            delay(FIRST_LOAD_GRACE_MS)
            firstLoadWaited = true
        }
    }
    val favoritesReady = firstLoadWaited || favoritesTotal > 0
    val myQuotesReady = firstLoadWaited || userAddedTotal > 0
    SideEffect { if (favoritesReady && myQuotesReady) memory.listsLoaded = true }

    val favoritesListState = rememberSavedListState(memory, SUB_TAB_FAVORITES)
    val myQuotesListState = rememberSavedListState(memory, SUB_TAB_MY_QUOTES)
    ScrollToTopOnChange(favoritesListState, favorites, sort, searchQuery)
    ScrollToTopOnChange(myQuotesListState, userAdded, sort, searchQuery, statusFilter)

    var searchOpen by rememberSaveable { mutableStateOf(searchQuery.isNotEmpty()) }
    var focusSearch by remember { mutableStateOf(false) }
    val closeSearch = {
        searchOpen = false
        viewModel.setSavedSearchQuery("")
    }

    // Long-press selection, for the sub-tab it was made in.
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<Int>()) }
    var selectionTab by rememberSaveable { mutableStateOf(activeSubTab) }
    val activeList = if (activeSubTab == SUB_TAB_FAVORITES) favorites else userAdded
    val selecting = selectionTab == activeSubTab && selectedIds.isNotEmpty()
    val selectedQuotes = if (selecting) activeList.filter { it.id in selectedIds } else emptyList()
    LaunchedEffect(activeSubTab) {
        if (selectionTab != activeSubTab) {
            selectionTab = activeSubTab
            selectedIds = emptySet()
        }
    }
    // Quotes that left the list (deleted, filtered out) leave the selection.
    LaunchedEffect(activeList) {
        val listed = activeList.mapTo(HashSet()) { it.id }
        if (selectedIds.any { it !in listed }) selectedIds = selectedIds.filterTo(HashSet()) { it in listed }
    }
    val toggleSelected: (Int) -> Unit = { id ->
        selectionTab = activeSubTab
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }
    val clearSelection = { selectedIds = emptySet() }

    BackHandler(enabled = (selecting || searchOpen) && !newQuoteOpen && !devScreenOpen) {
        if (selecting) clearSelection() else closeSearch()
    }

    // An own quote by id: as My Quotes lists it, or among all own quotes when the search or the status
    // filter hides it (an edit can move it out of the filter while its dialog is open). All quotes are
    // only read for such a target, so Saved does not recompose for every change to the library, and
    // they are read as state, so a target restored before they have loaded appears once they have.
    val allQuotes by viewModel.allQuotes.collectAsStateWithLifecycle()
    val ownQuote: (Int) -> QuoteEntity? = { id ->
        userAdded.firstOrNull { it.id == id }
            ?: allQuotes.firstOrNull { it.id == id && it.isUserAdded }
    }
    // Dialog and sheet targets are kept by id and read as the quotes' current copies, so they survive
    // a rotation and a process restart.
    var submitTargets by rememberQuoteTargets(ownQuote)
    var submitAgain by rememberSaveable { mutableStateOf(false) }
    // The language chosen in the edit sheet before "Save and submit"; null: the quote's own.
    var submitLanguage by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTargets by rememberQuoteTargets(ownQuote)
    var withdrawTargets by rememberQuoteTargets(ownQuote)
    var editTargets by rememberQuoteTargets(ownQuote)
    val editOpen = editTargets.isNotEmpty()
    LaunchedEffect(editOpen) { viewModel.setSavedEditOpen(editOpen) }
    DisposableEffect(Unit) { onDispose { viewModel.setSavedEditOpen(false) } }
    var editResubmit by rememberSaveable { mutableStateOf(false) }
    // Kept by name, like Library's: the sheet is about the author, not about one quote.
    var sheetAuthor by rememberSaveable { mutableStateOf<String?>(null) }

    fun toast(english: String, long: Boolean = false) {
        Toast.makeText(context, localizeUi(english, uiLanguage), if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    // The ViewModel restores the stored rows, so Undo brings back the raw data (submission state
    // and saved date included), never these localized display copies.
    val deleteWithUndo: (List<QuoteEntity>) -> Unit = { quotes ->
        viewModel.deleteQuotes(quotes.mapTo(HashSet()) { it.id })
        snackbars.showUndo(UndoRequest(UndoKind.DELETED, quotes))
    }
    val unfavoriteWithUndo: (List<QuoteEntity>) -> Unit = { quotes ->
        viewModel.unfavoriteQuotes(quotes.mapTo(HashSet()) { it.id })
        snackbars.showUndo(UndoRequest(UndoKind.UNFAVORITED, quotes))
    }
    // A running submission or withdrawal must finish before a quote can go. Pending quotes are
    // withdrawn on delete and approved ones stay published: both get a dialog that says so.
    val requestDelete: (List<QuoteEntity>) -> Unit = { quotes ->
        val deletable = quotes.filter { it.id !in submittingQuoteIds }
        when {
            deletable.isEmpty() -> Unit
            deletable.any { it.deleteNeedsConfirmation() } -> deleteTargets = deletable
            else -> {
                deleteWithUndo(deletable)
                clearSelection()
            }
        }
    }
    val requestSubmit: (QuoteEntity) -> Unit = { quote ->
        val problem = quote.submissionProblem()
        if (problem != null) {
            toast(problem, long = true)
        } else {
            submitAgain = false
            submitLanguage = null
            submitTargets = listOf(quote)
        }
    }
    val checkNow: () -> Unit = {
        val language = uiLanguage
        viewModel.refreshSubmissionStatuses { result ->
            refreshResultMessage(result, language)?.let { snackbars.showMessage(SnackbarMessage(it)) }
        }
    }

    // Review decisions come as notifications too, which needs the permission on Android 13+. It is
    // asked for once, after the first submission that went through.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val askForReviewNotifications: () -> Unit = {
        val prefs = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            prefs.getBoolean(CommunitySyncWorker.KEY_SUBMISSION_NOTIFICATIONS, true) &&
            !prefs.getBoolean(KEY_SUBMISSION_PERMISSION_ASKED, false) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            try {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                prefs.edit().putBoolean(KEY_SUBMISSION_PERMISSION_ASKED, true).apply()
            } catch (e: IllegalStateException) {
                // Saved was left while the submission ran; the next one asks.
                Log.w("SavedScreen", "Notification permission not requested", e)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = activeSubTab,
            transitionSpec = {
                if (lowPerformanceMode) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else if (targetState == SUB_TAB_MY_QUOTES) {
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
                .fillMaxSize()
                .hazeSource(savedHazeState),
            label = "SavedQuotesTabsTransition"
        ) { subTab ->
            val inFavorites = subTab == SUB_TAB_FAVORITES
            val quotes = if (inFavorites) favorites else userAdded
            val total = if (inFavorites) favoritesTotal else userAddedTotal
            val ready = if (inFavorites) favoritesReady else myQuotesReady
            val filtering = searchQuery.isNotBlank() || (!inFavorites && statusFilter != SavedStatusFilter.ALL)
            val contentTop = headerHeight + SavedContentGap

            when {
                quotes.isNotEmpty() -> {
                    // The list scrolls under the floating header, bottom bar and FAB; the bottom
                    // padding clears the bar and the FAB above it, so the last card's buttons can
                    // scroll out from under the FAB.
                    LazyColumn(
                        state = if (inFavorites) favoritesListState else myQuotesListState,
                        // Search keeps the keyboard open; the last results must scroll above it.
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = contentTop + 16.dp,
                            end = 16.dp,
                            bottom = listBottom
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(quotes, key = { it.id }) { quote ->
                            // Own quotes are stored locally only, so the card never looks their author up
                            // online; only a tap on the author opens the sheet, which does, as in Library.
                            val lookUpAuthor = authorImagesEnabled && !quote.isUserAdded && !quote.hasUnknownAuthor
                            val authorInfoFlow = remember(quote.author, lookUpAuthor) {
                                if (lookUpAuthor) viewModel.authorInfo(quote.author) else null
                            }
                            val authorImageUrl = authorInfoFlow?.let { it.collectAsStateWithLifecycle().value }?.imageUrl
                            val animateEntry = remember(quote.id) {
                                !lowPerformanceMode && memory.animatedQuoteIds.add(quote.id)
                            }

                            PremiumCollectionQuoteCard(
                                quote = quote,
                                inFavorites = inFavorites,
                                nowMillis = timestampTick,
                                authorImageUrl = authorImageUrl,
                                isSubmitting = quote.id in submittingQuoteIds,
                                refreshRunning = refreshRunning,
                                lowPerformanceMode = lowPerformanceMode,
                                animateEntry = animateEntry,
                                selecting = selecting,
                                selected = selecting && quote.id in selectedIds,
                                onToggleSelected = { toggleSelected(quote.id) },
                                onUnfavorite = { unfavoriteWithUndo(listOf(quote)) },
                                onDelete = { requestDelete(listOf(quote)) },
                                onSubmit = { requestSubmit(quote) },
                                onEdit = { resubmit ->
                                    editResubmit = resubmit
                                    editTargets = listOf(quote)
                                },
                                onWithdraw = { withdrawTargets = listOf(quote) },
                                onCheckNow = checkNow,
                                onAuthorClick = { sheetAuthor = quote.author },
                                modifier = if (lowPerformanceMode) Modifier else Modifier.animateItem()
                            )
                        }
                    }
                }
                filtering && total > 0 -> NoMatchesState(
                    topPadding = contentTop,
                    onClear = {
                        viewModel.setSavedStatusFilter(SavedStatusFilter.ALL)
                        closeSearch()
                    }
                )
                ready && total == 0 -> EmptyCollectionState(inFavorites = inFavorites, topPadding = contentTop)
                // Not loaded yet.
                else -> Box(Modifier.fillMaxSize())
            }
        }

        FloatingChromeBackdrop(
            hazeState = savedHazeState,
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight + SavedContentGap)
        )
        val filterRowShown = activeSubTab == SUB_TAB_MY_QUOTES && (userAddedTotal > 0 || statusFilter != SavedStatusFilter.ALL)
        if (searchOpen || filterRowShown) {
            // The search field and the filter chips sit low in the backdrop, where it is nearly clear;
            // this keeps the list from showing through them and fades only below the last row.
            val backdropHeight = headerHeight + SavedContentGap
            val solidUntil = ((headerHeight - 12.dp) / backdropHeight).coerceIn(0f, 1f)
            val background = MaterialTheme.colorScheme.background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(backdropHeight)
                    .background(
                        Brush.verticalGradient(
                            0f to background.copy(alpha = 0.94f),
                            solidUntil to background.copy(alpha = 0.94f),
                            1f to Color.Transparent
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { headerHeightPx = it.height }
        ) {
            val inFavorites = activeSubTab == SUB_TAB_FAVORITES
            if (selecting) {
                SelectionBar(
                    count = selectedQuotes.size,
                    inFavorites = inFavorites,
                    onClose = clearSelection,
                    onSelectAll = {
                        selectionTab = activeSubTab
                        selectedIds = activeList.mapTo(HashSet()) { it.id }
                    },
                    onCopy = {
                        copyQuotes(context, selectedQuotes)
                        // Android 13 and newer confirm a copy themselves.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            snackbars.showMessage(
                                SnackbarMessage(
                                    if (selectedQuotes.size == 1) localizeUi("Copied", uiLanguage)
                                    else countText("%d quotes copied", selectedQuotes.size, uiLanguage)
                                )
                            )
                        }
                        clearSelection()
                    },
                    onShare = {
                        shareQuotes(context, selectedQuotes, uiLanguage)
                        clearSelection()
                    },
                    onUnfavorite = {
                        unfavoriteWithUndo(selectedQuotes)
                        clearSelection()
                    },
                    onDelete = { requestDelete(selectedQuotes) }
                )
            } else {
                SavedTitleRow(
                    showTools = searchOpen || (if (inFavorites) favoritesTotal else userAddedTotal) > 0,
                    searchOpen = searchOpen,
                    sort = sort,
                    onToggleSearch = {
                        if (searchOpen) {
                            closeSearch()
                        } else {
                            focusSearch = true
                            searchOpen = true
                        }
                    },
                    onSort = viewModel::setFavoritesSort
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SavedTabSwitcher(
                activeTab = activeSubTab,
                // No "(0)" before the lists have loaded.
                favoritesCount = favoritesTotal.takeIf { favoritesReady },
                myQuotesCount = userAddedTotal.takeIf { myQuotesReady },
                lowPerformanceMode = lowPerformanceMode,
                onSelect = viewModel::selectSavedSubTab
            )

            if (searchOpen) {
                SavedSearchField(
                    query = searchQuery,
                    requestFocus = focusSearch,
                    onFocusRequested = { focusSearch = false },
                    onQueryChange = viewModel::setSavedSearchQuery,
                    onClose = closeSearch
                )
            }

            if (filterRowShown) {
                StatusFilterRow(selected = statusFilter, onSelect = viewModel::setSavedStatusFilter)
            }
        }

        if (!selecting) {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.openNewQuoteScreen()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = fabBottom, end = 16.dp)
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
        }

        submitTargets.firstOrNull()?.let { quote ->
            val resubmit = submitAgain
            SubmissionConsentDialog(
                quote = quote,
                resubmit = resubmit,
                initialLanguage = submitLanguage,
                onDismiss = { submitTargets = emptyList() },
                onOpenPolicy = { openPrivacyPolicy(context, uiLanguage) },
                onConfirm = { language ->
                    submitTargets = emptyList()
                    val onSuccess = {
                        toast("Submitted for review")
                        askForReviewNotifications()
                    }
                    val onAlreadyPublished = { toast("This quote is already published in the community collection.", long = true) }
                    val onError = { code: String ->
                        Toast.makeText(context, submitErrorMessage(code, uiLanguage), Toast.LENGTH_LONG).show()
                    }
                    if (resubmit) {
                        viewModel.resubmitQuote(quote, onSuccess, onError, language, onAlreadyPublished)
                    } else {
                        viewModel.submitQuoteForReview(quote, onSuccess, onError, language, onAlreadyPublished)
                    }
                }
            )
        }

        val deleting = deleteTargets
        if (deleting.isNotEmpty()) {
            DeleteQuotesDialog(
                quotes = deleting,
                onDismiss = { deleteTargets = emptyList() },
                onConfirm = { quotes ->
                    deleteTargets = emptyList()
                    deleteWithUndo(quotes)
                    clearSelection()
                }
            )
        }

        withdrawTargets.firstOrNull()?.let { quote ->
            AlertDialog(
                onDismissRequest = { withdrawTargets = emptyList() },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                title = { Text(uiText("Withdraw submission?")) },
                text = {
                    Text(uiText("The quote leaves the review queue and stays in My Quotes. You can submit it again later."))
                },
                dismissButton = {
                    TextButton(onClick = { withdrawTargets = emptyList() }) {
                        Text(uiText("Cancel"))
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        withdrawTargets = emptyList()
                        viewModel.withdrawSubmission(
                            quote = quote,
                            onSuccess = { toast("Submission withdrawn") },
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

        editTargets.firstOrNull()?.let { quote ->
            val resubmit = editResubmit
            EditQuoteSheet(
                quote = quote,
                resubmit = resubmit,
                onDismiss = { editTargets = emptyList() },
                onSave = { text, author, category, tags, language ->
                    viewModel.updateUserQuote(quote, text, author, category, tags, language) { saved ->
                        if (!saved) {
                            toast("Changes could not be saved.", long = true)
                        } else if (resubmit) {
                            // The edit cleared the old review; the consent dialog sends the stored row.
                            // The list may still hold the copy from before the edit, so the dialog
                            // starts from the language chosen in the sheet, not from that copy.
                            submitAgain = true
                            submitLanguage = language
                            submitTargets = listOf(quote)
                        }
                    }
                }
            )
        }

        // Built as Library builds it; own quotes' authors open it too, as they do in Library.
        sheetAuthor?.let { author ->
            val authorInfo by remember(author) { viewModel.authorInfo(author) }.collectAsStateWithLifecycle()
            // Re-read when the library finishes loading behind an already open sheet.
            val authorQuoteCount = remember(author, allQuotes) { viewModel.authorQuoteCount(author) }
            AuthorSheet(
                name = author,
                info = authorInfo,
                imageUrl = authorInfo?.imageUrl?.takeIf { authorImagesEnabled },
                language = uiLanguage,
                onDismiss = { sheetAuthor = null },
                quoteCount = authorQuoteCount,
                onShowMoreQuotes = { viewModel.openLibrarySearch(author) }
            )
        }
    }
}

/**
 * Targets of a dialog or sheet (empty = closed). Only their ids are kept and saved; every read looks
 * them up with [lookup], so a dialog shows the quote's current copy. After a process restart the lists
 * are still empty in the first composition: a restored target appears as soon as they have loaded.
 */
private class QuoteTargets(ids: List<Int>, var lookup: (Int) -> QuoteEntity?) {
    var ids by mutableStateOf(ids)
        private set

    // Ids that have resolved since the targets were set. Not saved: a restored id waits for the lists.
    private val shown = HashSet<Int>()

    operator fun getValue(thisRef: Any?, property: KProperty<*>): List<QuoteEntity> = ids.mapNotNull(lookup)

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: List<QuoteEntity>) {
        shown.clear()
        ids = value.map { it.id }
    }

    // A target that resolved and no longer does was deleted meanwhile. Its id is dropped, so the dialog
    // cannot come back with it later (Undo restores a deleted quote under the same id).
    fun dropDeleted() {
        if (ids.isEmpty()) return
        val resolved = ids.filterTo(HashSet()) { lookup(it) != null }
        shown += resolved
        if (ids.any { it !in resolved && it in shown }) ids = ids.filter { it in resolved || it !in shown }
    }
}

// [lookup] must read the lists as state, so a read in composition recomposes when they change. It is
// handed over after each composition, not kept in a state: reading a state written in every
// composition would schedule the next one.
@Composable
private fun rememberQuoteTargets(lookup: (Int) -> QuoteEntity?): QuoteTargets {
    val targets = rememberSaveable(
        saver = Saver<QuoteTargets, ArrayList<Int>>(
            save = { ArrayList(it.ids) },
            restore = { ids -> QuoteTargets(ids, lookup) }
        )
    ) { QuoteTargets(emptyList(), lookup) }
    SideEffect {
        targets.lookup = lookup
        targets.dropDeleted()
    }
    return targets
}

// Kept across sub-tab switches and rotations, and through [memory] across visits to other tabs.
@Composable
private fun rememberSavedListState(memory: SavedScreenMemory, subTab: String): LazyListState {
    val state = rememberSaveable(subTab, saver = LazyListState.Saver) {
        val (index, offset) = memory.scrollPositions[subTab] ?: (0 to 0)
        LazyListState(index, offset)
    }
    DisposableEffect(state) {
        onDispose { memory.scrollPositions[subTab] = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
    }
    return state
}

/**
 * Jumps to the top when the order, search or filter ([keys]) changes. The new list arrives from the
 * ViewModel a moment later; scrolling only then keeps the list from holding on to its old first item.
 */
@Composable
private fun ScrollToTopOnChange(listState: LazyListState, quotes: List<QuoteEntity>, vararg keys: Any) {
    val currentQuotes by rememberUpdatedState(quotes)
    var initial by remember { mutableStateOf(true) }
    LaunchedEffect(*keys) {
        if (initial) {
            initial = false
            return@LaunchedEffect
        }
        val before = currentQuotes
        withTimeoutOrNull(500) { snapshotFlow { currentQuotes }.first { it !== before } }
        listState.scrollToItem(0)
    }
}

@Composable
private fun EmptyCollectionState(inFavorites: Boolean, topPadding: Dp) {
    CenteredEmptyState(top = topPadding) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = if (inFavorites) Icons.Default.FavoriteBorder else Icons.Default.NoteAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = if (inFavorites) uiText("No favorites yet") else uiText("No custom quotes yet"),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (inFavorites) {
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
}

@Composable
private fun NoMatchesState(topPadding: Dp, onClear: () -> Unit) {
    CenteredEmptyState(top = topPadding) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = uiText("No saved quotes match"),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = uiText("Try another search or filter."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            FilledTonalButton(onClick = onClear, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(uiText("Clear search and filters"), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SavedTitleRow(
    showTools: Boolean,
    searchOpen: Boolean,
    sort: String,
    onToggleSearch: () -> Unit,
    onSort: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var sortMenuOpen by remember { mutableStateOf(false) }
    val titleStyle = MaterialTheme.typography.headlineLarge
    val titleSize = titleStyle.fontSize.takeIf { it.isSp } ?: 32.sp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 16.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // One line beside the buttons: a long title ("Deine Sammlung", large fonts) shrinks a little
        // instead of taking a second line from the list.
        Text(
            text = uiText("Your Collection"),
            style = titleStyle,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = titleSize, stepSize = 0.5.sp),
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        if (showTools) {
            ExpressiveIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleSearch()
                },
                modifier = Modifier.size(48.dp),
                containerColor = if (searchOpen) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = uiText(if (searchOpen) "Close search" else "Search"),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Box {
                ExpressiveIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        sortMenuOpen = true
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = uiText("Sort"),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    SortOptions.forEach { (option, label) ->
                        val chosen = option == sort
                        DropdownMenuItem(
                            text = { Text(uiText(label), fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal) },
                            trailingIcon = {
                                if (chosen) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            onClick = {
                                sortMenuOpen = false
                                onSort(option)
                            },
                            modifier = Modifier.semantics { selected = chosen }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    inFavorites: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onUnfavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    fun withHaptic(action: () -> Unit): () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        action()
    }
    val titleStyle = MaterialTheme.typography.titleLarge
    val titleSize = titleStyle.fontSize.takeIf { it.isSp } ?: 22.sp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 16.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveIconButton(onClick = withHaptic(onClose)) {
            Icon(Icons.Default.Close, contentDescription = uiText("Cancel selection"))
        }
        Text(
            text = countText("%d selected", count, LocalAppLanguage.current),
            style = titleStyle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = titleSize, stepSize = 0.5.sp),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
        ExpressiveIconButton(onClick = withHaptic(onSelectAll)) {
            Icon(Icons.Default.SelectAll, contentDescription = uiText("Select all"))
        }
        ExpressiveIconButton(onClick = withHaptic(onCopy), enabled = count > 0) {
            Icon(Icons.Default.ContentCopy, contentDescription = uiText("Copy"))
        }
        ExpressiveIconButton(onClick = withHaptic(onShare), enabled = count > 0) {
            Icon(Icons.Default.Share, contentDescription = uiText("Share"))
        }
        if (inFavorites) {
            ExpressiveIconButton(onClick = withHaptic(onUnfavorite), enabled = count > 0) {
                Icon(Icons.Default.HeartBroken, contentDescription = uiText("Remove from favorites"))
            }
        } else {
            ExpressiveIconButton(onClick = withHaptic(onDelete), enabled = count > 0) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = uiText("Delete"),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SavedSearchField(
    query: String,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Only when the user opened the search, not when it comes back after a rotation.
    LaunchedEffect(Unit) {
        if (requestFocus) {
            focusRequester.requestFocus()
            onFocusRequested()
        }
    }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                uiText("Search saved quotes"),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingIcon = {
            ExpressiveIconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = uiText("Close search"))
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(32.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.background,
            unfocusedContainerColor = MaterialTheme.colorScheme.background,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp)
            .focusRequester(focusRequester)
    )
}

@Composable
private fun StatusFilterRow(selected: String, onSelect: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusFilters.forEach { (filter, label) ->
            FilterChip(
                selected = selected == filter,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(filter)
                },
                label = { Text(uiText(label)) },
                // Opaque: the row floats over the list.
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
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

/** The message for a "Check now" result, or null when the review decisions it found say enough. */
internal fun refreshResultMessage(result: SubmissionRefreshResult, language: String): String? = when {
    !result.complete -> localizeUi("Some review statuses could not be checked. Try again later.", language)
    // Each decision gets its own snackbar (QuoteUiEvent.ReviewDecisions).
    result.updates.isNotEmpty() -> null
    result.changed -> localizeUi("Review statuses updated.", language)
    else -> localizeUi("No new review decisions yet.", language)
}

/** The privacy and submission policy in the app language. */
internal fun privacyPolicyUrl(language: String): String =
    "$PRIVACY_POLICY_URL?lang=${if (language == QuoteLanguage.GERMAN) QuoteLanguage.GERMAN else QuoteLanguage.ENGLISH}"

private fun openPrivacyPolicy(context: Context, language: String) {
    val url = privacyPolicyUrl(language)
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // No browser (disabled, or a restricted work profile): the address is still shown.
        Toast.makeText(
            context,
            localizeUi("No app can open the link. The policy is at %s", language).format(url),
            Toast.LENGTH_LONG
        ).show()
    }
}

/** Several quotes as one text, blank lines between them, each written as QuoteSharing writes one. */
internal fun joinedQuoteText(quotes: List<QuoteEntity>): String = quotes.joinToString("\n\n") {
    QuoteSharing.quoteText(it.text, it.author, QuoteSharing.textLanguage(it, showingTranslation = false))
}

private fun copyQuotes(context: Context, quotes: List<QuoteEntity>) {
    val single = quotes.singleOrNull()
    if (single != null) {
        QuoteSharing.copyText(context, single.text, single.author, QuoteSharing.textLanguage(single, showingTranslation = false))
        return
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("quotes", joinedQuoteText(quotes)))
}

private fun shareQuotes(context: Context, quotes: List<QuoteEntity>, language: String) {
    val single = quotes.singleOrNull()
    if (single != null) {
        QuoteSharing.shareText(context, single.text, single.author, language, QuoteSharing.textLanguage(single, showingTranslation = false))
        return
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, joinedQuoteText(quotes))
    }
    // The same share sheet as QuoteSharing's: its title, without Quote's own "Save to Quote" target.
    val chooser = Intent.createChooser(send, localizeUi("Share Wisdom", language)).putExtra(
        Intent.EXTRA_EXCLUDE_COMPONENTS,
        arrayOf(ComponentName(context, MainActivity::class.java))
    )
    try {
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        Log.w("SavedScreen", "No share sheet available", e)
    }
}

private fun QuoteEntity.deleteNeedsConfirmation(): Boolean =
    isUserAdded && (submissionStatus == QuoteSubmissionStatus.PENDING || submissionStatus == QuoteSubmissionStatus.APPROVED)

@Composable
private fun DeleteQuotesDialog(quotes: List<QuoteEntity>, onDismiss: () -> Unit, onConfirm: (List<QuoteEntity>) -> Unit) {
    val language = LocalAppLanguage.current
    val single = quotes.singleOrNull()
    val anyPending = quotes.any { it.submissionStatus == QuoteSubmissionStatus.PENDING }
    val anyPublished = quotes.any { it.submissionStatus == QuoteSubmissionStatus.APPROVED }
    val title = when {
        single == null -> countText("Delete %d quotes?", quotes.size, language)
        anyPending -> localizeUi("Delete and withdraw?", language)
        else -> localizeUi("Delete quote?", language)
    }
    val text = if (single != null) {
        localizeUi(
            if (anyPending) {
                "This quote is still waiting for review. Deleting it also withdraws the submission, so it won't be published. Without a connection, Quote withdraws it as soon as it can reach the service."
            } else {
                "This only deletes your own copy. The published quote stays in the community collection."
            },
            language
        )
    } else {
        listOfNotNull(
            "Some of these quotes are still waiting for review. Deleting them also withdraws their submissions, so they won't be published."
                .takeIf { anyPending },
            "Published quotes stay in the community collection. This only deletes your own copies.".takeIf { anyPublished }
        ).joinToString("\n\n") { localizeUi(it, language) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        title = { Text(title) },
        text = { Text(text) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(uiText("Cancel"))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(quotes) }) {
                Text(uiText("Delete"), color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

@Composable
private fun QuoteLanguageChoice(language: String, onSelect: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = uiText("Language of the quote"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(QuoteLanguage.ENGLISH to "English", QuoteLanguage.GERMAN to "German").forEach { (code, name) ->
                FilterChip(
                    selected = language == code,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(code)
                    },
                    label = { Text(uiText(name)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                )
            }
        }
        Text(
            text = uiText("Decides who sees the quote if you submit it to the community."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// The language stored on the quote; rows from before the choice existed may hold anything else.
private fun QuoteEntity.knownLanguage(): String =
    if (language == QuoteLanguage.GERMAN) QuoteLanguage.GERMAN else QuoteLanguage.ENGLISH

@Composable
private fun SubmissionConsentDialog(
    quote: QuoteEntity,
    resubmit: Boolean,
    onDismiss: () -> Unit,
    onOpenPolicy: () -> Unit,
    onConfirm: (language: String) -> Unit,
    // A language the user has just chosen (in the edit sheet); null starts from the quote's.
    initialLanguage: String? = null
) {
    var language by rememberSaveable(quote.id) { mutableStateOf(initialLanguage ?: quote.knownLanguage()) }
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
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
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
                QuoteLanguageChoice(language = language, onSelect = { language = it })
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
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(uiText("Cancel"), textAlign = TextAlign.Center, maxLines = 2)
                    }
                    TextButton(onClick = { onConfirm(language) }) {
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
    favoritesCount: Int?,
    myQuotesCount: Int?,
    lowPerformanceMode: Boolean,
    onSelect: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            // Opaque underlay: the switcher floats over the list, cards must not show through it.
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(16.dp))
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp)
            )
            .padding(4.dp)
    ) {
        val segmentWidth = maxWidth / 2
        val indicatorOffset by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (activeTab == SUB_TAB_FAVORITES) 0.dp else segmentWidth,
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
                SUB_TAB_FAVORITES to tabLabel(uiText("Favorites"), favoritesCount),
                SUB_TAB_MY_QUOTES to tabLabel(uiText("My Quotes"), myQuotesCount)
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

private fun tabLabel(name: String, count: Int?): String = if (count == null) name else "$name ($count)"

@Composable
private fun PremiumCollectionQuoteCard(
    quote: QuoteEntity,
    // Favorites shows the heart for every quote, own ones included; My Quotes has delete and the review.
    inFavorites: Boolean,
    nowMillis: Long,
    authorImageUrl: String?,
    isSubmitting: Boolean,
    refreshRunning: Boolean,
    lowPerformanceMode: Boolean,
    animateEntry: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onUnfavorite: () -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    onEdit: (resubmit: Boolean) -> Unit,
    onWithdraw: () -> Unit,
    onCheckNow: () -> Unit,
    onAuthorClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // Fly-in from the side, only the first time the card appears.
    val offsetX = if (animateEntry) remember { Animatable(80f) } else null
    val alpha = if (animateEntry) remember { Animatable(0f) } else null
    if (offsetX != null && alpha != null) {
        LaunchedEffect(Unit) {
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f)
            )
        }
        LaunchedEffect(Unit) {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 250)
            )
        }
    }

    val bgBrush = when (quote.id % 4) {
        0 -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainer))
        1 -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f), MaterialTheme.colorScheme.surfaceContainer))
        2 -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.16f), MaterialTheme.colorScheme.surfaceContainerLow))
        else -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f), MaterialTheme.colorScheme.surfaceContainer))
    }
    val cardShape = RoundedCornerShape(24.dp)
    val strokeColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
    val cardScope = rememberCoroutineScope()
    var copyConfirmed by remember { mutableStateOf(false) }
    var shareMenuOpen by remember { mutableStateOf(false) }
    var imageShareRunning by remember { mutableStateOf(false) }

    val alternateText = remember(quote) { quote.alternateText() }
    val alternateLanguage = remember(quote) { quote.alternateLanguage() }
    // Resets when the app language changes the display text.
    var showTranslation by rememberSaveable(quote.id, quote.text) { mutableStateOf(false) }
    val displayedText = if (showTranslation && alternateText != null) alternateText else quote.text
    // Quotation marks (on screen, copied, shared) follow the language of the text on screen.
    val textLanguage = QuoteSharing.textLanguage(quote, showTranslation)
    val unknownAuthor = quote.hasUnknownAuthor
    val authorName = if (unknownAuthor) uiText("Unknown") else quote.author
    // While selecting, a tap on the author selects like a tap anywhere else on the card.
    val authorTappable = !unknownAuthor && !selecting

    val copy: () -> Unit = {
        QuoteSharing.copyText(context, displayedText, quote.author, textLanguage)
        copyConfirmed = true
        cardScope.launch {
            delay(1200)
            copyConfirmed = false
        }
    }
    val shareText: () -> Unit = {
        QuoteSharing.shareText(context, displayedText, quote.author, language, textLanguage)
    }
    val shareImage: () -> Unit = {
        if (!imageShareRunning) {
            imageShareRunning = true
            cardScope.launch {
                try {
                    QuoteSharing.shareImage(context, displayedText, quote.author, language, textLanguage)
                } finally {
                    imageShareRunning = false
                }
            }
        }
    }

    val copyLabel = uiText("Copy")
    val shareTextLabel = uiText("Share as text")
    val shareImageLabel = uiText("Share as image")
    val removeLabel = uiText(if (inFavorites) "Remove from favorites" else "Delete")
    val selectLabel = uiText("Select")
    val authorLabel = uiText("Explore this author")
    val currentToggleSelected by rememberUpdatedState(onToggleSelected)
    // While selecting, a tap anywhere on the card toggles it; otherwise a long press starts selecting.
    val selectionModifier = if (selecting) {
        Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onToggleSelected()
        })
    } else {
        Modifier.pointerInput(Unit) {
            detectTapGestures(onLongPress = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                currentToggleSelected()
            })
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (offsetX != null && alpha != null) {
                    Modifier.graphicsLayer {
                        translationX = offsetX.value
                        this.alpha = alpha.value
                    }
                } else {
                    Modifier
                }
            )
            .border(if (selected) 2.dp else 1.dp, strokeColor, cardShape)
            .clip(cardShape)
            .then(selectionModifier),
        shape = cardShape,
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
                // TalkBack reads quote and date as one item and offers the card's actions in its
                // actions menu. A known author is a button of its own after it, as in Library; an
                // unknown one is read with the quote. While selecting, the whole card is one checkbox.
                Column(
                    modifier = if (selecting) {
                        Modifier
                    } else {
                        Modifier.semantics(mergeDescendants = true) {
                            customActions = buildList {
                                add(CustomAccessibilityAction(copyLabel) { copy(); true })
                                add(CustomAccessibilityAction(shareTextLabel) { shareText(); true })
                                if (!imageShareRunning) add(CustomAccessibilityAction(shareImageLabel) { shareImage(); true })
                                if (authorTappable) add(CustomAccessibilityAction(authorLabel) { onAuthorClick(); true })
                                if (inFavorites) {
                                    add(CustomAccessibilityAction(removeLabel) { onUnfavorite(); true })
                                } else if (!isSubmitting) {
                                    add(CustomAccessibilityAction(removeLabel) { onDelete(); true })
                                }
                            }
                            onLongClick(label = selectLabel) {
                                onToggleSelected()
                                true
                            }
                        }
                    },
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Tags and saved date; skipped when both are empty so no blank row is left behind.
                    // Catalogue tags are shown in the app language; own tags stay as written.
                    val tagsSp = remember(quote.tags, quote.isUserAdded, language) {
                        quote.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            .map { if (quote.isUserAdded) it else tagLabel(it, language) }
                    }
                    // The saved date belongs to Favorites; My Quotes shows when a quote was submitted.
                    val savedDateText = if (inFavorites) formatSavedDate(quote.savedDate, nowMillis, language) else ""
                    if (tagsSp.isNotEmpty() || savedDateText.isNotEmpty()) Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // One line: tags that don't fit beside the date are left out instead of wrapping.
                        SingleLineFitRow(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            spacing = 6.dp
                        ) {
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
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        softWrap = false
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
                    AnimatedTranslation(showTranslation, lowPerformanceMode) { showing ->
                        val text = if (showing && alternateText != null) alternateText else quote.text
                        Text(
                            text = if (QuoteSharing.textLanguage(quote, showing) == QuoteLanguage.GERMAN) "„$text“" else "“$text”",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = SerifFontFamily,
                                fontStyle = FontStyle.Italic,
                                lineHeight = 28.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

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

                    // Author with avatar, then the actions. When the name can't fit in two lines beside
                    // the three actions (long names, large font scales), the actions move below the name.
                    val authorBlock: @Composable (Modifier) -> Unit = { rowModifier ->
                        Row(
                            modifier = rowModifier
                                .padding(end = 8.dp)
                                // The touch target stays while selecting, so the card keeps its height.
                                .then(if (unknownAuthor) Modifier else Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)))
                                .then(
                                    if (authorTappable) {
                                        // The long press still selects, as everywhere else on the card
                                        // (combinedClickable gives it the long-press haptic).
                                        Modifier.combinedClickable(
                                            onClickLabel = authorLabel,
                                            role = Role.Button,
                                            onLongClickLabel = selectLabel,
                                            onLongClick = onToggleSelected
                                        ) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onAuthorClick()
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
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
                            if (selecting) {
                                // The card's own buttons rest while selecting; this shows the card's state.
                                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            } else {
                                ExpressiveIconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        copy()
                                    },
                                    // TalkBack announces "Copied" when the label changes.
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                ) {
                                    Icon(
                                        imageVector = if (copyConfirmed) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = uiText(if (copyConfirmed) "Copied" else "Copy"),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Box {
                                    ExpressiveIconButton(onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        shareMenuOpen = true
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = uiText("Share"),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = shareMenuOpen,
                                        onDismissRequest = { shareMenuOpen = false },
                                        shape = RoundedCornerShape(18.dp),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(shareTextLabel) },
                                            leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                                            onClick = {
                                                shareMenuOpen = false
                                                shareText()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(shareImageLabel) },
                                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                            enabled = !imageShareRunning,
                                            onClick = {
                                                shareMenuOpen = false
                                                shareImage()
                                            }
                                        )
                                    }
                                }

                                if (inFavorites) {
                                    // Unfavoriting can be undone from the snackbar.
                                    ExpressiveIconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onUnfavorite()
                                        },
                                        stateDescription = uiText(if (quote.isFavorite) "Saved" else "Not saved")
                                    ) {
                                        Icon(
                                            imageVector = if (quote.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = uiText("Favorite"),
                                            tint = if (quote.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
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
                }

                if (!inFavorites && quote.isUserAdded) {
                    SubmissionSection(
                        quote = quote,
                        nowMillis = nowMillis,
                        isSubmitting = isSubmitting,
                        refreshRunning = refreshRunning,
                        enabled = !selecting,
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
                        },
                        onCheckNow = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCheckNow()
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
    nowMillis: Long,
    isSubmitting: Boolean,
    refreshRunning: Boolean,
    // False while selecting: the buttons stay in place but do nothing.
    enabled: Boolean,
    onSubmit: () -> Unit,
    onEdit: (resubmit: Boolean) -> Unit,
    onWithdraw: () -> Unit,
    onCheckNow: () -> Unit
) {
    val problem = remember(quote) { quote.submissionProblem() }
    val reviewNote = quote.reviewNote?.trim()?.takeIf { it.isNotEmpty() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (quote.submissionStatus) {
            QuoteSubmissionStatus.PENDING -> {
                QuoteReviewStatus(
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
                            enabled = enabled,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        ) {
                            Text(uiText("Withdraw"), textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
                PendingReviewInfo(
                    submittedAt = quote.submittedAt,
                    nowMillis = nowMillis,
                    refreshRunning = refreshRunning,
                    enabled = enabled,
                    onCheckNow = onCheckNow
                )
            }

            QuoteSubmissionStatus.APPROVED -> {
                QuoteReviewStatus(
                    label = uiText("Approved for community"),
                    icon = Icons.Default.CheckCircle,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
                // Moderators may explain a correction they made while approving.
                if (reviewNote != null) ReviewNote(reviewNote)
            }

            QuoteSubmissionStatus.REJECTED -> {
                QuoteReviewStatus(
                    label = uiText("Not approved"),
                    icon = Icons.Default.Cancel,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
                if (reviewNote != null) ReviewNote(reviewNote)
                // The server keeps its decision for this exact text, so resubmitting needs a revision;
                // the edit button changes the quote for the user alone and keeps the review.
                SubmissionActions(
                    label = uiText("Edit and resubmit"),
                    icon = Icons.Default.CloudUpload,
                    isSubmitting = isSubmitting,
                    enabled = enabled,
                    submitEnabled = true,
                    showEditButton = true,
                    onSubmit = { onEdit(true) },
                    onEdit = { onEdit(false) }
                )
            }

            QuoteSubmissionStatus.REMOVED -> {
                QuoteReviewStatus(
                    label = uiText("No longer published"),
                    icon = Icons.Default.CloudOff,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Only quotes that were published end up here: a withdrawn submission is not submitted.
                Text(
                    uiText("This quote was removed from the community collection. To submit it again, change its text or author."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (reviewNote != null) ReviewNote(reviewNote)
                // Like a rejected quote: the server does not accept the same text and author again.
                SubmissionActions(
                    label = uiText("Edit and resubmit"),
                    icon = Icons.Default.CloudUpload,
                    isSubmitting = isSubmitting,
                    enabled = enabled,
                    submitEnabled = true,
                    showEditButton = true,
                    onSubmit = { onEdit(true) },
                    onEdit = { onEdit(false) }
                )
            }

            else -> {
                SubmissionProblem(problem)
                SubmissionActions(
                    label = uiText(if (isSubmitting) "Submitting..." else "Submit for review"),
                    icon = Icons.Default.CloudUpload,
                    isSubmitting = isSubmitting,
                    enabled = enabled,
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
private fun PendingReviewInfo(
    submittedAt: Long?,
    nowMillis: Long,
    refreshRunning: Boolean,
    enabled: Boolean,
    onCheckNow: () -> Unit
) {
    val language = LocalAppLanguage.current
    val info = listOfNotNull(
        submittedAt?.let { submittedText(it, nowMillis, language) },
        uiText("Reviewed by hand; this can take a few days.")
    ).joinToString(". ")
    val checkingLabel = uiText("Checking review status")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = info,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (refreshRunning) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .size(18.dp)
                    .semantics { contentDescription = checkingLabel },
                strokeWidth = 2.dp
            )
        } else {
            TextButton(onClick = onCheckNow, enabled = enabled) {
                Text(uiText("Check now"), textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

@Composable
private fun SubmissionActions(
    label: String,
    icon: ImageVector,
    isSubmitting: Boolean,
    enabled: Boolean,
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
                enabled = enabled && !isSubmitting,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = uiText("Edit"))
            }
        }
        FilledTonalButton(
            onClick = onSubmit,
            enabled = enabled && submitEnabled && !isSubmitting,
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
    onSave: (text: String, author: String, category: String, tags: String, language: String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val originalText = remember(quote.id) { quote.originalText() }
    val originalAuthor = remember(quote.id) { quote.editableAuthor() }
    val originalLanguage = remember(quote.id) { quote.knownLanguage() }
    // Saveable: the sheet comes back with the edits after a rotation (the activity is recreated).
    var text by rememberSaveable(quote.id) { mutableStateOf(originalText) }
    var author by rememberSaveable(quote.id) { mutableStateOf(originalAuthor) }
    var category by rememberSaveable(quote.id) { mutableStateOf(quote.category) }
    var tags by rememberSaveable(quote.id) { mutableStateOf(quote.tags) }
    var language by rememberSaveable(quote.id) { mutableStateOf(originalLanguage) }
    var confirmDiscard by rememberSaveable(quote.id) { mutableStateOf(false) }

    // Counted as the server counts: code points of the text as it will be stored.
    val textLength = storedQuoteTextLength(text)
    val textTooShort = textLength < SubmissionLimits.MIN_TEXT_LENGTH
    val textTooLong = textLength > SubmissionLimits.MAX_TEXT_LENGTH
    val authorLength = submissionLength(author)
    val authorTooLong = authorLength > SubmissionLimits.MAX_AUTHOR_LENGTH
    val parsedTags = parseQuoteTags(tags)
    val tagsInvalid = parsedTags.size > SubmissionLimits.MAX_TAGS ||
        parsedTags.any { submissionLength(it) > SubmissionLimits.MAX_TAG_LENGTH }
    val reviewed = quote.submissionStatus == QuoteSubmissionStatus.REJECTED ||
        quote.submissionStatus == QuoteSubmissionStatus.REMOVED
    // The same rule the ViewModel uses: the server identifies a submission by its normalized text and
    // author, so an edit it would see as the same quote keeps the review and cannot be resubmitted.
    val keepsReview = remember(quote.id, quote.submissionStatus, text, author) {
        quote.copy(text = originalText).editKeepsReview(text, author)
    }
    val needsChange = resubmit && keepsReview
    val canSave = !textTooShort && !textTooLong && !authorTooLong && !tagsInvalid && !needsChange
    val hasChanges = text != originalText || author != originalAuthor || category != quote.category ||
        tags != quote.tags || language != originalLanguage

    val unsavedChanges by rememberUpdatedState(hasChanges)
    var closing by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // A swipe down, Back or a tap outside asks first when there are unsaved changes.
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden && unsavedChanges && !closing) {
                confirmDiscard = true
                false
            } else {
                true
            }
        }
    )
    val close = {
        closing = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        // Back calls this even when the hide above was refused, so unsaved changes keep the sheet here too.
        onDismissRequest = {
            if (unsavedChanges && !closing) confirmDiscard = true else onDismiss()
        },
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
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                val hint = when {
                    resubmit -> "Revise the quote, then submit it again."
                    reviewed && keepsReview -> "The review stays, because the service still sees the same text and author."
                    reviewed -> "Saving resets the review status so you can submit the quote again."
                    else -> null
                }
                if (hint != null) {
                    Text(
                        uiText(hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            quote.reviewNote?.trim()?.takeIf { resubmit && it.isNotEmpty() }?.let { ReviewNote(it) }

            OutlinedTextField(
                value = text,
                // Longer imported text can still be shortened, but never grow past the limit.
                onValueChange = {
                    if (storedQuoteTextLength(it) <= SubmissionLimits.MAX_TEXT_LENGTH || storedQuoteTextLength(it) < textLength) text = it
                },
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
                        Text("$textLength/${SubmissionLimits.MAX_TEXT_LENGTH}")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            )

            OutlinedTextField(
                value = author,
                onValueChange = {
                    if (submissionLength(it) <= SubmissionLimits.MAX_AUTHOR_LENGTH || submissionLength(it) < authorLength) author = it
                },
                label = { Text(uiText("Author")) },
                placeholder = { Text(uiText("Unknown")) },
                isError = authorTooLong,
                supportingText = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = if (authorTooLong) uiText("At most 100 characters.") else "",
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Text("$authorLength/${SubmissionLimits.MAX_AUTHOR_LENGTH}")
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
                // The chips scroll out to the sheet edges instead of being cut off at its padding.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bleedHorizontally(20.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
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

            QuoteLanguageChoice(language = language, onSelect = { language = it })

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
                    onSave(text.trim(), author.trim(), category, tags.trim(), language)
                    close()
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

        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                title = { Text(uiText("Discard changes?")) },
                text = { Text(uiText("Your changes to this quote will be lost.")) },
                dismissButton = {
                    TextButton(onClick = { confirmDiscard = false }) {
                        Text(uiText("Keep editing"))
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDiscard = false
                        close()
                    }) {
                        Text(uiText("Discard"), color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    }
}

// The quote in the language it was written in; a personal row's display copy may show a translation.
private fun QuoteEntity.originalText(): String =
    if (alternateLanguage() == language) alternateText() ?: text else text

// A blank field stands for "Unknown"; the localized copy may say "Unbekannt".
private fun QuoteEntity.editableAuthor(): String = if (hasUnknownAuthor) "" else author

/**
 * Why the backend would reject this own quote, as an English UI key, or null when it can be sent.
 * Mirrors the server's limits, counted as it counts them (code points), so the user sees the
 * reason before the consent dialog.
 */
internal fun QuoteEntity.submissionProblem(): String? {
    // The stored text is already normalized, so this is the length the server sees.
    val textLength = submissionLength(originalText())
    val tagList = tags.split(',').map(String::trim).filter(String::isNotEmpty)
    return when {
        textLength < SubmissionLimits.MIN_TEXT_LENGTH -> "Too short to submit: a quote needs at least 3 characters."
        textLength > SubmissionLimits.MAX_TEXT_LENGTH -> "Too long to submit: a quote can have at most 500 characters."
        submissionLength(editableAuthor()) > SubmissionLimits.MAX_AUTHOR_LENGTH ->
            "The author name is too long to submit (max. 100 characters)."
        tagList.distinctBy { it.lowercase() }.size > SubmissionLimits.MAX_TAGS -> "Too many tags to submit (max. 8)."
        tagList.any { submissionLength(it) > SubmissionLimits.MAX_TAG_LENGTH } ->
            "A tag is too long to submit (max. 30 characters each)."
        else -> null
    }
}

/** When a favorite was saved: [relativeTime] of the stored epoch millis; old "dd MMM yyyy" values as they are. */
internal fun formatSavedDate(savedDate: String?, now: Long, language: String): String {
    if (savedDate == null) return ""
    val millis = savedDate.toLongOrNull() ?: return savedDate
    return relativeTime(millis, now, language)
}

/** "just now", "5 min ago", "yesterday", "3 days ago", then the date ("14 Oct 2026", German "14. Okt. 2026"). */
internal fun relativeTime(millis: Long, now: Long, language: String): String {
    val diff = now - millis
    return when {
        diff < 60_000L -> localizeUi("just now", language)
        diff < 3_600_000L -> countText("%d min ago", (diff / 60_000L).toInt(), language)
        diff < 86_400_000L -> countText("%d h ago", (diff / 3_600_000L).toInt(), language)
        diff < 172_800_000L -> localizeUi("yesterday", language)
        diff < THIRTY_DAYS_MS -> countText("%d days ago", (diff / 86_400_000L).toInt(), language)
        else -> absoluteDate(millis, language)
    }
}

/** "Submitted 3 days ago" / "Vor 3 Tagen eingereicht", "Submitted on 14 Oct 2026" / "Am 14. Okt. 2026 eingereicht". */
internal fun submittedText(submittedAt: Long, now: Long, language: String): String {
    val locale = if (language == QuoteLanguage.GERMAN) Locale.GERMAN else Locale.ENGLISH
    val text = if (now - submittedAt >= THIRTY_DAYS_MS) {
        localizeUi("Submitted on %s", language).format(absoluteDate(submittedAt, language))
    } else {
        localizeUi("Submitted %s", language).format(relativeTime(submittedAt, now, language))
    }
    return text.replaceFirstChar { it.titlecase(locale) }
}

// German writes the day as an ordinal: "14. Okt. 2026".
private fun absoluteDate(millis: Long, language: String): String =
    if (language == QuoteLanguage.GERMAN) {
        SimpleDateFormat("d. MMM yyyy", Locale.GERMAN).format(Date(millis))
    } else {
        SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(millis))
    }
