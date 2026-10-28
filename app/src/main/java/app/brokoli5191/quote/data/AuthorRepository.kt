package app.brokoli5191.quote.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import app.brokoli5191.quote.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** User-Agent for every request to Wikipedia / Wikimedia (API calls and image loads). */
val WIKIMEDIA_USER_AGENT: String = "QuoteApp/${BuildConfig.VERSION_NAME} (https://github.com/Brokoli5191/Quote)"

/** What the UI shows about an author. Every field but [name] may be missing. */
data class AuthorInfo(
    val name: String,
    val wikiTitle: String?,
    val imageUrl: String?,
    val description: String?,
    val wikiUrl: String?
)

/** Cached runtime Wikipedia lookup. A row with a null [wikiTitle] is a cached "no match". */
@Entity(tableName = "author_info")
data class AuthorInfoEntity(
    @PrimaryKey val name: String,
    val wikiTitle: String?,
    val imageUrl: String?,
    val description: String?,
    val fetchedAt: Long
)

@Dao
interface AuthorInfoDao {
    @Query("SELECT * FROM author_info WHERE name = :name LIMIT 1")
    suspend fun get(name: String): AuthorInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(info: AuthorInfoEntity)

    @Query("DELETE FROM author_info WHERE fetchedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

internal data class BundledAuthor(
    val title: String,
    val image: String?,
    val en: String?,
    val de: String?,
    val dewiki: String?
)

internal data class WikiPage(val title: String, val imageUrl: String?, val description: String?, val wikidataId: String)

/**
 * Author portraits and short descriptions: first the bundled map (res/raw/author_wiki.json),
 * then, only when [allowNetwork] is set, a cached runtime lookup on English Wikipedia that
 * accepts only pages about a real person.
 */
class AuthorRepository(
    context: Context,
    private val dao: AuthorInfoDao
) {
    private val appContext = context.applicationContext
    private val lookupLocks = HashMap<String, Mutex>()

    suspend fun authorInfo(author: String, language: String, allowNetwork: Boolean): AuthorInfo? {
        val name = author.trim()
        if (name.isBlank() || name == "Unknown" || name == "Unbekannt") return null
        bundled(appContext)[name]?.let { return it.toAuthorInfo(name, language, allowNetwork) }
        if (!allowNetwork) return null

        val lock = synchronized(lookupLocks) { lookupLocks.getOrPut(name) { Mutex() } }
        return lock.withLock {
            val now = System.currentTimeMillis()
            val cached = dao.get(name)?.takeIf { now - it.fetchedAt < CACHE_TTL_MS }
            val entity = cached ?: fetch(name, now)?.also { dao.put(it) } ?: return@withLock null
            entity.wikiTitle?.let { title ->
                AuthorInfo(
                    name = name,
                    wikiTitle = title,
                    imageUrl = entity.imageUrl,
                    // Runtime descriptions come from English Wikipedia only.
                    description = entity.description.takeIf { language != QuoteLanguage.GERMAN },
                    wikiUrl = wikiUrl("en", title)
                )
            }
        }
    }

    // Returns null on network errors so nothing is cached; a definite "no match" is cached.
    private suspend fun fetch(name: String, now: Long): AuthorInfoEntity? = withContext(Dispatchers.IO) {
        val noMatch = AuthorInfoEntity(name, null, null, null, now)
        val pageJson = httpGet(
            "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&redirects=1" +
                "&prop=pageimages%7Cdescription%7Cpageprops&piprop=thumbnail&pithumbsize=330&titles=" +
                URLEncoder.encode(name, "UTF-8")
        ) ?: return@withContext null
        val page = runCatching { parseWikipediaPage(pageJson) }.getOrNull() ?: return@withContext noMatch
        val claimsJson = httpGet(
            "https://www.wikidata.org/w/api.php?action=wbgetclaims&format=json&property=P31&entity=" +
                URLEncoder.encode(page.wikidataId, "UTF-8")
        ) ?: return@withContext null
        if (!runCatching { isHumanEntity(claimsJson) }.getOrDefault(false)) return@withContext noMatch
        AuthorInfoEntity(name, page.title, page.imageUrl, page.description, now)
    }

    private fun httpGet(url: String): String? = try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", WIKIMEDIA_USER_AGENT)
        }
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000

        @Volatile
        private var bundledMap: Deferred<Map<String, BundledAuthor>>? = null

        // Parsed once per process, off the main thread.
        internal suspend fun bundled(context: Context): Map<String, BundledAuthor> {
            val deferred = bundledMap ?: synchronized(this) {
                bundledMap ?: CoroutineScope(Dispatchers.Default).async {
                    runCatching {
                        context.applicationContext.resources
                            .openRawResource(app.brokoli5191.quote.R.raw.author_wiki)
                            .bufferedReader().use { parseBundledAuthors(it.readText()) }
                    }.getOrElse { emptyMap() }
                }.also { bundledMap = it }
            }
            return deferred.await()
        }

        internal fun wikiUrl(wikiLanguage: String, title: String): String =
            "https://$wikiLanguage.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
    }
}

