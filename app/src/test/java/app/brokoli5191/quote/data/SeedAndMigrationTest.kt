package app.brokoli5191.quote.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.brokoli5191.quote.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SeedAndMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun rawArray(id: Int) = JSONArray(context.resources.openRawResource(id).bufferedReader().use { it.readText() })

    @Test
    fun seedText_isAlreadyNormalized() {
        // The seed was cleaned by tools/quote_seed_rules.mjs; the app's normalization must agree.
        val seed = rawArray(R.raw.quotes_seed)
        val changed = (0 until seed.length())
            .map { seed.getJSONObject(it).getString("quote") }
            .filter { normalizeQuoteText(it) != it }
        assertEquals(emptyList<String>(), changed)
    }

    @Test
    fun germanTranslations_keepTheirQuotationMarks() {
        // German „…“ closes with “; normalization must not put a space before it. The only allowed
        // change is dropping a pair that wraps the whole text, as for the English seed.
        val translations = listOf(R.raw.quotes_de, R.raw.anonymous_quotes).flatMap { id ->
            val array = rawArray(id)
            (0 until array.length()).map { array.getJSONObject(it).getString("quote_de") }
        }
        val changed = translations.filter {
            val normalized = normalizeQuoteText(it)
            normalized != it && normalized != it.substring(1, it.length - 1).trim()
        }
        assertEquals(emptyList<String>(), changed)
    }

    @Test
    fun everySeedQuote_hasGermanTranslation() {
        val seed = rawArray(R.raw.quotes_seed)
        val german = rawArray(R.raw.quotes_de)
        val translated = (0 until german.length()).map { normalizeQuoteText(german.getJSONObject(it).getString("quote")) }.toSet()
        val missing = (0 until seed.length())
            .map { normalizeQuoteText(seed.getJSONObject(it).getString("quote")) }
            .filter { it !in translated }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun everySeedQuote_hasValidCategoryAndCuratedTags() {
        val seed = rawArray(R.raw.quotes_seed)
        val problems = (0 until seed.length()).map { seed.getJSONObject(it) }.filter { item ->
            val tags = item.getJSONArray("tags")
            item.optString("category") !in CategoryMapper.categories || tags.length() !in 3..5
        }.map { it.getString("quote") }
        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun preseed_usesSeedCategoriesAndTagsAnonymousQuotes() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val repository = QuoteRepository(db.quoteDao(), 1L)
        repository.preseedDatabase(context)
        val rows = db.quoteDao().getAllQuotesSync()

        val seed = rawArray(R.raw.quotes_seed)
        val expected = (0 until seed.length()).map { seed.getJSONObject(it) }
            .associate { normalizeQuoteText(it.getString("quote")) to it.getString("category") }
        val wrongCategory = rows.filter { !it.isAnonymous && expected[it.text] != it.category }.map { it.text }
        assertEquals(emptyList<String>(), wrongCategory)

        val anonymous = rows.filter { it.isAnonymous }
        assertEquals(rawArray(R.raw.anonymous_quotes).length(), anonymous.size)
        assertTrue(anonymous.all { it.tags.split(", ").size in 3..5 && "anonymous" !in it.tags })
        db.close()
    }

    @Test
    fun tagLabel_translatesVocabularyTagsForGermanOnly() {
        assertEquals("Mut", tagLabel("courage", QuoteLanguage.GERMAN))
        assertEquals("Menschliche Natur", tagLabel("human-nature", QuoteLanguage.GERMAN))
        assertEquals("human nature", tagLabel("human-nature", QuoteLanguage.ENGLISH))
        assertEquals("my own tag", tagLabel("my-own-tag", QuoteLanguage.GERMAN))
    }

    @Test
    fun reseed_keepsFavoritesUserQuotesAndCommunityRows() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.quoteDao()
        val repository = QuoteRepository(dao, 1L)
        repository.preseedDatabase(context)
        val (first, renamed) = dao.getAllQuotesSync().filter { !it.isAnonymous }.take(2)
        dao.updateFavorite(first.id, true, "77")
        // Stored under an older author spelling that the new seed corrected.
        dao.insertQuote(renamed.copy(author = renamed.author + "x", isFavorite = true, savedDate = "78"))
        dao.insertQuotes(
            listOf(
                // A favorite that is no longer part of the seed, stored with the old quote stripping.
                QuoteEntity(text = "Retired favorite", author = "Old", category = "Life", isFavorite = true, savedDate = "1"),
                QuoteEntity(text = "Mine", author = "Me", category = "Life", isUserAdded = true, origin = QuoteOrigin.PERSONAL),
                QuoteEntity(text = "Shared", author = "R", category = "Life", origin = QuoteOrigin.COMMUNITY, serverId = "c-1")
            )
        )
        val before = dao.getQuotesCount()

        repository.reseedPreservingFavorites(context)

        val after = dao.getAllQuotesSync()
        assertEquals(before, after.size)
        assertTrue(after.single { it.text == first.text && it.author == first.author }.isFavorite)
        assertEquals("78", after.single { it.text == renamed.text }.savedDate)
        assertTrue(after.single { it.text == "Retired favorite" }.isFavorite)
        assertNotNull(after.singleOrNull { it.text == "Mine" })
        assertNotNull(after.singleOrNull { it.serverId == "c-1" })
        assertTrue(after.filter { it.isAnonymous }.all { it.author == "" && it.textDe != null })
        db.close()
    }

    @Test
    fun reseed_keepsIdsSoTodaysQuoteStaysAndRepairsTranslations() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.quoteDao()
        val repository = QuoteRepository(dao, 1L)
        repository.preseedDatabase(context)
        val today = repository.getDailyQuote("2026-11-15", QuoteSourceMode.ALL, QuoteLanguage.GERMAN, false)!!
        val idsBefore = dao.getBundledQuotes().associate { it.id to quoteMatchKey(it.text) }
        // A translation stored by an older normalizer, with a space before the closing mark.
        val dialogue = dao.getBundledQuotes().first { it.textDe?.endsWith(".“") == true }
        dao.updateText(dialogue.id, dialogue.text, dialogue.author, dialogue.textDe!!.dropLast(1) + " “", dialogue.textEn)

        repository.reseedPreservingFavorites(context)

        assertEquals(idsBefore, dao.getBundledQuotes().associate { it.id to quoteMatchKey(it.text) })
        assertEquals(today.id, repository.getDailyQuote("2026-11-15", QuoteSourceMode.ALL, QuoteLanguage.GERMAN, false)!!.id)
        assertEquals(dialogue.textDe, dao.getQuoteById(dialogue.id)!!.textDe)
        db.close()
    }

    @Test
    fun reseed_keepsAFavoriteTappedBetweenItsSnapshotAndSwap() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.quoteDao()
        QuoteRepository(dao, 1L).preseedDatabase(context)
        val target = dao.getBundledQuotes().first()
        val snapshotTaken = CompletableDeferred<Unit>()
        val tapReturned = CompletableDeferred<Unit>()
        // Holds the re-seed between its snapshot and its swap until the tap returns. A tap written
        // in that window would be overwritten by the stale snapshot; with the lock it waits for the
        // swap instead, so the pause ends after a moment and the tap lands on the swapped row.
        val pausingDao = object : QuoteDao by dao {
            override suspend fun getBundledQuotes(): List<QuoteEntity> = dao.getBundledQuotes().also {
                snapshotTaken.complete(Unit)
                withTimeoutOrNull(500) { tapReturned.await() }
            }
        }
        val repository = QuoteRepository(pausingDao, 1L)

        val reseed = launch(Dispatchers.Default) { repository.reseedPreservingFavorites(context) }
        snapshotTaken.await()
        assertTrue(repository.toggleFavorite(target.id, true))
        tapReturned.complete(Unit)
        reseed.join()

        assertTrue(dao.getQuoteById(target.id)!!.isFavorite)
        db.close()
    }

    @Test
    fun migration6To7_keepsRowsAndAddsColumns() = runBlocking {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `quotes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, " +
                    "`author` TEXT NOT NULL, `category` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, `isUserAdded` INTEGER NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, `aboutAuthor` TEXT NOT NULL, `tags` TEXT NOT NULL, `savedDate` TEXT, " +
                    "`submissionStatus` TEXT NOT NULL, `submissionId` TEXT, `submittedAt` INTEGER, `origin` TEXT NOT NULL, " +
                    "`serverId` TEXT, `serverRevision` INTEGER, `textDe` TEXT, `language` TEXT NOT NULL, `isAnonymous` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_quotes_serverId` ON `quotes` (`serverId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_selections` (`date` TEXT NOT NULL, `quoteId` INTEGER NOT NULL, PRIMARY KEY(`date`))"
            )
            db.execSQL(
                "INSERT INTO quotes (text, author, category, isFavorite, isUserAdded, timestamp, aboutAuthor, tags, " +
                    "submissionStatus, submissionId, origin, language, isAnonymous) VALUES " +
                    "('Mine', 'Me', 'Life', 1, 1, 1, '', '', 'pending', 'sub-1', 'personal', 'de', 0)"
            )
            db.version = 6
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        val migrated = room.quoteDao().getAllQuotesSync().single()
        assertEquals("Mine", migrated.text)
        assertEquals(QuoteSubmissionStatus.PENDING, migrated.submissionStatus)
        assertNull(migrated.reviewNote)
        assertNull(migrated.linkedServerId)
        assertNull(migrated.textEn)
        assertNull(room.authorInfoDao().get("Nobody"))
        room.close()
        context.deleteDatabase(name)
        Unit
    }
}
