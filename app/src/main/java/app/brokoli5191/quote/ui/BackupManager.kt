package app.brokoli5191.quote.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.CategoryMapper
import app.brokoli5191.quote.data.CommunitySyncManager
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.data.QuoteOrigin
import app.brokoli5191.quote.data.QuoteRepository
import app.brokoli5191.quote.data.QuoteSourceMode
import app.brokoli5191.quote.data.QuoteSubmissionStatus
import app.brokoli5191.quote.data.isVocabularyTag
import app.brokoli5191.quote.data.normalizedText
import app.brokoli5191.quote.data.quoteCompactKey
import app.brokoli5191.quote.data.quoteMatchKey
import app.brokoli5191.quote.utils.CommunitySyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Why a backup export or restore failed. The UI shows one fixed message per error. */
enum class BackupError { CANNOT_OPEN, INVALID_FORMAT, UNSUPPORTED_VERSION, WRITE_FAILED }

sealed interface BackupOutcome {
    data class Exported(val quoteCount: Int) : BackupOutcome

    data class Imported(
        /** Own quotes that were not on this device yet. */
        val insertedCustom: Int,
        /** Favorites the restore added, including community favorites that are favorited when the feed brings their quote. */
        val updatedFavorites: Int,
        /**
         * Favorites that could not be restored: community quotes that are no longer published (or have no
         * community id), and favorites of old backups without origins that match no stored quote.
         */
        val skippedFavorites: Int,
        val settingsRestored: Boolean,
        /** The app language ("en" or "de") after the restore. */
        val languageAfter: String
    ) : BackupOutcome

    data class Failed(val error: BackupError) : BackupOutcome
}

/**
 * Writes and restores the backup file: own quotes with their submission states, favorites, the user's
 * settings and every installation id submissions were sent with. Backups of older versions (a plain quote
 * array, format 2 without origins and community ids) still restore.
 */
