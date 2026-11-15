package app.brokoli5191.quote.ui

import android.app.Application
import android.content.Context
import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.CommunityQuoteClient
import app.brokoli5191.quote.data.CommunityQuotePage
import app.brokoli5191.quote.data.CommunitySyncManager
import app.brokoli5191.quote.data.QuoteDao
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.data.QuoteOrigin
import app.brokoli5191.quote.data.QuoteRepository
import app.brokoli5191.quote.data.QuoteSourceMode
import app.brokoli5191.quote.data.QuoteSubmissionClient
import app.brokoli5191.quote.data.QuoteSubmissionResult
import app.brokoli5191.quote.data.QuoteSubmissionStatus
import app.brokoli5191.quote.data.SubmissionError
import app.brokoli5191.quote.data.SubmissionRefreshResult
import app.brokoli5191.quote.data.SubmissionStatusInfo
import app.brokoli5191.quote.data.WithdrawResult
import app.brokoli5191.quote.data.dailyDateKey
import app.brokoli5191.quote.utils.AppLinks
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.io.IOException
import java.time.Duration
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuoteViewModelTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
    private lateinit var db: AppDatabase
    private lateinit var dao: QuoteDao
    private lateinit var repository: QuoteRepository
    private val store = ViewModelStore()
    private var created = 0
    private val community = FakeCommunityClient()
    private val submissions = FakeSubmissionClient()
    private val defaultLocale = Locale.getDefault()

    @Before
    fun setUp() {
        // Coroutines resume where Room or the fakes do, so tests can wait for flows without a looper.
        Dispatchers.setMain(Dispatchers.Unconfined)
        prefs.edit().clear()
            .putBoolean("onboarding_completed", true)
            .putString("app_language", QuoteLanguage.ENGLISH)
            .commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.quoteDao()
        repository = QuoteRepository(dao, 42L)
        installDatabase(db)
        initWorkManager()
    }

    @After
    fun tearDown() {
        store.clear()
        // Cancelled work that still holds one of the process-wide locks releases it when its database call
        // returns. Wait for that while Main is still the test dispatcher: after resetMain it would resume on
        // the paused main looper and keep the lock from every later test in this JVM.
        runBlocking {
            withTimeout(5_000) {
                CommunitySyncManager.mutex.withLock { }
                repository.toggleFavorite(-1, false)
            }
        }
        db.close()
        installDatabase(null)
        Dispatchers.resetMain()
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun aLibraryFilterOnlyCountsAsABackStepOnTheLibraryTab() {
        val vm = newViewModel()
        vm.selectTab("Library")
        vm.setSearchQuery("life")
        vm.selectCategory("Stoicism")
        vm.selectTab("Daily")
        vm.hasBackStack.await { !it }

        vm.selectTab("Saved")
        vm.hasBackStack.await { it }
        vm.popBackStack()
        assertEquals("Daily", vm.selectedTab.value)
        assertEquals("", vm.searchQuery.value)
        assertTrue(vm.selectedCategories.value.isEmpty())

        vm.selectTab("Library")
        vm.setSearchQuery("life")
        vm.popBackStack()
        assertEquals("Library", vm.selectedTab.value)
        assertEquals("", vm.searchQuery.value)
    }

    @Test
    fun navigationStateSurvivesProcessDeath() {
        val handle = SavedStateHandle()
        val before = newViewModel(handle)
        before.selectTab("Saved")
        before.selectSavedSubTab("My Quotes")
        before.setSearchQuery("rumi")
        before.setSelectedCategories(setOf("Community", "Not a category"))
        before.openNewQuoteScreen()
        before.setNewQuoteHasDraft(true)
        before.setSavedSearchQuery("kind")
        before.setSavedStatusFilter(SavedStatusFilter.PENDING)

        val after = newViewModel(restoredAfterProcessDeath(handle))
        assertEquals("Saved", after.selectedTab.value)
        assertEquals("My Quotes", after.savedSubTab.value)
        assertEquals("rumi", after.searchQuery.value)
        assertEquals(setOf("Community"), after.selectedCategories.value)
        assertTrue(after.showNewQuoteScreen.value)
        assertTrue(after.newQuoteHasDraft.value)
        assertEquals("kind", after.savedSearchQuery.value)
        assertEquals(SavedStatusFilter.PENDING, after.savedStatusFilter.value)
    }

    @Test
    fun entryPointsOpenTheirScreenAndKeepADraft() {
        val vm = newViewModel()
        vm.openDevScreen()
        vm.openDestination(AppLinks.DEST_FAVORITES)
        assertFalse(vm.showDevScreen.value)
        assertEquals("Saved", vm.selectedTab.value)
        assertEquals("Favorites", vm.savedSubTab.value)

        vm.openDestination(AppLinks.DEST_NEW_QUOTE)
        assertTrue(vm.showNewQuoteScreen.value)
        assertEquals("My Quotes", vm.savedSubTab.value)

        vm.setNewQuoteHasDraft(true)
        vm.openDestination(AppLinks.DEST_DAILY)
        assertEquals("Daily", vm.selectedTab.value)
        assertTrue("an entry point never drops a draft", vm.showNewQuoteScreen.value)

        vm.setNewQuoteHasDraft(false)
        vm.openDestination(AppLinks.DEST_MY_QUOTES)
        assertFalse(vm.showNewQuoteScreen.value)
        assertEquals("Saved", vm.selectedTab.value)
        assertEquals("My Quotes", vm.savedSubTab.value)

        vm.openDestination("unknown")
        assertEquals("Saved", vm.selectedTab.value)
    }

    @Test
    fun sharedTextWaitsForNewQuote() {
        val vm = newViewModel()
        vm.setPendingDraft("  Be kind.  ", " Rumi ")
        assertEquals(QuoteDraft("Be kind.", "Rumi"), vm.pendingDraft.value)
        assertTrue(vm.showNewQuoteScreen.value)
        vm.consumePendingDraft()
        assertNull(vm.pendingDraft.value)

        vm.setNewQuoteHasDraft(true)
        vm.closeNewQuoteScreen()
        assertFalse(vm.newQuoteHasDraft.value)
        vm.setNewQuoteHasDraft(true)
        assertFalse("a closed form has no draft", vm.newQuoteHasDraft.value)
    }

    @Test
    fun resettingOnboardingLeavesTheDeveloperScreen() {
        val vm = newViewModel()
        vm.openDevScreen()
        vm.resetOnboarding()
        assertTrue(vm.showOnboarding.value)
        assertFalse(vm.showDevScreen.value)
    }

    @Test
    fun firstStartFollowsAGermanDevice() {
        prefs.edit().remove("app_language").commit()
        Locale.setDefault(Locale.GERMANY)
        val vm = newViewModel()
        assertEquals(QuoteLanguage.GERMAN, vm.appLanguage.value)
        // Widget and reminder read the pref, so they start in German too.
        assertEquals(QuoteLanguage.GERMAN, prefs.getString("app_language", null))
    }

    @Test
    fun aStoredLanguageWinsOverTheDeviceLanguage() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals(QuoteLanguage.ENGLISH, newViewModel().appLanguage.value)
    }

    @Test
    fun otherDeviceLanguagesStartInEnglish() {
        prefs.edit().remove("app_language").commit()
        Locale.setDefault(Locale.FRANCE)
        assertEquals(QuoteLanguage.ENGLISH, newViewModel().appLanguage.value)
    }

    @Test
    fun favoritesKeepOwnUntranslatedAndAnonymousQuotesInEveryLanguage() {
        val ids = insert(
            own("Ein eigener Gedanke", language = QuoteLanguage.GERMAN, favorite = true),
            QuoteEntity(
                text = "Only in English",
                author = "Reader",
                category = "Life",
                origin = QuoteOrigin.COMMUNITY,
                serverId = "c-1",
                isFavorite = true,
                savedDate = "2000"
            ),
            QuoteEntity(
                text = "A reflection",
                textDe = "Eine Betrachtung",
                author = "",
                category = "Reflections",
                isAnonymous = true,
                isFavorite = true,
                savedDate = "3000"
            )
        )
        // English, anonymous quotes hidden.
        val vm = newViewModel()
        assertEquals(ids.toSet(), vm.favorites.await { it.size == 3 }.map { it.id }.toSet())
        vm.favoritesTotal.await { it == 3 }

        vm.setAppLanguage(QuoteLanguage.GERMAN)
        val german = vm.favorites.await { list -> list.any { it.text == "Eine Betrachtung" } }
        assertEquals(3, german.size)
        assertTrue(german.any { it.text == "Only in English" })
        assertTrue(german.any { it.text == "Ein eigener Gedanke" })
    }

    @Test
    fun theLibraryListsOwnQuotesInEveryLanguage() {
        insert(
            own("Ein eigener Gedanke", language = QuoteLanguage.GERMAN),
            bundled("Shown in English"),
            QuoteEntity(text = "A reflection", textDe = "Eine Betrachtung", author = "", category = "Reflections", isAnonymous = true)
        )
        val texts = newViewModel().allQuotes.await { it.isNotEmpty() }.map { it.text }
        assertTrue("Ein eigener Gedanke" in texts)
        assertTrue("Shown in English" in texts)
        assertFalse("A reflection" in texts)
    }

    @Test
    fun librarySearchIgnoresSpacesCaseAndAccents() {
        insert(
            bundled("The secret of getting ahead is getting started.", author = "Mark Twain"),
            bundled("I am no bird.", author = "Charlotte Brontë")
        )
        val vm = newViewModel()
        vm.setSearchQuery("Twain ")
        assertEquals(listOf("Mark Twain"), vm.filteredQuotes.await { it.size == 1 }.map { it.author })
        vm.setSearchQuery("bronte")
        assertEquals(listOf("Charlotte Brontë"), vm.filteredQuotes.await { list -> list.any { it.author != "Mark Twain" } }.map { it.author })
    }

    @Test
    fun authorShortcutsCountAndSearchTheLibrary() {
        insert(
            bundled("Luck is what happens when preparation meets opportunity.", author = "Seneca"),
            bundled("We suffer more in imagination than in reality.", author = "Seneca"),
            bundled("What you seek is seeking you.", author = "Rumi")
        )
        val vm = newViewModel()
        vm.allQuotes.await { it.size == 3 }
        awaitTrue { vm.authorQuoteCount("seneca") == 2 }
        assertEquals(1, vm.authorQuoteCount("Rumi"))
        assertEquals(0, vm.authorQuoteCount("Unknown"))

        vm.selectCategory("Stoicism")
        vm.openLibrarySearch(" Seneca ")
        assertEquals("Library", vm.selectedTab.value)
        assertEquals("Seneca", vm.searchQuery.value)
        assertTrue(vm.selectedCategories.value.isEmpty())
    }

    @Test
    fun savedListsSortAndSearch() {
        insert(
            bundled("Luck is what happens.", author = "Seneca", favorite = true, savedDate = "1000"),
            bundled("Know thyself.", author = "Aristotle", favorite = true, savedDate = "3000"),
            bundled("„Wealth consists in having few wants.“", author = "Epictetus", favorite = true, savedDate = "2000")
        )
        val vm = newViewModel()
        fun authors() = vm.favorites.value.map { it.author }
        vm.favorites.await { it.size == 3 }
        assertEquals(listOf("Aristotle", "Epictetus", "Seneca"), authors())

        vm.setFavoritesSort(SavedSort.OLDEST)
        vm.favorites.await { list -> list.map { it.author } == listOf("Seneca", "Epictetus", "Aristotle") }
        vm.setFavoritesSort(SavedSort.TEXT)
        // The leading quotation mark does not count.
        vm.favorites.await { list -> list.map { it.author } == listOf("Aristotle", "Seneca", "Epictetus") }
        vm.setFavoritesSort(SavedSort.AUTHOR)
        vm.favorites.await { list -> list.map { it.author } == listOf("Aristotle", "Epictetus", "Seneca") }
        assertEquals(SavedSort.AUTHOR, prefs.getString("favorites_sort", null))
        vm.setFavoritesSort("bogus")
        assertEquals(SavedSort.AUTHOR, vm.favoritesSort.value)

        vm.setSavedSearchQuery("seneca ")
        vm.favorites.await { list -> list.map { it.author } == listOf("Seneca") }
        assertEquals(3, vm.favoritesTotal.await { it == 3 })
    }

    @Test
    fun myQuotesFilterByReviewState() {
        insert(
            own("Draft"),
            own("Waiting", QuoteSubmissionStatus.PENDING, "s-1"),
            own("Published", QuoteSubmissionStatus.APPROVED, "s-2"),
            own("Declined", QuoteSubmissionStatus.REJECTED, "s-3"),
            own("Gone", QuoteSubmissionStatus.REMOVED, "s-4")
        )
        val vm = newViewModel()
        vm.userAdded.await { it.size == 5 }

        vm.setSavedStatusFilter(SavedStatusFilter.NOT_APPROVED)
        vm.userAdded.await { list -> list.map { it.text }.toSet() == setOf("Declined", "Gone") }
        vm.setSavedStatusFilter(SavedStatusFilter.PENDING)
        vm.userAdded.await { list -> list.map { it.text } == listOf("Waiting") }
        vm.setSavedStatusFilter("bogus")
        assertEquals(SavedStatusFilter.PENDING, vm.savedStatusFilter.value)

        vm.setSavedStatusFilter(SavedStatusFilter.ALL)
        vm.setSavedSearchQuery("publ")
        vm.userAdded.await { list -> list.map { it.text } == listOf("Published") }
        assertEquals(5, vm.userAddedTotal.await { it == 5 })
    }

    @Test
    fun addingChecksTheStoredTextInCodePoints() {
        val vm = newViewModel()
        // Stored without the wrapping marks: two characters.
        vm.addUserQuote("\"Hi\"", "", "Life", "")
        // Two characters for the server, three UTF-16 units.
        vm.addUserQuote("a😀", "", "Life", "")
        vm.addUserQuote("Nine tags are too many.", "", "Life", (1..9).joinToString(",") { "tag$it" })
        vm.addUserQuote("A tag is too long.", "", "Life", "x".repeat(31))
        vm.addUserQuote("😀😀😀", "", "Life", "Tag, tag")
        val stored = vm.userAdded.await { it.isNotEmpty() }
        assertEquals(listOf("😀😀😀"), stored.map { it.text })
        assertEquals("Tag", stored.single().tags)
    }

    @Test
    fun aDoubleTapAddsTheQuoteOnceInItsLanguage() {
        val vm = newViewModel()
        vm.addUserQuote("Stay curious.", "Me", "Life", "", language = QuoteLanguage.GERMAN)
        vm.addUserQuote("Stay curious.", "Me", "Life", "", language = QuoteLanguage.GERMAN)
        val first = vm.userAdded.await { it.isNotEmpty() }
        assertEquals(1, first.size)
        assertEquals(QuoteLanguage.GERMAN, runBlocking { dao.getQuoteById(first.single().id) }!!.language)

        // Adding the same quote again later is deliberate.
        ShadowSystemClock.advanceBy(Duration.ofSeconds(3))
        vm.addUserQuote("Stay curious.", "Me", "Life", "", language = QuoteLanguage.GERMAN)
        vm.userAdded.await { it.size == 2 }
    }

    @Test
    fun editingKeepsTheReviewUnlessTheSubmittedContentChanges() {
        val id = insert(own("Be kind.", QuoteSubmissionStatus.REJECTED, "s-1", author = "Anonymous"))[0]
        val vm = newViewModel()
        val displayed = vm.userAdded.await { it.isNotEmpty() }.single()
        assertTrue(displayed.editKeepsReview("\"Be kind.\"", ""))

        assertTrue(update(vm, displayed, "\"Be kind.\"", ""))
        val kept = runBlocking { dao.getQuoteById(id) }!!
        assertEquals("Be kind.", kept.text)
        assertEquals("Anonymous", kept.author)
        assertEquals(QuoteSubmissionStatus.REJECTED, kept.submissionStatus)
        assertEquals("s-1", kept.submissionId)

        assertTrue(update(vm, displayed, "Be kinder.", "", language = QuoteLanguage.GERMAN))
        val reset = runBlocking { dao.getQuoteById(id) }!!
        assertEquals(QuoteSubmissionStatus.NOT_SUBMITTED, reset.submissionStatus)
        assertNull(reset.submissionId)
        assertEquals("Anonymous", reset.author)
        assertEquals(QuoteLanguage.GERMAN, reset.language)

        assertFalse("too short once stored", update(vm, displayed, "\"Hi\"", ""))
    }

    @Test
    fun resubmittingAPublishedQuoteSaysSo() {
        prefs.edit().putString(CommunitySyncManager.KEY_INSTALLATION_ID, UUID.randomUUID().toString()).commit()
        val id = insert(own("Stay hungry."))[0]
        submissions.submitResult = QuoteSubmissionResult.Success(
            "s-9",
            QuoteSubmissionStatus.APPROVED,
            communityQuoteId = "c-9",
            duplicate = true
        )
        val vm = newViewModel()
        assertEquals("published", submit(vm, id, language = QuoteLanguage.GERMAN))
        val stored = runBlocking { dao.getQuoteById(id) }!!
        assertEquals(QuoteSubmissionStatus.APPROVED, stored.submissionStatus)
        assertEquals("c-9", stored.linkedServerId)
        assertEquals(QuoteLanguage.GERMAN, stored.language)
        assertEquals(listOf(QuoteLanguage.GERMAN), submissions.submittedLanguages)

        val rejectedId = insert(own("Be loud."))[0]
        submissions.submitResult = QuoteSubmissionResult.Success("s-10", QuoteSubmissionStatus.REJECTED, duplicate = true)
        assertEquals("error:${SubmissionError.ALREADY_REVIEWED}", submit(vm, rejectedId))

        val newId = insert(own("Be brave."))[0]
        submissions.submitResult = QuoteSubmissionResult.Success("s-11")
        assertEquals("success", submit(vm, newId))
    }

    @Test
    fun deletingAQuoteInReviewReportsAWithdrawalThatMustWait() {
        prefs.edit().putString(CommunitySyncManager.KEY_INSTALLATION_ID, UUID.randomUUID().toString()).commit()
        val id = insert(own("In review", QuoteSubmissionStatus.PENDING, "s-1"))[0]
        val quote = runBlocking { dao.getQuoteById(id) }!!
        submissions.withdrawResult = WithdrawResult.Error(SubmissionError.NETWORK)
        val vm = newViewModel()

        vm.deleteQuote(id)
        assertEquals(QuoteUiEvent.WithdrawalQueued(1), vm.events.awaitFirst())
        assertNull(runBlocking { dao.getQuoteById(id) })

        // Undo brings it back still in review; the queued withdrawal is dropped.
        vm.restoreDeletedQuote(quote)
        awaitTrue { runBlocking { dao.getQuoteById(id) }?.submissionStatus == QuoteSubmissionStatus.PENDING }
    }

    @Test
    fun deletingAQuoteAModeratorAlreadyReviewedReportsIt() {
        prefs.edit().putString(CommunitySyncManager.KEY_INSTALLATION_ID, UUID.randomUUID().toString()).commit()
        val ids = insert(
            own("In review", QuoteSubmissionStatus.PENDING, "s-1"),
            own("Also in review", QuoteSubmissionStatus.PENDING, "s-2")
        )
        submissions.withdrawResult = WithdrawResult.Error(SubmissionError.ALREADY_REVIEWED)
        val vm = newViewModel()

        vm.deleteQuotes(ids.toSet())
        assertEquals(QuoteUiEvent.WithdrawalFailed(SubmissionError.ALREADY_REVIEWED, 2), vm.events.awaitFirst())
    }

    @Test
    fun bulkDeleteIsUndoneAtOnce() {
        val ids = insert(own("First"), own("Second"))
        val vm = newViewModel()
        val quotes = vm.userAdded.await { it.size == 2 }
        vm.deleteQuotes(ids.toSet())
        vm.userAdded.await { it.isEmpty() }
        vm.restoreDeletedQuotes(quotes)
        vm.userAdded.await { list -> list.map { it.id }.toSet() == ids.toSet() }
    }

    @Test
    fun unfavoritingIsUndoneWithTheOriginalSavedDate() {
        val ids = insert(
            bundled("Know thyself.", favorite = true, savedDate = "12345"),
            own("My own favorite", favorite = true)
        )
        val vm = newViewModel()
        val favorites = vm.favorites.await { it.size == 2 }

        // Own quotes can be unfavorited like any other.
        vm.unfavoriteQuotes(ids.toSet())
        vm.favorites.await { it.isEmpty() }
        vm.restoreFavorites(favorites)
        val restored = vm.favorites.await { it.size == 2 }
        assertEquals("12345", restored.single { it.id == ids[0] }.savedDate)

        vm.toggleFavorite(restored.single { it.id == ids[1] })
        vm.favorites.await { list -> list.map { it.id } == listOf(ids[0]) }
    }

    @Test
    fun anotherQuoteCanBeUndone() {
        insert(*(1..5).map { bundled("Quote $it") }.toTypedArray())
        val vm = newViewModel()
        val first = vm.dailyQuote.await { it != null }!!
        assertFalse(vm.canUndoCycle.value)

        vm.cycleDailyQuote()
        vm.dailyQuote.await { it != null && it.id != first.id }
        vm.canUndoCycle.await { it }

        vm.undoCycleDailyQuote()
        vm.dailyQuote.await { it?.id == first.id }
        vm.canUndoCycle.await { !it }
        val stored = runBlocking {
            repository.getDailyQuote(dailyDateKey(), QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false)
        }
        assertEquals(first.id, stored!!.id)
    }

    @Test
    fun previousDaysListEarlierDailyQuotesOnly() {
        val ids = insert(bundled("Yesterday's quote"), bundled("Another quote"))
        val yesterday = dailyDateKey(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time)
        runBlocking { dao.storeDailySelection("${yesterday}_en_0_all", ids[0]) }
        val vm = newViewModel()
        vm.dailyQuote.await { it != null }

        val history = vm.dailyHistory.await { it.isNotEmpty() }
        assertEquals(listOf(yesterday), history.map { it.date })
        assertEquals(ids[0], history.single().quote.id)
    }

    @Test
    fun syncStateShowsFailuresAndResumingRetriesThem() {
        community.online = false
        val vm = newViewModel()
        val failed = vm.syncState.await { it is SyncUiState.Failed } as SyncUiState.Failed
        assertNull(failed.lastSuccessAt)
        assertTrue(vm.communitySyncFinished.await { it })

        // A failed sync does not hold off the next one.
        community.online = true
        val callsBefore = community.calls
        awaitTrue {
            vm.onAppResumed()
            community.calls > callsBefore
        }
        vm.syncState.await { it is SyncUiState.Success }

        // A fresh success does.
        val callsAfter = community.calls
        vm.onAppResumed()
        assertEquals(callsAfter, community.calls)
    }

    @Test
    fun checkingReviewStatesReportsAnIncompleteCheck() {
        prefs.edit().putString(CommunitySyncManager.KEY_INSTALLATION_ID, UUID.randomUUID().toString()).commit()
        insert(own("In review", QuoteSubmissionStatus.PENDING, "s-1"))
        val vm = newViewModel()
        val result = CompletableDeferred<SubmissionRefreshResult>()
        vm.refreshSubmissionStatuses { result.complete(it) }
        assertFalse(runBlocking { withTimeout(5_000) { result.await() } }.complete)
        vm.submissionRefreshRunning.await { !it }
    }

    private fun newViewModel(savedState: SavedStateHandle = SavedStateHandle()): QuoteViewModel {
        val manager = CommunitySyncManager(context, repository, community, submissions)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                QuoteViewModel(context, repository, savedState, submissions, manager) as T
        }
        return ViewModelProvider(store, factory)["vm${created++}", QuoteViewModel::class.java]
    }

    // What the activity's saved state goes through when the process dies: a Bundle written to a Parcel.
    private fun restoredAfterProcessDeath(handle: SavedStateHandle): SavedStateHandle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(handle.savedStateProvider().saveState())
            parcel.setDataPosition(0)
            return SavedStateHandle.createHandle(parcel.readBundle(javaClass.classLoader), null)
        } finally {
            parcel.recycle()
        }
    }

    private fun update(vm: QuoteViewModel, quote: QuoteEntity, text: String, author: String, language: String? = null): Boolean {
        val saved = CompletableDeferred<Boolean>()
        vm.updateUserQuote(quote, text, author, quote.category, quote.tags, language) { saved.complete(it) }
        return runBlocking { withTimeout(5_000) { saved.await() } }
    }

    private fun submit(vm: QuoteViewModel, id: Int, language: String? = null): String {
        val quote = runBlocking { dao.getQuoteById(id) }!!
        val outcome = CompletableDeferred<String>()
        vm.submitQuoteForReview(
            quote,
            onSuccess = { outcome.complete("success") },
            onError = { outcome.complete("error:$it") },
            language = language,
            onAlreadyPublished = { outcome.complete("published") }
        )
        return runBlocking { withTimeout(5_000) { outcome.await() } }
    }

    private fun insert(vararg quotes: QuoteEntity): List<Int> = runBlocking { quotes.map { dao.insertQuote(it).toInt() } }

    private fun bundled(text: String, author: String = "Author", favorite: Boolean = false, savedDate: String? = null) =
        QuoteEntity(text = text, textDe = "DE $text", author = author, category = "Life", isFavorite = favorite, savedDate = savedDate)

    private fun own(
        text: String,
        status: String = QuoteSubmissionStatus.NOT_SUBMITTED,
        submissionId: String? = null,
        author: String = "Me",
        language: String = QuoteLanguage.ENGLISH,
        favorite: Boolean = false
    ) = QuoteEntity(
        text = text,
        author = author,
        category = "Life",
        isUserAdded = true,
        origin = QuoteOrigin.PERSONAL,
        language = language,
        submissionStatus = status,
        submissionId = submissionId,
        isFavorite = favorite,
        savedDate = if (favorite) "1000" else null
    )

    private fun <T> Flow<T>.await(predicate: (T) -> Boolean): T = runBlocking { withTimeout(5_000) { first(predicate) } }

    private fun Flow<QuoteUiEvent>.awaitFirst(): QuoteUiEvent = runBlocking { withTimeout(5_000) { first() } }

    private fun awaitTrue(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met in time" }
            Thread.sleep(10)
        }
    }

    private fun installDatabase(database: AppDatabase?) {
        // The author lookups and the backup manager use AppDatabase.getInstance().
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, database)
    }

    // WorkManager is not initialized under Robolectric; the ViewModel schedules the sync worker.
    private fun initWorkManager() {
        WorkManagerImpl.setDelegate(null)
        WorkManager.initialize(
            context,
            Configuration.Builder()
                .setExecutor(workExecutor)
                .setTaskExecutor(workExecutor)
                .setMinimumLoggingLevel(android.util.Log.ERROR)
                .build()
        )
    }

    private class FakeCommunityClient : CommunityQuoteClient() {
        @Volatile var online = true
        @Volatile var calls = 0

        override suspend fun updates(after: Long): Result<CommunityQuotePage> {
            calls++
            return if (online) {
                Result.success(CommunityQuotePage(after, hasMore = false, quotes = emptyList(), deletedIds = emptyList()))
            } else {
                Result.failure(IOException("offline"))
            }
        }
    }

    private class FakeSubmissionClient : QuoteSubmissionClient() {
        @Volatile var submitResult: QuoteSubmissionResult = QuoteSubmissionResult.Success("s-1")
        @Volatile var withdrawResult: WithdrawResult = WithdrawResult.Success
        val submittedLanguages = mutableListOf<String>()

        override suspend fun submit(
            quote: QuoteEntity,
            installationId: String,
            appVersion: String,
            language: String
        ): QuoteSubmissionResult {
            submittedLanguages += language
            return submitResult
        }

        override suspend fun status(submissionId: String, installationId: String): Result<SubmissionStatusInfo> =
            Result.failure(IOException("offline"))

        override suspend fun withdraw(submissionId: String, installationId: String): WithdrawResult = withdrawResult
    }

    private companion object {
        val workExecutor = Executors.newSingleThreadExecutor()
    }
}
