package app.brokoli5191.quote.data

/**
 * A review decision that arrived during a status refresh, used for the "submission update"
 * notification. [newStatus] is one of the SubmissionStatus values (approved, rejected, removed).
 */
data class SubmissionUpdate(
    val quoteId: Int,
    val quoteText: String,
    val oldStatus: String,
    val newStatus: String,
    val reviewNote: String?
)