class BackupManager internal constructor(
    private val app: Application,
    private val repository: QuoteRepository,
    private val communitySyncManager: CommunitySyncManager,
    private val database: AppDatabase
) {
    constructor(app: Application, repository: QuoteRepository, communitySyncManager: CommunitySyncManager) :
        this(app, repository, communitySyncManager, AppDatabase.getInstance(app))

    private val prefs: SharedPreferences get() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun export(uri: Uri): BackupOutcome = guarded("export") {
        val quotes = repository.getAllQuotesSync().filter { it.isUserAdded || it.isFavorite }
        val bytes = backupJson(quotes).toString(4).toByteArray(Charsets.UTF_8)
        val output = failingWith(BackupError.CANNOT_OPEN) { app.contentResolver.openOutputStream(uri) }
            ?: throw BackupException(BackupError.CANNOT_OPEN)
        failingWith(BackupError.WRITE_FAILED) { output.use { it.write(bytes) } }
        BackupOutcome.Exported(quotes.size)
    }

    /**
     * Reads and checks the whole file before anything is written; the quotes are then written in one
     * transaction, so a file that cannot be restored changes nothing. Afterwards the next status refresh
     * checks every restored submission once.
     */
    suspend fun import(uri: Uri): BackupOutcome = guarded("restore") {
        val content = failingWith(BackupError.CANNOT_OPEN) { readBackup(uri) }
        restore(failingWith(BackupError.INVALID_FORMAT) { parseBackup(content) })
    }

    // Runs [block] on the IO dispatcher and turns every failure into a BackupError. Raw messages only go to
    // the log: they are English, and a parse error's message quotes the file.
    private suspend fun guarded(action: String, block: suspend () -> BackupOutcome): BackupOutcome =
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = (e as? BackupException)?.error ?: BackupError.WRITE_FAILED
                val cause = (e as? BackupException)?.cause ?: e
                if (error == BackupError.INVALID_FORMAT) {
                    Log.w(TAG, "Backup $action failed: $error (${cause.javaClass.simpleName})")
                } else {
                    Log.w(TAG, "Backup $action failed: $error", cause)
                }
                BackupOutcome.Failed(error)
            }
        }

    private fun backupJson(quotes: List<QuoteEntity>): JSONObject = JSONObject().apply {
        put("formatVersion", FORMAT_VERSION)
        put("exportedAt", System.currentTimeMillis())
        put("settings", JSONObject().also { settings -> SETTINGS.forEach { it.export(prefs, settings) } })
        // Every id this device sent submissions with, so a restored install can still check and withdraw all
        // of them, also after further restores. Earlier versions read only the single current id.
        prefs.getString(CommunitySyncManager.KEY_INSTALLATION_ID, null)?.let { put("submissionInstallationId", it) }
        val installationIds = CommunitySyncManager.submissionInstallationIds(prefs)
        if (installationIds.isNotEmpty()) put("submissionInstallationIds", JSONArray(installationIds))
        put("quotes", JSONArray().also { array -> quotes.forEach { array.put(it.toBackupJson()) } })
    }

    // The file as text. A file far larger than any backup is rejected before it fills the memory.
    private fun readBackup(uri: Uri): String {
        val input = app.contentResolver.openInputStream(uri) ?: throw BackupException(BackupError.CANNOT_OPEN)
        val bytes = input.use { it.readAtMost(MAX_BACKUP_BYTES) } ?: throw BackupException(BackupError.INVALID_FORMAT)
        return String(bytes, Charsets.UTF_8)
    }

    private suspend fun restore(backup: ParsedBackup): BackupOutcome {
        // Both happen before the quotes are written. A status refresh that sees a restored submission must
        // know the installation id it was sent with, or the 404 makes it look withdrawn. Remembering the
        // favorites waits for a sync that is running, and every later one favorites those quotes on arrival.
        // If the quotes then cannot be written, both stay: one more id to try, favorites waiting for quotes.
        val idsAdded = restoreInstallationIds(backup.installationIds)
        val pending = pendingCommunityFavorites(backup.quotes)
        communitySyncManager.rememberPendingFavorites(pending)

        val written = database.withTransaction { writeQuotes(backup.quotes, pending.keys) }
        // Only now: a refresh that ran before the rows were in would use up the request.
        if (written.submissionsRestored || idsAdded) CommunitySyncManager.requestFullStatusRecheck(app)

        val settingsRestored = restoreSettings(backup.settings)
        return BackupOutcome.Imported(
            insertedCustom = written.insertedCustom,
            updatedFavorites = written.updatedFavorites,
            skippedFavorites = written.skippedFavorites,
            settingsRestored = settingsRestored,
            languageAfter = prefs.getString(KEY_APP_LANGUAGE, null)
                ?.takeIf { it == QuoteLanguage.GERMAN } ?: QuoteLanguage.ENGLISH
        )
    }

    // The backup's ids become extra ids of this device; the first one becomes its id when it has none yet.
    // Returns whether an id was added.
    private fun restoreInstallationIds(ids: List<String>): Boolean {
        var current = prefs.getString(CommunitySyncManager.KEY_INSTALLATION_ID, null)
        val extra = prefs.getStringSet(CommunitySyncManager.KEY_EXTRA_INSTALLATION_IDS, null).orEmpty().toMutableSet()
        val editor = prefs.edit()
        var added = false
        for (id in ids) {
            when {
                current == null -> {
                    current = id
                    editor.putString(CommunitySyncManager.KEY_INSTALLATION_ID, id)
                    added = true
                }
                // Compared exactly: the backend hashes the string, so two spellings are two ids.
                id == current || id in extra -> Unit
                else -> {
                    extra += id
                    added = true
                }
            }
        }
        if (added) editor.putStringSet(CommunitySyncManager.KEY_EXTRA_INSTALLATION_IDS, extra).apply()
        return added
    }

    // Community favorites of the backup whose quotes are not stored yet: community id to saved date.
    private suspend fun pendingCommunityFavorites(quotes: List<BackupQuote>): Map<String, String?> {
        val candidates = quotes
            .filter { !it.isUserAdded && it.isFavorite && it.origin == QuoteOrigin.COMMUNITY }
            .mapNotNull { quote -> quote.serverId?.let { it to quote } }
        if (candidates.isEmpty()) return emptyMap()
        val stored = FavoriteIndex(repository.getAllQuotesSync().filter { !it.isUserAdded })
        return candidates
            .filter { (_, quote) -> stored.find(quote) == null }
            .associate { (serverId, quote) -> serverId to quote.savedDate }
    }

    // Runs inside the restore transaction, so it only uses the DAO: the repository's write lock is taken
    // by callers that wait for the database, which this transaction holds.
    private suspend fun writeQuotes(quotes: List<BackupQuote>, pendingServerIds: Set<String>): WriteResult {
        val dao = database.quoteDao()
        val stored = dao.getAllQuotesSync()
        val own = stored.filter { it.isUserAdded }.associateByTo(HashMap()) { it.matchKey() }
        val others = FavoriteIndex(stored.filter { !it.isUserAdded })
        val now = System.currentTimeMillis()
        var insertedCustom = 0
        var updatedFavorites = 0
        var skippedFavorites = 0
        var submissionsRestored = false

        for (quote in quotes) {
            if (quote.isUserAdded) {
                val existing = own[quote.key]
                if (existing == null) {
                    val row = quote.toOwnQuote(now)
                    own[quote.key] = row.copy(id = dao.insertQuote(row).toInt())
                    insertedCustom++
                    if (row.submissionId != null) submissionsRestored = true
                    continue
                }
                var merged = existing
                if (quote.isFavorite && !existing.isFavorite) {
                    val savedDate = quote.savedDate ?: now.toString()
                    dao.updateFavorite(existing.id, true, savedDate)
                    merged = merged.copy(isFavorite = true, savedDate = savedDate)
                    updatedFavorites++
                }
                val submission = quote.submission
                // A not submitted quote may keep the id of a submission this device could not find (see
                // CommunitySyncManager.applyFetchedStatus); a submission the backup knows replaces it.
                if (submission != null && (existing.submissionId == null ||
                        existing.submissionStatus == QuoteSubmissionStatus.NOT_SUBMITTED)
                ) {
                    // The local copy lost its submission (e.g. re-created by hand): take it from the backup.
                    dao.updateSubmission(existing.id, submission.status, submission.submissionId, submission.submittedAt)
                    dao.updateSubmissionState(existing.id, submission.status, submission.reviewNote, submission.linkedServerId)
                    merged = merged.copy(submissionId = submission.submissionId, submissionStatus = submission.status)
                    submissionsRestored = true
                }
                own[quote.key] = merged
            } else if (quote.isFavorite) {
                val match = others.find(quote)
                when {
                    match != null -> if (others.markFavorite(match)) {
                        dao.updateFavorite(match.id, true, quote.savedDate ?: now.toString())
                        updatedFavorites++
                    }
                    // Favorited by the feed when the quote arrives, unless it is no longer published.
                    quote.origin == QuoteOrigin.COMMUNITY ->
                        if (quote.serverId in pendingServerIds) updatedFavorites++ else skippedFavorites++
                    // A favorite whose quote left the seed is kept, as the re-seed keeps it.
                    quote.origin == QuoteOrigin.BUNDLED -> {
                        val row = quote.toKeptFavorite(now)
                        others.add(row.copy(id = dao.insertQuote(row).toInt()))
                        updatedFavorites++
                    }
                    // No origin (format 2): it may be a community quote that is not synced yet or no longer
                    // published, which must not come back as a curated copy of itself.
                    else -> skippedFavorites++
                }
            }
        }
        return WriteResult(insertedCustom, updatedFavorites, skippedFavorites, submissionsRestored)
    }

    private fun restoreSettings(settings: JSONObject?): Boolean {
        if (settings == null) return false
        val editor = prefs.edit()
        var restored = false
        for (setting in SETTINGS) {
            if (setting.restore(settings, editor)) restored = true
        }
        if (!restored) return false
        if (editor.commit()) return true
        Log.w(TAG, "Could not save the restored settings")
        return false
    }

    private companion object {
        const val TAG = "BackupManager"
        const val PREFS = "aura_prefs"
        const val KEY_APP_LANGUAGE = "app_language"
        const val MAX_BACKUP_BYTES = 16 * 1024 * 1024
    }
}

