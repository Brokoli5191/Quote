package app.brokoli5191.quote.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.brokoli5191.quote.BuildConfig
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
import app.brokoli5191.quote.data.isAvailableIn
import app.brokoli5191.quote.data.localized
import app.brokoli5191.quote.data.matchesSourceMode
import app.brokoli5191.quote.utils.NotificationHelper
import app.brokoli5191.quote.utils.NotificationScheduler
import app.brokoli5191.quote.utils.CategoryQuoteVerifier
import app.brokoli5191.quote.utils.CategoryVerificationResult
import app.brokoli5191.quote.utils.UpdateChecker
import app.brokoli5191.quote.utils.UpdateStatus
import app.brokoli5191.quote.utils.CommunitySyncWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class QuoteViewModel(application: Application, private val repository: QuoteRepository) : AndroidViewModel(application) {

    private val app get() = getApplication<Application>()
    private val prefs get() = app.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
    private val submissionClient = QuoteSubmissionClient()
    private val communitySyncManager = CommunitySyncManager(app, repository)
    private var communitySyncRunning = false
    private val updateCheckRunning = AtomicBoolean(false)

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

    private var lastLoadedDate = ""
    private var submissionStatusRefreshRunning = false

    private val rawAllQuotes = repository.allQuotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allQuotes: StateFlow<List<QuoteEntity>> = combine(
        rawAllQuotes, appLanguage, showAnonymousQuotes
    ) { quotes, language, showAnonymous ->
        quotes.asSequence()
            .filter { it.isAvailableIn(language) && (showAnonymous || !it.isAnonymous) }
            .map { it.localized(language) }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<QuoteEntity>> = combine(
        repository.favorites, appLanguage, showAnonymousQuotes
    ) { quotes, language, showAnonymous ->
        quotes.filter { it.isAvailableIn(language) && (showAnonymous || !it.isAnonymous) }
            .map { it.localized(language) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userAdded: StateFlow<List<QuoteEntity>> = combine(
        repository.userAdded, appLanguage
    ) { quotes, language ->
        quotes.filter { it.isAvailableIn(language) }.map { it.localized(language) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            quotes
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
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadThemeSettings()
        loadDailyQuote()
        syncCommunityQuotes()
        CommunitySyncWorker.schedule(app)
    }

    fun syncCommunityQuotes() {
        if (communitySyncRunning) return
        communitySyncRunning = true
        viewModelScope.launch {
            try {
                communitySyncManager.sync()
                loadDailyQuote()
            } finally {
                communitySyncRunning = false
                _communitySyncFinished.value = true
            }
        }
    }

    fun runVerification() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = CategoryQuoteVerifier.verify(repository)
            _verificationResult.value = result
        }
    }

    fun checkAndSeedDatabase() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val isSeeded = prefs.getBoolean("database_json_seeded_v11", false)
            val count = repository.getQuotesCount()
            if (!isSeeded || count < 30) {
                // Favorite-preserving re-seed: keeps user-added quotes and restores
                // favorites by (text, author) so the bigger DB costs no user data.
                repository.reseedPreservingFavorites(app)
                prefs.edit().putBoolean("database_json_seeded_v11", true).apply()
                loadDailyQuote()
            }
            if (!prefs.getBoolean("stored_quote_text_repaired_v1", false)) {
                repository.repairStoredText()
                prefs.edit().putBoolean("stored_quote_text_repaired_v1", true).apply()
                loadDailyQuote()
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
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
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        _autoUpdateEnabled.value = enabled
        prefs.edit().putBoolean("auto_update_enabled", enabled).apply()
    }

    fun checkForUpdatesIfNeeded() {
        if (!_autoUpdateEnabled.value) return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastCheck = prefs.getString("last_update_check_date", "") ?: ""
        if (lastCheck == today) return
        checkForUpdates()
    }

    fun checkForUpdates() {
        if (!updateCheckRunning.compareAndSet(false, true)) return
        _updateStatus.value = UpdateStatus.Checking
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
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
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
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
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (lastLoadedDate != todayStr) {
            loadDailyQuote()
        }
    }

    fun cycleDailyQuote() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
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
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
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

    fun deleteQuote(id: Int) {
        viewModelScope.launch {
            repository.deleteQuote(id)
        }
    }

    fun restoreDeletedQuote(quote: QuoteEntity) {
        viewModelScope.launch {
            repository.insertQuote(quote)
        }
    }

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
                val installationId = prefs.getString("submission_installation_id", null)
                    ?: UUID.randomUUID().toString().also {
                        prefs.edit().putString("submission_installation_id", it).apply()
                    }
                when (val result = submissionClient.submit(quote, installationId, BuildConfig.VERSION_NAME)) {
                    is QuoteSubmissionResult.Success -> {
                        repository.markSubmissionPending(quote.id, result.submissionId)
                        onSuccess()
                    }
                    is QuoteSubmissionResult.Error -> onError(result.message)
                }
            } finally {
                _submittingQuoteIds.value -= quote.id
            }
        }
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
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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
                        put("isAnonymous", quote.isAnonymous)
                    })
                }

                val backup = org.json.JSONObject().apply {
                    put("formatVersion", 2)
                    put("exportedAt", System.currentTimeMillis())
                    put("settings", createSettingsBackup())
                    put("quotes", jsonArray)
                }

                val output = app.contentResolver.openOutputStream(uri)
                    ?: throw java.io.IOException("Could not open the selected backup file")
                output.use { os ->
                    os.write(backup.toString(4).toByteArray(Charsets.UTF_8))
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
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
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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
                val dbQuotes = repository.getAllQuotesSync()
                val existingCustomKeys = dbQuotes
                    .filter { it.isUserAdded }
                    .map { it.text.trim() to it.author.trim() }
                    .toMutableSet()

                var insertedCustom = 0
                var updatedFavs = 0

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val text = obj.getString("text")
                    val author = obj.getString("author")
                    val isFavorite = obj.optBoolean("isFavorite", false)
                    val isUserAdded = obj.optBoolean("isUserAdded", false)

                    if (isUserAdded) {
                        val customKey = text.trim() to author.trim()
                        if (existingCustomKeys.add(customKey)) {
                            repository.insertQuote(QuoteEntity(
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
                                savedDate = obj.optString("savedDate", null).let { if (it.isNullOrEmpty()) null else it },
                                language = obj.optString("language", QuoteLanguage.ENGLISH)
                                    .takeIf { it in setOf(QuoteLanguage.ENGLISH, QuoteLanguage.GERMAN) }
                                    ?: QuoteLanguage.ENGLISH,
                                textDe = obj.optString("textDe", "").ifBlank { null },
                                isAnonymous = obj.optBoolean("isAnonymous", false)
                            ))
                            insertedCustom++
                        }
                    } else if (isFavorite) {
                        val match = dbQuotes.find { it.text.trim() == text.trim() && it.author.trim() == author.trim() }
                        if (match != null && !match.isFavorite) {
                            repository.toggleFavorite(match.id, true)
                            updatedFavs++
                        }
                    }
                }

                val settingsRestored = settings?.let(::restoreSettingsBackup) ?: false

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess(insertedCustom, updatedFavs, settingsRestored)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Unknown file format")
                }
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
