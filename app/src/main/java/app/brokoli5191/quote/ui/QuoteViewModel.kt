package app.brokoli5191.quote.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.brokoli5191.quote.BuildConfig
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.AuthorInfo
import app.brokoli5191.quote.data.AuthorRepository
import app.brokoli5191.quote.data.CategoryMapper
import app.brokoli5191.quote.data.CommunitySyncManager
import app.brokoli5191.quote.data.CommunitySyncStatus
import app.brokoli5191.quote.data.DailyHistoryEntry
import app.brokoli5191.quote.data.DeletedSubmissionOutcome
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.data.QuoteOrigin
import app.brokoli5191.quote.data.QuoteRepository
import app.brokoli5191.quote.data.QuoteSourceMode
import app.brokoli5191.quote.data.QuoteSubmissionClient
import app.brokoli5191.quote.data.QuoteSubmissionResult
import app.brokoli5191.quote.data.QuoteSubmissionStatus
import app.brokoli5191.quote.data.SubmissionError
import app.brokoli5191.quote.data.SubmissionLimits
import app.brokoli5191.quote.data.SubmissionRefreshResult
import app.brokoli5191.quote.data.SubmissionStatusInfo
import app.brokoli5191.quote.data.SubmissionUpdate
import app.brokoli5191.quote.data.dailyDateKey
import app.brokoli5191.quote.data.hasUnknownAuthor
import app.brokoli5191.quote.data.isListedIn
import app.brokoli5191.quote.data.isPlaceholderAuthor
import app.brokoli5191.quote.data.localized
import app.brokoli5191.quote.data.matchesSourceMode
import app.brokoli5191.quote.data.normalizeAuthorName
import app.brokoli5191.quote.data.normalizeQuoteText
import app.brokoli5191.quote.data.quoteMatchesQuery
import app.brokoli5191.quote.data.storedQuoteTextLength
import app.brokoli5191.quote.data.submissionLength
import app.brokoli5191.quote.data.submissionValidationError
import app.brokoli5191.quote.utils.AppLinks
import app.brokoli5191.quote.utils.CategoryQuoteVerifier
import app.brokoli5191.quote.utils.CategoryVerificationResult
import app.brokoli5191.quote.utils.CommunitySyncWorker
import app.brokoli5191.quote.utils.NotificationHelper
import app.brokoli5191.quote.utils.NotificationScheduler
import app.brokoli5191.quote.utils.ReleaseCheckResult
import app.brokoli5191.quote.utils.ReleaseInfo
import app.brokoli5191.quote.utils.UpdateChecker
import app.brokoli5191.quote.utils.UpdateStatus
import app.brokoli5191.quote.widget.WidgetRefreshScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Text and author handed to New Quote from another app (share sheet, text selection menu). */
data class QuoteDraft(val text: String, val author: String)

/** The community sync as the UI shows it. Times are epoch millis. */
sealed interface SyncUiState {
    data object Idle : SyncUiState
    data object Running : SyncUiState
    data class Success(val at: Long) : SyncUiState
    data class Failed(val at: Long, val lastSuccessAt: Long?) : SyncUiState
}

/** One-off messages for a snackbar or toast. [QuoteViewModel.events] delivers each one once. */
sealed interface QuoteUiEvent {
    /**
     * Review decisions (to approved, rejected or removed) that a sync or status check in the app found.
     * The background worker posts a notification for the ones it finds itself.
     */
    data class ReviewDecisions(val updates: List<SubmissionUpdate>) : QuoteUiEvent

    /** [count] deleted quotes are still in review: the service could not be reached, every sync tries again. */
    data class WithdrawalQueued(val count: Int) : QuoteUiEvent

    /**
     * [count] deleted quotes could not be withdrawn and may be published. [code] is a [SubmissionError] code,
     * [SubmissionError.ALREADY_REVIEWED] when a moderator was faster.
     */
    data class WithdrawalFailed(val code: String, val count: Int) : QuoteUiEvent
}

/** Orders of Favorites and My Quotes, see [QuoteViewModel.setFavoritesSort]. */
object SavedSort {
    /** Newest first: by save time in Favorites, by creation time in My Quotes. */
    const val RECENT = "recent"
    const val OLDEST = "oldest"
    const val AUTHOR = "author"
    const val TEXT = "text"

    internal val all = setOf(RECENT, OLDEST, AUTHOR, TEXT)
}

/** Review-state filters of My Quotes, see [QuoteViewModel.setSavedStatusFilter]. */
object SavedStatusFilter {
    const val ALL = "all"
    const val NOT_SUBMITTED = "not_submitted"
    const val PENDING = "pending"
    const val APPROVED = "approved"

    /** Rejected, or approved and no longer published. */
    const val NOT_APPROVED = "not_approved"

    internal val all = setOf(ALL, NOT_SUBMITTED, PENDING, APPROVED, NOT_APPROVED)
}

private const val UNKNOWN_AUTHOR = "Unknown"

// The backend's cleanText before it hashes a submission: invisible format characters and control
// characters are dropped, runs of whitespace (JavaScript's \s) become one space.
private val SERVER_DROPPED_CHARACTERS = Regex(
    "[\\u00AD\\u061C\\u180E\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u2066-\\u2069\\uFEFF" +
        "\\u0000-\\u0008\\u000E-\\u001F\\u007F-\\u009F]"
)
private val SERVER_WHITESPACE =
    Regex("[\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]+")

/**
 * What the backend identifies a submission by: text and author as the app stores and sends them,
 * cleaned like the server cleans them, NFKC-normalized and lowercased. Two versions with the same key
 * are one submission for the server, so a reviewed one cannot be submitted again with this content.
 */
fun submissionContentKey(text: String, author: String): String =
    listOf(normalizeQuoteText(text), normalizeAuthorName(author)).joinToString("\n") { value ->
        val cleaned = value.replace(SERVER_DROPPED_CHARACTERS, "").replace(SERVER_WHITESPACE, " ").trim()
        Normalizer.normalize(cleaned, Normalizer.Form.NFKC).lowercase(Locale.US)
    }

/**
 * True when editing this rejected or removed own quote to [text] and [author] keeps its review: the
 * server still sees the same submission ([submissionContentKey]), so it would refuse it again. Use it
 * before "Save and submit"; it works on the stored row and on its display copy. A blank [author]
 * keeps a placeholder author ("Unknown", "Anonymous"), as [QuoteViewModel.updateUserQuote] does.
 */
fun QuoteEntity.editKeepsReview(text: String, author: String): Boolean =
    (submissionStatus == QuoteSubmissionStatus.REJECTED || submissionStatus == QuoteSubmissionStatus.REMOVED) &&
        submissionContentKey(text, editedAuthor(this.author, author)) == submissionContentKey(this.text, this.author)

// The author an edit stores: a blank field keeps a placeholder author as it is stored, otherwise it
// means the author is not known.
private fun editedAuthor(stored: String, edited: String): String =
    edited.trim().ifBlank { stored.takeIf(::isPlaceholderAuthor) ?: UNKNOWN_AUTHOR }