// Raised only for changes older versions cannot read; new fields need no new version.
private const val FORMAT_VERSION = 3

private class BackupException(val error: BackupError, cause: Throwable? = null) : Exception(error.name, cause)

// Runs [block] and reports a failure in it, other than a BackupException, as [error].
private inline fun <T> failingWith(error: BackupError, block: () -> T): T = try {
    block()
} catch (e: BackupException) {
    throw e
} catch (e: Exception) {
    throw BackupException(error, e)
}

// Null when the stream holds more than [limit] bytes.
private fun InputStream.readAtMost(limit: Int): ByteArray? {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(chunk)
        if (read < 0) return buffer.toByteArray()
        if (buffer.size() + read > limit) return null
        buffer.write(chunk, 0, read)
    }
}

private class ParsedBackup(
    val quotes: List<BackupQuote>,
    /** The backup's current installation id first. */
    val installationIds: List<String>,
    val settings: JSONObject?
)

private class BackupQuote(
    val text: String,
    val author: String,
    val category: String,
    val isFavorite: Boolean,
    val isUserAdded: Boolean,
    /** personal for own quotes, else bundled or community; null when the backup does not say (format 2). */
    val origin: String?,
    val serverId: String?,
    val timestamp: Long?,
    val aboutAuthor: String,
    val tags: String,
    val savedDate: String?,
    val language: String,
    val textDe: String?,
    val textEn: String?,
    val isAnonymous: Boolean,
    val submission: BackupSubmission?
) {
    val key = quoteMatchKey(text) to quoteMatchKey(author)
}

