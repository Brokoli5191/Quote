package app.brokoli5191.quote.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
class CommunitySyncLinkingTest {
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

    private fun community(serverId: String, submissionId: String? = null, text: String = "Shared $serverId") = QuoteEntity(
        text = text,
        author = "Reader",
        category = "Life",
        origin = QuoteOrigin.COMMUNITY,
        serverId = serverId,
        serverRevision = 1,
        submissionId = submissionId
    )

    private suspend fun insertPending(submissionId: String): Int = dao.insertQuote(
        QuoteEntity(
            text = "My own thought",
            author = "Me",
            category = "Life",
            isUserAdded = true,
            origin = QuoteOrigin.PERSONAL,
            submissionStatus = QuoteSubmissionStatus.PENDING,
            submissionId = submissionId,
            submittedAt = System.currentTimeMillis()
        )
    ).toInt()

    @Test
    fun publishedCopy_linksPersonalRowAndMarksItApproved() = runBlocking {
        val personalId = insertPending("sub-1")

        // A moderator edited the text, so only the submission id ties the two together.
        val result = repository.applyCommunityUpdates(
            listOf(community("c-1", submissionId = "sub-1", text = "My own thought, edited")),
            emptyList()
        )

        assertTrue(result.changed)
        val personal = dao.getQuoteById(personalId)!!
        assertEquals(QuoteSubmissionStatus.APPROVED, personal.submissionStatus)
        assertEquals("c-1", personal.linkedServerId)
        assertEquals("sub-1", dao.getQuoteByServerId("c-1")!!.submissionId)
    }

    @Test
    fun unpublishedCopy_marksPersonalRowRemovedAndRemembersFavorite() = runBlocking {
        val personalId = insertPending("sub-1")
        repository.applyCommunityUpdates(listOf(community("c-1", submissionId = "sub-1")), emptyList())
        val copy = dao.getQuoteByServerId("c-1")!!
        dao.updateFavorite(copy.id, true, "123")

        val result = repository.applyCommunityUpdates(emptyList(), listOf("c-1"))

        assertNull(dao.getQuoteByServerId("c-1"))
        assertEquals(mapOf("c-1" to "123"), result.removedFavorites)
        val personal = dao.getQuoteById(personalId)!!
        assertEquals(QuoteSubmissionStatus.REMOVED, personal.submissionStatus)
        assertNull(personal.linkedServerId)

        // Republished: the favorite comes back.
        val republished = repository.applyCommunityUpdates(
            listOf(community("c-1", submissionId = "sub-1")),
            emptyList(),
            restoreFavorites = result.removedFavorites
        )
        assertEquals(setOf("c-1"), republished.restoredFavorites)
        val restored = dao.getQuoteByServerId("c-1")!!
        assertTrue(restored.isFavorite)
        assertEquals("123", restored.savedDate)
        assertEquals(QuoteSubmissionStatus.APPROVED, dao.getQuoteById(personalId)!!.submissionStatus)
    }

    @Test
    fun updatedCommunityQuote_keepsFavoriteAndRowId() = runBlocking {
        repository.applyCommunityUpdates(listOf(community("c-2")), emptyList())
        val first = dao.getQuoteByServerId("c-2")!!
        dao.updateFavorite(first.id, true, "5")

        repository.applyCommunityUpdates(listOf(community("c-2", text = "Edited")), emptyList())

        val updated = dao.getQuoteByServerId("c-2")!!
        assertEquals(first.id, updated.id)
        assertTrue(updated.isFavorite)
        assertEquals("Edited", updated.text)
    }

    @Test
    fun submissionsToRefresh_includeRecentRejectedButNotOldOnes() = runBlocking {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        fun own(status: String, submittedAt: Long, linked: String? = null) = QuoteEntity(
            text = "Own $status $submittedAt",
            author = "Me",
            category = "Life",
            isUserAdded = true,
            origin = QuoteOrigin.PERSONAL,
            submissionStatus = status,
            submissionId = "id-$status-$submittedAt",
            submittedAt = submittedAt,
            linkedServerId = linked
        )
        dao.insertQuotes(
            listOf(
                own(QuoteSubmissionStatus.PENDING, now - 90 * day),
                own(QuoteSubmissionStatus.REJECTED, now - 2 * day),
                own(QuoteSubmissionStatus.REJECTED, now - 60 * day),
                own(QuoteSubmissionStatus.REMOVED, now - 2 * day),
                own(QuoteSubmissionStatus.APPROVED, now - 2 * day, linked = "c-9"),
                own(QuoteSubmissionStatus.APPROVED, now - 2 * day)
            )
        )

        val statuses = repository.getSubmissionsToRefresh(now).map { it.submissionStatus to (it.linkedServerId != null) }

        assertEquals(4, statuses.size)
        assertTrue(QuoteSubmissionStatus.PENDING to false in statuses)
        assertTrue(QuoteSubmissionStatus.REMOVED to false in statuses)
        assertTrue(QuoteSubmissionStatus.APPROVED to false in statuses)
        assertFalse(QuoteSubmissionStatus.APPROVED to true in statuses)
    }

    @Test
    fun applySubmissionStatus_storesNoteAndLinkOnlyWhenApproved() = runBlocking {
        val id = insertPending("sub-3")
        repository.applySubmissionStatus(id, SubmissionStatusInfo(QuoteSubmissionStatus.REJECTED, "Needs a source", "c-3"))
        dao.getQuoteById(id)!!.let {
            assertEquals(QuoteSubmissionStatus.REJECTED, it.submissionStatus)
            assertEquals("Needs a source", it.reviewNote)
            assertNull(it.linkedServerId)
        }
        repository.applySubmissionStatus(id, SubmissionStatusInfo(QuoteSubmissionStatus.APPROVED, null, "c-3"))
        assertEquals("c-3", dao.getQuoteById(id)!!.linkedServerId)
    }

    @Test
    fun dailyQuote_usesSqlFilterForLanguageSourceAndAnonymous() = runBlocking {
        dao.insertQuotes(
            listOf(
                QuoteEntity(text = "English only", author = "A", category = "Life"),
                QuoteEntity(text = "Translated", textDe = "Übersetzt", author = "B", category = "Life"),
                QuoteEntity(text = "Anon", textDe = "Anonym", author = "", category = "Reflections", isAnonymous = true),
                community("c-de").copy(text = "Nur Deutsch", language = QuoteLanguage.GERMAN),
                QuoteEntity(
                    text = "Personal", author = "Me", category = "Life",
                    isUserAdded = true, origin = QuoteOrigin.PERSONAL, textDe = "Eigenes"
                )
            )
        )
        val germanPool = (1..30).mapNotNull {
            repository.cycleDailyQuote("2026-10-$it", QuoteSourceMode.ALL, QuoteLanguage.GERMAN, false)?.text
        }.toSet()
        assertEquals(setOf("Übersetzt", "Nur Deutsch"), germanPool)

        val curatedEnglish = (1..30).mapNotNull {
            repository.cycleDailyQuote("2026-11-$it", QuoteSourceMode.CURATED, QuoteLanguage.ENGLISH, true)?.text
        }.toSet()
        assertEquals(setOf("English only", "Translated", "Anon"), curatedEnglish)

        val daily = repository.getDailyQuote("2026-12-01", QuoteSourceMode.COMMUNITY, QuoteLanguage.GERMAN, false)
        assertEquals("Nur Deutsch", daily!!.text)
    }
}