class QuoteViewModel(
    application: Application,
    private val repository: QuoteRepository,
    // Navigation state that survives process death (tab, sub-tab, searches, filters, New Quote).
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val submissionClient: QuoteSubmissionClient = QuoteSubmissionClient(),
    private val communitySyncManager: CommunitySyncManager =
        CommunitySyncManager(application, repository, submissionClient = submissionClient)
) : AndroidViewModel(application) {

    private val app get() = getApplication<Application>()
    private val prefs get() = app.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
    private val authorRepository = AuthorRepository(app, AppDatabase.getInstance(app).authorInfoDao())
    private val backupManager = BackupManager(app, repository, communitySyncManager)

    private val updateCheckRunning = AtomicBoolean(false)
    // The release the last check found, for the expected size of its download.
    private var latestRelease: ReleaseInfo? = null
    private var downloadJob: Job? = null
    private var seedJob: Job? = null
    private var syncJob: Job? = null
    private var syncAgainWhenDone = false
    private var dailyQuoteJob: Job? = null
    private var dailyUserActionJob: Job? = null
    // The day of the quote in _dailyQuote.
    private var shownQuoteDate = ""
    private var widgetRefreshPending = false
    // Quotes that "Another quote" replaced today, newest last, for undoCycleDailyQuote.
    private val cycledAway = ArrayDeque<Int>()
    private var cycledAwayDate = ""
    // Raw rows of recently deleted quotes, so Undo restores stored data and not a localized display copy.
    private val recentlyDeleted = HashMap<Int, QuoteEntity>()
    // Saved dates of recently unfavorited quotes, so Undo keeps the original date.
    private val recentlyUnfavorited = HashMap<Int, String?>()
    // Favorite, delete and restore changes are applied in the order they were made, so an Undo never
    // lands before the change it undoes.
    private val quoteEditMutex = Mutex()
    private var addJob: Job? = null
    private var lastAddRequest: String? = null
    private var lastAddAt = 0L
    private val refreshCallbacks = ArrayList<(SubmissionRefreshResult) -> Unit>()

    private val _events = Channel<QuoteUiEvent>(Channel.BUFFERED)

    /** One-off messages (withdrawal outcomes, review decisions); collect it in one place. */
    val events: Flow<QuoteUiEvent> = _events.receiveAsFlow()

    // Must stay a MutableStateFlow named _dailyQuote: the screenshot harness sets it directly.
    private val _dailyQuote = MutableStateFlow<QuoteEntity?>(null)
    val dailyQuote: StateFlow<QuoteEntity?> = _dailyQuote.asStateFlow()

    // The day the shown daily quote belongs to.
    private val _dailyDate = MutableStateFlow("")

    private val _canUndoCycle = MutableStateFlow(false)
    val canUndoCycle: StateFlow<Boolean> = _canUndoCycle.asStateFlow()

    private val _verificationResult = MutableStateFlow<CategoryVerificationResult?>(null)
    val verificationResult: StateFlow<CategoryVerificationResult?> = _verificationResult.asStateFlow()

    private val _selectedTab = savedState.getMutableStateFlow(KEY_TAB, TAB_DAILY).apply {
        if (value !in TABS) value = TAB_DAILY
    }
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    private val _searchQuery = savedState.getMutableStateFlow(KEY_LIBRARY_SEARCH, "")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategories = savedState.getMutableStateFlow<Set<String>>(KEY_LIBRARY_CATEGORIES, emptySet()).apply {
        if (!SELECTABLE_CATEGORIES.containsAll(value)) value = value.intersect(SELECTABLE_CATEGORIES)
    }
    val selectedCategories: StateFlow<Set<String>> = _selectedCategories.asStateFlow()

    private val _widgetStyle = MutableStateFlow("Quote")
    val widgetStyle: StateFlow<String> = _widgetStyle.asStateFlow()

    private val _themeMode = MutableStateFlow("DARK")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _themeAccent = MutableStateFlow("Violet")
    val themeAccent: StateFlow<String> = _themeAccent.asStateFlow()

    private val _quoteSourceMode = MutableStateFlow(QuoteSourceMode.ALL)
    val quoteSourceMode: StateFlow<String> = _quoteSourceMode.asStateFlow()

    private val _appLanguage = MutableStateFlow(QuoteLanguage.ENGLISH)
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    private val _showAnonymousQuotes = MutableStateFlow(false)
    val showAnonymousQuotes: StateFlow<Boolean> = _showAnonymousQuotes.asStateFlow()

    private val _syncState = MutableStateFlow(syncStateOf(communitySyncManager.syncStatus()))

    /** Starts from the persisted outcome of the last sync (in the app or the background worker). */
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    /** True while no sync runs and one has finished, successfully or not. [syncState] tells which. */
    val communitySyncFinished: StateFlow<Boolean> = _syncState
        .map { it.isFinished() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _syncState.value.isFinished())

    private val _amoledBlack = MutableStateFlow(false)
    val amoledBlack: StateFlow<Boolean> = _amoledBlack.asStateFlow()

    private val _dailyReminderEnabled = MutableStateFlow(false)
    val dailyReminderEnabled: StateFlow<Boolean> = _dailyReminderEnabled.asStateFlow()

    private val _dailyReminderHour = MutableStateFlow(8)
    val dailyReminderHour: StateFlow<Int> = _dailyReminderHour.asStateFlow()

    private val _dailyReminderMinute = MutableStateFlow(0)
    val dailyReminderMinute: StateFlow<Int> = _dailyReminderMinute.asStateFlow()

    private val _lowPerformanceMode = MutableStateFlow(false)
    val lowPerformanceMode: StateFlow<Boolean> = _lowPerformanceMode.asStateFlow()

    private val _blurNavigationSurfaces = MutableStateFlow(false)
    val blurNavigationSurfaces: StateFlow<Boolean> = _blurNavigationSurfaces.asStateFlow()

    private val _devModeUnlocked = MutableStateFlow(false)
    val devModeUnlocked: StateFlow<Boolean> = _devModeUnlocked.asStateFlow()

    private val _showDevScreen = MutableStateFlow(false)
    val showDevScreen: StateFlow<Boolean> = _showDevScreen.asStateFlow()

    private val _showNewQuoteScreen = savedState.getMutableStateFlow(KEY_NEW_QUOTE_OPEN, false)
    val showNewQuoteScreen: StateFlow<Boolean> = _showNewQuoteScreen.asStateFlow()

    private val _newQuoteHasDraft = savedState.getMutableStateFlow(KEY_NEW_QUOTE_HAS_DRAFT, false)

    /** Whether the open New Quote form holds unsaved input; leaving it then needs the discard dialog. */
    val newQuoteHasDraft: StateFlow<Boolean> = _newQuoteHasDraft.asStateFlow()

    private val _pendingDraft = MutableStateFlow<QuoteDraft?>(null)

    /** Text from another app waiting for New Quote, see [setPendingDraft]. */
    val pendingDraft: StateFlow<QuoteDraft?> = _pendingDraft.asStateFlow()

    private val _savedSubTab = savedState.getMutableStateFlow(KEY_SAVED_SUB_TAB, SUB_TAB_FAVORITES).apply {
        if (value != SUB_TAB_FAVORITES && value != SUB_TAB_MY_QUOTES) value = SUB_TAB_FAVORITES
    }
    val savedSubTab: StateFlow<String> = _savedSubTab.asStateFlow()

    private val _favoritesSort = MutableStateFlow(SavedSort.RECENT)

    /** Order of Favorites and My Quotes, one of the [SavedSort] values. */
    val favoritesSort: StateFlow<String> = _favoritesSort.asStateFlow()

    private val _savedSearchQuery = savedState.getMutableStateFlow(KEY_SAVED_SEARCH, "")

    /** Search inside Saved, applied to Favorites and My Quotes; separate from the Library search. */
    val savedSearchQuery: StateFlow<String> = _savedSearchQuery.asStateFlow()

    private val _savedStatusFilter = savedState.getMutableStateFlow(KEY_SAVED_STATUS_FILTER, SavedStatusFilter.ALL).apply {
        if (value !in SavedStatusFilter.all) value = SavedStatusFilter.ALL
    }

    /** Review-state filter of My Quotes, one of the [SavedStatusFilter] values. */
    val savedStatusFilter: StateFlow<String> = _savedStatusFilter.asStateFlow()

    private val _autoUpdateEnabled = MutableStateFlow(false)
    val autoUpdateEnabled: StateFlow<Boolean> = _autoUpdateEnabled.asStateFlow()

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    private val _submittingQuoteIds = MutableStateFlow<Set<Int>>(emptySet())
    val submittingQuoteIds: StateFlow<Set<Int>> = _submittingQuoteIds.asStateFlow()

    private val _submissionRefreshRunning = MutableStateFlow(false)

    /** True while [refreshSubmissionStatuses] checks the review states. */
    val submissionRefreshRunning: StateFlow<Boolean> = _submissionRefreshRunning.asStateFlow()

    private val _showOnboarding = MutableStateFlow(false)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    private val _authorImagesEnabled = MutableStateFlow(true)
    val authorImagesEnabled: StateFlow<Boolean> = _authorImagesEnabled.asStateFlow()

    // A Library search or filter left behind on another tab is no back step: back there goes to Daily
    // (and drops it), and on Daily the app may close. On Library itself the tab already counts.
    val hasBackStack: StateFlow<Boolean> = combine(_showDevScreen, _showNewQuoteScreen, _selectedTab, ::backStackFor)
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            backStackFor(_showDevScreen.value, _showNewQuoteScreen.value, _selectedTab.value)
        )

    // Lists are filtered and localized on Dispatchers.Default. Room re-emits on every write to the
    // quotes table, so identical results are dropped before the work is done again.
    // Shared eagerly: authorQuoteCount reads from it on any screen.
    val allQuotes: StateFlow<List<QuoteEntity>> = combine(
        repository.allQuotes.distinctUntilChanged(), appLanguage, showAnonymousQuotes
    ) { quotes, language, showAnonymous ->
        // Own quotes are listed in every language, showing their original text without a translation.
        quotes.asSequence()
            .filter { it.isListedIn(language, showAnonymous) }
            .map { it.localized(language) }
            .toList()
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Library quotes per author (see libraryAuthorKey), as a search for the author lists them.
    private val authorQuoteCounts: StateFlow<Map<String, Int>> = combine(allQuotes, quoteSourceMode) { quotes, sourceMode ->
        quotes.asSequence()
            .filter { it.matchesSourceMode(sourceMode) && !it.hasUnknownAuthor }
            .distinctBy(::libraryKey)
            .groupingBy { libraryAuthorKey(it.author) }
            .eachCount()
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val storedFavorites: SharedFlow<List<QuoteEntity>> = repository.favorites
        .distinctUntilChanged()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    // Favorites are the user's data: listed in every language and whatever the anonymous setting, with
    // their original text when there is no translation. A favorited own quote and its favorited
    // community copy are the same quote: the own one is kept.
    private val listedFavorites: SharedFlow<List<QuoteEntity>> = combine(storedFavorites, appLanguage) { quotes, language ->
        val linkedCopies = linkedCommunityCopies(quotes)
        quotes.filter { it.serverId !in linkedCopies }.map { it.localized(language) }
    }
        .flowOn(Dispatchers.Default)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    /** Number of favorites before the Saved search applies. */
    val favoritesTotal: StateFlow<Int> = listedFavorites
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Favorites in the [favoritesSort] order, narrowed by [savedSearchQuery]. */
    val favorites: StateFlow<List<QuoteEntity>> = combine(
        listedFavorites, _favoritesSort, _savedSearchQuery, appLanguage
    ) { quotes, sort, query, language ->
        quotes.filter { quoteMatchesQuery(it, query, language) }
            .sortedForSaved(sort, language) { it.savedDate?.toLongOrNull() }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // My Quotes always shows every own quote, whatever the app language.
    private val listedOwnQuotes: SharedFlow<List<QuoteEntity>> = combine(
        repository.userAdded.distinctUntilChanged(), appLanguage
    ) { quotes, language ->
        quotes.map { it.localized(language) }
    }
        .flowOn(Dispatchers.Default)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    /** Number of own quotes before the Saved search and status filter apply. */
    val userAddedTotal: StateFlow<Int> = listedOwnQuotes
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Own quotes in the [favoritesSort] order, narrowed by [savedSearchQuery] and [savedStatusFilter]. */
    val userAdded: StateFlow<List<QuoteEntity>> = combine(
        listedOwnQuotes, _favoritesSort, _savedSearchQuery, _savedStatusFilter, appLanguage
    ) { quotes, sort, query, statusFilter, language ->
        quotes.filter { it.matchesStatusFilter(statusFilter) && quoteMatchesQuery(it, query, language) }
            .sortedForSaved(sort, language) { it.timestamp }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredQuotes: StateFlow<List<QuoteEntity>> = combine(
        allQuotes,
        searchQuery,
        selectedCategories,
        quoteSourceMode,
        appLanguage
    ) { quotes, query, categories, sourceMode, language ->
        var list = if ("Community" in categories || "Local" in categories) {
            // Own quotes are listed here, so their published community copies would show twice.
            val linkedCopies = linkedCommunityCopies(quotes)
            if (linkedCopies.isEmpty()) quotes else quotes.filter { it.serverId !in linkedCopies }
        } else {
            quotes.filter { it.matchesSourceMode(sourceMode) }
        }
        if (categories.isNotEmpty()) {
            list = list.filter { q ->
                categories.any { cat ->
                    when (cat) {
                        "Community" -> q.origin == QuoteOrigin.COMMUNITY ||
                            (q.origin == QuoteOrigin.PERSONAL && q.submissionStatus == QuoteSubmissionStatus.APPROVED)
                        "Local" -> q.origin == QuoteOrigin.PERSONAL &&
                            q.submissionStatus != QuoteSubmissionStatus.APPROVED
                        else -> q.category.equals(cat, ignoreCase = true)
                    }
                }
            }
        }
        list.filter { quoteMatchesQuery(it, query, language) }.distinctBy(::libraryKey)
    }
        // Filter ~2500 rows off the main thread so search keystrokes don't jank.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val dailySettings: Flow<DailySettings> =
        combine(_quoteSourceMode, _appLanguage, _showAnonymousQuotes, ::DailySettings)

    /**
     * The daily quotes of earlier days (newest first, today excluded) for the current source, language
     * and anonymous setting. Follows the daily quote, the day and favorite changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val dailyHistory: StateFlow<List<DailyHistoryEntry>> = combine(
        _dailyQuote.map { it?.id }.distinctUntilChanged(),
        _dailyDate,
        dailySettings,
        storedFavorites.map { quotes -> quotes.mapTo(HashSet()) { it.id } }.distinctUntilChanged()
    ) { _, today, settings, _ -> today to settings }
        .mapLatest { (today, settings) ->
            repository.dailyHistory(settings.sourceMode, settings.language, settings.showAnonymous)
                .filter { it.date != today }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        _showOnboarding.value = resolveOnboarding()
        loadThemeSettings()
        // Auto Backup, device transfers and force-stops keep the reminder setting but lose its alarm.
        NotificationScheduler.ensureScheduled(app)
        NotificationHelper.refreshChannels(app)
        loadDailyQuote()
        syncCommunityQuotes()
        reportFailedWithdrawals()
        CommunitySyncWorker.schedule(app)
    }

    // Must run before loadThemeSettings, which writes theme_mode on a fresh install.
    private fun resolveOnboarding(): Boolean {
        if (prefs.contains(KEY_ONBOARDING_COMPLETED)) return !prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        val existingInstall = prefs.contains("database_json_seeded_v11") || prefs.contains("theme_mode")
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, existingInstall).apply()
        return !existingInstall
    }

    fun completeOnboarding() {
        _showOnboarding.value = false
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
    }

    fun resetOnboarding() {
        // Onboarding shows right away; finishing it should land in the app, not back in the dev tools.
        _showDevScreen.value = false
        _showOnboarding.value = true
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, false).apply()
    }

    /** Pulls community quotes and refreshes submission states; [syncState] follows it. */
    fun syncCommunityQuotes() = startSync(queueIfRunning = false)

    // [queueIfRunning]: a sync that is already running may have started too early (e.g. before a restore
    // wrote its rows), so another one follows it.
    private fun startSync(queueIfRunning: Boolean) {
        if (syncJob?.isActive == true) {
            if (queueIfRunning) syncAgainWhenDone = true
            return
        }
        syncJob = viewModelScope.launch {
            do {
                syncAgainWhenDone = false
                _syncState.value = SyncUiState.Running
                val result = try {
                    // Also refreshes submission states and updates the widget when something changed.
                    communitySyncManager.syncWithResult()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
                val now = System.currentTimeMillis()
                _syncState.value = if (result?.feedSynced == true) {
                    SyncUiState.Success(now)
                } else {
                    SyncUiState.Failed(now, communitySyncManager.syncStatus().lastSuccessAt)
                }
                loadDailyQuote()
                result?.let { reportReviewDecisions(it.submissions.updates) }
                reportFailedWithdrawals()
            } while (syncAgainWhenDone)
        }
    }

    /**
     * Call from the activity's ON_RESUME. Picks up a new day and syncs community quotes and submission
     * states unless a sync (here or in the background worker) succeeded in the last 15 minutes.
     */
    fun onAppResumed() {
        refreshDailyQuoteIfNeeded()
        reportFailedWithdrawals()
        if (syncJob?.isActive == true) return
        val status = communitySyncManager.syncStatus()
        _syncState.value = syncStateOf(status)
        val sinceSuccess = System.currentTimeMillis() - (status.lastSuccessAt ?: 0L)
        if (!status.lastAttemptFailed && sinceSuccess in 0 until RESUME_SYNC_INTERVAL_MS) return
        syncCommunityQuotes()
    }

    fun runVerification() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = CategoryQuoteVerifier.verify(repository)
            _verificationResult.value = result
        }
    }

    fun checkAndSeedDatabase() {
        if (seedJob?.isActive == true) return
        seedJob = viewModelScope.launch {
            val changed = withContext(Dispatchers.IO) { seedAndRepairStoredQuotes() }
            // App, widget and reminder show the same stored pick, so a new seed reaches all of them.
            if (changed) loadDailyQuote(refreshWidget = true)
            checkForUpdatesIfNeeded()
        }
    }

    // Returns whether stored quotes changed.
    private suspend fun seedAndRepairStoredQuotes(): Boolean {
        var changed = false
        if (!prefs.getBoolean(SEEDED_FLAG, false) || repository.getQuotesCount() < MIN_SEEDED_QUOTES) {
            // Favorite-preserving re-seed: keeps user-added and community quotes, and bundled rows keep
            // their ids, favorites and saved dates, so today's quote and the no-repeat history stay.
            repository.reseedPreservingFavorites(app)
            prefs.edit().putBoolean(SEEDED_FLAG, true).apply()
            changed = true
        }
        if (!prefs.getBoolean(KEY_TEXT_REPAIRED, false)) {
            // The bundled rows were just re-seeded with the current normalizer, so only own and
            // community rows need the repair. Runs under the sync lock so it never interleaves with a
            // sync writing the same rows.
            CommunitySyncManager.mutex.withLock {
                repository.repairStoredText(setOf(QuoteOrigin.PERSONAL, QuoteOrigin.COMMUNITY))
            }
            prefs.edit().putBoolean(KEY_TEXT_REPAIRED, true).apply()
            changed = true
        }
        return changed
    }

    private fun loadThemeSettings() {
        val storedMode = prefs.getString("theme_mode", "DARK") ?: "DARK"
        val migratedMode = when {
            storedMode == "AMOLED" -> "AMOLED"
            storedMode == "DARK" && prefs.getBoolean("amoled_black", false) -> "AMOLED"
            storedMode in setOf("LIGHT", "DARK", "DYNAMIC") -> storedMode
            else -> "DARK"
        }
        _themeMode.value = migratedMode
        _amoledBlack.value = migratedMode == "AMOLED"
        val storedLanguage = prefs.getString(KEY_APP_LANGUAGE, null)
        val editor = prefs.edit()
            .putString("theme_mode", migratedMode)
            .putBoolean("amoled_black", migratedMode == "AMOLED")
        // First start: German devices start in German (onboarding, widget and reminder alike); a stored
        // choice, or a restored backup's, always wins.
        if (storedLanguage == null) editor.putString(KEY_APP_LANGUAGE, deviceLanguage())
        editor.apply()
        _themeAccent.value = prefs.getString("theme_accent", "Violet")
            ?.takeIf { it in setOf("Violet", "Amber", "Green", "Blue", "Rose") }
            ?: "Violet"
        _quoteSourceMode.value = prefs.getString("quote_source_mode", QuoteSourceMode.ALL)
            ?.takeIf { it in SOURCE_MODES }
            ?: QuoteSourceMode.ALL
        _appLanguage.value = (storedLanguage ?: deviceLanguage())
            .takeIf { it == QuoteLanguage.ENGLISH || it == QuoteLanguage.GERMAN }
            ?: QuoteLanguage.ENGLISH
        _showAnonymousQuotes.value = prefs.getBoolean("show_anonymous_quotes", false)
        _widgetStyle.value = prefs.getString("widget_style", "Quote") ?: "Quote"
        _dailyReminderEnabled.value = prefs.getBoolean("daily_reminder_enabled", false)
        _dailyReminderHour.value = prefs.getInt("daily_reminder_hour", 8)
        _dailyReminderMinute.value = prefs.getInt("daily_reminder_minute", 0)
        _lowPerformanceMode.value = prefs.getBoolean("low_performance_mode", false)
        _blurNavigationSurfaces.value = prefs.getBoolean("blur_navigation_surfaces", false)
        _autoUpdateEnabled.value = prefs.getBoolean("auto_update_enabled", true)
        _authorImagesEnabled.value = prefs.getBoolean(KEY_AUTHOR_IMAGES, true)
        _favoritesSort.value = prefs.getString(KEY_FAVORITES_SORT, SavedSort.RECENT)
            ?.takeIf { it in SavedSort.all }
            ?: SavedSort.RECENT
    }

    private fun deviceLanguage(): String =
        if (Locale.getDefault().language == QuoteLanguage.GERMAN) QuoteLanguage.GERMAN else QuoteLanguage.ENGLISH

    fun setAuthorImagesEnabled(enabled: Boolean) {
        _authorImagesEnabled.value = enabled
        prefs.edit().putBoolean(KEY_AUTHOR_IMAGES, enabled).apply()
    }

    /**
     * Portrait, short description and Wikipedia link for [author] in [language], or null for
     * unknown authors and names without a verified match. The image (and the runtime lookup for
     * names outside the bundled map) is only used when author images are enabled.
     */
    suspend fun loadAuthorInfo(author: String, language: String = _appLanguage.value): AuthorInfo? =
        authorRepository.authorInfo(author, language, _authorImagesEnabled.value)

    private val authorInfoFlows = object : LinkedHashMap<String, StateFlow<AuthorInfo?>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StateFlow<AuthorInfo?>>?): Boolean =
            size > 100
    }

    /** [loadAuthorInfo] as a flow that follows the app language and the author-images setting. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun authorInfo(author: String): StateFlow<AuthorInfo?> = synchronized(authorInfoFlows) {
        authorInfoFlows.getOrPut(author) {
            combine(_appLanguage, _authorImagesEnabled) { language, images -> language to images }
                .mapLatest { (language, images) -> authorRepository.authorInfo(author, language, images) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
        }
    }

    /**
     * How many Library quotes [author] has under the current source, language and anonymous setting
     * (same author, ignoring case), as the "More quotes by" button counts them. 0 for unknown authors.
     */
    fun authorQuoteCount(author: String): Int = authorQuoteCounts.value[libraryAuthorKey(author)] ?: 0

    /** Opens the Library with [query] as its search and no category filter (author or tag shortcuts). */
    fun openLibrarySearch(query: String) {
        _searchQuery.value = query.trim()
        clearCategorySelection()
        selectTab(TAB_LIBRARY)
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        _autoUpdateEnabled.value = enabled
        prefs.edit().putBoolean("auto_update_enabled", enabled).apply()
    }

    /** The automatic check: once a day when enabled, silent on failure. */
    fun checkForUpdatesIfNeeded() {
        if (!_autoUpdateEnabled.value || _updateStatus.value !is UpdateStatus.Idle) return
        val lastCheck = prefs.getString(KEY_LAST_UPDATE_CHECK, "") ?: ""
        if (lastCheck == dailyDateKey()) return
        checkForUpdates(manual = false)
    }

    /** Settings' "Check": community quotes and app updates. A failed update check shows an error. */
    fun checkForUpdatesManually() {
        syncCommunityQuotes()
        checkForUpdates(manual = true)
    }

    private fun checkForUpdates(manual: Boolean) {
        if (_updateStatus.value is UpdateStatus.Downloading) return
        if (!updateCheckRunning.compareAndSet(false, true)) return
        val before = _updateStatus.value
        _updateStatus.value = UpdateStatus.Checking
        viewModelScope.launch {
            try {
                _updateStatus.value = updateStatusAfter(UpdateChecker.fetchLatestRelease(), before, manual)
            } finally {
                updateCheckRunning.set(false)
            }
        }
    }

    private fun updateStatusAfter(result: ReleaseCheckResult, before: UpdateStatus, manual: Boolean): UpdateStatus {
        if (result is ReleaseCheckResult.Found || result is ReleaseCheckResult.NoApk) {
            // A real answer holds for the day; failures are tried again at the next start.
            prefs.edit().putString(KEY_LAST_UPDATE_CHECK, dailyDateKey()).apply()
        }
        val ready = before as? UpdateStatus.ReadyToInstall
        if (result !is ReleaseCheckResult.Found) {
            // A finished download stays ready, and an automatic check never shows an error.
            return ready ?: if (manual) UpdateStatus.Error(updateCheckErrorMessage(result)) else before
        }
        val release = result.release
        latestRelease = release
        return when {
            !UpdateChecker.isNewerVersion(BuildConfig.VERSION_NAME, release.version) -> UpdateStatus.UpToDate
            ready != null && ready.version == release.version && File(ready.filePath).exists() -> ready
            else -> UpdateStatus.UpdateAvailable(release.version, release.downloadUrl, release.sizeBytes)
        }
    }

    // English; the Settings screen localizes them.
    private fun updateCheckErrorMessage(result: ReleaseCheckResult): String = when (result) {
        ReleaseCheckResult.Offline -> UPDATE_ERROR_OFFLINE
        ReleaseCheckResult.RateLimited -> UPDATE_ERROR_RATE_LIMITED
        ReleaseCheckResult.NoApk -> UPDATE_ERROR_NO_APK
        else -> UPDATE_ERROR_SERVER
    }

    /** [expectedSize] 0 uses the size the last check reported for [downloadUrl]. */
    fun downloadUpdate(downloadUrl: String, version: String, expectedSize: Long = 0L) {
        if (downloadJob?.isActive == true) return
        val size = expectedSize.takeIf { it > 0L }
            ?: latestRelease?.takeIf { it.downloadUrl == downloadUrl }?.sizeBytes
            ?: 0L
        _updateStatus.value = UpdateStatus.Downloading(0)
        downloadJob = viewModelScope.launch {
            val path = UpdateChecker.downloadApk(app, downloadUrl, version, size) { progress ->
                _updateStatus.value = UpdateStatus.Downloading(progress)
            }
            _updateStatus.value = if (path != null) {
                UpdateStatus.ReadyToInstall(path, version)
            } else {
                UpdateStatus.Error(UPDATE_ERROR_DOWNLOAD)
            }
        }
    }

    fun installUpdate(context: Context, filePath: String) {
        UpdateChecker.installApk(context, filePath)
    }

    fun dismissUpdateError() {
        _updateStatus.value = UpdateStatus.Idle
    }

    fun setLowPerformanceMode(enabled: Boolean) {
        _lowPerformanceMode.value = enabled
        prefs.edit().putBoolean("low_performance_mode", enabled).apply()
    }

    fun setBlurNavigationSurfaces(enabled: Boolean) {
        _blurNavigationSurfaces.value = enabled
        prefs.edit().putBoolean("blur_navigation_surfaces", enabled).apply()
    }

    fun setThemeMode(mode: String) {
        if (mode !in setOf("LIGHT", "DARK", "AMOLED", "DYNAMIC")) return
        _themeMode.value = mode
        _amoledBlack.value = mode == "AMOLED"
        prefs.edit()
            .putString("theme_mode", mode)
            .putBoolean("amoled_black", mode == "AMOLED")
            .apply()
    }

    fun setAmoledBlack(enabled: Boolean) {
        setThemeMode(if (enabled) "AMOLED" else "DARK")
    }

    fun setThemeAccent(accent: String) {
        if (accent !in setOf("Violet", "Amber", "Green", "Blue", "Rose")) return
        _themeAccent.value = accent
        prefs.edit().putString("theme_accent", accent).apply()
    }

    fun setQuoteSourceMode(mode: String) {
        if (mode !in SOURCE_MODES) return
        _quoteSourceMode.value = mode
        prefs.edit().putString("quote_source_mode", mode).apply()
        loadDailyQuote(refreshWidget = true)
    }

    fun setAppLanguage(language: String) {
        if (language != QuoteLanguage.ENGLISH && language != QuoteLanguage.GERMAN) return
        _appLanguage.value = language
        prefs.edit().putString(KEY_APP_LANGUAGE, language).apply()
        clearLibraryFilter()
        // The system's notification settings list the channels in the app language.
        NotificationHelper.refreshChannels(app)
        loadDailyQuote(refreshWidget = true)
    }

    fun setShowAnonymousQuotes(enabled: Boolean) {
        _showAnonymousQuotes.value = enabled
        prefs.edit().putBoolean("show_anonymous_quotes", enabled).apply()
        if (!enabled) _selectedCategories.value = _selectedCategories.value - "Reflections"
        loadDailyQuote(refreshWidget = true)
    }

    /**
     * Loads today's quote for the current settings. [refreshWidget] re-renders the widget once the pick
     * is stored (settings changes, a new day, a new seed), so widget and app never disagree.
     */
    fun loadDailyQuote(refreshWidget: Boolean = false) {
        val today = dailyDateKey()
        if (refreshWidget) widgetRefreshPending = true
        updateDailyQuote(today, userAction = false) {
            repository.getDailyQuote(today, _quoteSourceMode.value, _appLanguage.value, _showAnonymousQuotes.value)
        }
    }

    // Every change of the shown daily quote goes through here, so the last one asked for is the one
    // shown. A newer change cancels a load still running (a slow pick made under earlier settings never
    // lands last) but waits for "Another quote" and its undo, which the user asked for. A widget refresh
    // that a cancelled load asked for is sent by the change that replaced it, after its pick is stored.
    private fun updateDailyQuote(today: String, userAction: Boolean, pick: suspend () -> QuoteEntity?) {
        if (today != _dailyDate.value) {
            _dailyDate.value = today
            clearCycleUndo()
        }
        val running = dailyQuoteJob
        if (running !== dailyUserActionJob) running?.cancel()
        val userActionBefore = dailyUserActionJob
        val job = viewModelScope.launch {
            userActionBefore?.join()
            _dailyQuote.value = pick()
            shownQuoteDate = today
            if (widgetRefreshPending) {
                widgetRefreshPending = false
                WidgetRefreshScheduler.requestUpdate(app)
            }
        }
        dailyQuoteJob = job
        if (userAction) dailyUserActionJob = job
    }

    /** Picks up a new day; the widget follows, since the app now shows the new day's quote. */
    fun refreshDailyQuoteIfNeeded() {
        if (_dailyDate.value != dailyDateKey()) loadDailyQuote(refreshWidget = true)
    }

    /**
     * "Another quote": replaces today's quote with one not shown in the current no-repeat cycle. The
     * replaced quote can be brought back with [undoCycleDailyQuote]; widget and reminder follow.
     */
    fun cycleDailyQuote() {
        val today = dailyDateKey()
        widgetRefreshPending = true
        updateDailyQuote(today, userAction = true) {
            // Read when it runs: an earlier "Another quote" may just have replaced the shown quote.
            val replaced = _dailyQuote.value?.id?.takeIf { shownQuoteDate == today }
            val next = repository.cycleDailyQuote(today, _quoteSourceMode.value, _appLanguage.value, _showAnonymousQuotes.value)
            if (replaced != null && next != null && next.id != replaced) rememberCycledAway(today, replaced)
            next
        }
    }

    /** Brings back the quote the last [cycleDailyQuote] replaced today. */
    fun undoCycleDailyQuote() {
        if (!_canUndoCycle.value) return
        val today = dailyDateKey()
        widgetRefreshPending = true
        updateDailyQuote(today, userAction = true) {
            if (cycledAwayDate != today) cycledAway.clear()
            val previous = cycledAway.removeLastOrNull()
            _canUndoCycle.value = cycledAway.isNotEmpty()
            val mode = _quoteSourceMode.value
            val language = _appLanguage.value
            val showAnonymous = _showAnonymousQuotes.value
            // Null when that quote is gone or no longer fits the settings: today's pick stays.
            previous?.let { repository.restoreDailySelection(today, mode, language, showAnonymous, it) }
                ?: repository.getDailyQuote(today, mode, language, showAnonymous)
        }
    }

    private fun rememberCycledAway(date: String, quoteId: Int) {
        if (cycledAwayDate != date) {
            cycledAway.clear()
            cycledAwayDate = date
        }
        cycledAway.addLast(quoteId)
        if (cycledAway.size > MAX_CYCLE_UNDO) cycledAway.removeFirst()
        _canUndoCycle.value = true
    }

    private fun clearCycleUndo() {
        cycledAway.clear()
        _canUndoCycle.value = false
    }

    fun triggerTestNotification() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val todayStr = dailyDateKey()
                val quote = repository.getDailyQuote(
                    todayStr,
                    _quoteSourceMode.value,
                    _appLanguage.value,
                    _showAnonymousQuotes.value
                )
                if (quote != null) {
                    NotificationHelper.showQuoteNotification(app, quote.text, quote.author, notificationId = 1002)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun unlockDevMode() {
        _devModeUnlocked.value = true
    }

    fun openDevScreen() {
        _showDevScreen.value = true
        _devModeUnlocked.value = false
    }

    fun closeDevScreen() {
        _showDevScreen.value = false
    }

    fun openNewQuoteScreen() {
        _showNewQuoteScreen.value = true
    }

    fun closeNewQuoteScreen() {
        _showNewQuoteScreen.value = false
        _newQuoteHasDraft.value = false
    }

    /** Called by New Quote whenever its form gains or loses unsaved input. */
    fun setNewQuoteHasDraft(hasDraft: Boolean) {
        _newQuoteHasDraft.value = hasDraft && _showNewQuoteScreen.value
    }

    /**
     * Hands text from another app to New Quote and opens it (as [openDestination] does for
     * [AppLinks.DEST_NEW_QUOTE]). New Quote fills its form from [pendingDraft] once, then calls
     * [consumePendingDraft].
     */
    fun setPendingDraft(text: String, author: String) {
        _pendingDraft.value = QuoteDraft(text.trim().cappedAt(MAX_DRAFT_TEXT_LENGTH), author.trim().cappedAt(MAX_DRAFT_AUTHOR_LENGTH))
        openDestination(AppLinks.DEST_NEW_QUOTE)
    }

    fun consumePendingDraft() {
        _pendingDraft.value = null
    }

    /**
     * Opens an entry point's destination ([AppLinks] DEST_* from the widget, a notification, a launcher
     * shortcut or the share sheet): closes the developer screen and selects the tab and sub-tab. New
     * Quote opens above Saved > My Quotes, where the added quote shows. An open New Quote form with a
     * draft stays open, so no entry point throws away what the user typed. Unknown values are ignored.
     */
    fun openDestination(dest: String) {
        val (tab, subTab) = when (dest) {
            AppLinks.DEST_DAILY -> TAB_DAILY to null
            AppLinks.DEST_FAVORITES -> TAB_SAVED to SUB_TAB_FAVORITES
            AppLinks.DEST_MY_QUOTES, AppLinks.DEST_NEW_QUOTE -> TAB_SAVED to SUB_TAB_MY_QUOTES
            else -> return
        }
        _showDevScreen.value = false
        selectTab(tab)
        subTab?.let(::selectSavedSubTab)
        if (dest == AppLinks.DEST_NEW_QUOTE) {
            openNewQuoteScreen()
        } else if (!_newQuoteHasDraft.value) {
            closeNewQuoteScreen()
        }
    }

    fun selectSavedSubTab(tab: String) {
        if (tab == SUB_TAB_FAVORITES || tab == SUB_TAB_MY_QUOTES) {
            _savedSubTab.value = tab
        }
    }

    fun popBackStack() {
        when {
            _showNewQuoteScreen.value -> closeNewQuoteScreen()
            _showDevScreen.value -> closeDevScreen()
            _selectedTab.value == TAB_LIBRARY && libraryFiltered() -> clearLibraryFilter()
            _selectedTab.value != TAB_DAILY -> {
                // Drops a Library search or filter left behind when the user switched tabs.
                clearLibraryFilter()
                _selectedTab.value = TAB_DAILY
            }
        }
    }

    private fun libraryFiltered(): Boolean = _selectedCategories.value.isNotEmpty() || _searchQuery.value.isNotBlank()

    private fun clearLibraryFilter() {
        _selectedCategories.value = emptySet()
        _searchQuery.value = ""
    }

    fun selectTab(tab: String) {
        if (tab in TABS) _selectedTab.value = tab
    }

    fun selectCategory(category: String?) {
        _selectedCategories.value = if (category == null) emptySet() else setOf(category)
    }

    fun toggleCategorySelected(category: String) {
        val newSet = if (_selectedCategories.value.contains(category)) {
            _selectedCategories.value - category
        } else {
            _selectedCategories.value + category
        }
        _selectedCategories.value = newSet
    }

    fun setSelectedCategories(categories: Set<String>) {
        _selectedCategories.value = categories.intersect(SELECTABLE_CATEGORIES)
    }

    fun clearCategorySelection() {
        _selectedCategories.value = emptySet()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** One of the [SavedSort] values; kept across starts. */
    fun setFavoritesSort(sort: String) {
        if (sort !in SavedSort.all) return
        _favoritesSort.value = sort
        prefs.edit().putString(KEY_FAVORITES_SORT, sort).apply()
    }

    fun setSavedSearchQuery(query: String) {
        _savedSearchQuery.value = query
    }

    /** One of the [SavedStatusFilter] values. */
    fun setSavedStatusFilter(filter: String) {
        if (filter in SavedStatusFilter.all) _savedStatusFilter.value = filter
    }

    fun setWidgetStyle(style: String) {
        _widgetStyle.value = style
        prefs.edit().putString("widget_style", style).apply()
    }

    /** Works for every quote, own ones included. Unfavoriting can be undone with [restoreFavorites]. */
    fun toggleFavorite(quote: QuoteEntity) {
        val favorite = !quote.isFavorite
        viewModelScope.launch {
            val exists = quoteEditMutex.withLock {
                if (!favorite) rememberUnfavorited(quote.id)
                repository.toggleFavorite(quote.id, favorite)
            }
            if (exists) {
                markDailyQuoteFavorite(setOf(quote.id), favorite)
            } else if (_dailyQuote.value?.id == quote.id) {
                // The quote is gone (e.g. unpublished meanwhile): show what is stored now.
                loadDailyQuote()
            }
        }
    }

    /** Removes several favorites (own quotes included); [restoreFavorites] undoes it. */
    fun unfavoriteQuotes(ids: Set<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            quoteEditMutex.withLock {
                ids.forEach { id ->
                    rememberUnfavorited(id)
                    repository.toggleFavorite(id, false)
                }
            }
            markDailyQuoteFavorite(ids, false)
        }
    }

    /** Undo for unfavoriting: the quotes become favorites again with their original saved dates. */
    fun restoreFavorites(quotes: List<QuoteEntity>) {
        if (quotes.isEmpty()) return
        viewModelScope.launch {
            quoteEditMutex.withLock {
                quotes.forEach { quote ->
                    val savedDate = if (recentlyUnfavorited.containsKey(quote.id)) {
                        recentlyUnfavorited.remove(quote.id)
                    } else {
                        quote.savedDate
                    }
                    repository.setFavorite(quote.id, savedDate)
                }
            }
            markDailyQuoteFavorite(quotes.mapTo(HashSet()) { it.id }, true)
        }
    }

    private suspend fun rememberUnfavorited(id: Int) {
        repository.getQuoteById(id)?.takeIf { it.isFavorite }?.let { recentlyUnfavorited[id] = it.savedDate }
    }

    private fun markDailyQuoteFavorite(ids: Set<Int>, favorite: Boolean) {
        val daily = _dailyQuote.value ?: return
        if (daily.id in ids) _dailyQuote.value = daily.copy(isFavorite = favorite)
    }

    /**
     * Adds an own quote in [language] ("en"/"de", the language the quote is written in; the app
     * language by default). Ignores input outside the submission limits, and a repeat of the same
     * quote while it is being saved or within a moment of it (a double tap on "Add").
     */
    fun addUserQuote(text: String, author: String, category: String, tags: String, language: String = _appLanguage.value) {
        val normalizedText = text.trim()
        val normalizedAuthor = author.trim()
        val tagList = parseTags(tags)
        if (!fitsSubmissionLimits(normalizedText, normalizedAuthor, tagList)) return
        val quoteLanguage = supportedLanguage(language) ?: _appLanguage.value
        val request = listOf(normalizedText, normalizedAuthor, category, tagList.joinToString(", "), quoteLanguage)
            .joinToString("\n")
        val now = SystemClock.elapsedRealtime()
        if (request == lastAddRequest && (addJob?.isActive == true || now - lastAddAt < DUPLICATE_ADD_WINDOW_MS)) return
        lastAddRequest = request
        lastAddAt = now
        addJob = viewModelScope.launch {
            val q = QuoteEntity(
                text = normalizedText,
                author = normalizedAuthor.ifBlank { UNKNOWN_AUTHOR },
                category = category.takeIf { it in CategoryMapper.categories } ?: "Uncategorized",
                tags = tagList.joinToString(", "),
                isUserAdded = true,
                origin = QuoteOrigin.PERSONAL,
                language = quoteLanguage,
                timestamp = System.currentTimeMillis()
            )
            repository.insertQuote(q)
        }
    }

    /**
     * Edits an own quote. Not allowed while a submission is pending (withdraw it first). A rejected
     * or removed submission is cleared when the edit changes what the server identifies it by (see
     * [editKeepsReview]), so the edited quote can be submitted again; other edits keep the review.
     * [language] changes the quote's language; null keeps it.
     */
    fun updateUserQuote(
        quote: QuoteEntity,
        text: String,
        author: String,
        category: String,
        tags: String,
        language: String? = null,
        onDone: (Boolean) -> Unit = {}
    ) {
        val normalizedText = text.trim()
        val tagList = parseTags(tags)
        if (!fitsSubmissionLimits(normalizedText, author.trim(), tagList)) {
            onDone(false)
            return
        }
        viewModelScope.launch {
            val raw = repository.getQuoteById(quote.id)
            if (raw == null || !raw.isUserAdded || raw.submissionStatus == QuoteSubmissionStatus.PENDING) {
                onDone(false)
                return@launch
            }
            val updated = raw.copy(
                text = normalizedText,
                author = editedAuthor(raw.author, author),
                category = category.takeIf { it in CategoryMapper.categories } ?: raw.category,
                tags = tagList.joinToString(", "),
                language = language?.let(::supportedLanguage) ?: raw.language
            )
            val resetSubmission = (raw.submissionStatus == QuoteSubmissionStatus.REJECTED ||
                raw.submissionStatus == QuoteSubmissionStatus.REMOVED) &&
                !raw.editKeepsReview(normalizedText, author)
            repository.insertQuote(if (resetSubmission) updated.withoutSubmission() else updated)
            onDone(true)
        }
    }

    // The backend's limits, counted as it counts them: in code points, on the text as it will be
    // stored. A quote that can be saved can then also be submitted.
    private fun fitsSubmissionLimits(text: String, author: String, tags: List<String>): Boolean =
        storedQuoteTextLength(text) in SubmissionLimits.MIN_TEXT_LENGTH..SubmissionLimits.MAX_TEXT_LENGTH &&
            submissionLength(author) <= SubmissionLimits.MAX_AUTHOR_LENGTH &&
            tags.size <= SubmissionLimits.MAX_TAGS &&
            tags.all { submissionLength(it) in 1..SubmissionLimits.MAX_TAG_LENGTH }

    private fun parseTags(tags: String): List<String> =
        tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

    private fun supportedLanguage(language: String): String? =
        language.takeIf { it == QuoteLanguage.ENGLISH || it == QuoteLanguage.GERMAN }

    /**
     * Deletes a quote. A pending submission is withdrawn on the server too, so a deleted quote is not
     * published later; [events] reports a withdrawal that is still queued or that failed.
     * [restoreDeletedQuote] brings back the stored row.
     */
    fun deleteQuote(id: Int) = deleteQuotes(setOf(id))

    /** [deleteQuote] for several quotes; one [restoreDeletedQuotes] call undoes it. */
    fun deleteQuotes(ids: Set<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val deleted = quoteEditMutex.withLock {
                ids.mapNotNull { id ->
                    repository.getQuoteById(id)?.also { raw ->
                        recentlyDeleted[id] = raw
                        repository.deleteQuote(id)
                    }
                }
            }
            withdrawDeletedSubmissions(deleted)
        }
    }

    // The manager queues each withdrawal before trying it, so one that fails for lack of a connection
    // is retried by every later sync, and one that comes too late is reported by reportFailedWithdrawals.
    private suspend fun withdrawDeletedSubmissions(deleted: List<QuoteEntity>) {
        var queued = 0
        val failed = LinkedHashMap<String, Int>()
        for (raw in deleted) {
            val submissionId = raw.submissionId
            if (!raw.isUserAdded || raw.submissionStatus != QuoteSubmissionStatus.PENDING || submissionId == null) continue
            when (val outcome = communitySyncManager.withdrawDeletedSubmission(submissionId)) {
                // An Undo now brings the quote back as not submitted.
                DeletedSubmissionOutcome.Withdrawn ->
                    recentlyDeleted[raw.id]?.let { recentlyDeleted[raw.id] = it.withoutSubmission() }
                is DeletedSubmissionOutcome.RetryQueued -> queued++
                is DeletedSubmissionOutcome.Failed -> failed[outcome.code] = (failed[outcome.code] ?: 0) + 1
                DeletedSubmissionOutcome.Kept -> Unit
            }
        }
        if (queued > 0) _events.trySend(QuoteUiEvent.WithdrawalQueued(queued))
        failed.forEach { (code, count) -> _events.trySend(QuoteUiEvent.WithdrawalFailed(code, count)) }
    }

    fun restoreDeletedQuote(quote: QuoteEntity) = restoreDeletedQuotes(listOf(quote))

    /** Undo for [deleteQuote] and [deleteQuotes]. */
    fun restoreDeletedQuotes(quotes: List<QuoteEntity>) {
        if (quotes.isEmpty()) return
        viewModelScope.launch {
            quoteEditMutex.withLock {
                quotes.forEach { quote -> repository.insertQuote(recentlyDeleted.remove(quote.id) ?: storableCopy(quote)) }
            }
        }
    }

    // Without the stored row (the ViewModel was recreated) the display copy goes back; its placeholder
    // author reads "Unknown"/"Unbekannt" in the app language and is stored as "Unknown".
    private fun storableCopy(quote: QuoteEntity): QuoteEntity =
        if (quote.isUserAdded && isPlaceholderAuthor(quote.author)) quote.copy(author = UNKNOWN_AUTHOR) else quote

    /**
     * Sends an own quote for review. [onError] receives a [SubmissionError] code, which the UI maps
     * to localized text. Works for new, rejected and removed quotes; a retry of a submission the
     * server already has (lost response, restored backup) recovers its id and state. When the server
     * says that submission is already published, [onAlreadyPublished] runs instead of [onSuccess].
     * [language] is the quote's language as the submitter confirmed it; it is stored on the quote
     * (null keeps the stored one).
     */
    fun submitQuoteForReview(
        quote: QuoteEntity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        language: String? = null,
        onAlreadyPublished: () -> Unit = onSuccess
    ) {
        if (!quote.isUserAdded || quote.submissionStatus == QuoteSubmissionStatus.PENDING) return
        if (quote.id in _submittingQuoteIds.value) return

        _submittingQuoteIds.value += quote.id
        viewModelScope.launch {
            try {
                // Always send the stored row, never a localized display copy.
                val stored = repository.getQuoteById(quote.id) ?: return@launch
                if (!stored.isUserAdded || stored.submissionStatus == QuoteSubmissionStatus.PENDING ||
                    stored.submissionStatus == QuoteSubmissionStatus.APPROVED
                ) return@launch
                val chosenLanguage = language?.let(::supportedLanguage)
                val raw = if (chosenLanguage != null && chosenLanguage != stored.language) {
                    stored.copy(language = chosenLanguage).also { repository.insertQuote(it) }
                } else {
                    stored
                }
                raw.submissionValidationError()?.let {
                    onError(it)
                    return@launch
                }
                when (val result = submissionClient.submit(raw, installationId(), BuildConfig.VERSION_NAME, raw.language)) {
                    is QuoteSubmissionResult.Success -> {
                        repository.markSubmitted(
                            raw.id,
                            result.submissionId,
                            SubmissionStatusInfo(result.status, result.reviewNote, result.communityQuoteId)
                        )
                        when {
                            !result.duplicate -> onSuccess()
                            // The server already published exactly this text and author; nothing new was sent.
                            result.status == QuoteSubmissionStatus.APPROVED -> onAlreadyPublished()
                            // The server already decided against it; nothing new was sent.
                            result.status == QuoteSubmissionStatus.REJECTED ||
                                result.status == QuoteSubmissionStatus.REMOVED -> onError(SubmissionError.ALREADY_REVIEWED)
                            // Already in review (e.g. a lost response): it is submitted.
                            else -> onSuccess()
                        }
                    }
                    is QuoteSubmissionResult.Error -> onError(result.code)
                }
            } finally {
                _submittingQuoteIds.value -= quote.id
            }
        }
    }

    /** Submits a rejected or removed quote again (edit it first with [updateUserQuote] if the reviewer asked). */
    fun resubmitQuote(
        quote: QuoteEntity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        language: String? = null,
        onAlreadyPublished: () -> Unit = onSuccess
    ) = submitQuoteForReview(quote, onSuccess, onError, language, onAlreadyPublished)

    /**
     * Withdraws a pending submission; the quote stays in My Quotes as not submitted. Runs under the
     * sync lock, so a status refresh cannot overwrite the withdrawal. [onError] receives a
     * [SubmissionError] code ([SubmissionError.ALREADY_REVIEWED] when a moderator was faster; the
     * state is refreshed then).
     */
    fun withdrawSubmission(
        quote: QuoteEntity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (quote.id in _submittingQuoteIds.value) return
        _submittingQuoteIds.value += quote.id
        viewModelScope.launch {
            try {
                when (val error = communitySyncManager.withdrawSubmission(quote.id)) {
                    null -> onSuccess()
                    else -> onError(error)
                }
            } finally {
                _submittingQuoteIds.value -= quote.id
            }
        }
    }

    private fun installationId(): String =
        prefs.getString(CommunitySyncManager.KEY_INSTALLATION_ID, null)
            ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(CommunitySyncManager.KEY_INSTALLATION_ID, it).apply()
            }

    /**
     * Checks the review state of the own submissions now ("Check now"). [onResult] gets the outcome:
     * complete = false when some could not be checked (offline, server error). A call while a check
     * runs gets that check's result. Decisions found are also sent as [QuoteUiEvent.ReviewDecisions].
     */
    fun refreshSubmissionStatuses(onResult: ((SubmissionRefreshResult) -> Unit)? = null) {
        onResult?.let(refreshCallbacks::add)
        if (_submissionRefreshRunning.value) return
        _submissionRefreshRunning.value = true
        viewModelScope.launch {
            val result = try {
                communitySyncManager.refreshSubmissionStatuses()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                SubmissionRefreshResult(complete = false, changed = false, updates = emptyList())
            } finally {
                _submissionRefreshRunning.value = false
            }
            reportReviewDecisions(result.updates)
            val callbacks = refreshCallbacks.toList()
            refreshCallbacks.clear()
            callbacks.forEach { it(result) }
        }
    }

    private fun reportReviewDecisions(updates: List<SubmissionUpdate>) {
        if (updates.isNotEmpty()) _events.trySend(QuoteUiEvent.ReviewDecisions(updates))
    }

    // Withdrawals of deleted quotes that a later sync (here or in the worker) retried and the server refused.
    private fun reportFailedWithdrawals() {
        communitySyncManager.takeFailedWithdrawals()
            .groupingBy { it.code }
            .eachCount()
            .forEach { (code, count) -> _events.trySend(QuoteUiEvent.WithdrawalFailed(code, count)) }
    }

    fun setDailyReminderEnabled(enabled: Boolean) {
        _dailyReminderEnabled.value = enabled
        prefs.edit().putBoolean("daily_reminder_enabled", enabled).apply()

        if (enabled) {
            NotificationScheduler.scheduleDailyNotification(app, _dailyReminderHour.value, _dailyReminderMinute.value)
        } else {
            NotificationScheduler.cancelDailyNotification(app)
        }
    }

    fun updateDailyReminderTime(hour: Int, minute: Int) {
        _dailyReminderHour.value = hour
        _dailyReminderMinute.value = minute
        prefs.edit()
            .putInt("daily_reminder_hour", hour)
            .putInt("daily_reminder_minute", minute)
            .apply()

        if (_dailyReminderEnabled.value) {
            NotificationScheduler.scheduleDailyNotification(app, hour, minute)
        }
    }

    /** Writes a backup. [onError] gets a fixed English message per failure, which the UI localizes. */
    fun exportBackup(uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val outcome = backupManager.export(uri)
            if (outcome is BackupOutcome.Failed) {
                onError(backupErrorMessage(outcome.error, exporting = true))
            } else {
                onSuccess()
            }
        }
    }

    /** Restores a backup. [onError] gets a fixed English message per failure, which the UI localizes. */
    fun importBackup(
        uri: Uri,
        onSuccess: (insertedCustom: Int, updatedFavs: Int, settingsRestored: Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val outcome = backupManager.import(uri)
            if (outcome is BackupOutcome.Imported) {
                applyRestoredState()
                onSuccess(outcome.insertedCustom, outcome.updatedFavorites, outcome.settingsRestored)
            } else {
                onError(backupErrorMessage((outcome as? BackupOutcome.Failed)?.error ?: BackupError.WRITE_FAILED, exporting = false))
            }
        }
    }

    // The restore wrote quotes and settings behind the ViewModel's back: show them and re-arm what
    // depends on the settings.
    private fun applyRestoredState() {
        val previousLanguage = _appLanguage.value
        loadThemeSettings()
        if (_appLanguage.value != previousLanguage) clearLibraryFilter()
        if (_dailyReminderEnabled.value) {
            NotificationScheduler.ensureScheduled(app)
        } else {
            NotificationScheduler.cancelDailyNotification(app)
        }
        NotificationHelper.refreshChannels(app)
        loadDailyQuote(refreshWidget = true)
        // The backup manager requested a full status recheck of the restored submissions; the sync runs
        // it after reading the feed.
        startSync(queueIfRunning = true)
    }

    private fun backupErrorMessage(error: BackupError, exporting: Boolean): String = when (error) {
        BackupError.CANNOT_OPEN -> "Could not open the backup file."
        BackupError.INVALID_FORMAT -> "This file is not a Quote backup."
        BackupError.UNSUPPORTED_VERSION -> "This backup was made by a newer version of Quote."
        BackupError.WRITE_FAILED ->
            if (exporting) "The backup could not be saved." else "The backup could not be restored."
    }

    private fun syncStateOf(status: CommunitySyncStatus): SyncUiState {
        val failedAt = status.lastFailureAt
        val succeededAt = status.lastSuccessAt
        return when {
            status.lastAttemptFailed && failedAt != null -> SyncUiState.Failed(failedAt, succeededAt)
            succeededAt != null -> SyncUiState.Success(succeededAt)
            else -> SyncUiState.Idle
        }
    }

    private fun SyncUiState.isFinished(): Boolean = this is SyncUiState.Success || this is SyncUiState.Failed

    // Server ids of community quotes that are the published copy of one of the user's own quotes.
    private fun linkedCommunityCopies(quotes: List<QuoteEntity>): Set<String> =
        quotes.mapNotNullTo(HashSet()) { quote -> quote.linkedServerId?.takeIf { quote.isUserAdded } }

    private fun QuoteEntity.withoutSubmission(): QuoteEntity = copy(
        submissionStatus = QuoteSubmissionStatus.NOT_SUBMITTED,
        submissionId = null,
        submittedAt = null,
        reviewNote = null,
        linkedServerId = null
    )

    private fun QuoteEntity.matchesStatusFilter(filter: String): Boolean = when (filter) {
        SavedStatusFilter.NOT_SUBMITTED -> submissionStatus == QuoteSubmissionStatus.NOT_SUBMITTED
        SavedStatusFilter.PENDING -> submissionStatus == QuoteSubmissionStatus.PENDING
        SavedStatusFilter.APPROVED -> submissionStatus == QuoteSubmissionStatus.APPROVED
        SavedStatusFilter.NOT_APPROVED -> submissionStatus == QuoteSubmissionStatus.REJECTED ||
            submissionStatus == QuoteSubmissionStatus.REMOVED
        else -> true
    }

    // RECENT keeps the database order (newest first); OLDEST sorts by [timeKey], rows without one last.
    // Author and text compare like a dictionary of the app language; unknown authors come last and
    // leading quotation marks do not count.
    private fun List<QuoteEntity>.sortedForSaved(
        sort: String,
        language: String,
        timeKey: (QuoteEntity) -> Long?
    ): List<QuoteEntity> {
        if (sort == SavedSort.OLDEST) return sortedWith(compareBy(nullsLast<Long>()) { timeKey(it) })
        if (sort != SavedSort.AUTHOR && sort != SavedSort.TEXT) return this
        val collator = Collator.getInstance(if (language == QuoteLanguage.GERMAN) Locale.GERMAN else Locale.ENGLISH)
            .apply { strength = Collator.PRIMARY }
        val byText = Comparator<QuoteEntity> { a, b -> collator.compare(a.sortableText(), b.sortableText()) }
        return if (sort == SavedSort.TEXT) {
            sortedWith(byText)
        } else {
            sortedWith(
                compareBy<QuoteEntity> { it.hasUnknownAuthor }
                    .thenComparator { a, b -> collator.compare(a.author, b.author) }
                    .then(byText)
            )
        }
    }

    private fun QuoteEntity.sortableText(): String = text.trimStart { !it.isLetterOrDigit() }

    private fun libraryKey(quote: QuoteEntity): Pair<String, String> =
        quote.text.trim().lowercase(Locale.ROOT) to quote.author.trim().lowercase(Locale.ROOT)

    private fun libraryAuthorKey(author: String): String = author.trim().lowercase(Locale.ROOT)

    private fun backStackFor(devScreen: Boolean, newQuoteScreen: Boolean, tab: String): Boolean =
        devScreen || newQuoteScreen || tab != TAB_DAILY

    private fun String.cappedAt(maxLength: Int): String {
        if (length <= maxLength) return this
        // Never cut an emoji in half.
        val end = if (this[maxLength - 1].isHighSurrogate()) maxLength - 1 else maxLength
        return substring(0, end)
    }

    private data class DailySettings(val sourceMode: String, val language: String, val showAnonymous: Boolean)

    private companion object {
        // Bumped to v14 so installs that seeded before the normalizer and category fixes re-seed;
        // the re-seed keeps bundled ids, favorites and the daily history.
        const val SEEDED_FLAG = "database_json_seeded_v14"
        // v3: the normalizer now also drops the stray space before German closing marks („Real. “).
        const val KEY_TEXT_REPAIRED = "stored_quote_text_repaired_v3"
        const val MIN_SEEDED_QUOTES = 30
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_AUTHOR_IMAGES = "author_images_enabled"
        const val KEY_APP_LANGUAGE = "app_language"
        const val KEY_FAVORITES_SORT = "favorites_sort"
        const val KEY_LAST_UPDATE_CHECK = "last_update_check_date"
        const val RESUME_SYNC_INTERVAL_MS = 15L * 60 * 1000
        const val DUPLICATE_ADD_WINDOW_MS = 2_000L
        const val MAX_CYCLE_UNDO = 20
        // Shared text is capped well above the 500-character limit, so it still fits the saved state.
        const val MAX_DRAFT_TEXT_LENGTH = 2_000
        const val MAX_DRAFT_AUTHOR_LENGTH = 200

        // SavedStateHandle keys.
        const val KEY_TAB = "tab"
        const val KEY_SAVED_SUB_TAB = "saved_sub_tab"
        const val KEY_LIBRARY_SEARCH = "library_search"
        const val KEY_LIBRARY_CATEGORIES = "library_categories"
        const val KEY_NEW_QUOTE_OPEN = "new_quote_open"
        const val KEY_NEW_QUOTE_HAS_DRAFT = "new_quote_has_draft"
        const val KEY_SAVED_SEARCH = "saved_search"
        const val KEY_SAVED_STATUS_FILTER = "saved_status_filter"

        const val TAB_DAILY = "Daily"
        const val TAB_LIBRARY = "Library"
        const val TAB_SAVED = "Saved"
        val TABS = setOf(TAB_DAILY, TAB_LIBRARY, TAB_SAVED, "Settings")
        const val SUB_TAB_FAVORITES = "Favorites"
        const val SUB_TAB_MY_QUOTES = "My Quotes"

        val SOURCE_MODES = setOf(QuoteSourceMode.ALL, QuoteSourceMode.CURATED, QuoteSourceMode.COMMUNITY)
        val SELECTABLE_CATEGORIES: Set<String> = CategoryMapper.categories.toSet() + setOf("Community", "Local", "Reflections")

        // Update-check messages, in English; the Settings screen localizes them.
        const val UPDATE_ERROR_OFFLINE = "No internet connection. Check your connection and try again."
        const val UPDATE_ERROR_RATE_LIMITED = "Too many update checks right now. Try again later."
        const val UPDATE_ERROR_NO_APK = "No downloadable update was found."
        const val UPDATE_ERROR_SERVER = "The update server is having problems. Try again later."
        const val UPDATE_ERROR_DOWNLOAD = "Download failed"
    }
}

/**
 * Creates the [QuoteViewModel] with a [SavedStateHandle], so the navigation state survives process
 * death. A ViewModelProvider made from the activity passes its saved-state owner in the creation
 * extras; without one (a bare ViewModelStore in tests) the state starts fresh.
 */
class QuoteViewModelFactory(
    private val application: Application,
    private val repository: QuoteRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(QuoteViewModel::class.java)) { "Unknown ViewModel class" }
        val savedState = if (extras[SAVED_STATE_REGISTRY_OWNER_KEY] != null && extras[VIEW_MODEL_STORE_OWNER_KEY] != null) {
            extras.createSavedStateHandle()
        } else {
            SavedStateHandle()
        }
        @Suppress("UNCHECKED_CAST")
        return QuoteViewModel(application, repository, savedState) as T
    }
}