private class BackupSubmission(
    val submissionId: String,
    val status: String,
    val submittedAt: Long?,
    val reviewNote: String?,
    val linkedServerId: String?
)

private class WriteResult(
    val insertedCustom: Int,
    val updatedFavorites: Int,
    val skippedFavorites: Int,
    val submissionsRestored: Boolean
)

private val SUBMITTED_STATES = setOf(
    QuoteSubmissionStatus.PENDING,
    QuoteSubmissionStatus.APPROVED,
    QuoteSubmissionStatus.REJECTED,
    QuoteSubmissionStatus.REMOVED
)

// The form the backend accepts. The id is kept exactly as written: the backend hashes the string.
private val INSTALLATION_ID_PATTERN =
    Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}", RegexOption.IGNORE_CASE)

/** Throws a BackupException, or org.json's exceptions for broken JSON, when the content is no usable backup. */
private fun parseBackup(content: String): ParsedBackup {
    // Text after the value is ignored, as it always was: a file overwritten in "w" mode without truncation
    // (some providers since Android 10) still ends with the tail of the longer file it replaced.
    val root = JSONTokener(content).nextValue()
    val quotes = when (root) {
        is JSONArray -> root // Format 1: only the quotes
        is JSONObject -> {
            if (root.optInt("formatVersion", 1) > FORMAT_VERSION) throw BackupException(BackupError.UNSUPPORTED_VERSION)
            root.opt("quotes") as? JSONArray ?: throw BackupException(BackupError.INVALID_FORMAT)
        }
        else -> throw BackupException(BackupError.INVALID_FORMAT)
    }
    return ParsedBackup(
        quotes = (0 until quotes.length()).map { index ->
            parseQuote(quotes.opt(index) as? JSONObject ?: throw BackupException(BackupError.INVALID_FORMAT))
        },
        installationIds = (root as? JSONObject)?.let(::parseInstallationIds).orEmpty(),
        settings = (root as? JSONObject)?.optJSONObject("settings")
    )
}

