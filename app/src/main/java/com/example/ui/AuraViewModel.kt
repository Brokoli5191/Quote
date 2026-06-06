package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.QuoteEntity
import com.example.data.QuoteRepository
import com.example.utils.NotificationScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AuraViewModel(private val repository: QuoteRepository) : ViewModel() {

    private val _dailyQuote = MutableStateFlow<QuoteEntity?>(null)
    val dailyQuote: StateFlow<QuoteEntity?> = _dailyQuote.asStateFlow()

    private val _verificationResult = MutableStateFlow<com.example.utils.CategoryVerificationResult?>(null)
    val verificationResult: StateFlow<com.example.utils.CategoryVerificationResult?> = _verificationResult.asStateFlow()

    private val _selectedTab = MutableStateFlow("Daily") // Daily, Library, Saved, Settings
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _selectedCategories = MutableStateFlow<Set<String>>(emptySet())
    val selectedCategories: StateFlow<Set<String>> = _selectedCategories.asStateFlow()

    private val _widgetStyle = MutableStateFlow("Expressive") // Expressive, Minimal, Compact
    val widgetStyle: StateFlow<String> = _widgetStyle.asStateFlow()

    private val _themeMode = MutableStateFlow("AMOLED") // LIGHT, DARK, AMOLED, DYNAMIC
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _themeAccent = MutableStateFlow("Violet") // Violet, Amber, Green, Blue, Rose
    val themeAccent: StateFlow<String> = _themeAccent.asStateFlow()

    private val _dailyReminderEnabled = MutableStateFlow(false)
    val dailyReminderEnabled: StateFlow<Boolean> = _dailyReminderEnabled.asStateFlow()

    private val _dailyReminderHour = MutableStateFlow(8)
    val dailyReminderHour: StateFlow<Int> = _dailyReminderHour.asStateFlow()

    private val _dailyReminderMinute = MutableStateFlow(0)
    val dailyReminderMinute: StateFlow<Int> = _dailyReminderMinute.asStateFlow()

    val allQuotes: StateFlow<List<QuoteEntity>> = repository.allQuotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<QuoteEntity>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userAdded: StateFlow<List<QuoteEntity>> = repository.userAdded
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredQuotes: StateFlow<List<QuoteEntity>> = combine(
        allQuotes,
        searchQuery,
        selectedCategory,
        selectedCategories
    ) { quotes, query, category, categories ->
        var list = quotes
        if (categories.isNotEmpty()) {
            list = list.filter { q ->
                categories.any { cat -> q.category.equals(cat, ignoreCase = true) }
            }
        } else if (category != null) {
            list = list.filter { it.category.equals(category, ignoreCase = true) }
        }
        if (query.isNotEmpty()) {
            list = list.filter {
                it.text.contains(query, ignoreCase = true) ||
                it.author.contains(query, ignoreCase = true) ||
                it.tags.contains(query, ignoreCase = true)
            }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadDailyQuote()
        runVerification()
    }

    fun runVerification() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = com.example.utils.CategoryQuoteVerifier.verify(repository)
            _verificationResult.value = result
        }
    }

    fun checkAndSeedDatabase(context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val prefs = context.getSharedPreferences("aura_prefs", android.content.Context.MODE_PRIVATE)
            val isSeeded = prefs.getBoolean("database_json_seeded_v4_0", false)
            val count = repository.getQuotesCount()
            if (!isSeeded || count < 30) {
                repository.clearAllQuotes()
                repository.preseedDatabase(context)
                prefs.edit().putBoolean("database_json_seeded_v4_0", true).apply()
                loadDailyQuote()
            }
            runVerification()
        }
    }

    fun loadThemeSettings(context: android.content.Context) {
        val prefs = context.getSharedPreferences("aura_prefs", android.content.Context.MODE_PRIVATE)
        _themeMode.value = prefs.getString("theme_mode", "AMOLED") ?: "AMOLED"
        _themeAccent.value = prefs.getString("theme_accent", "Violet") ?: "Violet"
        _widgetStyle.value = prefs.getString("widget_style", "Expressive") ?: "Expressive"
        _dailyReminderEnabled.value = prefs.getBoolean("daily_reminder_enabled", false)
        _dailyReminderHour.value = prefs.getInt("daily_reminder_hour", 8)
        _dailyReminderMinute.value = prefs.getInt("daily_reminder_minute", 0)
    }

    fun setThemeMode(context: android.content.Context, mode: String) {
        _themeMode.value = mode
        val prefs = context.getSharedPreferences("aura_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun setThemeAccent(context: android.content.Context, accent: String) {
        _themeAccent.value = accent
        val prefs = context.getSharedPreferences("aura_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("theme_accent", accent).apply()
    }

    fun loadDailyQuote() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date())
        viewModelScope.launch {
            val quote = repository.getDailyQuote(todayStr)
            _dailyQuote.value = quote
        }
    }

    fun cycleDailyQuote(context: android.content.Context) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date())
        viewModelScope.launch {
            val quote = repository.cycleDailyQuote(todayStr)
            _dailyQuote.value = quote
            
            // Notify widget to update instantly
            val updateIntent = android.content.Intent("com.example.UPDATE_WIDGET").apply {
                component = android.content.ComponentName(context, "com.example.widget.AuraWidgetProvider")
            }
            context.sendBroadcast(updateIntent)
        }
    }

    fun selectTab(tab: String) {
        _selectedTab.value = tab
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
        if (category == null) {
            _selectedCategories.value = emptySet()
        } else {
            _selectedCategories.value = setOf(category)
        }
    }

    fun toggleCategorySelected(category: String) {
        val currentSet = _selectedCategories.value
        val newSet = if (currentSet.contains(category)) {
            currentSet - category
        } else {
            currentSet + category
        }
        _selectedCategories.value = newSet
        if (newSet.isEmpty()) {
            _selectedCategory.value = null
        } else {
            _selectedCategory.value = newSet.first()
        }
    }

    fun clearCategorySelection() {
        _selectedCategories.value = emptySet()
        _selectedCategory.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setWidgetStyle(style: String) {
        _widgetStyle.value = style
    }

    fun toggleFavorite(quote: QuoteEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(quote.id, !quote.isFavorite)
            // If the favorited quote is the daily quote, update daily quote UI state
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

    fun setDailyReminderEnabled(context: android.content.Context, enabled: Boolean) {
        _dailyReminderEnabled.value = enabled
        val prefs = context.getSharedPreferences("aura_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("daily_reminder_enabled", enabled).apply()

        if (enabled) {
            NotificationScheduler.scheduleDailyNotification(context, _dailyReminderHour.value, _dailyReminderMinute.value)
        } else {
            NotificationScheduler.cancelDailyNotification(context)
        }
    }

    fun updateDailyReminderTime(context: android.content.Context, hour: Int, minute: Int) {
        _dailyReminderHour.value = hour
        _dailyReminderMinute.value = minute
        val prefs = context.getSharedPreferences("aura_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("daily_reminder_hour", hour)
            .putInt("daily_reminder_minute", minute)
            .apply()

        if (_dailyReminderEnabled.value) {
            NotificationScheduler.scheduleDailyNotification(context, hour, minute)
        }
    }
}

class AuraViewModelFactory(private val repository: QuoteRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuraViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuraViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
