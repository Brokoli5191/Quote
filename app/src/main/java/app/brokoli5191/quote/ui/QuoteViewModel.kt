package app.brokoli5191.quote.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.brokoli5191.quote.BuildConfig
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.AuthorInfo
import app.brokoli5191.quote.data.AuthorRepository
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteRepository
import app.brokoli5191.quote.data.QuoteSubmissionClient
import app.brokoli5191.quote.data.QuoteSubmissionResult
import app.brokoli5191.quote.data.QuoteSubmissionStatus
import app.brokoli5191.quote.data.QuoteOrigin
import app.brokoli5191.quote.data.CommunitySyncManager
import app.brokoli5191.quote.data.CategoryMapper
import app.brokoli5191.quote.data.QuoteSourceMode
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.data.SubmissionError
import app.brokoli5191.quote.data.SubmissionStatusInfo
import app.brokoli5191.quote.data.WithdrawResult
import app.brokoli5191.quote.data.dailyDateKey
import app.brokoli5191.quote.data.isAvailableIn
import app.brokoli5191.quote.data.localized
import app.brokoli5191.quote.data.matchesSourceMode
import app.brokoli5191.quote.data.normalizedText
import app.brokoli5191.quote.data.quoteMatchKey
import app.brokoli5191.quote.data.submissionValidationError
import app.brokoli5191.quote.utils.NotificationHelper
import app.brokoli5191.quote.utils.NotificationScheduler
import app.brokoli5191.quote.utils.CategoryQuoteVerifier
import app.brokoli5191.quote.utils.CategoryVerificationResult
import app.brokoli5191.quote.utils.UpdateChecker
import app.brokoli5191.quote.utils.UpdateStatus
import app.brokoli5191.quote.utils.CommunitySyncWorker
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class QuoteViewModel(application: Application, private val repository: QuoteRepository) : AndroidViewModel(application) {

    private val app get() = getApplication<Application>()
    private val prefs get() = app.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
    private val submissionClient = QuoteSubmissionClient()
    private val communitySyncManager = CommunitySyncManager(app, repository)
    private val authorRepository = AuthorRepository(app, AppDatabase.getInstance(app).authorInfoDao())
    private var communitySyncRunning = false
    private var lastCommunitySyncAt = 0L
    private val updateCheckRunning = AtomicBoolean(false)
    // Raw rows of recently deleted quotes, so Undo restores stored data and not a localized display copy.
    private val recentlyDeleted = HashMap<Int, QuoteEntity>()

    private val _dailyQuote = MutableStateFlow<QuoteEntity?>(null)
    val dailyQuote: StateFlow<QuoteEntity?> = _dailyQuote.asStateFlow()

    private val _verificationResult = MutableStateFlow<CategoryVerificationResult?>(null)
    val verificationResult: StateFlow<CategoryVerificationResult?> = _verificationResult.asStateFlow()

    private val _selectedTab = MutableStateFlow("Daily")
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategories = MutableStateFlow<Set<String>>(emptySet())
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

    private val _communitySyncFinished = MutableStateFlow(false)
    val communitySyncFinished: StateFlow<Boolean> = _communitySyncFinished.asStateFlow()

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

    private val _showNewQuoteScreen = MutableStateFlow(false)
    val showNewQuoteScreen: StateFlow<Boolean> = _showNewQuoteScreen.asStateFlow()

    private val _savedSubTab = MutableStateFlow("Favorites")
    val savedSubTab: StateFlow<String> = _savedSubTab.asStateFlow()

    private val _autoUpdateEnabled = MutableStateFlow(false)
    val autoUpdateEnabled: StateFlow<Boolean> = _autoUpdateEnabled.asStateFlow()

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    private val _submittingQuoteIds = MutableStateFlow<Set<Int>>(emptySet())
    val submittingQuoteIds: StateFlow<Set<Int>> = _submittingQuoteIds.asStateFlow()

    private val _showOnboarding = MutableStateFlow(false)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    private val _authorImagesEnabled = MutableStateFlow(true)
    val authorImagesEnabled: StateFlow<Boolean> = _authorImagesEnabled.asStateFlow()

    private var lastLoadedDate = ""
    private var submissionStatusRefreshRunning = false

    // Lists are filtered and localized on Dispatchers.Default. Room re-emits on every write to the
    // quotes table, so identical results are dropped before the work is done again.
    val allQuotes: StateFlow<List<QuoteEntity>> = combine(
        repository.allQuotes.distinctUntilChanged(), appLanguage, showAnonymousQuotes
    ) { quotes, language, showAnonymous ->
        quotes.asSequence()
            .filter { it.isAvailableIn(language) && (showAnonymous || !it.isAnonymous) }
            .map { it.localized(language) }
            .toList()
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<QuoteEntity>> = combine(
        repository.favorites.distinctUntilChanged(), appLanguage, showAnonymousQuotes
    ) { quotes, language, showAnonymous ->
        // A favorited own quote and its favorited community copy are the same quote: keep the own one.
        val linkedCopies = linkedCommunityCopies(quotes)
        quotes.filter {
            it.isAvailableIn(language) && (showAnonymous || !it.isAnonymous) && it.serverId !in linkedCopies
        }.map { it.localized(language) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // My Quotes always shows every own quote, whatever the app language.
    val userAdded: StateFlow<List<QuoteEntity>> = combine(
        repository.userAdded.distinctUntilChanged(), appLanguage
    ) { quotes, language ->
        quotes.map { it.localized(language) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasBackStack: StateFlow<Boolean> = combine(
        _showDevScreen, _showNewQuoteScreen, _selectedTab, _selectedCategories, _searchQuery
    ) { devScreen, newQuoteScreen, tab, categories, query ->
        devScreen || newQuoteScreen || tab != "Daily" || categories.isNotEmpty() || query.isNotBlank()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val filteredQuotes: StateFlow<List<QuoteEntity>> = combine(
        allQuotes,
        searchQuery,
        selectedCategories,
        quoteSourceMode
    ) { quotes, query, categories, sourceMode ->
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
        if (query.isNotEmpty()) {
            list = list.filter {
                it.text.contains(query, ignoreCase = true) ||
                it.author.contains(query, ignoreCase = true) ||
                it.tags.splitToSequence(',').any { rawTag ->
                    val tag = rawTag.trim()
                    tag.contains(query, ignoreCase = true) && (!tag.contains("misattributed", ignoreCase = true) || query.contains("misattributed", ignoreCase = true))
                }
            }
        }
        list.distinctBy {
            it.text.trim().lowercase(Locale.ROOT) to it.author.trim().lowercase(Locale.ROOT)
        }
    }
        // Filter ~2500 rows off the main thread so search keystrokes don't jank.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        _showOnboarding.value = resolveOnboarding()
        loadThemeSettings()
        loadDailyQuote()
        syncCommunityQuotes()
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
        _showOnboarding.value = true
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, false).apply()
    }

    fun syncCommunityQuotes() {
        if (communitySyncRunning) return
        communitySyncRunning = true
        lastCommunitySyncAt = SystemClock.elapsedRealtime()
        viewModelScope.launch {
            try {
                // Also refreshes submission states and updates the widget when something changed.
                communitySyncManager.sync()
                loadDailyQuote()
            } finally {
                communitySyncRunning = false
                _communitySyncFinished.value = true
            }
        }
    }

    /**
     * Call from the activity's ON_RESUME. Picks up a new day and, at most every 15 minutes,
     * syncs community quotes and refreshes submission states.
     */
    fun onAppResumed() {
        refreshDailyQuoteIfNeeded()
        val now = SystemClock.elapsedRealtime()
        if (lastCommunitySyncAt != 0L && now - lastCommunitySyncAt < RESUME_SYNC_INTERVAL_MS) return
        syncCommunityQuotes()
    }

    fun runVerification() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = CategoryQuoteVerifier.verify(repository)
            _verificationResult.value = result
        }
    }

    fun checkAndSeedDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            val isSeeded = prefs.getBoolean(SEEDED_FLAG, false)
            val count = repository.getQuotesCount()
            if (!isSeeded || count < 30) {
                // Favorite-preserving re-seed: keeps user-added and community quotes and restores
                // favorites by (text, author) so the new seed costs no user data.
                repository.reseedPreservingFavorites(app)
                prefs.edit().putBoolean(SEEDED_FLAG, true).apply()
                loadDailyQuote()
            }
            if (!prefs.getBoolean("stored_quote_text_repaired_v2", false)) {
                // Earlier versions dropped inner closing quotation marks; the bundled rows were
                // just re-seeded, so only own and community rows need the repair. Runs under the
                // sync lock so it never interleaves with a sync writing the same rows.
                CommunitySyncManager.mutex.withLock {
                    repository.repairStoredText(setOf(QuoteOrigin.PERSONAL, QuoteOrigin.COMMUNITY))
                }
                prefs.edit().putBoolean("stored_quote_text_repaired_v2", true).apply()
                loadDailyQuote()
            }
            withContext(Dispatchers.Main) {
                checkForUpdatesIfNeeded()
            }
        }
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
        prefs.edit()
            .putString("theme_mode", migratedMode)
            .putBoolean("amoled_black", migratedMode == "AMOLED")
            .apply()
        _themeAccent.value = prefs.getString("theme_accent", "Violet")
            ?.takeIf { it in setOf("Violet", "Amber", "Green", "Blue", "Rose") }
            ?: "Violet"
        _quoteSourceMode.value = prefs.getString("quote_source_mode", QuoteSourceMode.ALL)
            ?.takeIf { it in setOf(QuoteSourceMode.ALL, QuoteSourceMode.CURATED, QuoteSourceMode.COMMUNITY) }
            ?: QuoteSourceMode.ALL
        _appLanguage.value = prefs.getString("app_language", QuoteLanguage.ENGLISH)
            ?.takeIf { it == QuoteLanguage.ENGLISH || it == QuoteLanguage.GERMAN }
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
    }

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

    fun setAutoUpdateEnabled(enabled: Boolean) {
        _autoUpdateEnabled.value = enabled
        prefs.edit().putBoolean("auto_update_enabled", enabled).apply()
    }

    fun checkForUpdatesIfNeeded() {
        if (!_autoUpdateEnabled.value) return
        val today = dailyDateKey()
        val lastCheck = prefs.getString("last_update_check_date", "") ?: ""
        if (lastCheck == today) return
        checkForUpdates()
    }

    fun checkForUpdates() {
        if (!updateCheckRunning.compareAndSet(false, true)) return
        _updateStatus.value = UpdateStatus.Checking
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val release = UpdateChecker.checkLatestRelease()
                if (release == null) {
                    _updateStatus.value = UpdateStatus.Error("Could not reach update server")
                    return@launch
                }
                if (UpdateChecker.isNewerVersion(BuildConfig.VERSION_NAME, release.version)) {
                    _updateStatus.value = UpdateStatus.UpdateAvailable(
                        release.version,
                        release.downloadUrl,
                        release.sizeBytes
                    )
                } else {
                    _updateStatus.value = UpdateStatus.UpToDate
                }
                val today = dailyDateKey()
                prefs.edit().putString("last_update_check_date", today).apply()
            } finally {
                updateCheckRunning.set(false)
            }
        }
    }

    fun checkForUpdatesManually() {
        syncCommunityQuotes()
        checkForUpdates()
    }

    fun downloadUpdate(downloadUrl: String, version: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _updateStatus.value = UpdateStatus.Downloading(0)
            val path = UpdateChecker.downloadApk(app, downloadUrl, version) { progress ->
                _updateStatus.value = UpdateStatus.Downloading(progress)
            }
            if (path != null) {
                _updateStatus.value = UpdateStatus.ReadyToInstall(path, version)
            } else {
                _updateStatus.value = UpdateStatus.Error("Download failed")
            }
        }
    }

    fun installUpdate(context: android.content.Context, filePath: String) {
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
        if (mode !in setOf(QuoteSourceMode.ALL, QuoteSourceMode.CURATED, QuoteSourceMode.COMMUNITY)) return
        _quoteSourceMode.value = mode
        prefs.edit().putString("quote_source_mode", mode).apply()
        loadDailyQuote()
        app.sendBroadcast(Intent("app.brokoli5191.quote.UPDATE_WIDGET").apply {
            component = ComponentName(app, "app.brokoli5191.quote.widget.QuoteWidgetProvider")
        })
    }

    fun setAppLanguage(language: String) {
        if (language != QuoteLanguage.ENGLISH && language != QuoteLanguage.GERMAN) return
        _appLanguage.value = language
        prefs.edit().putString("app_language", language).apply()
        clearCategorySelection()
        _searchQuery.value = ""
        loadDailyQuote()
        app.sendBroadcast(Intent("app.brokoli5191.quote.UPDATE_WIDGET").apply {
            component = ComponentName(app, "app.brokoli5191.quote.widget.QuoteWidgetProvider")
        })
    }

    fun setShowAnonymousQuotes(enabled: Boolean) {
        _showAnonymousQuotes.value = enabled
        prefs.edit().putBoolean("show_anonymous_quotes", enabled).apply()
        if (!enabled) _selectedCategories.value = _selectedCategories.value - "Reflections"
        loadDailyQuote()
        app.sendBroadcast(Intent("app.brokoli5191.quote.UPDATE_WIDGET").apply {
            component = ComponentName(app, "app.brokoli5191.quote.widget.QuoteWidgetProvider")
        })
    }

    fun loadDailyQuote() {
        val todayStr = dailyDateKey()
        lastLoadedDate = todayStr
        viewModelScope.launch {
            val quote = repository.getDailyQuote(
                todayStr,
                _quoteSourceMode.value,
                _appLanguage.value,
                _showAnonymousQuotes.value
            )
            _dailyQuote.value = quote
        }
    }

    fun refreshDailyQuoteIfNeeded() {
        val todayStr = dailyDateKey()
        if (lastLoadedDate != todayStr) {
            loadDailyQuote()
        }
    }

    fun cycleDailyQuote() {
        val todayStr = dailyDateKey()
        viewModelScope.launch {
            val quote = repository.cycleDailyQuote(
                todayStr,
                _quoteSourceMode.value,
                _appLanguage.value,
                _showAnonymousQuotes.value
            )
            _dailyQuote.value = quote

            val updateIntent = Intent("app.brokoli5191.quote.UPDATE_WIDGET").apply {
                component = ComponentName(app, "app.brokoli5191.quote.widget.QuoteWidgetProvider")
            }
            app.sendBroadcast(updateIntent)
        }
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
    }

    fun selectSavedSubTab(tab: String) {
        if (tab == "Favorites" || tab == "My Quotes") {
            _savedSubTab.value = tab
        }
    }

    fun popBackStack() {
        when {
            _showNewQuoteScreen.value -> closeNewQuoteScreen()
            _showDevScreen.value -> closeDevScreen()
            _selectedTab.value == "Library" && (_selectedCategories.value.isNotEmpty() || _searchQuery.value.isNotBlank()) -> {
                clearCategorySelection()
                _searchQuery.value = ""
            }
            _selectedTab.value != "Daily" -> _selectedTab.value = "Daily"
        }
    }

    fun selectTab(tab: String) {
        _selectedTab.value = tab
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
        val allowed = CategoryMapper.categories.toSet() +
            setOf("Community", "Local", "Reflections")
        _selectedCategories.value = categories.intersect(allowed)
    }

    fun clearCategorySelection() {
        _selectedCategories.value = emptySet()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setWidgetStyle(style: String) {
        _widgetStyle.value = style
        prefs.edit().putString("widget_style", style).apply()
    }

    fun toggleFavorite(quote: QuoteEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(quote.id, !quote.isFavorite)
            if (_dailyQuote.value?.id == quote.id) {
                _dailyQuote.value = _dailyQuote.value?.copy(isFavorite = !quote.isFavorite)
            }
        }
    }

    fun addUserQuote(text: String, author: String, category: String, tags: String) {
        val normalizedText = text.trim()
        val normalizedAuthor = author.trim()
        val normalizedTags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (normalizedText.length !in 3..500 || normalizedAuthor.length > 100) return
        if (normalizedTags.size > 8 || normalizedTags.any { it.length > 30 }) return

        viewModelScope.launch {
            val q = QuoteEntity(
                text = normalizedText,
                author = normalizedAuthor.ifBlank { "Unknown" },
                category = category.takeIf { it in CategoryMapper.categories } ?: "Uncategorized",
                tags = normalizedTags.distinctBy { it.lowercase() }.joinToString(", "),
                isUserAdded = true,
                origin = QuoteOrigin.PERSONAL,
                language = _appLanguage.value,
                timestamp = System.currentTimeMillis()
            )
            repository.insertQuote(q)
        }
    }

    /**
     * Edits an own quote. Not allowed while a submission is pending (withdraw it first). A rejected
     * or removed submission is cleared when the text or author changed, so the edited quote can be
     * submitted again; the server identifies a submission by those two, so other edits keep the review.
     */
    fun updateUserQuote(
        quote: QuoteEntity,
        text: String,
        author: String,
        category: String,
        tags: String,
        onDone: (Boolean) -> Unit = {}
    ) {
        val normalizedText = text.trim()
        val normalizedAuthor = author.trim()
        val normalizedTags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (normalizedText.length !in 3..500 || normalizedAuthor.length > 100 ||
            normalizedTags.size > 8 || normalizedTags.any { it.length > 30 }
        ) {
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
                author = normalizedAuthor.ifBlank { "Unknown" },
                category = category.takeIf { it in CategoryMapper.categories } ?: raw.category,
                tags = normalizedTags.distinctBy { it.lowercase() }.joinToString(", ")
            )
            val resetSubmission = (raw.submissionStatus == QuoteSubmissionStatus.REJECTED ||
                raw.submissionStatus == QuoteSubmissionStatus.REMOVED) &&
                submissionContentKey(raw) != submissionContentKey(updated)
            repository.insertQuote(
                if (!resetSubmission) updated else updated.copy(
                    submissionStatus = QuoteSubmissionStatus.NOT_SUBMITTED,
                    submissionId = null,
                    submittedAt = null,
                    reviewNote = null,
                    linkedServerId = null
                )
            )
            onDone(true)
        }
    }

    /**
     * Deletes a quote. A pending submission is withdrawn on the server too, so a deleted quote
     * is not published later. [restoreDeletedQuote] brings back the stored row.
     */
    fun deleteQuote(id: Int) {
        viewModelScope.launch {
            val raw = repository.getQuoteById(id) ?: return@launch
            recentlyDeleted[id] = raw
            repository.deleteQuote(id)
            val submissionId = raw.submissionId
            if (raw.isUserAdded && raw.submissionStatus == QuoteSubmissionStatus.PENDING && submissionId != null) {
                if (withdrawOnServer(submissionId) == null) {
                    // Withdrawn: an Undo brings the quote back as not submitted.
                    if (repository.getQuoteById(id) != null) {
                        repository.clearSubmission(id)
                    } else {
                        recentlyDeleted[id]?.let { recentlyDeleted[id] = it.withoutSubmission() }
                    }
                }
            }
        }
    }

    fun restoreDeletedQuote(quote: QuoteEntity) {
        viewModelScope.launch {
            val raw = recentlyDeleted.remove(quote.id)
                ?: quote.copy(author = if (quote.isUserAdded && quote.author == "Unbekannt") "Unknown" else quote.author)
            repository.insertQuote(raw)
        }
    }

    /**
     * Sends an own quote for review. [onError] receives a [SubmissionError] code, which the UI maps
     * to localized text. Works for new, rejected and removed quotes; a retry of a submission the
     * server already has (lost response, restored backup) recovers its id and state.
     */
    fun submitQuoteForReview(
        quote: QuoteEntity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!quote.isUserAdded || quote.submissionStatus == QuoteSubmissionStatus.PENDING) return
        if (quote.id in _submittingQuoteIds.value) return

        _submittingQuoteIds.value += quote.id
        viewModelScope.launch {
            try {
                // Always send the stored row, never a localized display copy.
                val raw = repository.getQuoteById(quote.id) ?: return@launch
                if (!raw.isUserAdded || raw.submissionStatus == QuoteSubmissionStatus.PENDING ||
                    raw.submissionStatus == QuoteSubmissionStatus.APPROVED
                ) return@launch
                raw.submissionValidationError()?.let {
                    onError(it)
                    return@launch
                }
                when (val result = submissionClient.submit(raw, installationId(), BuildConfig.VERSION_NAME)) {
                    is QuoteSubmissionResult.Success -> {
                        repository.markSubmitted(
                            raw.id,
                            result.submissionId,
                            SubmissionStatusInfo(result.status, result.reviewNote, result.communityQuoteId)
                        )
                        if (result.duplicate && (
                                result.status == QuoteSubmissionStatus.REJECTED ||
                                    result.status == QuoteSubmissionStatus.REMOVED
                                )
                        ) {
                            // The server already decided on this exact text and author; nothing new was sent.
                            onError(SubmissionError.ALREADY_REVIEWED)
                        } else {
                            onSuccess()
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
        onError: (String) -> Unit
    ) = submitQuoteForReview(quote, onSuccess, onError)

    /**
     * Withdraws a pending submission; the quote stays in My Quotes as not submitted.
     * [onError] receives a [SubmissionError] code ([SubmissionError.ALREADY_REVIEWED] when a
     * moderator was faster; the state is refreshed then).
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
                val raw = repository.getQuoteById(quote.id) ?: return@launch
                val submissionId = raw.submissionId
                if (raw.submissionStatus != QuoteSubmissionStatus.PENDING || submissionId == null) {
                    if (raw.submissionStatus == QuoteSubmissionStatus.NOT_SUBMITTED) onSuccess()
                    else onError(SubmissionError.ALREADY_REVIEWED)
                    return@launch
                }
                when (val error = withdrawOnServer(submissionId)) {
                    null -> {
                        repository.clearSubmission(raw.id)
                        onSuccess()
                    }
                    else -> {
                        if (error == SubmissionError.ALREADY_REVIEWED) communitySyncManager.refreshSubmissionStatuses()
                        onError(error)
                    }
                }
            } finally {
                _submittingQuoteIds.value -= quote.id
            }
        }
    }

    // Returns null when the submission is gone from the server, else a SubmissionError code.
    private suspend fun withdrawOnServer(submissionId: String): String? {
        val installationIds = CommunitySyncManager.submissionInstallationIds(prefs)
        for (installationId in installationIds) {
            when (val result = submissionClient.withdraw(submissionId, installationId)) {
                WithdrawResult.Success -> return null
                WithdrawResult.NotFound -> continue
                is WithdrawResult.Error -> return result.code
            }
        }
        return null
    }

    // What the backend hashes to identify a submission: text and author, NFKC, lowercased, spacing collapsed.
    private fun submissionContentKey(quote: QuoteEntity): String {
        val stored = quote.normalizedText()
        return listOf(stored.text, stored.author).joinToString("\n") {
            java.text.Normalizer.normalize(it.trim().replace(Regex("\\s+"), " "), java.text.Normalizer.Form.NFKC)
                .lowercase(Locale.US)
        }
    }

    private fun installationId(): String =
        prefs.getString(CommunitySyncManager.KEY_INSTALLATION_ID, null)
            ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(CommunitySyncManager.KEY_INSTALLATION_ID, it).apply()
            }

    fun refreshSubmissionStatuses() {
        if (submissionStatusRefreshRunning) return
        submissionStatusRefreshRunning = true
        viewModelScope.launch {
            try {
                communitySyncManager.refreshSubmissionStatuses()
            } finally {
                submissionStatusRefreshRunning = false
            }
        }
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

    fun exportBackup(uri: android.net.Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val backupItems = repository.getAllQuotesSync().filter { it.isUserAdded || it.isFavorite }

                val jsonArray = org.json.JSONArray()
                for (quote in backupItems) {
                    jsonArray.put(org.json.JSONObject().apply {
                        put("text", quote.text)
                        put("author", quote.author)
                        put("category", quote.category)
                        put("isFavorite", quote.isFavorite)
                        put("isUserAdded", quote.isUserAdded)
                        put("timestamp", quote.timestamp)
                        put("aboutAuthor", quote.aboutAuthor)
                        put("tags", quote.tags)
                        put("savedDate", quote.savedDate ?: "")
                        put("language", quote.language)
                        put("textDe", quote.textDe ?: "")
                        put("textEn", quote.textEn ?: "")
                        put("isAnonymous", quote.isAnonymous)
                        put("origin", quote.origin)
                        quote.serverId?.let { put("serverId", it) }
                        if (quote.isUserAdded) {
                            put("submissionStatus", quote.submissionStatus)
                            quote.submissionId?.let { put("submissionId", it) }
                            quote.submittedAt?.let { put("submittedAt", it) }
                            quote.reviewNote?.let { put("reviewNote", it) }
                            quote.linkedServerId?.let { put("linkedServerId", it) }
                        }
                    })
                }

                val backup = org.json.JSONObject().apply {
                    put("formatVersion", 3)
                    put("exportedAt", System.currentTimeMillis())
                    put("settings", createSettingsBackup())
                    // Lets a restored install keep tracking (and withdrawing) its earlier submissions.
                    prefs.getString(CommunitySyncManager.KEY_INSTALLATION_ID, null)
                        ?.let { put("submissionInstallationId", it) }
                    put("quotes", jsonArray)
                }

                val output = app.contentResolver.openOutputStream(uri)
                    ?: throw java.io.IOException("Could not open the selected backup file")
                output.use { os ->
                    os.write(backup.toString(4).toByteArray(Charsets.UTF_8))
                }

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    fun importBackup(
        uri: android.net.Uri,
        onSuccess: (insertedCustom: Int, updatedFavs: Int, settingsRestored: Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = app.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                    ?: throw Exception("Could not open file")

                val backupRoot = org.json.JSONTokener(content).nextValue()
                val settings = (backupRoot as? org.json.JSONObject)?.optJSONObject("settings")
                val jsonArray = when (backupRoot) {
                    is org.json.JSONArray -> backupRoot // Legacy backups (format version 1)
                    is org.json.JSONObject -> backupRoot.optJSONArray("quotes")
                        ?: throw Exception("Backup does not contain a quotes list")
                    else -> throw Exception("Unknown backup format")
                }
                (backupRoot as? org.json.JSONObject)
                    ?.optString("submissionInstallationId", "")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::restoreInstallationId)

                val dbQuotes = repository.getAllQuotesSync()
                val customByKey = dbQuotes
                    .filter { it.isUserAdded }
                    .associateByTo(HashMap()) { quoteMatchKey(it.text) to quoteMatchKey(it.author) }
                val nonCustomByKey = dbQuotes
                    .filter { !it.isUserAdded }
                    .associateBy { quoteMatchKey(it.text) to quoteMatchKey(it.author) }
                val byServerId = dbQuotes.filter { it.serverId != null }.associateBy { it.serverId }

                var insertedCustom = 0
                var updatedFavs = 0
                // Community favorites whose quotes are not synced yet; favorited when the feed brings them.
                val pendingFavorites = LinkedHashMap<String, String?>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val text = obj.getString("text")
                    val author = obj.getString("author")
                    val isFavorite = obj.optBoolean("isFavorite", false)
                    val isUserAdded = obj.optBoolean("isUserAdded", false)
                    val key = quoteMatchKey(text) to quoteMatchKey(author)

                    if (isUserAdded) {
                        val submission = backupSubmission(obj)
                        val existing = customByKey[key]
                        if (existing == null) {
                            val imported = QuoteEntity(
                                text = text,
                                author = author,
                                category = obj.optString("category", "Uncategorized")
                                    .let { if (it.equals("Inspiration", ignoreCase = true)) "Inspirational" else it }
                                    .takeIf { it in CategoryMapper.categories }
                                    ?: "Uncategorized",
                                isFavorite = isFavorite,
                                isUserAdded = true,
                                origin = QuoteOrigin.PERSONAL,
                                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                                aboutAuthor = obj.optString("aboutAuthor", ""),
                                tags = obj.optString("tags", ""),
                                savedDate = obj.optString("savedDate", "").ifEmpty { null },
                                language = obj.optString("language", QuoteLanguage.ENGLISH)
                                    .takeIf { it in setOf(QuoteLanguage.ENGLISH, QuoteLanguage.GERMAN) }
                                    ?: QuoteLanguage.ENGLISH,
                                textDe = obj.optString("textDe", "").ifBlank { null },
                                textEn = obj.optString("textEn", "").ifBlank { null },
                                isAnonymous = obj.optBoolean("isAnonymous", false),
                                submissionStatus = submission?.status ?: QuoteSubmissionStatus.NOT_SUBMITTED,
                                submissionId = submission?.let { obj.optString("submissionId") },
                                submittedAt = submission?.let { obj.optLong("submittedAt", 0L).takeIf { it > 0L } },
                                reviewNote = submission?.reviewNote,
                                linkedServerId = submission?.communityQuoteId
                            )
                            val id = repository.insertQuote(imported).toInt()
                            customByKey[key] = imported.copy(id = id)
                            insertedCustom++
                        } else if (submission != null && existing.submissionId == null) {
                            // The local copy lost its submission (e.g. re-created by hand): take it from the backup.
                            repository.markSubmitted(existing.id, obj.optString("submissionId"), submission)
                        }
                    } else if (isFavorite) {
                        val serverId = obj.optString("serverId", "").takeIf { it.isNotBlank() }
                        val savedDate = obj.optString("savedDate", "").ifEmpty { null }
                        val match = serverId?.let(byServerId::get) ?: nonCustomByKey[key]
                        if (match != null) {
                            if (!match.isFavorite) {
                                repository.setFavorite(match.id, savedDate)
                                updatedFavs++
                            }
                        } else if (serverId != null) {
                            pendingFavorites[serverId] = savedDate
                            updatedFavs++
                        }
                    }
                }
                communitySyncManager.rememberPendingFavorites(pendingFavorites)

                val settingsRestored = settings?.let(::restoreSettingsBackup) ?: false

                withContext(Dispatchers.Main) {
                    if (insertedCustom > 0) refreshSubmissionStatuses()
                    onSuccess(insertedCustom, updatedFavs, settingsRestored)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Unknown file format")
                }
            }
        }
    }

    // Submission state of a backed-up own quote, or null when it was never submitted.
    private fun backupSubmission(obj: org.json.JSONObject): SubmissionStatusInfo? {
        val submissionId = obj.optString("submissionId", "").takeIf { it.isNotBlank() } ?: return null
        val status = obj.optString("submissionStatus", "").takeIf {
            it in setOf(
                QuoteSubmissionStatus.PENDING,
                QuoteSubmissionStatus.APPROVED,
                QuoteSubmissionStatus.REJECTED,
                QuoteSubmissionStatus.REMOVED
            )
        } ?: return null
        if (submissionId.isBlank()) return null
        return SubmissionStatusInfo(
            status = status,
            reviewNote = obj.optString("reviewNote", "").ifBlank { null },
            communityQuoteId = obj.optString("linkedServerId", "").ifBlank { null }
        )
    }

    // The backup's installation id becomes this device's id only if it has none yet; otherwise it is
    // kept as an extra id so status checks and withdrawals still find the old submissions.
    private fun restoreInstallationId(id: String) {
        val uuid = runCatching { UUID.fromString(id) }.getOrNull()?.toString() ?: return
        val current = prefs.getString(CommunitySyncManager.KEY_INSTALLATION_ID, null)
        when {
            current == null -> prefs.edit().putString(CommunitySyncManager.KEY_INSTALLATION_ID, uuid).apply()
            !current.equals(uuid, ignoreCase = true) -> {
                val extra = prefs.getStringSet(CommunitySyncManager.KEY_EXTRA_INSTALLATION_IDS, emptySet()).orEmpty()
                prefs.edit().putStringSet(CommunitySyncManager.KEY_EXTRA_INSTALLATION_IDS, extra + uuid).apply()
            }
        }
    }

    private fun createSettingsBackup(): org.json.JSONObject = org.json.JSONObject().apply {
        put("themeMode", _themeMode.value)
        put("themeAccent", _themeAccent.value)
        put("amoledBlack", _amoledBlack.value)
        put("quoteSourceMode", _quoteSourceMode.value)
        put("appLanguage", _appLanguage.value)
        put("showAnonymousQuotes", _showAnonymousQuotes.value)
        put("widgetStyle", _widgetStyle.value)
        put("dailyReminderEnabled", _dailyReminderEnabled.value)
        put("dailyReminderHour", _dailyReminderHour.value)
        put("dailyReminderMinute", _dailyReminderMinute.value)
        put("lowPerformanceMode", _lowPerformanceMode.value)
        put("blurNavigationSurfaces", _blurNavigationSurfaces.value)
        put("autoUpdateEnabled", _autoUpdateEnabled.value)
        put("authorImagesEnabled", _authorImagesEnabled.value)
    }

    private fun restoreSettingsBackup(settings: org.json.JSONObject): Boolean {
        val editor = prefs.edit()
        var restored = false

        fun restoreString(jsonKey: String, prefKey: String, allowed: Set<String>) {
            if (!settings.has(jsonKey)) return
            val value = settings.optString(jsonKey)
            if (value in allowed) {
                editor.putString(prefKey, value)
                restored = true
            }
        }

        fun restoreBoolean(jsonKey: String, prefKey: String) {
            if (!settings.has(jsonKey)) return
            editor.putBoolean(prefKey, settings.optBoolean(jsonKey))
            restored = true
        }

        restoreString("themeMode", "theme_mode", setOf("LIGHT", "DARK", "AMOLED", "DYNAMIC"))
        restoreString("themeAccent", "theme_accent", setOf("Violet", "Amber", "Green", "Blue", "Rose"))
        restoreBoolean("amoledBlack", "amoled_black")
        restoreString(
            "quoteSourceMode",
            "quote_source_mode",
            setOf(QuoteSourceMode.ALL, QuoteSourceMode.CURATED, QuoteSourceMode.COMMUNITY)
        )
        restoreString("appLanguage", "app_language", setOf(QuoteLanguage.ENGLISH, QuoteLanguage.GERMAN))
        restoreBoolean("showAnonymousQuotes", "show_anonymous_quotes")
        if (settings.has("widgetStyle")) {
            editor.putString("widget_style", settings.optString("widgetStyle", "Quote").take(50))
            restored = true
        }
        restoreBoolean("dailyReminderEnabled", "daily_reminder_enabled")
        if (settings.has("dailyReminderHour")) {
            editor.putInt("daily_reminder_hour", settings.optInt("dailyReminderHour", 8).coerceIn(0, 23))
            restored = true
        }
        if (settings.has("dailyReminderMinute")) {
            editor.putInt("daily_reminder_minute", settings.optInt("dailyReminderMinute", 0).coerceIn(0, 59))
            restored = true
        }
        restoreBoolean("lowPerformanceMode", "low_performance_mode")
        restoreBoolean("blurNavigationSurfaces", "blur_navigation_surfaces")
        restoreBoolean("autoUpdateEnabled", "auto_update_enabled")
        restoreBoolean("authorImagesEnabled", KEY_AUTHOR_IMAGES)

        if (!restored) return false
        if (!editor.commit()) throw java.io.IOException("Could not save restored settings")

        loadThemeSettings()
        if (_dailyReminderEnabled.value) {
            NotificationScheduler.scheduleDailyNotification(app, _dailyReminderHour.value, _dailyReminderMinute.value)
        } else {
            NotificationScheduler.cancelDailyNotification(app)
        }
        loadDailyQuote()
        app.sendBroadcast(Intent("app.brokoli5191.quote.UPDATE_WIDGET").apply {
            component = ComponentName(app, "app.brokoli5191.quote.widget.QuoteWidgetProvider")
        })
        return true
    }

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

    private companion object {
        const val SEEDED_FLAG = "database_json_seeded_v12"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_AUTHOR_IMAGES = "author_images_enabled"
        const val RESUME_SYNC_INTERVAL_MS = 15L * 60 * 1000
    }
}

class QuoteViewModelFactory(
    private val application: Application,
    private val repository: QuoteRepository
) : ViewModelProvider.AndroidViewModelFactory(application) {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QuoteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return QuoteViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
