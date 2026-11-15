package app.brokoli5191.quote.data

import android.content.Context
import android.content.SharedPreferences
import app.brokoli5191.quote.widget.WidgetRefreshScheduler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Outcome of the latest community syncs, in-app and background alike. Times are epoch millis. */
data class CommunitySyncStatus(
    val lastSuccessAt: Long?,
    val lastFailureAt: Long?,
    /** The most recent attempt could not read the community feed (offline or a server error). */
    val lastAttemptFailed: Boolean
)

/** Result of one submission status refresh. */
data class SubmissionRefreshResult(
    /** Every submission that was due answered; false when some could not be checked (offline, server error). */
    val complete: Boolean,
    /** The refresh wrote a stored submission state (status, review note or community link). */
    val changed: Boolean,
    /** Review decisions that arrived (to approved, rejected or removed), through the feed or the status endpoint. */
    val updates: List<SubmissionUpdate>
)

data class CommunitySyncResult(
    /** The community feed was read to the end; this is what [CommunitySyncManager.sync] returns. */
    val feedSynced: Boolean,
    val submissions: SubmissionRefreshResult
)

/** What happened to the submission of a quote deleted with [CommunitySyncManager.withdrawDeletedSubmission]. */
sealed interface DeletedSubmissionOutcome {
    /** The submission is gone from the server and will not be published. */
    data object Withdrawn : DeletedSubmissionOutcome

    /** The service could not be reached ([code]); every later sync retries until the server answers. */
    data class RetryQueued(val code: String) : DeletedSubmissionOutcome

    /** The server refused for good ([code]), e.g. [SubmissionError.ALREADY_REVIEWED] when a moderator was faster. */
    data class Failed(val code: String) : DeletedSubmissionOutcome

    /** The quote came back (Undo, backup) before the withdrawal went through, so its submission stays. */
    data object Kept : DeletedSubmissionOutcome
}

/**
 * A withdrawal that [CommunitySyncManager.withdrawDeletedSubmission] queued, that a later sync tried again and
 * the server refused for good ([code], e.g. [SubmissionError.ALREADY_REVIEWED]): the submission may be published.
 */
data class FailedWithdrawal(val submissionId: String, val code: String)