internal fun BundledAuthor.toAuthorInfo(name: String, language: String, allowImages: Boolean): AuthorInfo {
    val german = language == QuoteLanguage.GERMAN
    val title = if (german && dewiki != null) dewiki else title
    return AuthorInfo(
        name = name,
        wikiTitle = title,
        imageUrl = image.takeIf { allowImages },
        description = if (german) de else en,
        wikiUrl = AuthorRepository.wikiUrl(if (german && dewiki != null) "de" else "en", title)
    )
}

internal fun parseBundledAuthors(json: String): Map<String, BundledAuthor> {
    val root = JSONObject(json)
    val result = HashMap<String, BundledAuthor>(root.length() * 2)
    root.keys().forEach { name ->
        val item = root.optJSONObject(name) ?: return@forEach
        val title = item.optNullableString("title")?.takeIf { it.isNotBlank() } ?: return@forEach
        result[name] = BundledAuthor(
            title = title,
            image = item.optNullableString("image")?.takeIf { it.startsWith("https://") },
            en = item.optNullableString("en")?.takeIf { it.isNotBlank() },
            de = item.optNullableString("de")?.takeIf { it.isNotBlank() },
            dewiki = item.optNullableString("dewiki")?.takeIf { it.isNotBlank() }
        )
    }
    return result
}

/** Reads the page from a Wikipedia query response; null for missing or disambiguation pages. */
internal fun parseWikipediaPage(json: String): WikiPage? {
    val pages = JSONObject(json).optJSONObject("query")?.optJSONArray("pages") ?: return null
    val page = pages.optJSONObject(0) ?: return null
    if (page.optBoolean("missing", false) || page.optBoolean("invalid", false)) return null
    val pageProps = page.optJSONObject("pageprops")
    if (pageProps == null || pageProps.has("disambiguation")) return null
    val wikidataId = pageProps.optNullableString("wikibase_item")?.takeIf { it.isNotBlank() } ?: return null
    val title = page.optNullableString("title")?.takeIf { it.isNotBlank() } ?: return null
    return WikiPage(
        title = title,
        imageUrl = page.optJSONObject("thumbnail")?.optNullableString("source")?.takeIf { it.startsWith("https://") },
        description = page.optNullableString("description")?.takeIf { it.isNotBlank() },
        wikidataId = wikidataId
    )
}

/** True when a Wikidata wbgetclaims response says the entity is an instance of human (Q5). */
internal fun isHumanEntity(json: String): Boolean {
    val claims = JSONObject(json).optJSONObject("claims")?.optJSONArray("P31") ?: return false
    for (index in 0 until claims.length()) {
        val id = claims.optJSONObject(index)
            ?.optJSONObject("mainsnak")
            ?.optJSONObject("datavalue")
            ?.optJSONObject("value")
            ?.optNullableString("id")
        if (id == "Q5") return true
    }
    return false
}
