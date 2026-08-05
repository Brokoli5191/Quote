package app.brokoli5191.quote.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.nio.charset.StandardCharsets
import java.nio.charset.Charset

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
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            

            
            // Highly optimized batch insert in a single transaction
            quoteDao.insertQuotes(quoteEntities)
        } catch (e: Exception) {
            e.printStackTrace()
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
        return quoteDao.insertQuote(quote)
    }

    suspend fun getAllQuotesSync(): List<QuoteEntity> {
        return quoteDao.getAllQuotesSync()
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
        quotes.forEach { incoming ->
            val existing = incoming.serverId?.let { quoteDao.getQuoteByServerId(it) }
            quoteDao.insertQuote(
                if (existing == null) incoming else incoming.copy(
                    id = existing.id,
                    isFavorite = existing.isFavorite,
                    savedDate = existing.savedDate,
                    timestamp = existing.timestamp
                )
            )
        }
        if (deletedIds.isNotEmpty()) quoteDao.deleteCommunityQuotes(deletedIds)
    }

    // Daily quote selector
    suspend fun getDailyQuote(date: String, sourceMode: String = QuoteSourceMode.ALL): QuoteEntity? {
        // Check if there is already a selection for today
        val selection = quoteDao.getDailySelection(date)
        if (selection != null) {
            val q = quoteDao.getQuoteById(selection.quoteId)
            if (q != null && q.matchesSourceMode(sourceMode)) return q
        }

        // Otherwise select a random quote
        val all = quoteDao.getAllQuotesSync().filter { it.matchesSourceMode(sourceMode) }
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
        quoteDao.insertDailySelection(DailySelectionEntity(date, selectedQuote.id))
        return selectedQuote
    }

    suspend fun cycleDailyQuote(date: String, sourceMode: String = QuoteSourceMode.ALL): QuoteEntity? {
        val all = quoteDao.getAllQuotesSync().filter { it.matchesSourceMode(sourceMode) }
        if (all.isEmpty()) {
            return null
        }

        val selection = quoteDao.getDailySelection(date)
        val currentQuoteId = selection?.quoteId ?: -1

        // Filter out the current one so it rotates to another
        val choices = all.filter { it.id != currentQuoteId }
        val finalChoices = if (choices.isEmpty()) all else choices

        // Choose a random quote from finalChoices
        val nextQuote = finalChoices.shuffled().first()

        quoteDao.insertDailySelection(DailySelectionEntity(date, nextQuote.id))
        return nextQuote
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
    return author.trim().trimEnd(',').trim()
}

internal fun normalizeQuoteText(value: String): String = repairMojibake(value)
    .replace(Regex("\\s+"), " ")
    .replace(Regex("([.!?,”])(?=“)"), "$1 ")
    .trim()
    .trim('"', '“', '”', '„')
    .trim()

private fun repairMojibake(value: String): String {
    if ("Ø£Ø¬" in value && "Ø­Ø¨" in value) {
        return "أجمل حب هو الذي نعثر عليه أثناء بحثنا عن شيء آخر"
    }
    val normalized = value
        .replace("â€™", "’").replace("â€˜", "‘")
        .replace("â€œ", "“").replace("â€�", "”")
        .replace("â€“", "–").replace("â€”", "—")
        .replace("â€¦", "…").replace("â€²", "′")
        .replace("â€", "—")
    if (normalized.none { it == 'Ã' || it == 'Â' || it == 'â' || it == 'Ø' || it == 'Ù' }) return normalized
    val repaired = String(normalized.toByteArray(Charset.forName("windows-1252")), StandardCharsets.UTF_8)
    return if ('\uFFFD' in repaired) normalized else repaired
}
