package app.brokoli5191.quote.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DailyQuoteTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: QuoteDao
    private lateinit var repository: QuoteRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.quoteDao()
        repository = QuoteRepository(dao, 42L)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertBundled(count: Int, translated: Boolean = true) = dao.insertQuotes(
        (1..count).map {
            QuoteEntity(text = "Quote $it", textDe = if (translated) "Zitat $it" else null, author = "Author $it", category = "Life")
        }
    )

    private fun community(serverId: String, text: String, language: String = QuoteLanguage.ENGLISH) = QuoteEntity(
        text = text,
        author = "Reader",
        category = "Life",
        origin = QuoteOrigin.COMMUNITY,
        serverId = serverId,
        language = language
    )

    @Test
    fun todaysQuote_staysWhenLanguageAnonymousOrSourceChange() = runBlocking {
        insertBundled(10)
        val date = "2026-11-15"
        val english = repository.getDailyQuote(date, QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false)!!

        val german = repository.getDailyQuote(date, QuoteSourceMode.ALL, QuoteLanguage.GERMAN, false)!!
        assertEquals(english.id, german.id)
        assertTrue(german.text.startsWith("Zitat"))
        assertEquals(english.id, repository.getDailyQuote(date, QuoteSourceMode.ALL, QuoteLanguage.GERMAN, true)!!.id)
        assertEquals(english.id, repository.getDailyQuote(date, QuoteSourceMode.CURATED, QuoteLanguage.ENGLISH, false)!!.id)
    }

    @Test
    fun todaysQuote_changesOnlyWhenItNoLongerFits() = runBlocking {
        insertBundled(3)
        dao.insertQuote(community("c-1", "Shared"))
        val date = "2026-11-15"
        val curated = repository.getDailyQuote(date, QuoteSourceMode.CURATED, QuoteLanguage.ENGLISH, false)!!

        val communityOnly = repository.getDailyQuote(date, QuoteSourceMode.COMMUNITY, QuoteLanguage.ENGLISH, false)!!
        assertEquals("c-1", communityOnly.serverId)
        // Back in Curated mode the bundled quote of the morning fits again.
        assertEquals(curated.id, repository.getDailyQuote(date, QuoteSourceMode.CURATED, QuoteLanguage.ENGLISH, false)!!.id)
    }

    @Test
    fun switchingBack_keepsTheEarlierPickInTheNoRepeatHistory() = runBlocking {
        insertBundled(3)
        val date = "2026-11-15"
        val morning = repository.getDailyQuote(date, QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false)!!
        dao.insertQuote(community("c-1", "Shared"))
        val communityOnly = repository.getDailyQuote(date, QuoteSourceMode.COMMUNITY, QuoteLanguage.ENGLISH, false)!!

        // Back in All mode the community quote stays, as it fits; the morning quote keeps its place
        // in the history although its key now holds the community quote.
        assertEquals(communityOnly.id, repository.getDailyQuote(date, QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false)!!.id)
        assertEquals(listOf(morning.id, communityOnly.id, communityOnly.id), dao.getDailySelectionIdsOldestFirst())
        val tomorrow = repository.getDailyQuote("2026-11-16", QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false)!!
        assertFalse(tomorrow.id in setOf(morning.id, communityOnly.id))
    }

    @Test
    fun concurrentCallers_storeOneQuoteForTheDay() = runBlocking {
        insertBundled(40)
        dao.insertDailySelection(DailySelectionEntity("2026-11-14_en_0_all", 3))
        val ids = (1..8).map { index ->
            async(Dispatchers.Default) {
                val language = if (index % 2 == 0) QuoteLanguage.GERMAN else QuoteLanguage.ENGLISH
                repository.getDailyQuote("2026-11-15", QuoteSourceMode.ALL, language, false)!!.id
            }
        }.awaitAll()
        assertEquals(1, ids.toSet().size)
    }

    @Test
    fun noRepeat_showsEveryQuoteOnceThenStartsANewCycle() = runBlocking {
        insertBundled(5)
        val picks = (1..20).map { day ->
            repository.getDailyQuote("2026-01-%02d".format(day), QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false)!!.id
        }
        picks.chunked(5).forEach { assertEquals(5, it.toSet().size) }
        assertTrue(picks.zipWithNext().none { (yesterday, today) -> yesterday == today })
    }

    @Test
    fun anotherQuote_skipsShownQuotesAndCanBeUndone() = runBlocking {
        insertBundled(6)
        val yesterday = repository.getDailyQuote("2026-11-14", QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false)!!
        val today = repository.getDailyQuote("2026-11-15", QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false)!!

        val next = repository.cycleDailyQuote("2026-11-15", QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false)!!
        assertTrue(next.id != today.id && next.id != yesterday.id)
        // The cycled quote is today's quote everywhere, also after a language switch.
        assertEquals(next.id, repository.getDailyQuote("2026-11-15", QuoteSourceMode.ALL, QuoteLanguage.GERMAN, false)!!.id)

        val restored = repository.restoreDailySelection("2026-11-15", QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false, today.id)
        assertEquals(today.id, restored!!.id)
        assertEquals(today.id, repository.getDailyQuote("2026-11-15", QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false)!!.id)
        assertNull(repository.restoreDailySelection("2026-11-15", QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false, 9999))

        // The replaced quote stays in the no-repeat history.
        val tomorrow = repository.getDailyQuote("2026-11-16", QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false)!!
        assertFalse(tomorrow.id in setOf(yesterday.id, today.id, next.id))
    }

    @Test
    fun anotherQuote_withASingleCandidateKeepsIt() = runBlocking {
        insertBundled(1)
        val today = repository.getDailyQuote("2026-11-15", QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false)!!
        assertEquals(today.id, repository.cycleDailyQuote("2026-11-15", QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false)!!.id)
    }

    @Test
    fun history_listsTheQuoteEachDayEndedWith() = runBlocking {
        insertBundled(10)
        val first = repository.getDailyQuote("2026-11-13", QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false)!!
        val second = repository.getDailyQuote("2026-11-14", QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false)!!
        repository.getDailyQuote("2026-11-15", QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false)
        val third = repository.cycleDailyQuote("2026-11-15", QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false)!!

        val history = repository.dailyHistory(QuoteSourceMode.ALL, QuoteLanguage.GERMAN, false)
        assertEquals(listOf("2026-11-15", "2026-11-14", "2026-11-13"), history.map { it.date })
        assertEquals(listOf(third.id, second.id, first.id), history.map { it.quote.id })
        assertTrue(history.all { it.quote.text.startsWith("Zitat") })
        assertEquals(2, repository.dailyHistory(QuoteSourceMode.ALL, QuoteLanguage.ENGLISH, false, limit = 2).size)
        assertEquals(emptyList<DailyHistoryEntry>(), repository.dailyHistory(QuoteSourceMode.COMMUNITY, QuoteLanguage.ENGLISH, false))
    }

    @Test
    fun germanCommunityMode_fallsBackToUntranslatedCommunityQuotes() = runBlocking {
        insertBundled(2)
        dao.insertQuote(community("c-en", "Only in English"))

        val fallback = repository.getDailyQuote("2026-11-15", QuoteSourceMode.COMMUNITY, QuoteLanguage.GERMAN, false)
        assertEquals("Only in English", fallback!!.text)
        // Other modes keep requiring a German version.
        assertEquals(QuoteOrigin.BUNDLED, repository.getDailyQuote("2026-11-15", QuoteSourceMode.ALL, QuoteLanguage.GERMAN, false)!!.origin)

        // A community quote in German ends the fallback, also for today, and the untranslated pick
        // stays in the no-repeat history.
        dao.insertQuote(community("c-de", "Nur Deutsch", QuoteLanguage.GERMAN))
        assertEquals("Nur Deutsch", repository.getDailyQuote("2026-11-15", QuoteSourceMode.COMMUNITY, QuoteLanguage.GERMAN, false)!!.text)
        assertTrue(fallback.id in dao.getDailySelectionIdsOldestFirst())
    }

    @Test
    fun communityMode_withoutCommunityQuotesIsEmpty() = runBlocking {
        insertBundled(2)
        assertNull(repository.getDailyQuote("2026-11-15", QuoteSourceMode.COMMUNITY, QuoteLanguage.GERMAN, false))
    }

    @Test
    fun favorites_newestSaveFirstAndLegacyDatesLast() = runBlocking {
        insertBundled(4)
        val (a, b, c, d) = dao.getAllQuotesSync().sortedBy { it.id }
        dao.updateFavorite(a.id, true, "05 Jun 2026")
        dao.updateFavorite(b.id, true, "1000")
        dao.updateFavorite(c.id, true, "2000")
        assertEquals(0, dao.updateFavorite(9999, true, "1"))
        assertFalse(repository.toggleFavorite(9999, true))
        assertTrue(repository.toggleFavorite(d.id, true))

        assertEquals(listOf(d.id, c.id, b.id, a.id), repository.favorites.first().map { it.id })
    }
}
