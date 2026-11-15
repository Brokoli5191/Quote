package app.brokoli5191.quote.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Result of applying one community feed page. */
data class CommunityApplyResult(
    /** Favorited community quotes that were unpublished: serverId to savedDate. */
    val removedFavorites: Map<String, String?>,
    /** Server ids whose favorite was restored from [QuoteRepository.applyCommunityUpdates]'s input. */
    val restoredFavorites: Set<String>,
    val changed: Boolean
)

/** What the status endpoint reported for one submission. */
data class SubmissionStatusInfo(
    val status: String,
    val reviewNote: String? = null,
    val communityQuoteId: String? = null,
    /**
     * The status endpoint answered 404 for every installation id (withdrawn or deleted on the server);
     * [status] is then removed, and the stored state decides what that means.
     */
    val notFound: Boolean = false,
    /** The answer came from a pre-v2 backend, which never reports "removed". */
    val legacy: Boolean = false
)

/** Day key for daily selections. Fixed locale so the key never contains non-ASCII digits. */
fun dailyDateKey(date: Date = Date()): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

/** One day of the daily-quote history. */
data class DailyHistoryEntry(
    val date: String, // yyyy-MM-dd
    val quote: QuoteEntity // Localized display copy
)

class QuoteRepository(
    private val quoteDao: QuoteDao,
    private val installationSeed: Long = java.security.SecureRandom().nextLong()
) {
    val allQuotes: Flow<List<QuoteEntity>> = quoteDao.getAllQuotes()
    val favorites: Flow<List<QuoteEntity>> = quoteDao.getFavoriteQuotes()
    val userAdded: Flow<List<QuoteEntity>> = quoteDao.getUserAddedQuotes()

    suspend fun getQuotesCount(): Int {
        return quoteDao.getQuotesCount()
    }

    suspend fun getCommunityQuotesCount(): Int = quoteDao.getCommunityQuotesCount()

    suspend fun clearAllQuotes() {
        quoteDao.deleteAllQuotes()
    }

    suspend fun preseedDatabase(context: Context) {
        try {
            quoteDao.insertQuotes(loadBundledQuotes(context))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadBundledQuotes(context: Context): List<QuoteEntity> {
        val inputStream = context.resources.openRawResource(app.brokoli5191.quote.R.raw.quotes_seed)
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        val jsonArray = org.json.JSONArray(jsonString)
        val quoteEntities = mutableListOf<QuoteEntity>()
        val germanTranslations = loadGermanTranslations(context)
        val now = System.currentTimeMillis()

        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val quoteText = item.getString("quote")
            val author = item.getString("author")

            val tagsList = readTags(item)

            // The seed freezes each quote's category (tools/apply_quote_tags.mjs), so curated tags
            // never move a quote; the mapper is only the fallback for rows without a valid one.
            val category = item.optString("category").takeIf { it in CategoryMapper.categories }
                ?: CategoryMapper.map(tagsList, quoteText)
            val cleanText = normalizeQuoteText(quoteText)
            quoteEntities.add(
                QuoteEntity(
                    text = cleanText,
                    author = normalizeAuthorName(author),
                    category = category,
                    aboutAuthor = "",
                    tags = tagsList.joinToString(", "),
                    isFavorite = false,
                    timestamp = now,
                    textDe = germanTranslations[cleanText]
                )
            )
        }

        val anonymousInput = context.resources.openRawResource(app.brokoli5191.quote.R.raw.anonymous_quotes)
        val anonymousJson = org.json.JSONArray(anonymousInput.bufferedReader().use { it.readText() })
        for (i in 0 until anonymousJson.length()) {
            val item = anonymousJson.getJSONObject(i)
            quoteEntities.add(
                QuoteEntity(
                    text = normalizeQuoteText(item.getString("quote")),
                    textDe = normalizeQuoteText(item.getString("quote_de")),
                    author = "",
                    category = "Reflections",
                    tags = readTags(item).ifEmpty { listOf("reflection", "anonymous", "original") }.joinToString(", "),
                    isAnonymous = true,
                    timestamp = now + i
                )
            )
        }
        return quoteEntities
    }

    private fun readTags(item: org.json.JSONObject): List<String> {
        val tagsArray = item.optJSONArray("tags") ?: return emptyList()
        return (0 until tagsArray.length()).map { tagsArray.getString(it) }
    }

    private fun loadGermanTranslations(context: Context): Map<String, String> {
        val input = context.resources.openRawResource(app.brokoli5191.quote.R.raw.quotes_de)
        val json = org.json.JSONArray(input.bufferedReader().use { it.readText() })
        return buildMap {
            for (i in 0 until json.length()) {
                val item = json.getJSONObject(i)
                put(normalizeQuoteText(item.getString("quote")), normalizeQuoteText(item.getString("quote_de")))
            }
        }
    }

    /**
     * Re-seeds the bundled quotes without losing user data: user-added and community rows are left
     * untouched, and bundled rows keep their ids, favorites and saved dates (see [mergeReseed]), so
     * today's quote and the no-repeat history stay attached. The swap runs under the lock that
     * favorite changes and the daily pick take, so a heart tapped meanwhile is applied afterwards
     * to the same row instead of being overwritten.
     */
    suspend fun reseedPreservingFavorites(context: Context) {
        val fresh = try {
            loadBundledQuotes(context)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }
        writeLock.withLock {
            quoteDao.replaceBundledQuotes(mergeReseed(quoteDao.getBundledQuotes(), fresh))
        }
    }

    fun getQuotesByCategory(category: String): Flow<List<QuoteEntity>> {
        return quoteDao.getQuotesByCategory(category)
    }

    suspend fun getQuoteById(id: Int): QuoteEntity? {
        return quoteDao.getQuoteById(id)
    }

    /** Returns false when the quote no longer exists, so the caller can reload what it shows. */
    suspend fun toggleFavorite(id: Int, isFav: Boolean): Boolean = writeLock.withLock {
        val savedString = if (isFav) System.currentTimeMillis().toString() else null
        quoteDao.updateFavorite(id, isFav, savedString) > 0
    }

    /** Marks a quote as favorite with a given saved date (e.g. from a backup). */
    suspend fun setFavorite(id: Int, savedDate: String?) {
        writeLock.withLock { quoteDao.updateFavorite(id, true, savedDate ?: System.currentTimeMillis().toString()) }
    }

    suspend fun insertQuote(quote: QuoteEntity): Long {
        return quoteDao.insertQuote(quote.normalizedText())
    }

    /**
     * Re-applies text normalization to stored rows; [onlyOrigins] limits it to some origins.
     * Only the text columns are written, so other changes made meanwhile are kept.
     */
    suspend fun repairStoredText(onlyOrigins: Set<String>? = null) {
        quoteDao.getAllQuotesSync()
            .filter { onlyOrigins == null || it.origin in onlyOrigins }
            .forEach { current ->
                val normalized = current.normalizedText()
                if (normalized != current) {
                    quoteDao.updateText(current.id, normalized.text, normalized.author, normalized.textDe, normalized.textEn)
                }
            }
    }

    suspend fun getAllQuotesSync(): List<QuoteEntity> {
        return quoteDao.getAllQuotesSync()
    }

    suspend fun getPendingSubmittedQuotes(): List<QuoteEntity> {
        return quoteDao.getPendingSubmittedQuotes()
    }

    /** Submissions whose server state should be polled: pending ones and recently reviewed ones. */
    suspend fun getSubmissionsToRefresh(
        now: Long = System.currentTimeMillis(),
        since: Long = now - SUBMISSION_REFRESH_WINDOW_MS
    ): List<QuoteEntity> {
        return quoteDao.getSubmissionsToRefresh(since)
    }

    /** Own quotes that carry a submission id, whatever their state or age. */
    suspend fun getSubmittedQuotes(): List<QuoteEntity> = quoteDao.getSubmittedQuotes()

    suspend fun getPersonalQuotesBySubmissionId(submissionId: String): List<QuoteEntity> =
        quoteDao.getPersonalQuotesBySubmissionIds(listOf(submissionId))

    /** Whether the community copy [serverId] (e.g. a personal quote's [QuoteEntity.linkedServerId]) is stored. */
    suspend fun hasCommunityQuote(serverId: String): Boolean =
        quoteDao.getQuoteByServerId(serverId)?.origin == QuoteOrigin.COMMUNITY

    suspend fun deleteQuote(id: Int) {
        quoteDao.deleteQuoteById(id)
    }

    suspend fun markSubmissionPending(id: Int, submissionId: String) {
        quoteDao.updateSubmission(
            id = id,
            status = QuoteSubmissionStatus.PENDING,
            submissionId = submissionId,
            submittedAt = System.currentTimeMillis()
        )
    }

    /** Stores a successful (or idempotently repeated) submission and the state the server reported. */
    suspend fun markSubmitted(id: Int, submissionId: String, info: SubmissionStatusInfo) {
        quoteDao.updateSubmission(id, info.status, submissionId, System.currentTimeMillis())
        applySubmissionStatus(id, info)
    }

    suspend fun updateSubmissionStatus(id: Int, status: String) {
        quoteDao.updateSubmissionStatus(id, status)
    }

    suspend fun applySubmissionStatus(id: Int, info: SubmissionStatusInfo) {
        val linked = info.communityQuoteId?.takeIf { info.status == QuoteSubmissionStatus.APPROVED }
        quoteDao.updateSubmissionState(id, info.status, info.reviewNote?.takeIf { it.isNotBlank() }, linked)
    }

    suspend fun clearSubmission(id: Int) {
        quoteDao.clearSubmission(id)
    }

    /**
     * [applySubmissionStatus] for a state fetched after [snapshot] was read: it is written only while the
     * row still has the snapshot's submission id and status, so a slow status request never overwrites a
     * withdrawal, edit or resubmission made meanwhile. Returns whether the row was written.
     */
    suspend fun applySubmissionStatusIfUnchanged(snapshot: QuoteEntity, info: SubmissionStatusInfo): Boolean {
        val linked = info.communityQuoteId?.takeIf { info.status == QuoteSubmissionStatus.APPROVED }
        return quoteDao.updateSubmissionStateIf(
            snapshot.id,
            snapshot.submissionId,
            snapshot.submissionStatus,
            info.status,
            info.reviewNote?.takeIf { it.isNotBlank() },
            linked
        ) > 0
    }

    /** [clearSubmission], but only while the row still has [snapshot]'s submission id and status. */
    suspend fun clearSubmissionIfUnchanged(snapshot: QuoteEntity): Boolean =
        quoteDao.clearSubmissionIf(snapshot.id, snapshot.submissionId, snapshot.submissionStatus) > 0

    /**
     * Applies one community feed page. Personal rows whose submission was published get linked to
     * the community copy (status approved), unpublished links turn into "removed", and favorites
     * survive an unpublish/republish through [restoreFavorites] / [CommunityApplyResult.removedFavorites].
     */
    suspend fun applyCommunityUpdates(
        quotes: List<QuoteEntity>,
        deletedIds: List<String>,
        restoreFavorites: Map<String, String?> = emptyMap()
    ): CommunityApplyResult {
        val normalizedQuotes = quotes.map { it.normalizedText() }
        val existingByServerId = normalizedQuotes
            .mapNotNull { it.serverId }
            .distinct()
            .chunked(400)
            .flatMap { quoteDao.getQuotesByServerIds(it) }
            .associateBy { it.serverId }

        val restored = HashSet<String>()
        if (normalizedQuotes.isNotEmpty()) {
            quoteDao.insertQuotes(normalizedQuotes.map { incoming ->
                val existing = incoming.serverId?.let(existingByServerId::get)
                when {
                    existing != null -> incoming.copy(
                        id = existing.id,
                        isFavorite = existing.isFavorite,
                        savedDate = existing.savedDate,
                        timestamp = existing.timestamp
                    )
                    incoming.serverId != null && restoreFavorites.containsKey(incoming.serverId) -> {
                        restored += incoming.serverId
                        incoming.copy(
                            isFavorite = true,
                            savedDate = restoreFavorites[incoming.serverId] ?: System.currentTimeMillis().toString()
                        )
                    }
                    else -> incoming
                }
            })
        }

        // Link the submitter's personal row to its published copy.
        val bySubmissionId = normalizedQuotes
            .filter { !it.submissionId.isNullOrBlank() && it.serverId != null }
            .associate { it.submissionId!! to it.serverId!! }
        if (bySubmissionId.isNotEmpty()) {
            bySubmissionId.keys.chunked(400)
                .flatMap { quoteDao.getPersonalQuotesBySubmissionIds(it) }
                .forEach { personal ->
                    val serverId = bySubmissionId[personal.submissionId] ?: return@forEach
                    if (personal.linkedServerId != serverId ||
                        personal.submissionStatus != QuoteSubmissionStatus.APPROVED
                    ) {
                        quoteDao.updateSubmissionStateIf(
                            personal.id,
                            personal.submissionId,
                            personal.submissionStatus,
                            QuoteSubmissionStatus.APPROVED,
                            personal.reviewNote,
                            serverId
                        )
                    }
                }
        }

        val removedFavorites = HashMap<String, String?>()
        if (deletedIds.isNotEmpty()) {
            deletedIds.chunked(400).forEach { chunk ->
                quoteDao.getFavoriteCommunityQuotes(chunk).forEach { removedFavorites[it.serverId!!] = it.savedDate }
                quoteDao.getPersonalQuotesByLinkedServerIds(chunk).forEach { personal ->
                    quoteDao.updateSubmissionStateIf(
                        personal.id,
                        personal.submissionId,
                        personal.submissionStatus,
                        QuoteSubmissionStatus.REMOVED,
                        personal.reviewNote,
                        null
                    )
                }
                quoteDao.deleteCommunityQuotes(chunk)
            }
        }
        return CommunityApplyResult(
            removedFavorites = removedFavorites,
            restoredFavorites = restored,
            changed = normalizedQuotes.isNotEmpty() || deletedIds.isNotEmpty()
        )
    }

    // Daily quote. App, widget and reminder all read the pick stored here, so they show the same quote.
    suspend fun getDailyQuote(
        date: String,
        sourceMode: String = QuoteSourceMode.ALL,
        language: String = QuoteLanguage.ENGLISH,
        showAnonymous: Boolean = false
    ): QuoteEntity? = getDailyQuoteRaw(date, sourceMode, language, showAnonymous)?.localized(language)

    /**
     * Same as [getDailyQuote] but returns the stored row, not a localized display copy. Today's quote
     * stays when the language, source or anonymous setting changes, as long as it fits the new
     * settings; otherwise a quote not shown in the current no-repeat cycle is picked and stored.
     */
    suspend fun getDailyQuoteRaw(
        date: String,
        sourceMode: String = QuoteSourceMode.ALL,
        language: String = QuoteLanguage.ENGLISH,
        showAnonymous: Boolean = false
    ): QuoteEntity? = writeLock.withLock {
        val filter = dailyFilter(sourceMode, language, showAnonymous)
        todaysPick(date, filter) ?: pickNew(date, filter)
    }

    /**
     * "Another quote": replaces today's quote with one not shown in the current no-repeat cycle. The
     * replaced quote stays in the history, so it does not come back as a later daily quote, and
     * [restoreDailySelection] can bring it back today. Returns the new quote (localized), today's
     * quote when there is no other candidate, or null when there are no candidates.
     */
    suspend fun cycleDailyQuote(
        date: String,
        sourceMode: String = QuoteSourceMode.ALL,
        language: String = QuoteLanguage.ENGLISH,
        showAnonymous: Boolean = false
    ): QuoteEntity? = writeLock.withLock {
        val filter = dailyFilter(sourceMode, language, showAnonymous)
        val current = todaysPick(date, filter)
        val candidates = candidateIds(filter)
        val others = candidates.filter { it != current?.id }
        val nextId = notRecentlyShown(candidates).filter { it != current?.id }.ifEmpty { others }.randomOrNull()
        val next = nextId?.let { quoteDao.getQuoteById(it) } ?: return@withLock current?.localized(language)
        quoteDao.storeDailySelection(filter.key(date), next.id)
        next.localized(language)
    }

    /**
     * Undo for [cycleDailyQuote]: makes [quoteId] today's quote again for these settings. Returns it
     * (localized), or null without changing anything when it no longer exists or does not fit them.
     */
    suspend fun restoreDailySelection(
        date: String,
        sourceMode: String,
        language: String,
        showAnonymous: Boolean,
        quoteId: Int
    ): QuoteEntity? = writeLock.withLock {
        val filter = dailyFilter(sourceMode, language, showAnonymous)
        val quote = quoteDao.getQuoteById(quoteId)?.takeIf(filter::accepts) ?: return@withLock null
        quoteDao.storeDailySelection(filter.key(date), quote.id)
        quote.localized(language)
    }

    /**
     * The daily quotes of the last [limit] days that have a pick fitting these settings, newest
     * first and today included, as localized copies. A day lists the quote it ended with, so one
     * replaced by "Another quote" is not listed.
     */
    suspend fun dailyHistory(
        sourceMode: String,
        language: String,
        showAnonymous: Boolean,
        limit: Int = 30
    ): List<DailyHistoryEntry> {
        if (limit <= 0) return emptyList()
        val filter = dailyFilter(sourceMode, language, showAnonymous)
        val picksByDay = quoteDao.getCurrentDailySelections().groupBy { it.date.substringBefore('_') }
        val quotes = HashMap<Int, QuoteEntity>()
        val history = ArrayList<DailyHistoryEntry>(limit)
        for (days in picksByDay.keys.sortedDescending().chunked(limit)) {
            days.flatMap { day -> picksByDay.getValue(day).map { it.quoteId } }
                .filter { it !in quotes }
                .distinct()
                .chunked(500)
                .forEach { ids -> quoteDao.getQuotesByIds(ids).associateByTo(quotes) { it.id } }
            for (day in days) {
                val quote = picksByDay.getValue(day)
                    .firstNotNullOfOrNull { pick -> quotes[pick.quoteId]?.takeIf(filter::accepts) }
                    ?: continue
                history += DailyHistoryEntry(day, quote.localized(language))
                if (history.size == limit) return history
            }
        }
        return history
    }

    // What the daily quote may be for one combination of settings, and the key its pick is stored
    // under: "<yyyy-MM-dd>_<language>_<anonymous 0/1>_<source mode>".
    private class DailyFilter(
        val sourceMode: String,
        val language: String,
        val showAnonymous: Boolean,
        // Community-only mode without a single community quote in [language]: community quotes in
        // any language qualify and show their original text, so the mode only comes up empty when
        // there are no community quotes at all.
        val anyLanguage: Boolean
    ) {
        fun key(date: String) = "${date}_${language}_${if (showAnonymous) 1 else 0}_${sourceMode}"

        fun accepts(quote: QuoteEntity): Boolean =
            quote.matchesSourceMode(sourceMode) && (showAnonymous || !quote.isAnonymous) &&
                (anyLanguage || quote.isAvailableIn(language))
    }

    private suspend fun dailyFilter(sourceMode: String, language: String, showAnonymous: Boolean): DailyFilter {
        val anyLanguage = sourceMode == QuoteSourceMode.COMMUNITY &&
            quoteDao.getDailyCandidateIds(listOf(QuoteOrigin.COMMUNITY), language, showAnonymous).isEmpty()
        return DailyFilter(sourceMode, language, showAnonymous, anyLanguage)
    }

    private suspend fun candidateIds(filter: DailyFilter): List<Int> {
        if (filter.anyLanguage) return quoteDao.getCommunityCandidateIdsAnyLanguage(filter.showAnonymous)
        val origins = when (filter.sourceMode) {
            QuoteSourceMode.CURATED -> listOf(QuoteOrigin.BUNDLED)
            QuoteSourceMode.COMMUNITY -> listOf(QuoteOrigin.COMMUNITY)
            else -> listOf(QuoteOrigin.BUNDLED, QuoteOrigin.COMMUNITY)
        }
        return quoteDao.getDailyCandidateIds(origins, filter.language, filter.showAnonymous)
    }

    // Today's quote: the most recent pick of the day that fits these settings, whatever settings it
    // was made under, so switching language, source or the anonymous setting keeps the quote. It is
    // stored under this key too, which makes it the most recent pick again; a different quote this
    // key held stays in the no-repeat history.
    private suspend fun todaysPick(date: String, filter: DailyFilter): QuoteEntity? {
        val picks = quoteDao.getDailySelectionsForDay("${date}_")
        if (picks.isEmpty()) return null
        val quotes = quoteDao.getQuotesByIds(picks.map { it.quoteId }.distinct()).associateBy { it.id }
        val pick = picks.firstOrNull { quotes[it.quoteId]?.let(filter::accepts) == true } ?: return null
        val key = filter.key(date)
        if (pick.date != key) quoteDao.storeDailySelection(key, pick.quoteId)
        return quotes.getValue(pick.quoteId)
    }

    // The index depends only on the install and the date, and the pick is stored under the lock, so
    // whoever asks first (app, widget or reminder) stores the quote that all of them show.
    private suspend fun pickNew(date: String, filter: DailyFilter): QuoteEntity? {
        val candidates = candidateIds(filter)
        if (candidates.isEmpty()) return null
        val pool = notRecentlyShown(candidates)
        val quote = quoteDao.getQuoteById(pool[dailyQuoteIndex(installationSeed, date, pool.size)]) ?: return null
        quoteDao.storeDailySelection(filter.key(date), quote.id)
        return quote
    }

    private suspend fun notRecentlyShown(candidates: List<Int>): List<Int> {
        val shown = currentNoRepeatCycle(quoteDao.getDailySelectionIdsOldestFirst(), candidates.toHashSet())
        return candidates.filter { it !in shown }
    }

    companion object {
        /** How long rejected/removed/unlinked approved submissions keep being polled. */
        const val SUBMISSION_REFRESH_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

        // Shared by every instance in the process (app, widget, reminder, workers), so the daily
        // pick, the re-seed and favorite changes never interleave.
        private val writeLock = Mutex()
    }
}

internal fun dailyQuoteIndex(installationSeed: Long, date: String, poolSize: Int): Int {
    require(poolSize > 0)
    return kotlin.random.Random(installationSeed xor date.hashCode().toLong()).nextInt(poolSize)
}

/**
 * The ids of the current no-repeat cycle in [historyOldestFirst]: every candidate is shown once
 * before any comes back. When all have been shown a new cycle starts, which never opens with the
 * quote that closed the last one. It never holds every candidate, so a pick is always possible.
 */
internal fun currentNoRepeatCycle(historyOldestFirst: List<Int>, candidates: Set<Int>): Set<Int> {
    val cycle = HashSet<Int>()
    var last: Int? = null
    for (id in historyOldestFirst) {
        if (id !in candidates) continue
        cycle += id
        last = id
        if (cycle.size == candidates.size) cycle.clear()
    }
    if (cycle.isEmpty() && last != null && candidates.size > 1) cycle += last
    return cycle
}

/**
 * The rows a re-seed writes. Each [fresh] row takes over the id, favorite state, saved date and
 * timestamp of the old bundled row it matches: by (text, author) first, then by text alone when
 * that text is unique among the old rows, for author names the new seed corrected. Old favorites
 * that left the seed are kept as they were, but with only their curated tags (older seeds carried
 * noisy source tags). Other old rows are dropped.
 */
internal fun mergeReseed(old: List<QuoteEntity>, fresh: List<QuoteEntity>): List<QuoteEntity> {
    val oldKeys = old.map { quoteMatchKey(it.text) to quoteMatchKey(it.author) }
    val byTextAndAuthor = old.indices.groupBy({ oldKeys[it] }, { old[it] })
    val byText = old.indices.groupBy({ oldKeys[it].first }, { old[it] }).filterValues { it.size == 1 }
    val matched = HashSet<Int>()
    fun claim(rows: List<QuoteEntity>?): QuoteEntity? =
        rows?.firstOrNull { it.id !in matched }?.also { matched += it.id }

    val freshKeys = fresh.map { quoteMatchKey(it.text) to quoteMatchKey(it.author) }
    val exact = freshKeys.map { claim(byTextAndAuthor[it]) }
    val now = System.currentTimeMillis().toString()
    val merged = fresh.mapIndexed { index, quote ->
        val match = exact[index] ?: claim(byText[freshKeys[index].first]) ?: return@mapIndexed quote
        quote.copy(
            id = match.id,
            isFavorite = match.isFavorite,
            savedDate = if (match.isFavorite) match.savedDate ?: now else null,
            timestamp = match.timestamp
        )
    }
    val keptFavorites = old.filter { it.isFavorite && it.id !in matched }.map { favorite ->
        favorite.copy(tags = favorite.tags.split(',').map { it.trim() }.filter { isVocabularyTag(it) }.joinToString(", "))
    }
    return merged + keptFavorites
}
