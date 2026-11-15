package app.brokoli5191.quote.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    private suspend fun insertPending(submissionId: String): Int =
        insertOwn(submissionId, QuoteSubmissionStatus.PENDING, text = "My own thought")

    private suspend fun insertOwn(
        submissionId: String,
        status: String,
        text: String = "Own $submissionId",
        reviewNote: String? = null,
        linked: String? = null,
        submittedAt: Long = System.currentTimeMillis()
    ): Int = dao.insertQuote(
        QuoteEntity(
            text = text,
            author = "Me",
            category = "Life",
            isUserAdded = true,
            origin = QuoteOrigin.PERSONAL,
            submissionStatus = status,
            submissionId = submissionId,
            submittedAt = submittedAt,
            reviewNote = reviewNote,
            linkedServerId = linked
        )
    ).toInt()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)

    private fun installationIds(current: String, vararg extra: String) {
        prefs.edit()
            .putString(CommunitySyncManager.KEY_INSTALLATION_ID, current)
            .putStringSet(CommunitySyncManager.KEY_EXTRA_INSTALLATION_IDS, extra.toSet())
            .apply()
    }

    // Skips the one-time v2 passes, so tests see the regular feed cursor and status window.
    private fun afterV2Upgrade() {
        prefs.edit()
            .putBoolean("community_feed_v2_resynced", true)
            .putBoolean("submission_status_v2_refreshed", true)
            .apply()
    }

    private fun page(
        revision: Long,
        quotes: List<QuoteEntity> = emptyList(),
        deleted: List<String> = emptyList(),
        hasMore: Boolean = false
    ) = Result.success(CommunityQuotePage(revision, hasMore, quotes, deleted))

    private class FakeFeed : CommunityQuoteClient() {
        val pages = ArrayDeque<Result<CommunityQuotePage>>()
        val requested = mutableListOf<Long>()

        override suspend fun updates(after: Long): Result<CommunityQuotePage> {
            requested += after
            return pages.removeFirstOrNull() ?: Result.success(CommunityQuotePage(after, false, emptyList(), emptyList()))
        }
    }

    private class FakeSubmissions : QuoteSubmissionClient() {
        /** (submission id, installation id) to the answer; anything else answers 404. */
        val statuses = HashMap<Pair<String, String>, Result<SubmissionStatusInfo>>()
        val statusRequests = mutableListOf<Pair<String, String>>()
        var beforeStatusAnswer: suspend (String) -> Unit = {}
        val withdrawResults = ArrayDeque<WithdrawResult>()
        val withdrawRequests = mutableListOf<String>()

        override suspend fun status(submissionId: String, installationId: String): Result<SubmissionStatusInfo> {
            statusRequests += submissionId to installationId
            beforeStatusAnswer(submissionId)
            return statuses[submissionId to installationId]
                ?: Result.success(SubmissionStatusInfo(QuoteSubmissionStatus.REMOVED, notFound = true))
        }

        override suspend fun withdraw(submissionId: String, installationId: String): WithdrawResult {
            withdrawRequests += submissionId
            return withdrawResults.removeFirstOrNull() ?: WithdrawResult.Success
        }
    }

    private fun manager(feed: FakeFeed = FakeFeed(), submissions: FakeSubmissions = FakeSubmissions()) =
        CommunitySyncManager(context, repository, feed, submissions)

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

        // The one-time pass after the upgrade ignores the window.
        assertEquals(5, repository.getSubmissionsToRefresh(now, since = 0L).size)
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

    @Test
    fun statusRefresh_404MeansWithdrawnForPendingAndUnpublishedForApproved() = runBlocking {
        installationIds("11111111-1111-4111-8111-111111111111")
        afterV2Upgrade()
        val pending = insertPending("sub-1")
        val approved = insertOwn("sub-2", QuoteSubmissionStatus.APPROVED)
        val rejected = insertOwn("sub-3", QuoteSubmissionStatus.REJECTED, reviewNote = "Needs a source")

        val result = manager().refreshSubmissionStatuses()

        assertTrue(result.complete)
        dao.getQuoteById(pending)!!.let {
            assertEquals(QuoteSubmissionStatus.NOT_SUBMITTED, it.submissionStatus)
            // Kept for the feed (statusRefresh_404ForAPendingQuoteStillLetsTheFeedLinkIt).
            assertEquals("sub-1", it.submissionId)
        }
        assertEquals(QuoteSubmissionStatus.REMOVED, dao.getQuoteById(approved)!!.submissionStatus)
        dao.getQuoteById(rejected)!!.let {
            assertEquals(QuoteSubmissionStatus.REJECTED, it.submissionStatus)
            assertEquals("Needs a source", it.reviewNote)
        }
        // A withdrawal is not a review decision.
        assertEquals(listOf(approved to QuoteSubmissionStatus.REMOVED), result.updates.map { it.quoteId to it.newStatus })
    }

    @Test
    fun statusRefresh_404ForAPendingQuoteStillLetsTheFeedLinkIt() = runBlocking {
        // Sent with an installation id this device does not know: every status request answers 404,
        // although the submission is still in review.
        installationIds("11111111-1111-4111-8111-111111111111")
        afterV2Upgrade()
        val id = insertPending("sub-1")
        val feed = FakeFeed()
        val submissions = FakeSubmissions()
        val manager = manager(feed, submissions)

        val first = manager.syncWithResult()

        dao.getQuoteById(id)!!.let {
            assertEquals(QuoteSubmissionStatus.NOT_SUBMITTED, it.submissionStatus)
            assertEquals("sub-1", it.submissionId)
        }
        assertTrue(first.submissions.updates.isEmpty())

        // A moderator approves it with an edit.
        feed.pages += page(1, quotes = listOf(community("c-1", "sub-1", text = "My own thought, edited")))
        val second = manager.syncWithResult()

        dao.getQuoteById(id)!!.let {
            assertEquals(QuoteSubmissionStatus.APPROVED, it.submissionStatus)
            assertEquals("c-1", it.linkedServerId)
        }
        assertEquals(
            listOf(
                SubmissionUpdate(
                    id,
                    "My own thought",
                    QuoteSubmissionStatus.NOT_SUBMITTED,
                    QuoteSubmissionStatus.APPROVED,
                    null
                )
            ),
            second.submissions.updates
        )
        // The note recheck answers 404 too and leaves the link to the feed; nothing is polled afterwards.
        assertTrue(manager.syncWithResult().submissions.updates.isEmpty())
        assertEquals(QuoteSubmissionStatus.APPROVED, dao.getQuoteById(id)!!.submissionStatus)
        assertEquals(listOf("sub-1", "sub-1"), submissions.statusRequests.map { it.first })
    }

    @Test
    fun statusRefresh_realAnswerIsNotReplacedByAnotherInstallationIds404() = runBlocking {
        val current = "11111111-1111-4111-8111-111111111111"
        val restored = "22222222-2222-4222-8222-222222222222"
        installationIds(current, restored)
        afterV2Upgrade()
        val rejected = insertOwn("sub-1", QuoteSubmissionStatus.REJECTED, reviewNote = "Typo")
        val other = insertOwn("sub-2", QuoteSubmissionStatus.PENDING)
        val submissions = FakeSubmissions().apply {
            statuses["sub-1" to current] =
                Result.success(SubmissionStatusInfo(QuoteSubmissionStatus.REMOVED, reviewNote = "Taken down"))
            statuses["sub-2" to restored] =
                Result.success(SubmissionStatusInfo(QuoteSubmissionStatus.APPROVED, communityQuoteId = "c-2"))
        }

        manager(submissions = submissions).refreshSubmissionStatuses()

        dao.getQuoteById(rejected)!!.let {
            assertEquals(QuoteSubmissionStatus.REMOVED, it.submissionStatus)
            assertEquals("Taken down", it.reviewNote)
        }
        assertEquals(listOf("sub-1" to current), submissions.statusRequests.filter { it.first == "sub-1" })
        dao.getQuoteById(other)!!.let {
            assertEquals(QuoteSubmissionStatus.APPROVED, it.submissionStatus)
            assertEquals("c-2", it.linkedServerId)
        }
    }

    @Test
    fun statusRefresh_neverWritesOverAWithdrawalMadeDuringTheRequest() = runBlocking {
        installationIds("11111111-1111-4111-8111-111111111111")
        afterV2Upgrade()
        val id = insertPending("sub-1")
        val submissions = FakeSubmissions().apply {
            statuses["sub-1" to "11111111-1111-4111-8111-111111111111"] =
                Result.success(SubmissionStatusInfo(QuoteSubmissionStatus.REJECTED, reviewNote = "Late"))
            // A withdrawal that does not take the sync lock lands while the status request runs.
            beforeStatusAnswer = { repository.clearSubmission(id) }
        }

        val result = manager(submissions = submissions).refreshSubmissionStatuses()

        dao.getQuoteById(id)!!.let {
            assertEquals(QuoteSubmissionStatus.NOT_SUBMITTED, it.submissionStatus)
            assertNull(it.reviewNote)
        }
        assertFalse(result.changed)
        assertTrue(result.updates.isEmpty())
    }

    @Test
    fun statusRefresh_v2AnswerReplacesAnOutdatedNoteButALegacyOneKeepsIt() = runBlocking {
        val installation = "11111111-1111-4111-8111-111111111111"
        installationIds(installation)
        afterV2Upgrade()
        val v2 = insertOwn("sub-1", QuoteSubmissionStatus.REJECTED, reviewNote = "Needs a source")
        val legacy = insertOwn("sub-2", QuoteSubmissionStatus.REJECTED, reviewNote = "Typo")
        val submissions = FakeSubmissions().apply {
            statuses["sub-1" to installation] =
                Result.success(SubmissionStatusInfo(QuoteSubmissionStatus.APPROVED, communityQuoteId = "c-1"))
            statuses["sub-2" to installation] =
                Result.success(SubmissionStatusInfo(QuoteSubmissionStatus.APPROVED, communityQuoteId = "c-2", legacy = true))
        }

        val result = manager(submissions = submissions).refreshSubmissionStatuses()

        assertNull(dao.getQuoteById(v2)!!.reviewNote)
        assertEquals("Typo", dao.getQuoteById(legacy)!!.reviewNote)
        assertEquals(
            listOf(v2 to QuoteSubmissionStatus.APPROVED, legacy to QuoteSubmissionStatus.APPROVED),
            result.updates.sortedBy { it.quoteId }.map { it.quoteId to it.newStatus }
        )
    }

    @Test
    fun withdrawSubmission_clearsTheQuoteOrRefreshesWhenAModeratorWasFaster() = runBlocking {
        val installation = "11111111-1111-4111-8111-111111111111"
        installationIds(installation)
        afterV2Upgrade()
        val withdrawn = insertPending("sub-1")
        val reviewed = insertOwn("sub-2", QuoteSubmissionStatus.PENDING)
        val submissions = FakeSubmissions().apply {
            statuses["sub-2" to installation] =
                Result.success(SubmissionStatusInfo(QuoteSubmissionStatus.APPROVED, communityQuoteId = "c-2"))
        }
        val manager = manager(submissions = submissions)

        assertNull(manager.withdrawSubmission(withdrawn))
        assertEquals(QuoteSubmissionStatus.NOT_SUBMITTED, dao.getQuoteById(withdrawn)!!.submissionStatus)
        // Withdrawing again is a no-op success.
        assertNull(manager.withdrawSubmission(withdrawn))

        submissions.withdrawResults += WithdrawResult.Error(SubmissionError.ALREADY_REVIEWED)
        assertEquals(SubmissionError.ALREADY_REVIEWED, manager.withdrawSubmission(reviewed))
        dao.getQuoteById(reviewed)!!.let {
            assertEquals(QuoteSubmissionStatus.APPROVED, it.submissionStatus)
            assertEquals("c-2", it.linkedServerId)
        }
        assertEquals(listOf("sub-1", "sub-2"), submissions.withdrawRequests)
    }

    @Test
    fun deletedPendingQuote_failedWithdrawalIsRetriedBySync() = runBlocking {
        installationIds("11111111-1111-4111-8111-111111111111")
        afterV2Upgrade()
        val id = insertPending("sub-1")
        dao.deleteQuoteById(id)
        val submissions = FakeSubmissions().apply { withdrawResults += WithdrawResult.Error(SubmissionError.NETWORK) }
        val manager = manager(submissions = submissions)

        assertEquals(
            DeletedSubmissionOutcome.RetryQueued(SubmissionError.NETWORK),
            manager.withdrawDeletedSubmission("sub-1")
        )
        manager.sync()
        manager.sync()

        // Retried once by the first sync, which succeeded; the second had nothing left to do.
        assertEquals(listOf("sub-1", "sub-1"), submissions.withdrawRequests)
    }

    @Test
    fun deletedPendingQuote_finalRefusalIsReportedAndNotRetried() = runBlocking {
        installationIds("11111111-1111-4111-8111-111111111111")
        afterV2Upgrade()
        val submissions = FakeSubmissions().apply {
            withdrawResults += WithdrawResult.Error(SubmissionError.ALREADY_REVIEWED)
        }
        val manager = manager(submissions = submissions)

        assertEquals(
            DeletedSubmissionOutcome.Failed(SubmissionError.ALREADY_REVIEWED),
            manager.withdrawDeletedSubmission("sub-1")
        )
        manager.sync()

        assertEquals(listOf("sub-1"), submissions.withdrawRequests)
        // The caller got this refusal directly; it is not reported a second time.
        assertTrue(manager.takeFailedWithdrawals().isEmpty())
    }

    @Test
    fun deletedPendingQuote_refusalOfARetryIsReportedOnce() = runBlocking {
        installationIds("11111111-1111-4111-8111-111111111111")
        afterV2Upgrade()
        val submissions = FakeSubmissions().apply {
            withdrawResults += WithdrawResult.Error(SubmissionError.NETWORK)
            withdrawResults += WithdrawResult.Error(SubmissionError.ALREADY_REVIEWED)
        }
        val manager = manager(submissions = submissions)
        assertEquals(
            DeletedSubmissionOutcome.RetryQueued(SubmissionError.NETWORK),
            manager.withdrawDeletedSubmission("sub-1")
        )
        assertTrue(manager.takeFailedWithdrawals().isEmpty())

        // A moderator reviewed it before the retry (in the app or in the background) got through.
        manager.sync()

        assertEquals(
            listOf(FailedWithdrawal("sub-1", SubmissionError.ALREADY_REVIEWED)),
            manager.takeFailedWithdrawals()
        )
        assertTrue(manager.takeFailedWithdrawals().isEmpty())
        manager.sync()
        assertEquals(listOf("sub-1", "sub-1"), submissions.withdrawRequests)
    }

    @Test
    fun deletedPendingQuote_restoredByUndoKeepsItsSubmission() = runBlocking {
        installationIds("11111111-1111-4111-8111-111111111111")
        afterV2Upgrade()
        val id = insertPending("sub-1")
        val raw = dao.getQuoteById(id)!!
        dao.deleteQuoteById(id)
        val submissions = FakeSubmissions().apply {
            withdrawResults += WithdrawResult.Error(SubmissionError.SERVER)
            statuses["sub-1" to "11111111-1111-4111-8111-111111111111"] =
                Result.success(SubmissionStatusInfo(QuoteSubmissionStatus.PENDING))
        }
        val manager = manager(submissions = submissions)
        assertEquals(
            DeletedSubmissionOutcome.RetryQueued(SubmissionError.SERVER),
            manager.withdrawDeletedSubmission("sub-1")
        )

        dao.insertQuote(raw)
        manager.sync()

        assertEquals(listOf("sub-1"), submissions.withdrawRequests)
        assertEquals(QuoteSubmissionStatus.PENDING, dao.getQuoteById(id)!!.submissionStatus)
        assertEquals(DeletedSubmissionOutcome.Kept, manager.withdrawDeletedSubmission("sub-1"))
    }

    @Test
    fun feedSync_keepsFavoriteTombstonesWhenALaterPageFails() = runBlocking {
        afterV2Upgrade()
        prefs.edit().putLong("community_quote_revision", 3L).apply()
        dao.insertQuotes(listOf(community("c-1").copy(isFavorite = true, savedDate = "77"), community("c-2")))
        val feed = FakeFeed().apply {
            pages += page(5, deleted = listOf("c-1"), hasMore = true)
            pages += Result.failure(java.io.IOException("timeout"))
        }
        val manager = manager(feed = feed)
        assertFalse(manager.sync())
        assertNull(dao.getQuoteByServerId("c-1"))

        // Republished later: the favorite comes back although the page that deleted it is not sent again.
        feed.pages += page(6, quotes = listOf(community("c-1")))
        assertTrue(manager.sync())

        assertEquals(listOf(3L, 5L, 5L), feed.requested)
        val restored = dao.getQuoteByServerId("c-1")!!
        assertTrue(restored.isFavorite)
        assertEquals("77", restored.savedDate)
    }

    @Test
    fun sync_reportsAnApprovalLinkedByTheFeedWithTheModeratorsNote() = runBlocking {
        val installation = "11111111-1111-4111-8111-111111111111"
        installationIds(installation)
        afterV2Upgrade()
        val id = insertPending("sub-1")
        val feed = FakeFeed().apply { pages += page(1, quotes = listOf(community("c-1", submissionId = "sub-1"))) }
        val submissions = FakeSubmissions().apply {
            statuses["sub-1" to installation] = Result.success(
                SubmissionStatusInfo(QuoteSubmissionStatus.APPROVED, "Fixed the author's name", "c-1")
            )
        }
        val manager = manager(feed, submissions)

        val result = manager.syncWithResult()

        assertTrue(result.feedSynced)
        assertEquals(
            listOf(
                SubmissionUpdate(
                    id,
                    "My own thought",
                    QuoteSubmissionStatus.PENDING,
                    QuoteSubmissionStatus.APPROVED,
                    "Fixed the author's name"
                )
            ),
            result.submissions.updates
        )
        dao.getQuoteById(id)!!.let {
            assertEquals("c-1", it.linkedServerId)
            assertEquals("Fixed the author's name", it.reviewNote)
        }
        // The note is fetched once; an approved, linked quote then leaves the regular refresh.
        val second = manager.syncWithResult()
        assertTrue(second.submissions.updates.isEmpty())
        assertEquals(1, submissions.statusRequests.size)
    }

    @Test
    fun fullStatusRecheck_pollsApprovedLinkedAndOldSubmissionsOnce() = runBlocking {
        val installation = "11111111-1111-4111-8111-111111111111"
        installationIds(installation)
        afterV2Upgrade()
        val old = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        val id = insertOwn("sub-1", QuoteSubmissionStatus.APPROVED, linked = "c-1", submittedAt = old)
        val submissions = FakeSubmissions().apply {
            statuses["sub-1" to installation] = Result.success(SubmissionStatusInfo(QuoteSubmissionStatus.REMOVED))
        }
        val manager = manager(submissions = submissions)

        manager.refreshSubmissionStatuses()
        assertTrue(submissions.statusRequests.isEmpty())

        CommunitySyncManager.requestFullStatusRecheck(context)
        val result = manager.refreshSubmissionStatuses()
        manager.refreshSubmissionStatuses()

        assertEquals(1, submissions.statusRequests.size)
        dao.getQuoteById(id)!!.let {
            assertEquals(QuoteSubmissionStatus.REMOVED, it.submissionStatus)
            assertNull(it.linkedServerId)
        }
        assertEquals(listOf(QuoteSubmissionStatus.REMOVED), result.updates.map { it.newStatus })
    }

    @Test
    fun fullStatusRecheck_requestMadeWhileThePassReadsTheQuotesIsKept() = runBlocking {
        val installation = "11111111-1111-4111-8111-111111111111"
        installationIds(installation)
        afterV2Upgrade()
        insertPending("sub-1")
        CommunitySyncManager.requestFullStatusRecheck(context)
        // A backup restore lands right after the pass has read the submitted quotes (the second read; the
        // first is the snapshot for the review decisions).
        var reads = 0
        val hookedDao = object : QuoteDao by dao {
            override suspend fun getSubmittedQuotes(): List<QuoteEntity> = dao.getSubmittedQuotes().also {
                if (++reads == 2) {
                    insertOwn("sub-2", QuoteSubmissionStatus.APPROVED, linked = "c-2")
                    CommunitySyncManager.requestFullStatusRecheck(context)
                }
            }
        }
        val submissions = FakeSubmissions()
        val manager = CommunitySyncManager(context, QuoteRepository(hookedDao, 42L), FakeFeed(), submissions)

        manager.refreshSubmissionStatuses()
        assertTrue(submissions.statusRequests.none { it.first == "sub-2" })
        manager.refreshSubmissionStatuses()

        assertTrue(submissions.statusRequests.any { it.first == "sub-2" })
    }

    @Test
    fun statusRefresh_404KeepsALinkedQuoteWhoseCommunityCopyIsStored() = runBlocking {
        // Sent with an installation id this device no longer has: every status request answers 404.
        installationIds("11111111-1111-4111-8111-111111111111")
        afterV2Upgrade()
        dao.insertQuotes(listOf(community("c-1", submissionId = "sub-1"), community("c-9")))
        val published = insertOwn("sub-1", QuoteSubmissionStatus.APPROVED, linked = "c-1")
        val unpublished = insertOwn("sub-2", QuoteSubmissionStatus.APPROVED, linked = "c-2")
        CommunitySyncManager.requestFullStatusRecheck(context)

        val result = manager().refreshSubmissionStatuses()

        dao.getQuoteById(published)!!.let {
            assertEquals(QuoteSubmissionStatus.APPROVED, it.submissionStatus)
            assertEquals("c-1", it.linkedServerId)
        }
        dao.getQuoteById(unpublished)!!.let {
            assertEquals(QuoteSubmissionStatus.REMOVED, it.submissionStatus)
            assertNull(it.linkedServerId)
        }
        assertEquals(listOf(unpublished), result.updates.map { it.quoteId })
    }

    @Test
    fun statusRefresh_404LeavesALinkedQuoteToTheFeedUntilItWasRead() = runBlocking {
        installationIds("11111111-1111-4111-8111-111111111111")
        afterV2Upgrade()
        // Restored from a backup before the first community sync.
        val id = insertOwn("sub-1", QuoteSubmissionStatus.APPROVED, linked = "c-1")
        CommunitySyncManager.requestFullStatusRecheck(context)
        val feed = FakeFeed().apply { pages += page(1, quotes = listOf(community("c-1", submissionId = "sub-1"))) }
        val manager = manager(feed = feed)

        assertTrue(manager.refreshSubmissionStatuses().updates.isEmpty())
        val synced = manager.syncWithResult()

        assertTrue(synced.submissions.updates.isEmpty())
        dao.getQuoteById(id)!!.let {
            assertEquals(QuoteSubmissionStatus.APPROVED, it.submissionStatus)
            assertEquals("c-1", it.linkedServerId)
        }
    }

    @Test
    fun syncStatus_recordsTheLastSuccessAndFailure() = runBlocking {
        afterV2Upgrade()
        val feed = FakeFeed().apply { pages += Result.failure(java.io.IOException("offline")) }
        val manager = manager(feed = feed)
        assertEquals(CommunitySyncStatus(null, null, false), manager.syncStatus())

        assertFalse(manager.sync())
        val failed = manager.syncStatus()
        assertTrue(failed.lastAttemptFailed)
        assertNotNull(failed.lastFailureAt)
        assertNull(failed.lastSuccessAt)

        assertTrue(manager.sync())
        val synced = manager.syncStatus()
        assertFalse(synced.lastAttemptFailed)
        assertNotNull(synced.lastSuccessAt)
        assertEquals(failed.lastFailureAt, synced.lastFailureAt)
    }
}
