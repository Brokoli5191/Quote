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
        return true
    }

    /** Returns true when a stored state changed. */
    private suspend fun refreshSubmissionStatusesLocked(): Boolean {
        val installationIds = submissionInstallationIds(prefs)
        if (installationIds.isEmpty()) return false
        var changed = false
        repository.getSubmissionsToRefresh().forEach { quote ->
            val submissionId = quote.submissionId ?: return@forEach
            val info = fetchStatus(submissionId, installationIds) ?: return@forEach
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
