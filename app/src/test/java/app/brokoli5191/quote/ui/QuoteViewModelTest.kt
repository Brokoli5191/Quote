package app.brokoli5191.quote.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.room.Room
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.CommunityQuoteClient
import app.brokoli5191.quote.data.CommunityQuotePage
import app.brokoli5191.quote.data.CommunitySyncManager
import app.brokoli5191.quote.data.DailySelectionEntity
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
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

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
    private val activityStores = ArrayList<ViewModelStore>()
    private var created = 0
    private val community = FakeCommunityClient()
    private val submissions = FakeSubmissionClient()
    private val defaultLocale = Locale.getDefault()
    private val defaultTimeZone = TimeZone.getDefault()

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
        activityStores.forEach { it.clear() }
        // Cancelled work that still holds one of the process-wide locks releases it when its database call
        // returns. Wait for that while Main is still the test dispatcher: after resetMain it would resume on
        // the paused main looper and keep the lock from every later test in this JVM.
        runBlocking {
            withTimeout(AWAIT_TIMEOUT_MS) {
                CommunitySyncManager.mutex.withLock { }
                repository.toggleFavorite(-1, false)
            }
        }
        db.close()
        installDatabase(null)
        Dispatchers.resetMain()
        Locale.setDefault(defaultLocale)
        TimeZone.setDefault(defaultTimeZone)
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
    fun entryPointsKeepSavedWhileItsEditSheetIsOpen() {
        val vm = newViewModel()
        vm.openDestination(AppLinks.DEST_MY_QUOTES)
        vm.setSavedEditOpen(true)
        vm.openDestination(AppLinks.DEST_DAILY)
        assertEquals("an entry point never drops an open edit", "Saved", vm.selectedTab.value)

        vm.setSavedEditOpen(false)
        vm.openDestination(AppLinks.DEST_DAILY)
        assertEquals("Daily", vm.selectedTab.value)
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
    fun aFreshInstallFollowsAGermanDevice() {
        prefs.edit().clear().commit()
        Locale.setDefault(Locale.GERMANY)
        val vm = newViewModel()
        assertEquals(QuoteLanguage.GERMAN, vm.appLanguage.value)
        assertTrue(vm.showOnboarding.value)
        // Widget and reminder read the pref, so they start in German too.
        assertEquals(QuoteLanguage.GERMAN, prefs.getString("app_language", null))
    }

    @Test
    fun aStoredLanguageWinsOverTheDeviceLanguage() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals(QuoteLanguage.ENGLISH, newViewModel().appLanguage.value)
    }

    @Test
    fun anEarlierInstallWithoutAStoredLanguageStaysEnglish() {
        Locale.setDefault(Locale.GERMANY)
        // Onboarding stores no language when the English default is kept.
        prefs.edit().remove("app_language").putString("theme_mode", "DARK").commit()
        assertEquals(QuoteLanguage.ENGLISH, newViewModel().appLanguage.value)
        assertEquals(QuoteLanguage.ENGLISH, prefs.getString("app_language", null))

        // From before 1.7.0 and the onboarding: only an old seed flag.
        prefs.edit().clear().putBoolean("database_json_seeded_v9", true).commit()
        val old = newViewModel()
        assertEquals(QuoteLanguage.ENGLISH, old.appLanguage.value)
        assertFalse(old.showOnboarding.value)
    }

    @Test
    fun otherDeviceLanguagesStartInEnglish() {
        prefs.edit().clear().commit()
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
    fun aCategoryCombinedWithLocalStillFollowsTheSourceMode() {
        insert(
            bundled("A curated thought."),
            QuoteEntity(text = "A community thought.", author = "Reader", category = "Life", origin = QuoteOrigin.COMMUNITY, serverId = "c-1"),
            own("My own thought.")
        )
        val vm = newViewModel()
        vm.setQuoteSourceMode(QuoteSourceMode.CURATED)
        vm.setSelectedCategories(setOf("Life", "Local"))
        val texts = vm.filteredQuotes.await { it.size >= 2 }.map { it.text }.toSet()
        assertEquals(setOf("A curated thought.", "My own thought."), texts)
    }

    @Test
    fun unfavoritingAnOwnQuoteAlsoUnfavoritesItsCommunityCopy() {
        val (ownId, copyId) = insert(
            own("Kindness is free.", status = QuoteSubmissionStatus.APPROVED, submissionId = "sub-9", favorite = true)
                .copy(linkedServerId = "c-9", savedDate = "1"),
            QuoteEntity(
                text = "Kindness is free.", author = "Me", category = "Life", origin = QuoteOrigin.COMMUNITY,
                serverId = "c-9", isFavorite = true, savedDate = "2"
            )
        )
        val vm = newViewModel()
        vm.toggleFavorite(runBlocking { dao.getQuoteById(ownId) }!!)
        awaitTrue { runBlocking { dao.getQuoteById(ownId) }?.isFavorite == false }
        awaitTrue { runBlocking { dao.getQuoteById(copyId) }?.isFavorite == false }
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
    fun editingANotSubmittedQuoteDropsAKeptSubmissionIdOnlyWhenTheContentChanges() {
        // A 404 from an unknown installation id leaves a quote not submitted with its id (see
        // CommunitySyncManager.applyFetchedStatus), so the feed can still link it.
        val id = insert(own("Be kind.", QuoteSubmissionStatus.NOT_SUBMITTED, "s-1"))[0]
        val vm = newViewModel()
        val displayed = vm.userAdded.await { it.isNotEmpty() }.single()

        val saved = CompletableDeferred<Boolean>()
        vm.updateUserQuote(displayed, displayed.text, displayed.author, displayed.category, "calm") { saved.complete(it) }
        assertTrue(runBlocking { withTimeout(AWAIT_TIMEOUT_MS) { saved.await() } })
        val tagged = runBlocking { dao.getQuoteById(id) }!!
        assertEquals("calm", tagged.tags)
        assertEquals("s-1", tagged.submissionId)

        assertTrue(update(vm, displayed, "Be kinder.", displayed.author))
        val rewritten = runBlocking { dao.getQuoteById(id) }!!
        assertEquals(QuoteSubmissionStatus.NOT_SUBMITTED, rewritten.submissionStatus)
        assertNull(rewritten.submissionId)
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
        holdSyncs()
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
    fun theWidgetFollowsSettingsAnotherQuoteAndItsUndo() {
        insert(*(1..5).map { bundled("Quote $it") }.toTypedArray())
        holdSyncs()
        val app = WidgetRecordingApp()
        val vm = newViewModel(app = app)
        val first = vm.dailyQuote.await { it != null }!!
        assertTrue("starting the app leaves the widget alone", app.refreshes.isEmpty())

        vm.setAppLanguage(QuoteLanguage.GERMAN)
        awaitTrue { app.refreshes.size == 1 }
        // Today's quote stays across a language change.
        assertEquals(first.id, app.refreshes[0])

        vm.cycleDailyQuote()
        val next = vm.dailyQuote.await { it != null && it.id != first.id }!!
        awaitTrue { app.refreshes.size == 2 }
        assertEquals("the widget is asked once the new pick is stored", next.id, app.refreshes[1])

        vm.undoCycleDailyQuote()
        vm.dailyQuote.await { it?.id == first.id }
        awaitTrue { app.refreshes.size == 3 }
        assertEquals(first.id, app.refreshes[2])
    }

    @Test
    fun theWidgetFollowsANewDayWhicheverLoadNoticesIt() {
        insert(*(1..5).map { bundled("Quote $it") }.toTypedArray())
        TimeZone.setDefault(TimeZone.getTimeZone("Etc/GMT+12"))
        holdSyncs()
        val app = WidgetRecordingApp()
        val vm = newViewModel(app = app)
        val earlier = vm.dailyQuote.await { it != null }!!

        // Fourteen hours east of UTC it is a later day, as after midnight. A plain load (after a sync,
        // Try again) notices it.
        TimeZone.setDefault(TimeZone.getTimeZone("Etc/GMT-14"))
        vm.loadDailyQuote()
        awaitTrue { app.refreshes.isNotEmpty() }
        val later = vm.dailyQuote.await { it != null && it.id != earlier.id }!!
        assertEquals(later.id, app.refreshes[0])

        // The same day again needs no refresh; resuming on another day does.
        vm.refreshDailyQuoteIfNeeded()
        vm.loadDailyQuote()
        TimeZone.setDefault(TimeZone.getTimeZone("Etc/GMT+12"))
        vm.refreshDailyQuoteIfNeeded()
        awaitTrue { app.refreshes.size >= 2 }
        vm.dailyQuote.await { it?.id == earlier.id }
        assertEquals(listOf(later.id, earlier.id), app.refreshes.toList())
    }

    @Test
    fun aNewDayNoticedWhileTheFirstLoadRunsReachesTheWidgetOnce() {
        insert(*(1..5).map { bundled("Quote $it") }.toTypedArray())
        TimeZone.setDefault(TimeZone.getTimeZone("Etc/GMT+12"))
        val earlierDay = dailyDateKey()
        val gated = GatedDao(dao).apply { holdKeysContaining = "${earlierDay}_" }
        holdSyncs()
        val app = WidgetRecordingApp()
        val vm = newViewModel(app = app, repository = QuoteRepository(gated, 42L))
        try {
            // The first load holds the repository lock while it stores its pick.
            runBlocking { withTimeout(AWAIT_TIMEOUT_MS) { gated.held.await() } }
            val readsBefore = gated.dayReads.get()
            TimeZone.setDefault(TimeZone.getTimeZone("Etc/GMT-14"))
            vm.loadDailyQuote()
            // The new day's load gets the lock: the first load was cancelled.
            awaitTrue { gated.dayReads.get() > readsBefore }
        } finally {
            gated.release.complete(Unit)
        }
        val later = vm.dailyQuote.await { it != null }!!
        awaitTrue { app.refreshes.isNotEmpty() }
        // The refresh the new day asks for is sent once, after the new day's pick is stored; the cancelled
        // first load neither stores its pick nor sends one.
        assertEquals(listOf(later.id), app.refreshes.toList())
        assertTrue(runBlocking { dao.getDailySelectionsForDay("${earlierDay}_") }.isEmpty())
    }

    @Test
    fun aNewSeedReachesTheWidget() {
        // No update check against the network.
        prefs.edit().putBoolean("auto_update_enabled", false).commit()
        val app = WidgetRecordingApp()
        val vm = newViewModel(app = app)
        // The text repair needs the sync lock, so the start-up sync finishes first instead of being held.
        vm.syncState.await { it is SyncUiState.Success }
        vm.checkAndSeedDatabase()
        val quote = vm.dailyQuote.await(timeoutMs = 60_000) { it != null }!!
        awaitTrue { app.refreshes.isNotEmpty() }
        assertEquals(listOf(quote.id), app.refreshes.toList())
    }

    @Test
    fun aNewerLoadCancelsAStaleOne() {
        insert(*(1..5).map { bundled("Quote $it") }.toTypedArray())
        val gated = GatedDao(dao)
        holdSyncs()
        val vm = newViewModel(repository = QuoteRepository(gated, 42L))
        val first = vm.dailyQuote.await { it != null }!!

        gated.holdKeysContaining = "_${QuoteLanguage.GERMAN}_"
        try {
            vm.setAppLanguage(QuoteLanguage.GERMAN)
            // The German load now holds the repository lock and does not finish.
            runBlocking { withTimeout(AWAIT_TIMEOUT_MS) { gated.held.await() } }
            val readsBefore = gated.dayReads.get()
            vm.setAppLanguage(QuoteLanguage.ENGLISH)
            // The English load gets the lock while the German one would still hold it: it was cancelled.
            awaitTrue { gated.dayReads.get() > readsBefore }
        } finally {
            gated.release.complete(Unit)
        }
        assertEquals(first.text, vm.dailyQuote.await { it != null }!!.text)
        val picks = runBlocking { dao.getDailySelectionsForDay("${dailyDateKey()}_") }
        assertTrue("the stale German pick never lands", picks.none { "_${QuoteLanguage.GERMAN}_" in it.date })
    }

    @Test
    fun theFactoryKeepsTheStateInTheActivitysSavedState() {
        val factory = QuoteViewModelFactory { savedState ->
            QuoteViewModel(context, repository, savedState, submissions, newManager(repository))
        }
        val before = FakeActivity(null)
        val vm = before.viewModel(factory)
        vm.selectTab("Library")
        vm.setSearchQuery("rumi")
        vm.openNewQuoteScreen()
        val saved = parcelled(before.saveState())
        before.viewModelStore.clear()

        val after = FakeActivity(saved).viewModel(factory)
        assertEquals("Library", after.selectedTab.value)
        assertEquals("rumi", after.searchQuery.value)
        assertTrue(after.showNewQuoteScreen.value)

        // Without a saved-state owner in the extras (a bare ViewModelStore) the state starts fresh.
        val fresh = ViewModelProvider(store, factory)["fresh", QuoteViewModel::class.java]
        assertEquals("Daily", fresh.selectedTab.value)
        assertEquals("", fresh.searchQuery.value)
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
        assertFalse(runBlocking { withTimeout(AWAIT_TIMEOUT_MS) { result.await() } }.complete)
        vm.submissionRefreshRunning.await { !it }
    }

    private fun newViewModel(
        savedState: SavedStateHandle = SavedStateHandle(),
        app: Application = context,
        repository: QuoteRepository = this.repository
    ): QuoteViewModel {
        val manager = newManager(repository)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                QuoteViewModel(app, repository, savedState, submissions, manager) as T
        }
        return ViewModelProvider(store, factory)["vm${created++}", QuoteViewModel::class.java]
    }

    // Syncs wait until the ViewModel is cleared, so no sync reloads the daily quote alongside the test.
    private fun holdSyncs() {
        community.hold = CompletableDeferred()
    }

    private fun newManager(repository: QuoteRepository) = CommunitySyncManager(context, repository, community, submissions)

    private fun restoredAfterProcessDeath(handle: SavedStateHandle): SavedStateHandle =
        SavedStateHandle.createHandle(parcelled(handle.savedStateProvider().saveState()), null)

    // What the activity's saved state goes through when the process dies: a Bundle written to a Parcel.
    private fun parcelled(state: Bundle): Bundle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(state)
            parcel.setDataPosition(0)
            return parcel.readBundle(javaClass.classLoader)!!
        } finally {
            parcel.recycle()
        }
    }

    private fun update(vm: QuoteViewModel, quote: QuoteEntity, text: String, author: String, language: String? = null): Boolean {
        val saved = CompletableDeferred<Boolean>()
        vm.updateUserQuote(quote, text, author, quote.category, quote.tags, language) { saved.complete(it) }
        return runBlocking { withTimeout(AWAIT_TIMEOUT_MS) { saved.await() } }
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
        return runBlocking { withTimeout(AWAIT_TIMEOUT_MS) { outcome.await() } }
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

    // Generous limits: the waits are real Room I/O, which can take seconds on a loaded CI machine;
    // a passing test returns as soon as the condition holds.
    private fun <T> Flow<T>.await(timeoutMs: Long = AWAIT_TIMEOUT_MS, predicate: (T) -> Boolean): T =
        runBlocking { withTimeout(timeoutMs) { first(predicate) } }

    private fun Flow<QuoteUiEvent>.awaitFirst(): QuoteUiEvent = runBlocking { withTimeout(AWAIT_TIMEOUT_MS) { first() } }

    private fun awaitTrue(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
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

    // The application as the ViewModel sees it. Records each widget refresh the ViewModel asks for, with
    // the quote stored as today's latest pick at that moment.
    private inner class WidgetRecordingApp : Application() {
        val refreshes = CopyOnWriteArrayList<Int?>()

        init {
            attachBaseContext(context)
        }

        override fun sendBroadcast(intent: Intent) {
            if (intent.action == AppLinks.ACTION_UPDATE_WIDGET) {
                refreshes += runBlocking { dao.getDailySelectionsForDay("${dailyDateKey()}_") }.firstOrNull()?.quoteId
            } else {
                super.sendBroadcast(intent)
            }
        }
    }

    // What an activity gives the factory: a ViewModelStore and saved state with SavedStateHandle support.
    private inner class FakeActivity(restored: Bundle?) : SavedStateRegistryOwner, ViewModelStoreOwner {
        private val lifecycleRegistry = LifecycleRegistry.createUnsafe(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
        override val viewModelStore = ViewModelStore()

        init {
            activityStores += viewModelStore
            // The order ComponentActivity uses: attach and enable in the constructor, restore in onCreate.
            savedStateController.performAttach()
            enableSavedStateHandles()
            savedStateController.performRestore(restored)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }

        fun viewModel(factory: ViewModelProvider.Factory): QuoteViewModel {
            val extras = MutableCreationExtras().apply {
                this[SAVED_STATE_REGISTRY_OWNER_KEY] = this@FakeActivity
                this[VIEW_MODEL_STORE_OWNER_KEY] = this@FakeActivity
            }
            return ViewModelProvider(viewModelStore, factory, extras)[QuoteViewModel::class.java]
        }

        fun saveState(): Bundle = Bundle().also(savedStateController::performSave)
    }

    // Holds back storing a daily pick under a key containing [holdKeysContaining], with the repository's
    // lock held, until [release] completes. Counts reads of a day's picks, which happen under that lock.
    private class GatedDao(private val dao: QuoteDao) : QuoteDao by dao {
        @Volatile var holdKeysContaining: String? = null
        val held = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val dayReads = AtomicInteger()

        override suspend fun storeDailySelection(key: String, quoteId: Int) {
            if (holdKeysContaining?.let { it in key } == true) {
                held.complete(Unit)
                release.await()
            }
            dao.storeDailySelection(key, quoteId)
        }

        override suspend fun getDailySelectionsForDay(keyPrefix: String): List<DailySelectionEntity> {
            dayReads.incrementAndGet()
            return dao.getDailySelectionsForDay(keyPrefix)
        }
    }

    private class FakeCommunityClient : CommunityQuoteClient() {
        @Volatile var online = true
        @Volatile var calls = 0
        // While set, a sync waits here and holds the sync lock until the ViewModel is cleared.
        @Volatile var hold: CompletableDeferred<Unit>? = null

        override suspend fun updates(after: Long): Result<CommunityQuotePage> {
            calls++
            hold?.await()
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
        const val AWAIT_TIMEOUT_MS = 15_000L
    }
}
