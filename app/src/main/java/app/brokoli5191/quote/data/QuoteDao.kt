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

    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun deleteQuoteById(id: Int)

    // Daily selections
    @Query("SELECT * FROM daily_selections WHERE date = :date LIMIT 1")
    suspend fun getDailySelection(date: String): DailySelectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailySelection(selection: DailySelectionEntity)

    // Static synchronous helpers
    @Query("SELECT * FROM quotes")
    suspend fun getAllQuotesSync(): List<QuoteEntity>

    @Query("SELECT COUNT(*) FROM quotes")
    suspend fun getQuotesCount(): Int
}
