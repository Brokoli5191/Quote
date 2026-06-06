package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

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

    suspend fun preseedDatabase(context: Context) {
        try {
            val inputStream = context.resources.openRawResource(com.example.R.raw.quotes_seed)
            val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
            val quoteEntities = mutableListOf<QuoteEntity>()
            
            reader.useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) {
                        val parts = line.split("|")
                        if (parts.size >= 3) {
                            val category = parts[0].trim()
                            val text = parts[1].trim()
                            val author = parts[2].trim()
                            
                            val cleanText = text.replace("\\'", "'")
                            val cleanAuthor = author.replace("\\'", "'")
                            
                            val tags = category
                            val aboutAuthor = "Famous reflection on $category."
                            
                            quoteEntities.add(
                                QuoteEntity(
                                    text = cleanText,
                                    author = cleanAuthor,
                                    category = category,
                                    aboutAuthor = aboutAuthor,
                                    tags = tags,
                                    isFavorite = false
                                )
                            )
                        }
                    }
                }
            }
            
            // Set some favorites default
            if (quoteEntities.isNotEmpty()) {
                quoteEntities[0] = quoteEntities[0].copy(isFavorite = true, savedDate = "2 days ago")
                if (quoteEntities.size > 5) {
                    quoteEntities[5] = quoteEntities[5].copy(isFavorite = true, savedDate = "2 days ago")
                }
                if (quoteEntities.size > 40) {
                    quoteEntities[40] = quoteEntities[40].copy(isFavorite = true, savedDate = "1 day ago")
                }
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
        val savedString = if (isFav) "Saved just now" else null
        quoteDao.updateFavorite(id, isFav, savedString)
    }

    suspend fun insertQuote(quote: QuoteEntity): Long {
        return quoteDao.insertQuote(quote)
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

        // Choose randomly based on date hash to make it stable but random per date fallback
        val seed = date.hashCode().absoluteValue
        val selectedIdx = seed % all.size
        val selectedQuote = all[selectedIdx]

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

    private val Int.absoluteValue: Int get() = if (this < 0) -this else this
}
