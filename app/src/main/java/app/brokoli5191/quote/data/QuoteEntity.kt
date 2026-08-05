package app.brokoli5191.quote.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quotes",
    indices = [Index(value = ["serverId"], unique = true)]
)
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
    val savedDate: String? = null, // For showing "Saved 2 days ago" etc.
    val submissionStatus: String = QuoteSubmissionStatus.NOT_SUBMITTED,
    val submissionId: String? = null,
    val submittedAt: Long? = null,
    val origin: String = QuoteOrigin.BUNDLED,
    val serverId: String? = null,
    val serverRevision: Long? = null
)

object QuoteSubmissionStatus {
    const val NOT_SUBMITTED = "not_submitted"
    const val PENDING = "pending"
    const val APPROVED = "approved"
    const val REJECTED = "rejected"
}

object QuoteOrigin {
    const val BUNDLED = "bundled"
    const val COMMUNITY = "community"
    const val PERSONAL = "personal"
}

object QuoteSourceMode {
    const val ALL = "all"
    const val CURATED = "curated"
    const val COMMUNITY = "community"
}

fun QuoteEntity.matchesSourceMode(mode: String): Boolean = when (mode) {
    QuoteSourceMode.CURATED -> origin == QuoteOrigin.BUNDLED
    QuoteSourceMode.COMMUNITY -> origin == QuoteOrigin.COMMUNITY
    else -> origin == QuoteOrigin.BUNDLED || origin == QuoteOrigin.COMMUNITY
}

@Entity(tableName = "daily_selections")
data class DailySelectionEntity(
    @PrimaryKey val date: String, // e.g., "2026-06-05"
    val quoteId: Int
)
