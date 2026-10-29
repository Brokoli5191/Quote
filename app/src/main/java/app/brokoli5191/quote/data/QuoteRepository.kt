package app.brokoli5191.quote.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
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
    /** The status endpoint answered 404 (withdrawn or deleted); [status] is then removed. */
    val notFound: Boolean = false,
    /** The answer came from a pre-v2 backend, which never reports "removed". */
    val legacy: Boolean = false
)

/** Day key for daily selections. Fixed locale so the key never contains non-ASCII digits. */
fun dailyDateKey(date: Date = Date()): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

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
     * Re-seeds the bundled quotes without destroying user data: user-added and community rows
     * are left untouched, favorites on seed rows are carried over by a loose (text, author) key
     * before the single batch insert, and favorited quotes that left the seed are kept as they were.
     */
    suspend fun reseedPreservingFavorites(context: Context) {
        val oldFavorites = quoteDao.getAllQuotesSync()
            .filter { it.isFavorite && !it.isUserAdded && it.origin != QuoteOrigin.COMMUNITY }
        val byTextAndAuthor = oldFavorites.associateBy { quoteMatchKey(it.text) to quoteMatchKey(it.author) }
        // Fallback for author names whose spelling was corrected in the new seed.
        val byText = oldFavorites.groupBy { quoteMatchKey(it.text) }
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }
        val fresh = try {
            loadBundledQuotes(context)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }
        val matched = HashSet<Int>()
        val seeded = fresh.map { quote ->
            val textKey = quoteMatchKey(quote.text)
            val favorite = (byTextAndAuthor[textKey to quoteMatchKey(quote.author)] ?: byText[textKey])
                ?.takeIf { it.id !in matched }
                ?: return@map quote
            matched += favorite.id
            quote.copy(isFavorite = true, savedDate = favorite.savedDate ?: System.currentTimeMillis().toString())
        }
        // Kept favorites drop the noisy source tags of older seeds and keep only curated ones.
        val keptFavorites = oldFavorites.filter { it.id !in matched }.map { favorite ->
            val tags = favorite.tags.split(',').map { it.trim() }.filter { isVocabularyTag(it) }
            favorite.copy(id = 0, tags = tags.joinToString(", "))
        }
        quoteDao.replaceBundledQuotes(seeded + keptFavorites)
    }

    fun getQuotesByCategory(category: String): Flow<List<QuoteEntity>> {
        return quoteDao.getQuotesByCategory(category)
    }

    suspend fun getQuoteById(id: Int): QuoteEntity? {
        return quoteDao.getQuoteById(id)
    }

    suspend fun toggleFavorite(id: Int, isFav: Boolean) {
        val savedString = if (isFav) System.currentTimeMillis().toString() else null
        quoteDao.updateFavorite(id, isFav, savedString)
    }

    /** Marks a quote as favorite with a given saved date (e.g. from a backup). */
    suspend fun setFavorite(id: Int, savedDate: String?) {
        quoteDao.updateFavorite(id, true, savedDate ?: System.currentTimeMillis().toString())
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
                        quoteDao.updateSubmissionState(
                            personal.id,
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
                    quoteDao.updateSubmissionState(
                        personal.id,
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

    // Daily quote selector
    suspend fun getDailyQuote(
        date: String,
        sourceMode: String = QuoteSourceMode.ALL,
        language: String = QuoteLanguage.ENGLISH,
        showAnonymous: Boolean = false
    ): QuoteEntity? = getDailyQuoteRaw(date, sourceMode, language, showAnonymous)?.localized(language)

    /** Same as [getDailyQuote] but returns the stored row, not a localized display copy. */
    suspend fun getDailyQuoteRaw(
        date: String,
        sourceMode: String = QuoteSourceMode.ALL,
        language: String = QuoteLanguage.ENGLISH,
        showAnonymous: Boolean = false
    ): QuoteEntity? {
        val selectionKey = "${date}_${language}_${if (showAnonymous) 1 else 0}_${sourceMode}"
        // Check if there is already a selection for today
        val selection = quoteDao.getDailySelection(selectionKey)
        if (selection != null) {
            val q = quoteDao.getQuoteById(selection.quoteId)
            if (q != null && q.matchesSourceMode(sourceMode) && q.isAvailableIn(language) && (showAnonymous || !q.isAnonymous)) {
                return q
            }
        }

        // Otherwise select a random quote
        val all = candidateIds(sourceMode, language, showAnonymous)
        if (all.isEmpty()) {
            return null
        }

        // Exclude previously used daily quotes to avoid repeats
        val usedIds = quoteDao.getAllDailySelectionIds().toHashSet()
        val available = all.filter { it !in usedIds }
        val pool = if (available.isEmpty()) all else available

        val selectedId = pool[dailyQuoteIndex(installationSeed, date, pool.size)]
        val selectedQuote = quoteDao.getQuoteById(selectedId) ?: return null

        // Save selection for today
        quoteDao.insertDailySelection(DailySelectionEntity(selectionKey, selectedQuote.id))
        return selectedQuote
    }

    suspend fun cycleDailyQuote(
        date: String,
        sourceMode: String = QuoteSourceMode.ALL,
        language: String = QuoteLanguage.ENGLISH,
        showAnonymous: Boolean = false
    ): QuoteEntity? {
        val selectionKey = "${date}_${language}_${if (showAnonymous) 1 else 0}_${sourceMode}"
        val all = candidateIds(sourceMode, language, showAnonymous)
        if (all.isEmpty()) {
            return null
        }

        val selection = quoteDao.getDailySelection(selectionKey)
        val currentQuoteId = selection?.quoteId ?: -1

        // Filter out the current one so it rotates to another
        val choices = all.filter { it != currentQuoteId }
        val finalChoices = if (choices.isEmpty()) all else choices

        val nextQuote = quoteDao.getQuoteById(finalChoices.random()) ?: return null

        quoteDao.insertDailySelection(DailySelectionEntity(selectionKey, nextQuote.id))
        return nextQuote.localized(language)
    }

    private suspend fun candidateIds(sourceMode: String, language: String, showAnonymous: Boolean): List<Int> {
        val origins = when (sourceMode) {
            QuoteSourceMode.CURATED -> listOf(QuoteOrigin.BUNDLED)
            QuoteSourceMode.COMMUNITY -> listOf(QuoteOrigin.COMMUNITY)
            else -> listOf(QuoteOrigin.BUNDLED, QuoteOrigin.COMMUNITY)
        }
        return quoteDao.getDailyCandidateIds(origins, language, showAnonymous)
    }

    companion object {
        /** How long rejected/removed/unlinked approved submissions keep being polled. */
        const val SUBMISSION_REFRESH_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
    }
}

internal fun dailyQuoteIndex(installationSeed: Long, date: String, poolSize: Int): Int {
    require(poolSize > 0)
    return kotlin.random.Random(installationSeed xor date.hashCode().toLong()).nextInt(poolSize)
}