private fun parseQuote(obj: JSONObject): BackupQuote {
    val text = obj.stringOrNull("text")?.takeIf { it.isNotBlank() } ?: throw BackupException(BackupError.INVALID_FORMAT)
    val isUserAdded = obj.optBoolean("isUserAdded", false)
    val serverId = obj.stringOrNull("serverId")?.takeIf { it.isNotBlank() && !isUserAdded }
    val writtenOrigin = obj.stringOrNull("origin")
    return BackupQuote(
        text = text,
        author = obj.stringOrNull("author").orEmpty(),
        category = backupCategory(obj.stringOrNull("category")),
        isFavorite = obj.optBoolean("isFavorite", false),
        isUserAdded = isUserAdded,
        origin = when {
            isUserAdded -> QuoteOrigin.PERSONAL
            serverId != null || writtenOrigin == QuoteOrigin.COMMUNITY -> QuoteOrigin.COMMUNITY
            writtenOrigin == QuoteOrigin.BUNDLED -> QuoteOrigin.BUNDLED
            // Format 2 has no origins, also not for its community favorites.
            else -> null
        },
        serverId = serverId,
        timestamp = obj.optLong("timestamp", 0L).takeIf { it > 0L },
        aboutAuthor = obj.stringOrNull("aboutAuthor").orEmpty(),
        tags = obj.stringOrNull("tags").orEmpty(),
        savedDate = obj.stringOrNull("savedDate")?.takeIf { it.isNotBlank() },
        language = obj.stringOrNull("language")?.takeIf { it == QuoteLanguage.GERMAN } ?: QuoteLanguage.ENGLISH,
        textDe = obj.stringOrNull("textDe")?.takeIf { it.isNotBlank() },
        textEn = obj.stringOrNull("textEn")?.takeIf { it.isNotBlank() },
        isAnonymous = obj.optBoolean("isAnonymous", false),
        submission = if (isUserAdded) parseSubmission(obj) else null
    )
}

// The submission of a backed-up own quote, or null when it was never submitted.
private fun parseSubmission(obj: JSONObject): BackupSubmission? {
    val submissionId = obj.stringOrNull("submissionId")?.takeIf { it.isNotBlank() } ?: return null
    val status = obj.stringOrNull("submissionStatus")?.takeIf { it in SUBMITTED_STATES } ?: return null
    return BackupSubmission(
        submissionId = submissionId,
        status = status,
        submittedAt = obj.optLong("submittedAt", 0L).takeIf { it > 0L },
        reviewNote = obj.stringOrNull("reviewNote")?.takeIf { it.isNotBlank() },
        linkedServerId = obj.stringOrNull("linkedServerId")
            ?.takeIf { it.isNotBlank() && status == QuoteSubmissionStatus.APPROVED }
    )
}

// Format 3 before the list existed carries only "submissionInstallationId", the current id.
private fun parseInstallationIds(root: JSONObject): List<String> {
    val ids = listOfNotNull(root.stringOrNull("submissionInstallationId")) +
        root.optJSONArray("submissionInstallationIds")
            ?.let { array -> (0 until array.length()).mapNotNull { array.opt(it) as? String } }
            .orEmpty()
    return ids.map { it.trim() }.filter { INSTALLATION_ID_PATTERN.matches(it) }.distinct()
}

// Older versions used "Inspiration"; unknown categories become "Uncategorized".
private fun backupCategory(value: String?): String {
    val category = if (value.equals("Inspiration", ignoreCase = true)) "Inspirational" else value
    return category?.takeIf { it in CategoryMapper.categories } ?: "Uncategorized"
}

private fun JSONObject.stringOrNull(name: String): String? = opt(name) as? String

private fun QuoteEntity.matchKey(): Pair<String, String> = quoteMatchKey(text) to quoteMatchKey(author)

private fun QuoteEntity.toBackupJson(): JSONObject = JSONObject().apply {
    put("text", text)
    put("author", author)
    put("category", category)
    put("isFavorite", isFavorite)
    put("isUserAdded", isUserAdded)
    put("timestamp", timestamp)
    put("aboutAuthor", aboutAuthor)
    put("tags", tags)
    put("savedDate", savedDate ?: "")
    put("language", language)
    put("textDe", textDe ?: "")
    put("textEn", textEn ?: "")
    put("isAnonymous", isAnonymous)
    put("origin", origin)
    serverId?.let { put("serverId", it) }
    if (isUserAdded) {
        put("submissionStatus", submissionStatus)
        submissionId?.let { put("submissionId", it) }
        submittedAt?.let { put("submittedAt", it) }
        reviewNote?.let { put("reviewNote", it) }
        linkedServerId?.let { put("linkedServerId", it) }
    }
}

