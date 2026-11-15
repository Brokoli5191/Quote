package app.brokoli5191.quote.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteDao {
    @Query("SELECT * FROM quotes ORDER BY timestamp DESC")
    fun getAllQuotes(): Flow<List<QuoteEntity>>

    // Newest save first. savedDate holds epoch millis as text; legacy "dd MMM yyyy" values and rows
    // without one sort last.
    @Query(
        "SELECT * FROM quotes WHERE isFavorite = 1 ORDER BY " +
            "CASE WHEN savedDate GLOB '[0-9]*' AND savedDate NOT GLOB '*[^0-9]*' THEN CAST(savedDate AS INTEGER) ELSE 0 END DESC, " +
            "timestamp DESC, id DESC"
    )
    fun getFavoriteQuotes(): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes WHERE isUserAdded = 1 ORDER BY timestamp DESC")
    fun getUserAddedQuotes(): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes WHERE id = :id LIMIT 1")
    suspend fun getQuoteById(id: Int): QuoteEntity?

    @Query("SELECT * FROM quotes WHERE serverId = :serverId LIMIT 1")
    suspend fun getQuoteByServerId(serverId: String): QuoteEntity?

    @Query("SELECT * FROM quotes WHERE serverId IN (:serverIds)")
    suspend fun getQuotesByServerIds(serverIds: List<String>): List<QuoteEntity>

    @Query("SELECT * FROM quotes WHERE isUserAdded = 1 AND submissionStatus = 'pending' AND submissionId IS NOT NULL")
    suspend fun getPendingSubmittedQuotes(): List<QuoteEntity>

    // Pending submissions, plus recently submitted ones whose state can still change on the server
    // (a rejected quote approved later, an approved one unpublished or republished).
    @Query(
        "SELECT * FROM quotes WHERE isUserAdded = 1 AND submissionId IS NOT NULL AND (" +
            "submissionStatus = 'pending' OR (COALESCE(submittedAt, 0) >= :since AND (" +
            "submissionStatus IN ('rejected', 'removed') OR " +
            "(submissionStatus = 'approved' AND linkedServerId IS NULL))))"
    )
    suspend fun getSubmissionsToRefresh(since: Long): List<QuoteEntity>

    @Query("SELECT * FROM quotes WHERE isUserAdded = 1 AND submissionId IS NOT NULL")
    suspend fun getSubmittedQuotes(): List<QuoteEntity>

    @Query("SELECT * FROM quotes WHERE isUserAdded = 1 AND submissionId IN (:submissionIds)")
    suspend fun getPersonalQuotesBySubmissionIds(submissionIds: List<String>): List<QuoteEntity>

    @Query("SELECT * FROM quotes WHERE isUserAdded = 1 AND linkedServerId IN (:serverIds)")
    suspend fun getPersonalQuotesByLinkedServerIds(serverIds: List<String>): List<QuoteEntity>

    @Query("SELECT * FROM quotes WHERE origin = 'community' AND isFavorite = 1 AND serverId IN (:serverIds)")
    suspend fun getFavoriteCommunityQuotes(serverIds: List<String>): List<QuoteEntity>

    @Query("SELECT COUNT(*) FROM quotes WHERE origin = 'community'")
    suspend fun getCommunityQuotesCount(): Int

    // Candidate ids for the daily quote, filtered in SQL so the whole table is not loaded.
    // Mirrors matchesSourceMode, isAvailableIn and the anonymous filter.
    @Query(
        "SELECT id FROM quotes WHERE origin IN (:origins) " +
            "AND (:showAnonymous = 1 OR isAnonymous = 0) " +
            "AND ((:language = 'de' AND (language = 'de' OR (textDe IS NOT NULL AND trim(textDe) != ''))) " +
            "OR (:language != 'de' AND (language != 'de' OR (textEn IS NOT NULL AND trim(textEn) != '')))) " +
            "ORDER BY id"
    )
    suspend fun getDailyCandidateIds(origins: List<String>, language: String, showAnonymous: Boolean): List<Int>

    // Community quotes in any language, for Community-only mode when none has a version in the app language.
    @Query("SELECT id FROM quotes WHERE origin = 'community' AND (:showAnonymous = 1 OR isAnonymous = 0) ORDER BY id")
    suspend fun getCommunityCandidateIdsAnyLanguage(showAnonymous: Boolean): List<Int>

    @Query("SELECT * FROM quotes WHERE id IN (:ids)")
    suspend fun getQuotesByIds(ids: List<Int>): List<QuoteEntity>

    @Query("SELECT * FROM quotes WHERE isUserAdded = 0 AND origin != 'community'")
    suspend fun getBundledQuotes(): List<QuoteEntity>

    @Query("SELECT * FROM quotes WHERE category = :category ORDER BY timestamp DESC")
    fun getQuotesByCategory(category: String): Flow<List<QuoteEntity>>

    // Returns the rows written: 0 when the row is gone.
    @Query("UPDATE quotes SET isFavorite = :isFav, savedDate = :savedDate WHERE id = :id")
    suspend fun updateFavorite(id: Int, isFav: Boolean, savedDate: String?): Int

    @Query("UPDATE quotes SET text = :text, author = :author, textDe = :textDe, textEn = :textEn WHERE id = :id")
    suspend fun updateText(id: Int, text: String, author: String, textDe: String?, textEn: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuote(quote: QuoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuotes(quotes: List<QuoteEntity>)

    @Query("DELETE FROM quotes")
    suspend fun deleteAllQuotes()

    @Query("DELETE FROM quotes WHERE isUserAdded = 0 AND origin != 'community'")
    suspend fun deleteNonUserQuotes()

    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun deleteQuoteById(id: Int)

    // Swaps the bundled set in one transaction so an interrupted re-seed never leaves it half empty.
    @Transaction
    suspend fun replaceBundledQuotes(quotes: List<QuoteEntity>) {
        deleteNonUserQuotes()
        insertQuotes(quotes)
    }

    @Query("UPDATE quotes SET submissionStatus = :status, submissionId = :submissionId, submittedAt = :submittedAt WHERE id = :id AND isUserAdded = 1")
    suspend fun updateSubmission(id: Int, status: String, submissionId: String?, submittedAt: Long?)

    @Query("UPDATE quotes SET submissionStatus = :status WHERE id = :id AND isUserAdded = 1")
    suspend fun updateSubmissionStatus(id: Int, status: String)

    @Query(
        "UPDATE quotes SET submissionStatus = :status, reviewNote = :reviewNote, linkedServerId = :linkedServerId " +
            "WHERE id = :id AND isUserAdded = 1"
    )
    suspend fun updateSubmissionState(id: Int, status: String, reviewNote: String?, linkedServerId: String?)

    @Query(
        "UPDATE quotes SET submissionStatus = 'not_submitted', submissionId = NULL, submittedAt = NULL, " +
            "reviewNote = NULL, linkedServerId = NULL WHERE id = :id AND isUserAdded = 1"
    )
    suspend fun clearSubmission(id: Int)

    // Conditional writes for a state read before a network call: they apply only while the row still
    // has the submission id and status that were read (IS also matches NULL). Return the rows written.
    @Query(
        "UPDATE quotes SET submissionStatus = :status, reviewNote = :reviewNote, linkedServerId = :linkedServerId " +
            "WHERE id = :id AND isUserAdded = 1 AND submissionId IS :expectedSubmissionId " +
            "AND submissionStatus = :expectedStatus"
    )
    suspend fun updateSubmissionStateIf(
        id: Int,
        expectedSubmissionId: String?,
        expectedStatus: String,
        status: String,
        reviewNote: String?,
        linkedServerId: String?
    ): Int

    @Query(
        "UPDATE quotes SET submissionStatus = 'not_submitted', submissionId = NULL, submittedAt = NULL, " +
            "reviewNote = NULL, linkedServerId = NULL WHERE id = :id AND isUserAdded = 1 " +
            "AND submissionId IS :expectedSubmissionId AND submissionStatus = :expectedStatus"
    )
    suspend fun clearSubmissionIf(id: Int, expectedSubmissionId: String?, expectedStatus: String): Int

    @Query("DELETE FROM quotes WHERE origin = 'community' AND serverId IN (:serverIds)")
    suspend fun deleteCommunityQuotes(serverIds: List<String>)

    // Daily selections. Keys are "<yyyy-MM-dd>_<language>_<anonymous 0/1>_<source mode>"; a pick that
    // was replaced under its key stays in the history as "<key>#<quote id>". REPLACE writes a new
    // row, so rowid order is the order in which picks were made.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailySelection(selection: DailySelectionEntity)

    // Stores [quoteId] as the pick under [key]. A different quote stored there before stays in the
    // no-repeat history: its row is renamed to "<key>#<quote id>", which keeps its rowid and so its
    // place in the history.
    @Transaction
    suspend fun storeDailySelection(key: String, quoteId: Int) {
        retireDailySelection(key, quoteId)
        insertDailySelection(DailySelectionEntity(key, quoteId))
    }

    @Query("UPDATE OR REPLACE daily_selections SET date = date || '#' || quoteId WHERE date = :key AND quoteId != :quoteId")
    suspend fun retireDailySelection(key: String, quoteId: Int)

    // The current picks of one day (keyPrefix = "<yyyy-MM-dd>_"), most recent first.
    @Query(
        "SELECT * FROM daily_selections WHERE substr(date, 1, length(:keyPrefix)) = :keyPrefix " +
            "AND instr(date, '#') = 0 ORDER BY rowid DESC"
    )
    suspend fun getDailySelectionsForDay(keyPrefix: String): List<DailySelectionEntity>

    // The current picks of every day, most recent first.
    @Query("SELECT * FROM daily_selections WHERE instr(date, '#') = 0 ORDER BY rowid DESC")
    suspend fun getCurrentDailySelections(): List<DailySelectionEntity>

    // Every quote ever picked, replaced picks included, oldest first: the no-repeat history.
    @Query("SELECT quoteId FROM daily_selections ORDER BY rowid")
    suspend fun getDailySelectionIdsOldestFirst(): List<Int>

    // Static synchronous helpers
    @Query("SELECT * FROM quotes")
    suspend fun getAllQuotesSync(): List<QuoteEntity>

    @Query("SELECT COUNT(*) FROM quotes")
    suspend fun getQuotesCount(): Int
}
