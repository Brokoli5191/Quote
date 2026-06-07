package app.brokoli5191.quote.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quotes")
data class QuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val author: String,
    val category: String, // Stoicism, Resilience, Joy, Focus, Love, Custom
    val isFavorite: Boolean = false,
    val isUserAdded: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val aboutAuthor: String = "",
    val tags: String = "", // comma-separated strings inside
    val savedDate: String? = null // For showing "Saved 2 days ago" etc.
)

@Entity(tableName = "daily_selections")
data class DailySelectionEntity(
    @PrimaryKey val date: String, // e.g., "2026-06-05"
    val quoteId: Int
)
