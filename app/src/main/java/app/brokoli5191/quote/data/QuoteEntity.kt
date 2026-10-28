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
    val serverRevision: Long? = null,
    val textDe: String? = null,
    val language: String = QuoteLanguage.ENGLISH,
    val isAnonymous: Boolean = false,
    val reviewNote: String? = null, // Reviewer note from the submission status endpoint
    val linkedServerId: String? = null, // Personal rows: id of the published community copy
    val textEn: String? = null // English translation of a German-original quote
)

object QuoteSubmissionStatus {
    const val NOT_SUBMITTED = "not_submitted"
    const val PENDING = "pending"
    const val APPROVED = "approved"
    const val REJECTED = "rejected"
    const val REMOVED = "removed" // Was approved, but is no longer published (or the submission is gone)
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

object QuoteLanguage {
    const val ENGLISH = "en"
    const val GERMAN = "de"
}

fun QuoteEntity.isAvailableIn(language: String): Boolean = when (language) {
    QuoteLanguage.GERMAN -> this.language == QuoteLanguage.GERMAN || !textDe.isNullOrBlank()
    else -> this.language != QuoteLanguage.GERMAN || !textEn.isNullOrBlank()
}

/** True when the UI should show "Unknown" and a question-mark avatar instead of a name. */
val QuoteEntity.hasUnknownAuthor: Boolean
    get() = isAnonymous || author.isBlank() || author == "Unknown" || author == "Unbekannt"

// The English / German versions of a quote. Works on raw rows and on localized() copies,
// which keep both versions in textEn / textDe.
private fun QuoteEntity.englishVersion(): String? =
    textEn?.takeIf { it.isNotBlank() } ?: text.takeIf { language != QuoteLanguage.GERMAN }

private fun QuoteEntity.germanVersion(): String? =
    textDe?.takeIf { it.isNotBlank() } ?: text.takeIf { language == QuoteLanguage.GERMAN }

private fun QuoteEntity.displayedLanguage(): String = when (text) {
    englishVersion() -> QuoteLanguage.ENGLISH
    germanVersion() -> QuoteLanguage.GERMAN
    else -> language
}

/**
 * Display copy for [language]: `text` is the translation when one exists. The copy carries both
 * versions so [alternateText] still works on it. Display only: never write it back or submit it.
 */
fun QuoteEntity.localized(language: String): QuoteEntity {
    val english = englishVersion()
    val german = germanVersion()
    val display = (if (language == QuoteLanguage.GERMAN) german else english) ?: text
    val displayAuthor = if (language == QuoteLanguage.GERMAN && author == "Unknown") "Unbekannt" else author
    if (display == text && displayAuthor == author) return this
    return copy(text = display, author = displayAuthor, textEn = english, textDe = german)
}

/** The language of the other version of the displayed text ("en"/"de"), or null without a translation. */
fun QuoteEntity.alternateLanguage(): String? {
    val other = if (displayedLanguage() == QuoteLanguage.GERMAN) QuoteLanguage.ENGLISH else QuoteLanguage.GERMAN
    val otherText = if (other == QuoteLanguage.GERMAN) germanVersion() else englishVersion()
    return other.takeIf { !otherText.isNullOrBlank() && otherText != text }
}

/** The displayed quote in the other language, or null without a translation. */
fun QuoteEntity.alternateText(): String? = when (alternateLanguage()) {
    QuoteLanguage.GERMAN -> germanVersion()
    QuoteLanguage.ENGLISH -> englishVersion()
    else -> null
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
