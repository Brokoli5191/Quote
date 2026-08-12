package app.brokoli5191.quote.data

import android.content.Context

class CommunitySyncManager(
    context: Context,
    private val repository: QuoteRepository,
    private val communityClient: CommunityQuoteClient = CommunityQuoteClient(),
    private val submissionClient: QuoteSubmissionClient = QuoteSubmissionClient()
) {
    private val prefs = context.applicationContext.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)

    suspend fun sync(): Boolean {
        var revision = prefs.getLong("community_quote_revision", 0L)
        do {
            val page = communityClient.updates(revision).getOrElse { return false }
            if (page.hasMore && page.revision <= revision) return false
            repository.applyCommunityUpdates(page.quotes, page.deletedIds)
            revision = page.revision
            prefs.edit().putLong("community_quote_revision", revision).apply()
        } while (page.hasMore)
        refreshSubmissionStatuses()
        return true
    }

    suspend fun refreshSubmissionStatuses() {
        val installationId = prefs.getString("submission_installation_id", null) ?: return
        repository.getPendingSubmittedQuotes()
            .filter { !it.submissionId.isNullOrBlank() }
            .forEach { quote ->
                val status = submissionClient.status(quote.submissionId!!, installationId).getOrNull()
                if (status == QuoteSubmissionStatus.APPROVED || status == QuoteSubmissionStatus.REJECTED) {
                    repository.updateSubmissionStatus(quote.id, status)
                }
            }
    }
}
