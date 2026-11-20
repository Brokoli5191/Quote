package app.brokoli5191.quote.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.CommunitySyncManager
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteOrigin
import app.brokoli5191.quote.data.QuoteRepository
import app.brokoli5191.quote.data.QuoteSubmissionClient
import app.brokoli5191.quote.data.QuoteSubmissionStatus
import app.brokoli5191.quote.data.SubmissionStatusInfo
import app.brokoli5191.quote.data.mergeReseed
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupManagerTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = app.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
    private val databases = mutableListOf<AppDatabase>()
    private var fileCount = 0

    @After
    fun tearDown() = databases.forEach { it.close() }

    // One install: its own database and the backup manager that works on it. Prefs are the app's.
    private inner class Device(submissionClient: QuoteSubmissionClient = QuoteSubmissionClient()) {
        val db: AppDatabase = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { databases += it }
        val repository = QuoteRepository(db.quoteDao(), 42L)
        val sync = CommunitySyncManager(app, repository, submissionClient = submissionClient)
        val backups = BackupManager(app, repository, sync, db)

        suspend fun insert(vararg quotes: QuoteEntity) = db.quoteDao().insertQuotes(quotes.toList())
        suspend fun quotes(): List<QuoteEntity> = db.quoteDao().getAllQuotesSync()
    }

    private suspend fun export(device: Device): Pair<BackupOutcome, ByteArray> {
        val uri = Uri.parse("content://backups/export-${fileCount++}.json")
        val output = ByteArrayOutputStream()
        shadowOf(app.contentResolver).registerOutputStream(uri, output)
        return device.backups.export(uri) to output.toByteArray()
    }

    private suspend fun import(device: Device, content: ByteArray): BackupOutcome {
        val uri = Uri.parse("content://backups/import-${fileCount++}.json")
        shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(content))
        return device.backups.import(uri)
    }

    private suspend fun import(device: Device, content: String) = import(device, content.toByteArray())

    private fun bundled(text: String, author: String, savedDate: String? = null) = QuoteEntity(
        text = text,
        author = author,
        category = "Wisdom",
        tags = "wisdom",
        isFavorite = savedDate != null,
        savedDate = savedDate
    )

    private fun community(serverId: String, text: String, savedDate: String? = null) = QuoteEntity(
        text = text,
        author = "Reader",
        category = "Life",
        origin = QuoteOrigin.COMMUNITY,
        serverId = serverId,
        serverRevision = 1,
        isFavorite = savedDate != null,
        savedDate = savedDate
    )

    private fun own(
        text: String,
        submissionId: String? = null,
        status: String = QuoteSubmissionStatus.NOT_SUBMITTED,
        submittedAt: Long? = null,
        reviewNote: String? = null,
        linked: String? = null,
        savedDate: String? = null
    ) = QuoteEntity(
        text = text,
        author = "Me",
        category = "Life",
        isUserAdded = true,
        origin = QuoteOrigin.PERSONAL,
        isFavorite = savedDate != null,
        savedDate = savedDate,
        submissionStatus = status,
        submissionId = submissionId,
        submittedAt = submittedAt,
        reviewNote = reviewNote,
        linkedServerId = linked
    )

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private fun extraIds(): Set<String>? = prefs.getStringSet(CommunitySyncManager.KEY_EXTRA_INSTALLATION_IDS, null)

    @Test
    fun exportThenImport_restoresQuotesFavoritesSubmissionsAndSettings() = runBlocking {
        val old = Device()
        old.insert(
            bundled("Know thyself.", "Socrates", savedDate = "1700000000000"),
            bundled("The unexamined life is not worth living.", "Socrates"),
            community("c-1", "Shared wisdom from a reader.", savedDate = "1700000000100"),
            community("c-2", "Another shared thought.", savedDate = "1700000000200"),
            own("My first thought.", "sub-1", QuoteSubmissionStatus.PENDING, 1234L, savedDate = "1700000000300"),
            own("My approved thought.", "sub-2", QuoteSubmissionStatus.APPROVED, 5678L, reviewNote = "Nice", linked = "c-9"),
            own("Never submitted.")
        )
        prefs.edit()
            .putString(CommunitySyncManager.KEY_INSTALLATION_ID, A_ID)
            .putStringSet(CommunitySyncManager.KEY_EXTRA_INSTALLATION_IDS, setOf(B_ID))
            .putString("theme_mode", "LIGHT")
            .putString("app_language", "de")
            .putString("favorites_sort", "author")
            .putBoolean("submission_notifications_enabled", false)
            .putInt("daily_reminder_hour", 7)
            .putLong("community_quote_revision", 42L)
            .commit()

        val (exported, bytes) = export(old)
        assertEquals(BackupOutcome.Exported(6), exported)
        val json = JSONObject(String(bytes))
        assertEquals(3, json.getInt("formatVersion"))
        assertEquals(A_ID, json.getString("submissionInstallationId"))
        assertEquals(listOf(A_ID, B_ID), json.getJSONArray("submissionInstallationIds").strings())

        // A new phone: the seed and one community quote are there, everything else comes from the backup.
        prefs.edit().clear().commit()
        val fresh = Device()
        fresh.insert(
            bundled("Know thyself.", "Socrates"),
            bundled("The unexamined life is not worth living.", "Socrates"),
            community("c-1", "Shared wisdom from a reader.")
        )
        assertEquals(
            BackupOutcome.Imported(
                insertedCustom = 3,
                updatedFavorites = 3,
                skippedFavorites = 0,
                settingsRestored = true,
                languageAfter = "de"
            ),
            import(fresh, bytes)
        )

        val rows = fresh.quotes()
        val pending = rows.single { it.text == "My first thought." }
        assertTrue(pending.isUserAdded)
        assertEquals(QuoteOrigin.PERSONAL, pending.origin)
        assertEquals(QuoteSubmissionStatus.PENDING, pending.submissionStatus)
        assertEquals("sub-1", pending.submissionId)
        assertEquals(1234L, pending.submittedAt)
        assertTrue(pending.isFavorite)
        assertEquals("1700000000300", pending.savedDate)
        val approved = rows.single { it.text == "My approved thought." }
        assertEquals(QuoteSubmissionStatus.APPROVED, approved.submissionStatus)
        assertEquals("c-9", approved.linkedServerId)
        assertEquals("Nice", approved.reviewNote)
        assertEquals(QuoteSubmissionStatus.NOT_SUBMITTED, rows.single { it.text == "Never submitted." }.submissionStatus)
        val socrates = rows.single { it.text == "Know thyself." }
        assertTrue(socrates.isFavorite)
        assertEquals("1700000000000", socrates.savedDate)
        assertFalse(rows.single { it.text == "The unexamined life is not worth living." }.isFavorite)
        assertTrue(rows.single { it.serverId == "c-1" }.isFavorite)
        // c-2 is not synced yet: the feed favorites it when it arrives.
        assertNull(rows.firstOrNull { it.serverId == "c-2" })
        assertTrue(JSONObject(prefs.getString("community_favorite_tombstones", null)!!).has("c-2"))

        assertEquals(A_ID, prefs.getString(CommunitySyncManager.KEY_INSTALLATION_ID, null))
        assertEquals(setOf(B_ID), extraIds())
        assertEquals("LIGHT", prefs.getString("theme_mode", null))
        assertEquals("de", prefs.getString("app_language", null))
        assertEquals("author", prefs.getString("favorites_sort", null))
        assertFalse(prefs.getBoolean("submission_notifications_enabled", true))
        assertEquals(7, prefs.getInt("daily_reminder_hour", -1))
        // Device state is not part of the backup.
        assertFalse(prefs.contains("community_quote_revision"))
        // The next status refresh checks every restored submission once, whatever its age or link.
        assertTrue(prefs.getLong("submission_status_recheck_request", 0L) > 0L)

        // Restoring the same file again adds no rows; only the community favorite is still waiting. That holds
        // also when the file still ends with the tail of a longer one it overwrote without truncating.
        val staleTail = "\"isAnonymous\": false\n        }\n    ]\n}".toByteArray()
        assertEquals(BackupOutcome.Imported(0, 1, 0, true, "de"), import(fresh, bytes + staleTail))
        assertEquals(rows.size, fresh.quotes().size)
    }

    @Test
    fun legacyBackup_matchesStoredQuotesAndSkipsFavoritesItCannotPlace() = runBlocking {
        val device = Device()
        device.insert(
            bundled("Genius is one percent inspiration and ninety-nine percent perspiration.", "Thomas Edison"),
            bundled("We suffer more often in imagination than in reality.", "Seneca"),
            bundled("To live is the rarest thing in the world.", "Oscar Wilde"),
            community("c-1", "Shared wisdom from a reader."),
            own("Coffee first.")
        )
        // As 1.7.0 wrote it: format 2, without origins, community ids or submission fields.
        val backup = """
            {
              "formatVersion": 2,
              "exportedAt": 1726000000000,
              "settings": {"themeMode": "DYNAMIC", "appLanguage": "en", "dailyReminderEnabled": true,
                           "dailyReminderHour": 9, "dailyReminderMinute": 30, "autoUpdateEnabled": "yes"},
              "quotes": [
                {"text": "Genius is one percent inspiration and ninety-nine percent perspiration.",
                 "author": "Thomas A. Edison", "category": "Inspiration", "isFavorite": true, "isUserAdded": false,
                 "timestamp": 1, "aboutAuthor": "", "tags": "inspiration, genius", "savedDate": "1720000000000",
                 "language": "en", "textDe": "", "isAnonymous": false},
                {"text": "We suffer more often in imagination than in reality.", "author": "Seneca",
                 "category": "Wisdom", "isFavorite": true, "isUserAdded": false, "timestamp": 2, "aboutAuthor": "",
                 "tags": "wisdom", "savedDate": "1720000000001", "language": "en", "textDe": "", "isAnonymous": false},
                {"text": "Shared wisdom from a reader.", "author": "Reader", "category": "Life",
                 "isFavorite": true, "isUserAdded": false, "timestamp": 6, "aboutAuthor": "", "tags": "",
                 "savedDate": "1725000000000", "language": "en", "textDe": "", "isAnonymous": false},
                {"text": "A quote this device does not have.", "author": "Someone", "category": "Wisdom",
                 "isFavorite": true, "isUserAdded": false, "timestamp": 3, "aboutAuthor": "",
                 "tags": "wisdom", "savedDate": "1710000000000", "language": "en", "textDe": "",
                 "isAnonymous": false},
                {"text": "Coffee first.", "author": "Me", "category": "Life", "isFavorite": true, "isUserAdded": true,
                 "timestamp": 4, "aboutAuthor": "", "tags": "", "savedDate": "1730000000000", "language": "en",
                 "textDe": "", "isAnonymous": false},
                {"text": "A new own quote.", "author": "Me", "category": "Inspiration", "isFavorite": true,
                 "isUserAdded": true, "timestamp": 5, "aboutAuthor": "", "tags": "morning", "savedDate": "",
                 "language": "de", "textDe": "", "isAnonymous": false}
              ]
            }
        """.trimIndent()

        assertEquals(BackupOutcome.Imported(1, 4, 1, true, "en"), import(device, backup))

        val rows = device.quotes()
        val edison = rows.single { it.text.startsWith("Genius") }
        assertEquals("Thomas Edison", edison.author)
        assertTrue(edison.isFavorite)
        assertEquals("1720000000000", edison.savedDate)
        assertTrue(rows.single { it.author == "Seneca" }.isFavorite)
        assertFalse(rows.single { it.author == "Oscar Wilde" }.isFavorite)
        // Format 2 has no origins, yet a community favorite still finds its stored quote.
        val shared = rows.single { it.serverId == "c-1" }
        assertTrue(shared.isFavorite)
        assertEquals("1725000000000", shared.savedDate)
        // Without an origin, a favorite no stored quote matches may be a community quote that is not synced
        // yet or no longer published: it is skipped, not turned into a curated quote the feed later doubles.
        assertTrue(rows.none { it.text == "A quote this device does not have." })
        val coffee = rows.single { it.text == "Coffee first." }
        assertTrue(coffee.isFavorite)
        assertEquals("1730000000000", coffee.savedDate)
        val added = rows.single { it.text == "A new own quote." }
        assertTrue(added.isUserAdded)
        assertEquals("Inspirational", added.category)
        assertEquals("de", added.language)
        // A favorite without a saved date gets one, as on every other restore path.
        assertTrue(added.isFavorite)
        assertTrue(added.savedDate!!.toLong() > 0L)
        assertEquals(6, rows.size)

        assertEquals("DYNAMIC", prefs.getString("theme_mode", null))
        assertTrue(prefs.getBoolean("daily_reminder_enabled", false))
        assertEquals(9, prefs.getInt("daily_reminder_hour", -1))
        assertEquals(30, prefs.getInt("daily_reminder_minute", -1))
        // Not a boolean: left alone.
        assertFalse(prefs.contains("auto_update_enabled"))
        // Nothing in this backup was submitted, so no full status recheck is requested.
        assertFalse(prefs.contains("submission_status_recheck_request"))
    }

    @Test
    fun favoriteSavedBeforeATextCorrection_marksTheCorrectedBundledRow() = runBlocking {
        val device = Device()
        device.insert(bundled("There is a crack in everything. That's how the light gets in.", "Leonard Cohen"))
        val backup = """
            {"formatVersion": 3, "quotes": [
              {"text": "There is a crack in everything.That's how the light gets in", "author": "Leonard Cohen",
               "category": "Life", "isFavorite": true, "isUserAdded": false, "savedDate": "1710000000000",
               "origin": "bundled"}
            ]}
        """.trimIndent()

        import(device, backup)

        val row = device.quotes().single()
        assertEquals("There is a crack in everything. That's how the light gets in.", row.text)
        assertTrue(row.isFavorite)
        assertEquals("1710000000000", row.savedDate)
    }

    @Test
    fun currentBackup_keepsBundledFavoritesThatLeftTheSeed() = runBlocking {
        val device = Device()
        device.insert(bundled("Know thyself.", "Socrates"))
        val backup = """
            {"formatVersion": 3, "quotes": [
              {"text": "A quote that left the seed.", "author": "Old Author", "category": "Wisdom",
               "isFavorite": true, "isUserAdded": false, "timestamp": 3, "aboutAuthor": "",
               "tags": "wisdom, goodreads-noise", "savedDate": "1710000000000", "language": "en",
               "textDe": "Ein Quote, das ging.", "textEn": "", "isAnonymous": false, "origin": "bundled"},
              {"text": "A reader's quote without an id.", "author": "Reader", "category": "Life",
               "isFavorite": true, "isUserAdded": false, "savedDate": "1710000000001", "origin": "community"}
            ]}
        """.trimIndent()

        assertEquals(BackupOutcome.Imported(0, 1, 1, false, "en"), import(device, backup))

        val rows = device.quotes()
        val kept = rows.single { it.text == "A quote that left the seed." }
        assertEquals(QuoteOrigin.BUNDLED, kept.origin)
        assertFalse(kept.isUserAdded)
        assertTrue(kept.isFavorite)
        assertEquals("1710000000000", kept.savedDate)
        assertEquals("wisdom", kept.tags)
        assertEquals("Ein Quote, das ging.", kept.textDe)
        // A community favorite without a community id cannot be found again.
        assertTrue(rows.none { it.author == "Reader" })
        assertEquals(2, rows.size)

        // The next re-seed keeps the restored favorite although its quote left the seed.
        val reseeded = mergeReseed(device.db.quoteDao().getBundledQuotes(), listOf(bundled("Know thyself.", "Socrates")))
        assertEquals(setOf("Old Author"), reseeded.filter { it.isFavorite }.map { it.author }.toSet())
    }

    @Test
    fun brokenFiles_failWithoutWritingAnything() = runBlocking {
        val device = Device()
        device.insert(bundled("Know thyself.", "Socrates"))
        val before = device.quotes()
        // Valid JSON whose third entry has no text. Earlier versions had written the first two by then.
        val oneBadEntry = """
            {"formatVersion": 3, "submissionInstallationId": "$A_ID", "settings": {"appLanguage": "de"},
             "quotes": [
               {"text": "My first thought.", "author": "Me", "isUserAdded": true, "submissionStatus": "pending",
                "submissionId": "sub-1", "submittedAt": 5},
               {"text": "Know thyself.", "author": "Socrates", "isFavorite": true, "origin": "bundled"},
               {"author": "Nobody", "isFavorite": true},
               {"text": "Shared.", "author": "Reader", "isFavorite": true, "origin": "community", "serverId": "c-5"}
             ]}
        """.trimIndent()
        val broken = listOf(
            "",
            "   ",
            """{"formatVersion": 3, "quotes": [""",
            "<html><body>Not a backup</body></html>",
            "Just some text",
            "null",
            """{"formatVersion": 3}""",
            """{"quotes": {"text": "Not a list"}}""",
            "[1, 2, 3]",
            oneBadEntry
        )
        for (content in broken) {
            assertEquals(content, BackupOutcome.Failed(BackupError.INVALID_FORMAT), import(device, content))
        }
        assertEquals(
            BackupOutcome.Failed(BackupError.UNSUPPORTED_VERSION),
            import(device, """{"formatVersion": 4, "quotes": []}""")
        )
        // A file far larger than any backup is not read into memory.
        assertEquals(BackupOutcome.Failed(BackupError.INVALID_FORMAT), import(device, ByteArray(16 * 1024 * 1024 + 1)))
        // Deep nesting used to overflow the stack in org.json's recursive parser and crash the app.
        assertEquals(BackupOutcome.Failed(BackupError.INVALID_FORMAT), import(device, "[".repeat(200_000)))
        assertEquals(BackupOutcome.Failed(BackupError.INVALID_FORMAT), import(device, "{\"a\":".repeat(50_000)))

        assertEquals(before, device.quotes())
        assertFalse(prefs.contains(CommunitySyncManager.KEY_INSTALLATION_ID))
        assertFalse(prefs.contains("app_language"))
        assertFalse(prefs.contains("community_favorite_tombstones"))
        assertFalse(prefs.contains("submission_status_recheck_request"))
    }

    @Test
    fun installationIds_surviveRestoresOnDevicesThatHaveTheirOwn() = runBlocking {
        // Phone B has its own id and restores phone A's backup, which carries only the single id.
        prefs.edit().putString(CommunitySyncManager.KEY_INSTALLATION_ID, B_ID).commit()
        val phoneB = Device()
        val backupFromA = """
            {"formatVersion": 3, "submissionInstallationId": "$A_ID", "quotes": [
              {"text": "Sent from phone A.", "author": "Me", "isUserAdded": true, "submissionStatus": "pending",
               "submissionId": "sub-a", "submittedAt": 10}
            ]}
        """.trimIndent()
        assertEquals(BackupOutcome.Imported(1, 0, 0, false, "en"), import(phoneB, backupFromA))
        assertEquals(B_ID, prefs.getString(CommunitySyncManager.KEY_INSTALLATION_ID, null))
        assertEquals(setOf(A_ID), extraIds())

        // Phone B's backup carries both ids ...
        val bytes = export(phoneB).second
        assertEquals(listOf(B_ID, A_ID), JSONObject(String(bytes)).getJSONArray("submissionInstallationIds").strings())

        // ... so phone C, which has an id of its own as well, still finds phone A's pending submission.
        prefs.edit().clear().putString(CommunitySyncManager.KEY_INSTALLATION_ID, C_ID).commit()
        val server = object : QuoteSubmissionClient() {
            override suspend fun status(submissionId: String, installationId: String): Result<SubmissionStatusInfo> =
                Result.success(
                    if (submissionId == "sub-a" && installationId == A_ID) {
                        SubmissionStatusInfo(QuoteSubmissionStatus.PENDING)
                    } else {
                        SubmissionStatusInfo(QuoteSubmissionStatus.REMOVED, notFound = true)
                    }
                )
        }
        val phoneC = Device(server)
        assertEquals(BackupOutcome.Imported(1, 0, 0, true, "en"), import(phoneC, bytes))
        assertEquals(listOf(C_ID, A_ID, B_ID), CommunitySyncManager.submissionInstallationIds(prefs))
        assertTrue(phoneC.sync.refreshSubmissionStatuses().complete)
        val restored = phoneC.quotes().single()
        assertEquals("sub-a", restored.submissionId)
        assertEquals(QuoteSubmissionStatus.PENDING, restored.submissionStatus)

        // An install without an id takes the backup's current one; values that are no UUID are ignored.
        prefs.edit().clear().commit()
        val fresh = Device()
        import(fresh, """{"submissionInstallationId": "$A_ID", "submissionInstallationIds": ["$A_ID", "not-an-id", "$B_ID"], "quotes": []}""")
        assertEquals(A_ID, prefs.getString(CommunitySyncManager.KEY_INSTALLATION_ID, null))
        assertEquals(setOf(B_ID), extraIds())

        // The backend hashes the id as written, so another spelling of the same UUID is another id.
        prefs.edit().clear().putString(CommunitySyncManager.KEY_INSTALLATION_ID, A_ID).commit()
        import(fresh, """{"submissionInstallationIds": ["$A_ID", "${A_ID.uppercase()}"], "quotes": []}""")
        assertEquals(A_ID, prefs.getString(CommunitySyncManager.KEY_INSTALLATION_ID, null))
        assertEquals(setOf(A_ID.uppercase()), extraIds())
    }

    @Test
    fun restore_replacesASubmissionIdKeptAfterA404() = runBlocking {
        // A pending quote that answered 404 for every known installation id is not submitted but keeps its id
        // (CommunitySyncManager.applyFetchedStatus). A backup that knows its real submission wins.
        val device = Device()
        device.insert(own("Kept after a 404.", submissionId = "sub-1"))
        val backup = """
            {"formatVersion": 3, "quotes": [
              {"text": "Kept after a 404.", "author": "Me", "isUserAdded": true, "origin": "personal",
               "submissionStatus": "pending", "submissionId": "sub-2", "submittedAt": 20}
            ]}
        """.trimIndent()
        assertEquals(BackupOutcome.Imported(0, 0, 0, false, "en"), import(device, backup))
        val restored = device.quotes().single()
        assertEquals("sub-2", restored.submissionId)
        assertEquals(QuoteSubmissionStatus.PENDING, restored.submissionStatus)
        assertTrue(prefs.getLong("submission_status_recheck_request", 0L) > 0L)
    }

    private companion object {
        const val A_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        const val B_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        const val C_ID = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
    }
}
