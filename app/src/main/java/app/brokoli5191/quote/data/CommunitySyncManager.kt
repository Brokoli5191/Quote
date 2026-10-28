package app.brokoli5191.quote.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class CommunitySyncManager(
    context: Context,
    private val repository: QuoteRepository,
    private val communityClient: CommunityQuoteClient = CommunityQuoteClient(),
    private val submissionClient: QuoteSubmissionClient = QuoteSubmissionClient()
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Pulls community updates, then refreshes submission states. Runs under a process-wide lock
     * shared with [app.brokoli5191.quote.utils.CommunitySyncWorker], and updates the widget when
     * anything changed.
     */
    suspend fun sync(): Boolean = mutex.withLock {
        var changed = false
        val synced = syncFeedLocked { changed = true }
        if (refreshSubmissionStatusesLocked()) changed = true
        if (changed) notifyWidget()
        synced
    }

    suspend fun refreshSubmissionStatuses() {
        val changed = mutex.withLock { refreshSubmissionStatusesLocked() }
        if (changed) notifyWidget()
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
        var tombstonesChanged = false
        do {
            val page = communityClient.updates(revision).getOrElse { return false }
            if (page.hasMore && page.revision <= revision) return false
            val result = repository.applyCommunityUpdates(page.quotes, page.deletedIds, tombstones)
            if (result.changed) onChanged()
            if (result.removedFavorites.isNotEmpty() || result.restoredFavorites.isNotEmpty()) {
                tombstones.keys.removeAll(result.restoredFavorites)
                tombstones.putAll(result.removedFavorites)
                tombstonesChanged = true
            }
            // Persisted only after the page is in the database.
            revision = page.revision
            prefs.edit().putLong(KEY_REVISION, revision).apply()
        } while (page.hasMore)
        if (tombstonesChanged) saveFavoriteTombstones(tombstones)
        if (fullResync) prefs.edit().putBoolean(KEY_FEED_V2_RESYNCED, true).apply()
        return true
    }

    /** Returns true when a stored state changed. */
    private suspend fun refreshSubmissionStatusesLocked(): Boolean {
        val installationIds = submissionInstallationIds(prefs)
        if (installationIds.isEmpty()) return false
        var changed = false
        // Once after the v2 upgrade every submission is checked, so older approved quotes get their
        // community id (and older unpublished ones their state) even outside the refresh window.
        val fullPass = !prefs.getBoolean(KEY_STATUS_V2_REFRESHED, false)
        var allFetched = true
        val quotes = if (fullPass) repository.getSubmissionsToRefresh(since = 0L) else repository.getSubmissionsToRefresh()
        quotes.forEach { quote ->
            val submissionId = quote.submissionId ?: return@forEach
            val fetched = fetchStatus(submissionId, installationIds)
            if (fetched == null) {
                allFetched = false
                return@forEach
            }
            // A deleted rejected submission (404) stays rejected: it was never published.
            if (fetched.notFound && quote.submissionStatus == QuoteSubmissionStatus.REJECTED) return@forEach
            // An old backend has no "removed" state and still says "approved" for an unpublished
            // quote; only the feed linking it again brings a removed quote back.
            if (fetched.legacy && quote.submissionStatus == QuoteSubmissionStatus.REMOVED &&
                fetched.status == QuoteSubmissionStatus.APPROVED
            ) return@forEach
            // A response without a note (404, old backend) keeps the stored one.
            val info = if (fetched.reviewNote == null) fetched.copy(reviewNote = quote.reviewNote) else fetched
            val linked = info.communityQuoteId?.takeIf { info.status == QuoteSubmissionStatus.APPROVED }
                // The feed may already have linked it; a status without the id keeps that link.
                ?: quote.linkedServerId.takeIf { info.status == QuoteSubmissionStatus.APPROVED }
            if (info.status != quote.submissionStatus ||
                info.reviewNote != quote.reviewNote ||
                linked != quote.linkedServerId
            ) {
                repository.applySubmissionStatus(quote.id, info.copy(communityQuoteId = linked))
                changed = true
            }
        }
        if (fullPass && allFetched) prefs.edit().putBoolean(KEY_STATUS_V2_REFRESHED, true).apply()
        return changed
    }

    // Tries every installation id this device has used (a restored backup may carry an older one).
    // Only a 404 for all of them means the submission is gone.
    private suspend fun fetchStatus(submissionId: String, installationIds: List<String>): SubmissionStatusInfo? {
        var removed: SubmissionStatusInfo? = null
        for (installationId in installationIds) {
            val info = submissionClient.status(submissionId, installationId).getOrNull() ?: return null
            if (info.status != QuoteSubmissionStatus.REMOVED) return info
            removed = info
        }
        return removed
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
            saveFavoriteTombstones(tombstones)
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

    private fun saveFavoriteTombstones(tombstones: Map<String, String?>) {
        val json = JSONObject()
        tombstones.entries.toList().takeLast(MAX_FAVORITE_TOMBSTONES).forEach { (key, value) ->
            json.put(key, value ?: JSONObject.NULL)
        }
        prefs.edit().putString(KEY_FAVORITE_TOMBSTONES, json.toString()).apply()
    }

    private fun notifyWidget() {
        appContext.sendBroadcast(Intent("app.brokoli5191.quote.UPDATE_WIDGET").apply {
            component = ComponentName(appContext, "app.brokoli5191.quote.widget.QuoteWidgetProvider")
        })
    }

    companion object {
        private const val PREFS = "aura_prefs"
        private const val KEY_REVISION = "community_quote_revision"
        private const val KEY_FAVORITE_TOMBSTONES = "community_favorite_tombstones"
        private const val KEY_FEED_V2_RESYNCED = "community_feed_v2_resynced"
        private const val KEY_STATUS_V2_REFRESHED = "submission_status_v2_refreshed"
        private const val MAX_FAVORITE_TOMBSTONES = 200
        const val KEY_INSTALLATION_ID = "submission_installation_id"
        const val KEY_EXTRA_INSTALLATION_IDS = "submission_installation_ids_extra"

        /** One lock for every sync in the process (ViewModel and worker). */
        val mutex = Mutex()

        fun resetCursor(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_REVISION)
                .apply()
        }

        /** The current installation id first, then older ones restored from backups. */
        fun submissionInstallationIds(prefs: android.content.SharedPreferences): List<String> {
            val current = prefs.getString(KEY_INSTALLATION_ID, null)
            val extra = prefs.getStringSet(KEY_EXTRA_INSTALLATION_IDS, emptySet()).orEmpty().sorted()
            return (listOfNotNull(current) + extra).distinct()
        }
    }
}
