package app.brokoli5191.quote.utils

import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteRepository

data class CategoryVerificationResult(
    val totalQuotesCount: Int,
    val categoryCounts: Map<String, Int>,
    val emptyCategories: List<String>,
    val isValid: Boolean
)

object CategoryQuoteVerifier {
    val categoryList = listOf(
        "Inspirational", "Life", "Humor", "Love", "Books", "Truth", "Reading", "Wisdom",
        "Happiness", "Writing", "Inspiration", "Philosophy", "Death", "Poetry", "Optimism"
    )

    suspend fun verify(repository: QuoteRepository): CategoryVerificationResult {
        // Retrieve all quotes lists directly from DB synchronously to avoid flow subscription latencies
        val quotesList = repository.getAllQuotesSync()
        val totalCount = quotesList.size
        
        val counts = categoryList.associateWith { cat ->
            quotesList.count { it.category.equals(cat, ignoreCase = true) }
        }.toSortedMap()
        
        val empty = counts.filter { it.value == 0 }.keys.toList()
        
        return CategoryVerificationResult(
            totalQuotesCount = totalCount,
            categoryCounts = counts,
            emptyCategories = empty,
            isValid = empty.isEmpty() && totalCount >= 1000
        )
    }
}