private fun BackupQuote.toOwnQuote(now: Long): QuoteEntity = QuoteEntity(
    text = text,
    author = author,
    category = category,
    isFavorite = isFavorite,
    isUserAdded = true,
    origin = QuoteOrigin.PERSONAL,
    timestamp = timestamp ?: now,
    aboutAuthor = aboutAuthor,
    tags = tags,
    savedDate = if (isFavorite) savedDate ?: now.toString() else null,
    language = language,
    textDe = textDe,
    textEn = textEn,
    isAnonymous = isAnonymous,
    submissionStatus = submission?.status ?: QuoteSubmissionStatus.NOT_SUBMITTED,
    submissionId = submission?.submissionId,
    submittedAt = submission?.submittedAt,
    reviewNote = submission?.reviewNote,
    linkedServerId = submission?.linkedServerId
).normalizedText()

// A bundled favorite that is no longer in the seed, kept like mergeReseed keeps one: with only its
// curated tags, since older seeds carried noisy source tags.
private fun BackupQuote.toKeptFavorite(now: Long): QuoteEntity = QuoteEntity(
    text = text,
    author = author,
    category = category,
    isFavorite = true,
    origin = QuoteOrigin.BUNDLED,
    timestamp = timestamp ?: now,
    aboutAuthor = aboutAuthor,
    tags = tags.split(',').map { it.trim() }.filter { isVocabularyTag(it) }.joinToString(", "),
    savedDate = savedDate ?: now.toString(),
    language = language,
    textDe = textDe,
    textEn = textEn,
    isAnonymous = isAnonymous
).normalizedText()

/**
 * Finds the stored quote a backed-up favorite belongs to, the way the re-seed (mergeReseed) matches: by
 * community id, by (text, author), then for bundled favorites and those without an origin by the text alone
 * when only one bundled quote has it, for author names the seed corrected since the backup was made.
 */
private class FavoriteIndex(rows: List<QuoteEntity>) {
    private val byServerId = HashMap<String, QuoteEntity>()
    private val byKey = HashMap<Pair<String, String>, MutableList<QuoteEntity>>()
    private val bundledByText = HashMap<String, MutableList<QuoteEntity>>()
    private val bundledByLetters = HashMap<String, MutableList<QuoteEntity>>()
    private val favoriteIds = HashSet<Int>()

    init {
        rows.forEach(::add)
    }

    fun add(row: QuoteEntity) {
        val key = row.matchKey()
        row.serverId?.let { byServerId[it] = row }
        byKey.getOrPut(key) { ArrayList(1) } += row
        if (row.origin == QuoteOrigin.BUNDLED) {
            bundledByText.getOrPut(key.first) { ArrayList(1) } += row
            bundledByLetters.getOrPut(quoteCompactKey(row.text)) { ArrayList(1) } += row
        }
        if (row.isFavorite) favoriteIds += row.id
    }

    fun find(quote: BackupQuote): QuoteEntity? {
        quote.serverId?.let(byServerId::get)?.let { return it }
        // A favorite without an origin (format 2) is looked for like a bundled one, as it always was.
        val origin = quote.origin ?: QuoteOrigin.BUNDLED
        byKey[quote.key]?.let { rows -> return rows.firstOrNull { it.origin == origin } ?: rows.first() }
        if (origin != QuoteOrigin.BUNDLED) return null
        // Then by its letters alone: a backup made before a bundled text was corrected (lost spaces
        // and line breaks, missing punctuation) still finds the corrected row.
        return bundledByText[quote.key.first]?.singleOrNull()
            ?: bundledByLetters[quoteCompactKey(quote.text)]?.singleOrNull()
    }

    /** Records [row] as a favorite; false when it already is one. */
    fun markFavorite(row: QuoteEntity): Boolean = favoriteIds.add(row.id)
}

// A user setting the backup carries: its JSON key, its aura_prefs key and the values a restore accepts.
private sealed class BackupSetting(val jsonKey: String, val prefKey: String) {
    abstract fun export(prefs: SharedPreferences, json: JSONObject)

    /** Puts the backup's value into [editor] when it has a valid one; returns whether it did. */
    abstract fun restore(json: JSONObject, editor: SharedPreferences.Editor): Boolean

