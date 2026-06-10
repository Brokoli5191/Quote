package app.brokoli5191.quote.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class QuoteRepository(private val quoteDao: QuoteDao) {
    val allQuotes: Flow<List<QuoteEntity>> = quoteDao.getAllQuotes()
    val favorites: Flow<List<QuoteEntity>> = quoteDao.getFavoriteQuotes()
    val userAdded: Flow<List<QuoteEntity>> = quoteDao.getUserAddedQuotes()

    suspend fun getQuotesCount(): Int {
        return quoteDao.getQuotesCount()
    }

    suspend fun clearAllQuotes() {
        quoteDao.deleteAllQuotes()
    }

    private fun mapTagsToCategory(quoteText: String, author: String, tags: List<String>): String {
        val allLowerTags = tags.map { it.lowercase().trim() }
        val textLower = quoteText.lowercase()
        
        // Define our 15 specific categories requested by the user
        val userCategories = listOf(
            "Inspirational", "Life", "Humor", "Love", "Books", "Truth", "Reading", "Wisdom",
            "Happiness", "Writing", "Inspiration", "Philosophy", "Death", "Poetry", "Optimism"
        )
        
        // 1. Try to find an exact match in the tags first (after formatting)
        for (category in userCategories) {
            val categoryLower = category.lowercase()
            if (allLowerTags.contains(categoryLower)) {
                return category
            }
        }
        
        // 2. Try to match tags that contain the category word
        for (category in userCategories) {
            val categoryLower = category.lowercase()
            if (allLowerTags.any { it.contains(categoryLower) }) {
                return category
            }
        }
        
        // 3. Try to check the quote text for the category word
        for (category in userCategories) {
            val categoryLower = category.lowercase()
            if (textLower.contains(categoryLower)) {
                return category
            }
        }
        
        // 4. Default fallback: "Inspirational"
        return "Inspirational"
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
                
                val category = mapTagsToCategory(quoteText, author, tagsList)
                val cleanText = quoteText.replace("\"", "").replace("“", "").replace("”", "").trim()
                val cleanAuthor = author.replace("\"", "").replace("“", "").replace("”", "").trim()
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

    // Daily quote selector
    suspend fun getDailyQuote(date: String): QuoteEntity {
        // Check if there is already a selection for today
        val selection = quoteDao.getDailySelection(date)
        if (selection != null) {
            val q = quoteDao.getQuoteById(selection.quoteId)
            if (q != null) return q
        }

        // Otherwise select a random quote
        val all = quoteDao.getAllQuotesSync()
        if (all.isEmpty()) {
            // Seeding might be running, or we fall back to a hardcoded standard quote temporarily
            return QuoteEntity(
                id = 9999,
                text = "Be yourself; everyone else is already taken.",
                author = "Oscar Wilde",
                category = "Love",
                aboutAuthor = "Irish poet and playwright.",
                tags = "Identity"
            )
        }

        // Exclude previously used daily quotes to avoid repeats
        val usedIds = quoteDao.getAllDailySelectionIds().toSet()
        val available = all.filter { it.id !in usedIds }
        val pool = if (available.isEmpty()) all else available

        val seed = kotlin.math.abs(date.hashCode())
        val selectedIdx = seed % pool.size
        val selectedQuote = pool[selectedIdx]

        // Save selection for today
        quoteDao.insertDailySelection(DailySelectionEntity(date, selectedQuote.id))
        return selectedQuote
    }

    suspend fun cycleDailyQuote(date: String): QuoteEntity {
        val all = quoteDao.getAllQuotesSync()
        if (all.isEmpty()) {
            return QuoteEntity(
                id = 9999,
                text = "Be yourself; everyone else is already taken.",
                author = "Oscar Wilde",
                category = "Love",
                aboutAuthor = "Irish poet and playwright.",
                tags = "Identity"
            )
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