class CommunitySyncManager(
    context: Context,
    private val repository: QuoteRepository,
    private val communityClient: CommunityQuoteClient = CommunityQuoteClient(),
    private val submissionClient: QuoteSubmissionClient = QuoteSubmissionClient()
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** [syncWithResult], returning whether the community feed was read to the end. */
    suspend fun sync(): Boolean = syncWithResult().feedSynced

    /**
     * Retries queued withdrawals, pulls community updates, then refreshes submission states. Runs under a
     * process-wide lock shared with [app.brokoli5191.quote.utils.CommunitySyncWorker], records the outcome
     * for [syncStatus] and updates the widget when anything changed. Posts no notification itself: the
     * caller decides what to do with the review decisions in [CommunitySyncResult.submissions].
     */
    suspend fun syncWithResult(): CommunitySyncResult = mutex.withLock {
        val before = repository.getSubmittedQuotes()
        retryQueuedWithdrawalsLocked()
        var feedChanged = false
        val synced = syncFeedLocked { feedChanged = true }
        recordSyncAttempt(synced)
        val submissions = refreshSubmissionStatusesLocked(before)
        if (feedChanged || submissions.changed) WidgetRefreshScheduler.requestUpdate(appContext)
        CommunitySyncResult(synced, submissions)
    }

    suspend fun refreshSubmissionStatuses(): SubmissionRefreshResult {
        val result = mutex.withLock { refreshSubmissionStatusesLocked(repository.getSubmittedQuotes()) }
        if (result.changed) WidgetRefreshScheduler.requestUpdate(appContext)
        return result
    }

    fun syncStatus(): CommunitySyncStatus = CommunitySyncStatus(
        lastSuccessAt = prefs.getLong(KEY_LAST_SYNC_SUCCESS_AT, 0L).takeIf { it > 0L },
        lastFailureAt = prefs.getLong(KEY_LAST_SYNC_FAILURE_AT, 0L).takeIf { it > 0L },
        lastAttemptFailed = prefs.getBoolean(KEY_LAST_SYNC_FAILED, false)
    )

    /**
     * Withdraws the pending submission of own quote [quoteId]; the quote stays as not submitted. Runs under
     * the sync lock, so a status refresh cannot write over the cleared state. Returns null when the quote is
     * not submitted anymore, else a [SubmissionError] code: [SubmissionError.NOT_FOUND] when the quote is
     * gone, [SubmissionError.ALREADY_REVIEWED] when a moderator was faster (the stored states are refreshed
     * then, so the quote shows the decision).
     */
    suspend fun withdrawSubmission(quoteId: Int): String? = mutex.withLock {
        val quote = repository.getQuoteById(quoteId)?.takeIf { it.isUserAdded }
            ?: return@withLock SubmissionError.NOT_FOUND
        if (quote.submissionStatus == QuoteSubmissionStatus.NOT_SUBMITTED) return@withLock null
        val submissionId = quote.submissionId
        if (quote.submissionStatus != QuoteSubmissionStatus.PENDING || submissionId == null) {
            return@withLock SubmissionError.ALREADY_REVIEWED
        }
        val error = withdrawOnServer(submissionId)
        when (error) {
            null -> repository.clearSubmissionIfUnchanged(quote)
            SubmissionError.ALREADY_REVIEWED -> refreshSubmissionStatusesLocked(repository.getSubmittedQuotes())
        }
        error
    }

    /**
     * Withdraws the pending submission [submissionId] of an own quote the caller has just deleted, so it is
     * not published. The id is queued before anything else, so a withdrawal that fails for lack of a
     * connection (or is interrupted) is retried by every later [sync] until the server answers; a retry the
     * server refuses for good is kept for [takeFailedWithdrawals]. If the quote comes back meanwhile (Undo, a
     * restored backup), a withdrawal that has not gone through is dropped and the submission stays; one that
     * went through leaves the restored quote as not submitted.
     */
    suspend fun withdrawDeletedSubmission(submissionId: String): DeletedSubmissionOutcome {
        editQueuedWithdrawals { it += submissionId }
        return mutex.withLock {
            val outcome = withdrawQueuedLocked(submissionId)
            // A sync that got the lock first may have retried it already; this caller reports its own outcome.
            forgetFailedWithdrawal(submissionId)
            outcome
        }
    }

    /**
     * The queued withdrawals that a sync since the last call (in the app or in the background) retried and
     * the server refused for good. Each one is returned once, so the caller can tell the user that the
     * deleted quote was not withdrawn after all.
     */
    fun takeFailedWithdrawals(): List<FailedWithdrawal> = synchronized(prefsLock) {
        val failed = failedWithdrawals()
        if (failed.isNotEmpty()) prefs.edit().remove(KEY_FAILED_WITHDRAWALS).apply()
        failed.map { (submissionId, code) -> FailedWithdrawal(submissionId, code) }
    }

    private suspend fun retryQueuedWithdrawalsLocked() {
        for (submissionId in queuedWithdrawals()) {
            when (val outcome = withdrawQueuedLocked(submissionId)) {
                // The user was told it would be withdrawn later, so the refusal must reach them.
                is DeletedSubmissionOutcome.Failed -> keepFailedWithdrawal(submissionId, outcome.code)
                // Offline: the others would only fail (or time out) as well.
                is DeletedSubmissionOutcome.RetryQueued -> if (outcome.code == SubmissionError.NETWORK) break
                else -> Unit
            }
        }
    }

    // One attempt for a queued withdrawal. The id stays queued only when trying again can help.
    private suspend fun withdrawQueuedLocked(submissionId: String): DeletedSubmissionOutcome {
        val outcome = if (repository.getPersonalQuotesBySubmissionId(submissionId).isNotEmpty()) {
            DeletedSubmissionOutcome.Kept
        } else {
            val error = withdrawOnServer(submissionId)
            // The quote may have been restored while the request ran.
            val restored = repository.getPersonalQuotesBySubmissionId(submissionId)
            when {
                error == null -> {
                    restored.forEach { repository.clearSubmissionIfUnchanged(it) }
                    DeletedSubmissionOutcome.Withdrawn
                }
                restored.isNotEmpty() -> DeletedSubmissionOutcome.Kept
                error in RETRYABLE_WITHDRAW_ERRORS -> return DeletedSubmissionOutcome.RetryQueued(error)
                else -> DeletedSubmissionOutcome.Failed(error)
            }
        }
        editQueuedWithdrawals { it -= submissionId }
        return outcome
    }

    // Null when the submission is gone from the server, else a SubmissionError code. Tries every
    // installation id this device has used; only a 404 for all of them means it is gone.
    private suspend fun withdrawOnServer(submissionId: String): String? {
        for (installationId in submissionInstallationIds(prefs)) {
            when (val result = submissionClient.withdraw(submissionId, installationId)) {
                WithdrawResult.Success -> return null
                WithdrawResult.NotFound -> continue
                is WithdrawResult.Error -> return result.code
            }
        }
        return null
    }

    private suspend fun syncFeedLocked(onChanged: () -> Unit): Boolean {
        var revision = prefs.getLong(KEY_REVISION, 0L)
        // A recreated database (or cleared rows) must not keep a cursor past rows it no longer has.
        if (revision > 0L && repository.getCommunityQuotesCount() == 0) revision = 0L
        // Rows stored before the v2 feed lack submissionId, language and translation, and may hold
        // text the old normalizer damaged: read the whole feed once more. Favorites are kept.
        val fullResync = !prefs.getBoolean(KEY_FEED_V2_RESYNCED, false)
        if (fullResync) revision = 0L
        val tombstones = loadFavoriteTombstones()
        do {
            val page = communityClient.updates(revision).getOrElse { return false }
            if (page.hasMore && page.revision <= revision) return false
            val result = repository.applyCommunityUpdates(page.quotes, page.deletedIds, tombstones)
            if (result.changed) onChanged()
            val editor = prefs.edit()
            if (result.removedFavorites.isNotEmpty() || result.restoredFavorites.isNotEmpty()) {
                tombstones.keys.removeAll(result.restoredFavorites)
                tombstones.putAll(result.removedFavorites)
                editor.putFavoriteTombstones(tombstones)
            }
            // Written together and only after the page is in the database: the cursor never moves past
            // deleted favorites whose tombstones are lost when a later page fails.
            revision = page.revision
            editor.putLong(KEY_REVISION, revision).apply()
        } while (page.hasMore)
        if (fullResync) prefs.edit().putBoolean(KEY_FEED_V2_RESYNCED, true).apply()
        return true
    }

    private fun recordSyncAttempt(synced: Boolean) {
        prefs.edit()
            .putLong(if (synced) KEY_LAST_SYNC_SUCCESS_AT else KEY_LAST_SYNC_FAILURE_AT, System.currentTimeMillis())
            .putBoolean(KEY_LAST_SYNC_FAILED, !synced)
            .apply()
    }

    // [before]: the submitted quotes as they were when the sync started, for SubmissionRefreshResult.updates.
    private suspend fun refreshSubmissionStatusesLocked(before: List<QuoteEntity>): SubmissionRefreshResult {
        val installationIds = submissionInstallationIds(prefs)
        var changed = false
        var complete = true
        if (installationIds.isNotEmpty()) {
            // A full pass checks every submission once, whatever its age or link: after the v2 upgrade (older
            // approved quotes get their community id) and when a restored backup put stored states back. The
            // request is read before the quotes, so rows restored after this read come with a newer request.
            val recheckRequest = prefs.getLong(KEY_STATUS_RECHECK_REQUEST, 0L)
            val fullPass = !prefs.getBoolean(KEY_STATUS_V2_REFRESHED, false) || recheckRequest != 0L
            val submitted = repository.getSubmittedQuotes()
            // A quote the feed has just linked is approved and leaves the regular refresh; one more check
            // fetches the moderator's note. Kept until it succeeds, for quotes that still exist.
            val submittedIds = submitted.mapNotNullTo(HashSet()) { it.submissionId }
            val rechecks = (prefs.getStringSet(KEY_STATUS_RECHECK_IDS, null).orEmpty() + newlyLinked(before, submitted))
                .filterTo(HashSet()) { it in submittedIds }
            val due = if (fullPass) {
                submitted
            } else {
                (repository.getSubmissionsToRefresh() + submitted.filter { it.submissionId in rechecks })
                    .distinctBy { it.id }
            }
            due.forEach { quote ->
                val submissionId = quote.submissionId ?: return@forEach
                val fetched = fetchStatus(submissionId, installationIds)
                if (fetched == null) {
                    complete = false
                    return@forEach
                }
                rechecks -= submissionId
                if (applyFetchedStatus(quote, fetched)) changed = true
            }
            val editor = prefs.edit().putStringSet(KEY_STATUS_RECHECK_IDS, rechecks)
            if (fullPass && complete) editor.putBoolean(KEY_STATUS_V2_REFRESHED, true)
            editor.apply()
            if (fullPass && complete && recheckRequest != 0L) clearRecheckRequest(recheckRequest)
        }
        return SubmissionRefreshResult(complete, changed, reviewUpdates(before, repository.getSubmittedQuotes()))
    }

    // Writes a fetched state over [quote] unless the row changed since it was read. Returns whether it wrote.
    private suspend fun applyFetchedStatus(quote: QuoteEntity, fetched: SubmissionStatusInfo): Boolean {
        if (fetched.notFound) {
            return when (quote.submissionStatus) {
                // Only a withdrawal deletes a pending submission (a lost response, another device with the
                // same installation id), and the server accepts the quote again: it is just not submitted.
                QuoteSubmissionStatus.PENDING -> repository.clearSubmissionIfUnchanged(quote)
                QuoteSubmissionStatus.APPROVED -> if (feedDecidesPublication(quote)) {
                    false
                } else {
                    // A deleted approved submission is no longer published.
                    repository.applySubmissionStatusIfUnchanged(
                        quote,
                        SubmissionStatusInfo(QuoteSubmissionStatus.REMOVED, quote.reviewNote)
                    )
                }
                // A deleted rejected submission stays rejected: it was never published.
                else -> false
            }
        }
        // An old backend has no "removed" state and still says "approved" for an unpublished quote; only
        // the feed linking it again brings a removed quote back.
        if (fetched.legacy && quote.submissionStatus == QuoteSubmissionStatus.REMOVED &&
            fetched.status == QuoteSubmissionStatus.APPROVED
        ) return false
        // A v2 answer carries the current note (null: none, e.g. an approval after a rejection with a note);
        // an old backend sends none, so the stored one is kept.
        val note = if (fetched.legacy) quote.reviewNote else fetched.reviewNote
        // The feed may already have linked it; a status without the id keeps that link.
        val linked = if (fetched.status == QuoteSubmissionStatus.APPROVED) {
            fetched.communityQuoteId ?: quote.linkedServerId
        } else {
            null
        }
        if (fetched.status == quote.submissionStatus && note == quote.reviewNote && linked == quote.linkedServerId) {
            return false
        }
        return repository.applySubmissionStatusIfUnchanged(quote, SubmissionStatusInfo(fetched.status, note, linked))
    }

    // The feed unpublishes a linked quote through its deletions. A 404 can also come from an installation id
    // this device does not know (e.g. one a restored backup did not carry), so it does not unpublish a linked
    // quote whose community copy is stored, nor one while no community quote is stored at all: the next
    // feed read then starts from the beginning and links or unpublishes it.
    private suspend fun feedDecidesPublication(quote: QuoteEntity): Boolean {
        val linked = quote.linkedServerId ?: return false
        return repository.hasCommunityQuote(linked) || repository.getCommunityQuotesCount() == 0
    }

    // Tries every installation id this device has used (a restored backup may carry an older one). The
    // first real answer counts; only a 404 for all of them means the submission is gone.
    private suspend fun fetchStatus(submissionId: String, installationIds: List<String>): SubmissionStatusInfo? {
        for (installationId in installationIds) {
            val info = submissionClient.status(submissionId, installationId).getOrNull() ?: return null
            if (!info.notFound) return info
        }
        return SubmissionStatusInfo(QuoteSubmissionStatus.REMOVED, notFound = true)
    }

    // Submission ids of quotes that are approved and linked now, but were not (or to another copy) in [before].
    private fun newlyLinked(before: List<QuoteEntity>, now: List<QuoteEntity>): List<String> {
        val previous = before.associateBy { it.id }
        return now.filter { quote ->
            val old = previous[quote.id]
            quote.submissionStatus == QuoteSubmissionStatus.APPROVED && quote.linkedServerId != null &&
                (old == null || old.submissionStatus != QuoteSubmissionStatus.APPROVED ||
                    old.linkedServerId != quote.linkedServerId)
        }.mapNotNull { it.submissionId }
    }

    // Review decisions between two states of the same submissions. A new submission id (resubmitted or
    // withdrawn meanwhile) is the user's own doing, not a decision.
    private fun reviewUpdates(before: List<QuoteEntity>, after: List<QuoteEntity>): List<SubmissionUpdate> {
        val previous = before.associateBy { it.id }
        return after.mapNotNull { quote ->
            val old = previous[quote.id] ?: return@mapNotNull null
            if (old.submissionId != quote.submissionId || old.submissionStatus == quote.submissionStatus ||
                quote.submissionStatus !in REVIEW_DECISIONS
            ) return@mapNotNull null
            SubmissionUpdate(quote.id, quote.text, old.submissionStatus, quote.submissionStatus, quote.reviewNote)
        }
    }

    /**
     * Remembers favorites (community id to saved date) whose quotes are not stored yet, e.g. from a
     * backup restored before the first sync; the feed favorites them when they arrive.
     */
    suspend fun rememberPendingFavorites(favorites: Map<String, String?>) {
        if (favorites.isEmpty()) return
        mutex.withLock {
            val tombstones = loadFavoriteTombstones()
            // Re-inserted so they count as the newest entries when the list is capped.
            tombstones.keys.removeAll(favorites.keys)
            tombstones.putAll(favorites)
            prefs.edit().putFavoriteTombstones(tombstones).apply()
        }
    }

    private fun loadFavoriteTombstones(): MutableMap<String, String?> {
        val raw = prefs.getString(KEY_FAVORITE_TOMBSTONES, null) ?: return LinkedHashMap()
        return runCatching {
            val json = JSONObject(raw)
            val result = LinkedHashMap<String, String?>()
            json.keys().forEach { key -> result[key] = json.optNullableString(key) }
            result
        }.getOrElse { LinkedHashMap() }
    }

    private fun SharedPreferences.Editor.putFavoriteTombstones(
        tombstones: Map<String, String?>
    ): SharedPreferences.Editor {
        val json = JSONObject()
        tombstones.entries.toList().takeLast(MAX_FAVORITE_TOMBSTONES).forEach { (key, value) ->
            json.put(key, value ?: JSONObject.NULL)
        }
        return putString(KEY_FAVORITE_TOMBSTONES, json.toString())
    }

    // The queue is also written outside the sync lock (withdrawDeletedSubmission), hence its own lock.
    private fun queuedWithdrawals(): Set<String> = synchronized(prefsLock) {
        prefs.getStringSet(KEY_QUEUED_WITHDRAWALS, null).orEmpty().toSet()
    }

    private fun editQueuedWithdrawals(change: (MutableSet<String>) -> Unit) = synchronized(prefsLock) {
        val ids = prefs.getStringSet(KEY_QUEUED_WITHDRAWALS, null).orEmpty().toMutableSet()
        change(ids)
        prefs.edit().putStringSet(KEY_QUEUED_WITHDRAWALS, ids).apply()
    }

    // Submission id to SubmissionError code; callers hold prefsLock.
    private fun failedWithdrawals(): MutableMap<String, String> {
        val raw = prefs.getString(KEY_FAILED_WITHDRAWALS, null) ?: return LinkedHashMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWithTo(LinkedHashMap()) { json.getString(it) }
        }.getOrElse { LinkedHashMap() }
    }

    private fun saveFailedWithdrawals(failed: Map<String, String>) {
        prefs.edit().putString(KEY_FAILED_WITHDRAWALS, JSONObject(failed).toString()).apply()
    }

    private fun keepFailedWithdrawal(submissionId: String, code: String) = synchronized(prefsLock) {
        saveFailedWithdrawals(failedWithdrawals().apply { put(submissionId, code) })
    }

    private fun forgetFailedWithdrawal(submissionId: String) = synchronized(prefsLock) {
        val failed = failedWithdrawals()
        if (failed.remove(submissionId) != null) saveFailedWithdrawals(failed)
    }

    private fun clearRecheckRequest(handled: Long) = synchronized(prefsLock) {
        // A request made while the pass ran has a new value and stays for the next refresh.
        if (prefs.getLong(KEY_STATUS_RECHECK_REQUEST, 0L) == handled) {
            prefs.edit().remove(KEY_STATUS_RECHECK_REQUEST).apply()
        }
    }

    companion object {
        private const val PREFS = "aura_prefs"
        private const val KEY_REVISION = "community_quote_revision"
        private const val KEY_FAVORITE_TOMBSTONES = "community_favorite_tombstones"
        private const val KEY_FEED_V2_RESYNCED = "community_feed_v2_resynced"
        private const val KEY_STATUS_V2_REFRESHED = "submission_status_v2_refreshed"
        private const val KEY_STATUS_RECHECK_REQUEST = "submission_status_recheck_request"
        private const val KEY_STATUS_RECHECK_IDS = "submission_status_recheck_ids"
        private const val KEY_QUEUED_WITHDRAWALS = "submission_queued_withdrawals"
        private const val KEY_FAILED_WITHDRAWALS = "submission_failed_withdrawals"
        private const val KEY_LAST_SYNC_SUCCESS_AT = "community_last_sync_success_at"
        private const val KEY_LAST_SYNC_FAILURE_AT = "community_last_sync_failure_at"
        private const val KEY_LAST_SYNC_FAILED = "community_last_sync_failed"
        private const val MAX_FAVORITE_TOMBSTONES = 200
        private val REVIEW_DECISIONS = setOf(
            QuoteSubmissionStatus.APPROVED,
            QuoteSubmissionStatus.REJECTED,
            QuoteSubmissionStatus.REMOVED
        )
        // Errors a later attempt can get past; anything else (e.g. already reviewed) is final.
        private val RETRYABLE_WITHDRAW_ERRORS = setOf(
            SubmissionError.NETWORK,
            SubmissionError.SERVER,
            SubmissionError.RATE_LIMITED,
            SubmissionError.UNKNOWN
        )
        private val prefsLock = Any()
        const val KEY_INSTALLATION_ID = "submission_installation_id"
        const val KEY_EXTRA_INSTALLATION_IDS = "submission_installation_ids_extra"

        /** One lock for every sync in the process (ViewModel and worker). */
        val mutex = Mutex()

        fun resetCursor(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_REVISION)
                .apply()
        }

        /**
         * Makes the next status refresh check every own submission once, whatever its age or community
         * link. Call it after a backup restore has put stored submission states into the database (not
         * before: a refresh running in between would use up the request), then refresh.
         */
        fun requestFullStatusRecheck(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            synchronized(prefsLock) {
                // A new value each time, so a pass that started before this request does not clear it.
                prefs.edit()
                    .putLong(KEY_STATUS_RECHECK_REQUEST, prefs.getLong(KEY_STATUS_RECHECK_REQUEST, 0L) + 1)
                    .apply()
            }
        }

        /** The current installation id first, then older ones restored from backups. */
        fun submissionInstallationIds(prefs: SharedPreferences): List<String> {
            val current = prefs.getString(KEY_INSTALLATION_ID, null)
            val extra = prefs.getStringSet(KEY_EXTRA_INSTALLATION_IDS, emptySet()).orEmpty().sorted()
            return (listOfNotNull(current) + extra).distinct()
        }
    }
}
