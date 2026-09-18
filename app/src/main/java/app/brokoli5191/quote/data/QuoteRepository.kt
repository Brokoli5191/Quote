package app.brokoli5191.quote.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.nio.charset.StandardCharsets
import java.nio.charset.Charset

private val WINDOWS_1252: Charset = Charset.forName("windows-1252")
private val WHITESPACE_REGEX = Regex("\\s+")
private val DIALOGUE_SPACING_REGEX = Regex("([.!?,”])(?=“)")

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

    suspend fun clearAllQuotes() {
        quoteDao.deleteAllQuotes()
    }

    suspend fun preseedDatabase(context: Context) {
        try {
            val inputStream = context.resources.openRawResource(app.brokoli5191.quote.R.raw.quotes_seed)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonArray = org.json.JSONArray(jsonString)
            val quoteEntities = mutableListOf<QuoteEntity>()
            val germanTranslations = loadGermanTranslations(context)
            
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val quoteText = item.getString("quote")
                val author = item.getString("author")
                
                val tagsArray = item.getJSONArray("tags")
                val tagsList = mutableListOf<String>()
                for (j in 0 until tagsArray.length()) {
                    tagsList.add(tagsArray.getString(j))
                }
                
                val category = CategoryMapper.map(tagsList, quoteText)
                val cleanText = normalizeQuoteText(quoteText)
                val cleanAuthor = normalizeAuthorName(author)
                val tagsStr = tagsList.joinToString(", ")

                quoteEntities.add(
                    QuoteEntity(
                        text = cleanText,
                        author = cleanAuthor,
                        category = category,
                        aboutAuthor = "",
                        tags = tagsStr,
                        isFavorite = false,
                        timestamp = System.currentTimeMillis(),
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
                        tags = "reflection, anonymous, original",
                        isAnonymous = true,
                        timestamp = System.currentTimeMillis() + i
                    )
                )
            }
            

            
            // Highly optimized batch insert in a single transaction
            quoteDao.insertQuotes(quoteEntities)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
     * Re-seeds the bundled quotes without destroying user data: user-added rows
     * are left untouched, and favorites on seed rows are restored by matching
     * (text, author) after re-insert (row ids change on re-seed).
     */
    suspend fun reseedPreservingFavorites(context: Context) {
        val oldFavKeys = quoteDao.getAllQuotesSync()
            .filter { it.isFavorite && !it.isUserAdded }
            .map { normalizeQuoteText(it.text) to normalizeAuthorName(it.author) }
            .toSet()
        quoteDao.deleteNonUserQuotes()
        preseedDatabase(context)
        if (oldFavKeys.isNotEmpty()) {
            val now = System.currentTimeMillis().toString()
            quoteDao.getAllQuotesSync().forEach { q ->
                if (!q.isUserAdded && (normalizeQuoteText(q.text) to normalizeAuthorName(q.author)) in oldFavKeys) {
                    quoteDao.updateFavorite(q.id, true, now)
                }
            }
        }
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

    suspend fun insertQuote(quote: QuoteEntity): Long {
        return quoteDao.insertQuote(quote.normalizedText())
    }

    suspend fun repairStoredText() {
        val repaired = quoteDao.getAllQuotesSync().mapNotNull { current ->
            current.normalizedText().takeIf { normalized ->
                normalized != current
            }
        }
        if (repaired.isNotEmpty()) quoteDao.insertQuotes(repaired)
    }

    suspend fun getAllQuotesSync(): List<QuoteEntity> {
        return quoteDao.getAllQuotesSync()
    }

    suspend fun getPendingSubmittedQuotes(): List<QuoteEntity> {
        return quoteDao.getPendingSubmittedQuotes()
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

    suspend fun updateSubmissionStatus(id: Int, status: String) {
        quoteDao.updateSubmissionStatus(id, status)
    }

    suspend fun applyCommunityUpdates(quotes: List<QuoteEntity>, deletedIds: List<String>) {
        val normalizedQuotes = quotes.map { it.normalizedText() }
        val existingByServerId = normalizedQuotes
            .mapNotNull { it.serverId }
            .distinct()
            .chunked(400)
            .flatMap { quoteDao.getQuotesByServerIds(it) }
            .associateBy { it.serverId }

        if (normalizedQuotes.isNotEmpty()) {
            quoteDao.insertQuotes(normalizedQuotes.map { incoming ->
                val existing = incoming.serverId?.let(existingByServerId::get)
                if (existing == null) incoming else incoming.copy(
                    id = existing.id,
                    isFavorite = existing.isFavorite,
                    savedDate = existing.savedDate,
                    timestamp = existing.timestamp
                )
            })
        }
        if (deletedIds.isNotEmpty()) quoteDao.deleteCommunityQuotes(deletedIds)
    }

    // Daily quote selector
    suspend fun getDailyQuote(
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
                return q.localized(language)
            }
        }

        // Otherwise select a random quote
        val all = quoteDao.getAllQuotesSync().filter {
            it.matchesSourceMode(sourceMode) && it.isAvailableIn(language) && (showAnonymous || !it.isAnonymous)
        }
        if (all.isEmpty()) {
            return null
        }

        // Exclude previously used daily quotes to avoid repeats
        val usedIds = quoteDao.getAllDailySelectionIds().toSet()
        val available = all.filter { it.id !in usedIds }
        val pool = if (available.isEmpty()) all else available

        val selectedIdx = dailyQuoteIndex(installationSeed, date, pool.size)
        val selectedQuote = pool[selectedIdx]

        // Save selection for today
        quoteDao.insertDailySelection(DailySelectionEntity(selectionKey, selectedQuote.id))
        return selectedQuote.localized(language)
    }

    suspend fun cycleDailyQuote(
        date: String,
        sourceMode: String = QuoteSourceMode.ALL,
        language: String = QuoteLanguage.ENGLISH,
        showAnonymous: Boolean = false
    ): QuoteEntity? {
        val selectionKey = "${date}_${language}_${if (showAnonymous) 1 else 0}_${sourceMode}"
        val all = quoteDao.getAllQuotesSync().filter {
            it.matchesSourceMode(sourceMode) && it.isAvailableIn(language) && (showAnonymous || !it.isAnonymous)
        }
        if (all.isEmpty()) {
            return null
        }

        val selection = quoteDao.getDailySelection(selectionKey)
        val currentQuoteId = selection?.quoteId ?: -1

        // Filter out the current one so it rotates to another
        val choices = all.filter { it.id != currentQuoteId }
        val finalChoices = if (choices.isEmpty()) all else choices

        // Choose a random quote from finalChoices
        val nextQuote = finalChoices.shuffled().first()

        quoteDao.insertDailySelection(DailySelectionEntity(selectionKey, nextQuote.id))
        return nextQuote.localized(language)
    }

}

