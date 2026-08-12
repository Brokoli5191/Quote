package app.brokoli5191.quote.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteDao {
    @Query("SELECT * FROM quotes ORDER BY timestamp DESC")
    fun getAllQuotes(): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes WHERE isFavorite = 1 ORDER BY timestamp DESC")
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

    @Query("SELECT * FROM quotes WHERE category = :category ORDER BY timestamp DESC")
    fun getQuotesByCategory(category: String): Flow<List<QuoteEntity>>

    @Query("UPDATE quotes SET isFavorite = :isFav, savedDate = :savedDate WHERE id = :id")
    suspend fun updateFavorite(id: Int, isFav: Boolean, savedDate: String?)

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

    @Query("UPDATE quotes SET submissionStatus = :status, submissionId = :submissionId, submittedAt = :submittedAt WHERE id = :id AND isUserAdded = 1")
    suspend fun updateSubmission(id: Int, status: String, submissionId: String?, submittedAt: Long?)

    @Query("UPDATE quotes SET submissionStatus = :status WHERE id = :id AND isUserAdded = 1")
    suspend fun updateSubmissionStatus(id: Int, status: String)

    @Query("DELETE FROM quotes WHERE origin = 'community' AND serverId IN (:serverIds)")
    suspend fun deleteCommunityQuotes(serverIds: List<String>)

    // Daily selections
    @Query("SELECT * FROM daily_selections WHERE date = :date LIMIT 1")
    suspend fun getDailySelection(date: String): DailySelectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailySelection(selection: DailySelectionEntity)

    @Query("SELECT quoteId FROM daily_selections")
    suspend fun getAllDailySelectionIds(): List<Int>

    // Static synchronous helpers
    @Query("SELECT * FROM quotes")
    suspend fun getAllQuotesSync(): List<QuoteEntity>

    @Query("SELECT COUNT(*) FROM quotes")
    suspend fun getQuotesCount(): Int
}