    class Choice(jsonKey: String, prefKey: String, private val default: String, private val allowed: Set<String>) :
        BackupSetting(jsonKey, prefKey) {
        override fun export(prefs: SharedPreferences, json: JSONObject) {
            json.put(jsonKey, prefs.getString(prefKey, default))
        }

        override fun restore(json: JSONObject, editor: SharedPreferences.Editor): Boolean {
            val value = json.stringOrNull(jsonKey)?.takeIf { it in allowed } ?: return false
            editor.putString(prefKey, value)
            return true
        }
    }

    class Text(jsonKey: String, prefKey: String, private val default: String, private val maxLength: Int) :
        BackupSetting(jsonKey, prefKey) {
        override fun export(prefs: SharedPreferences, json: JSONObject) {
            json.put(jsonKey, prefs.getString(prefKey, default))
        }

        override fun restore(json: JSONObject, editor: SharedPreferences.Editor): Boolean {
            val value = json.stringOrNull(jsonKey) ?: return false
            editor.putString(prefKey, value.take(maxLength))
            return true
        }
    }

    class Flag(jsonKey: String, prefKey: String, private val default: Boolean) : BackupSetting(jsonKey, prefKey) {
        override fun export(prefs: SharedPreferences, json: JSONObject) {
            json.put(jsonKey, prefs.getBoolean(prefKey, default))
        }

        override fun restore(json: JSONObject, editor: SharedPreferences.Editor): Boolean {
            val value = json.opt(jsonKey) as? Boolean ?: return false
            editor.putBoolean(prefKey, value)
            return true
        }
    }

    class Bounded(jsonKey: String, prefKey: String, private val default: Int, private val range: IntRange) :
        BackupSetting(jsonKey, prefKey) {
        override fun export(prefs: SharedPreferences, json: JSONObject) {
            json.put(jsonKey, prefs.getInt(prefKey, default))
        }

        override fun restore(json: JSONObject, editor: SharedPreferences.Editor): Boolean {
            val value = (json.opt(jsonKey) as? Number)?.toInt() ?: return false
            editor.putInt(prefKey, value.coerceIn(range))
            return true
        }
    }
}

// Only settings that belong to the user; device state (sync cursors, queues, installation ids) stays out.
private val SETTINGS: List<BackupSetting> = listOf(
    BackupSetting.Choice("themeMode", "theme_mode", "DARK", setOf("LIGHT", "DARK", "AMOLED", "DYNAMIC")),
    BackupSetting.Choice("themeAccent", "theme_accent", "Violet", setOf("Violet", "Amber", "Green", "Blue", "Rose")),
    BackupSetting.Flag("amoledBlack", "amoled_black", false),
    BackupSetting.Choice(
        "quoteSourceMode",
        "quote_source_mode",
        QuoteSourceMode.ALL,
        setOf(QuoteSourceMode.ALL, QuoteSourceMode.CURATED, QuoteSourceMode.COMMUNITY)
    ),
    BackupSetting.Choice("appLanguage", "app_language", QuoteLanguage.ENGLISH, setOf(QuoteLanguage.ENGLISH, QuoteLanguage.GERMAN)),
    BackupSetting.Flag("showAnonymousQuotes", "show_anonymous_quotes", false),
    BackupSetting.Text("widgetStyle", "widget_style", "Quote", 50),
    BackupSetting.Flag("dailyReminderEnabled", "daily_reminder_enabled", false),
    BackupSetting.Bounded("dailyReminderHour", "daily_reminder_hour", 8, 0..23),
    BackupSetting.Bounded("dailyReminderMinute", "daily_reminder_minute", 0, 0..59),
    BackupSetting.Flag("lowPerformanceMode", "low_performance_mode", false),
    BackupSetting.Flag("blurNavigationSurfaces", "blur_navigation_surfaces", false),
    BackupSetting.Flag("autoUpdateEnabled", "auto_update_enabled", true),
    BackupSetting.Flag("authorImagesEnabled", "author_images_enabled", true),
    BackupSetting.Choice("favoritesSort", "favorites_sort", "recent", setOf("recent", "oldest", "author", "text")),
    BackupSetting.Flag("submissionNotificationsEnabled", CommunitySyncWorker.KEY_SUBMISSION_NOTIFICATIONS, true)
)
