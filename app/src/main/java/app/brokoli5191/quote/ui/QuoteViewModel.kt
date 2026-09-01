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
import app.brokoli5191.quote.data.QuoteSourceMode
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

    val allQuotes: StateFlow<List<QuoteEntity>> = repository.allQuotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<QuoteEntity>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userAdded: StateFlow<List<QuoteEntity>> = repository.userAdded
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
            val isSeeded = prefs.getBoolean("database_json_seeded_v10", false)
            val count = repository.getQuotesCount()
            if (!isSeeded || count < 30) {
                // Favorite-preserving re-seed: keeps user-added quotes and restores
                // favorites by (text, author) so the bigger DB costs no user data.
                repository.reseedPreservingFavorites(app)
                prefs.edit().putBoolean("database_json_seeded_v10", true).apply()
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
        // Migration: the old single "AMOLED" mode becomes DARK + amoledBlack toggle,
        // so AMOLED can now combine with Material You (DYNAMIC).
        val storedMode = prefs.getString("theme_mode", "DARK") ?: "DARK"
        if (storedMode == "AMOLED") {
            _themeMode.value = "DARK"
            _amoledBlack.value = true
            prefs.edit().putString("theme_mode", "DARK").putBoolean("amoled_black", true).apply()
        } else {
            _themeMode.value = storedMode
            _amoledBlack.value = prefs.getBoolean("amoled_black", false)
        }
        _themeAccent.value = prefs.getString("theme_accent", "Violet") ?: "Violet"
        _quoteSourceMode.value = prefs.getString("quote_source_mode", QuoteSourceMode.ALL)
            ?.takeIf { it in setOf(QuoteSourceMode.ALL, QuoteSourceMode.CURATED, QuoteSourceMode.COMMUNITY) }
            ?: QuoteSourceMode.ALL
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
        prefs.edit().putString("last_update_check_date", today).apply()
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
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun setAmoledBlack(enabled: Boolean) {
        _amoledBlack.value = enabled
        prefs.edit().putBoolean("amoled_black", enabled).apply()
    }

    fun setThemeAccent(accent: String) {
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

    fun loadDailyQuote() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        lastLoadedDate = todayStr
        viewModelScope.launch {
            val quote = repository.getDailyQuote(todayStr, _quoteSourceMode.value)
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
            val quote = repository.cycleDailyQuote(todayStr, _quoteSourceMode.value)
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
                val quote = repository.getDailyQuote(todayStr, _quoteSourceMode.value)
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
        viewModelScope.launch {
            val q = QuoteEntity(
                text = text,
                author = if (author.isBlank()) "Unknown" else author,
                category = if (category.isBlank()) "Love" else category,
                tags = tags,
                isUserAdded = true,
                origin = QuoteOrigin.PERSONAL,
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
                    })
                }

                app.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(jsonArray.toString(4).toByteArray())
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

    fun importBackup(uri: android.net.Uri, onSuccess: (insertedCustom: Int, updatedFavs: Int) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val content = app.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                    ?: throw Exception("Could not open file")

                val jsonArray = org.json.JSONArray(content)
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
                                category = obj.optString("category", "Stoicism"),
                                isFavorite = isFavorite,
                                isUserAdded = true,
                                origin = QuoteOrigin.PERSONAL,
                                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                                aboutAuthor = obj.optString("aboutAuthor", ""),
                                tags = obj.optString("tags", ""),
                                savedDate = obj.optString("savedDate", null).let { if (it.isNullOrEmpty()) null else it }
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

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess(insertedCustom, updatedFavs)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Unknown file format")
                }
            }
        }
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