internal fun dailyQuoteIndex(installationSeed: Long, date: String, poolSize: Int): Int {
    require(poolSize > 0)
    return kotlin.random.Random(installationSeed xor date.hashCode().toLong()).nextInt(poolSize)
}

internal fun normalizeAuthorName(value: String): String {
    var author = value.replace("\"", "").replace("“", "").replace("”", "").trim()
    author = when (author) {
        "Ø£Ø­ÙØ§Ù… Ù…Ø³ØªØºØ§Ù†Ù…ÙŠ" -> "Ahlam Mosteghanemi"
        "Ø£Ø­Ù…Ø¯ Ø®Ø§ÙØ¯ ØªÙˆÙ�ÙŠÙ‚" -> "Ahmed Khaled Towfik"
        else -> author
    }
    author = repairMojibake(author)
    return author
        .replace('\uFFFD'.toString(), "")
        .replace('\u00A0', ' ')
        .trim()
        .trimEnd(',')
        .trim()
}

internal fun normalizeQuoteText(value: String): String = repairMojibake(value)
    .replace('\uFFFD'.toString(), "")
    .replace('\u00A0', ' ')
    .replace(WHITESPACE_REGEX, " ")
    .replace(DIALOGUE_SPACING_REGEX, "$1 ")
    .trim()
    .trim('"', '“', '”', '„')
    .trim()

private fun repairMojibake(value: String): String {
    if ("Ø£Ø¬" in value && "Ø­Ø¨" in value) {
        return "أجمل حب هو الذي نعثر عليه أثناء بحثنا عن شيء آخر"
    }
    var repaired = value
    repeat(2) {
        repaired = replaceCommonMojibake(repaired)
        if (repaired.none(::isMojibakeMarker)) return repaired

        val decoded = String(
            repaired.toByteArray(WINDOWS_1252),
            StandardCharsets.UTF_8
        )
        if ('\uFFFD' in decoded || mojibakeScore(decoded) >= mojibakeScore(repaired)) return repaired
        repaired = decoded
    }
    return replaceCommonMojibake(repaired)
}

private fun replaceCommonMojibake(value: String): String = value
    .replace("â€™", "’").replace("â€˜", "‘")
    .replace("â€œ", "“").replace("â€�", "”")
    .replace("â€“", "–").replace("â€”", "—")
    .replace("â€¦", "…").replace("â€²", "′")
    .replace("â€\"", "—")
    .replace("â€", "—")
    .replace("Â\u00A0", " ")
    .replace("Â ", " ")

private fun isMojibakeMarker(char: Char): Boolean =
    char == 'Ã' || char == 'Â' || char == 'â' || char == 'Ø' || char == 'Ù'

private fun mojibakeScore(value: String): Int = value.count(::isMojibakeMarker)

private fun QuoteEntity.normalizedText(): QuoteEntity = copy(
    text = normalizeQuoteText(text),
    author = normalizeAuthorName(author)
)
