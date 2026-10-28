package app.brokoli5191.quote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val COMMUNITY_QUOTES_URL = "https://quote.cowsay.win/api/community/quotes"

data class CommunityQuotePage(
    val revision: Long,
    val hasMore: Boolean,
    val quotes: List<QuoteEntity>,
    val deletedIds: List<String>
)

class CommunityQuoteClient {
    suspend fun updates(after: Long): Result<CommunityQuotePage> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("$COMMUNITY_QUOTES_URL?after=$after").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("Community quote service returned ${connection.responseCode}")
                }
                parseCommunityQuotePage(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }
    }
}

internal fun parseCommunityQuotePage(json: String): CommunityQuotePage {
    val root = JSONObject(json)
    val quoteArray = root.optJSONArray("quotes") ?: JSONArray()
    val quotes = buildList {
        for (index in 0 until quoteArray.length()) {
            val item = quoteArray.optJSONObject(index) ?: continue
            parseCommunityQuote(item)?.let(::add)
        }
    }
    val deletedArray = root.optJSONArray("deletedIds") ?: JSONArray()
    val deletedIds = buildList {
        for (index in 0 until deletedArray.length()) {
            deletedArray.optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
    return CommunityQuotePage(
        revision = root.getLong("revision"),
        hasMore = root.optBoolean("hasMore", false),
        quotes = quotes,
        deletedIds = deletedIds
    )
}

// Fields added by the v2 backend (language, translation, submissionId) are optional so the
// app keeps working against an older deployment.
private fun parseCommunityQuote(item: JSONObject): QuoteEntity? {
    val id = item.optNullableString("id")?.takeIf { it.isNotBlank() } ?: return null
    val text = item.optNullableString("text")?.takeIf { it.isNotBlank() } ?: return null
    val tagsArray = item.optJSONArray("tags") ?: JSONArray()
    val tags = buildList {
        for (tagIndex in 0 until tagsArray.length()) {
            tagsArray.optString(tagIndex).trim().takeIf { it.isNotEmpty() }?.let(::add)
        }
    }
    val language = if (item.optNullableString("language") == QuoteLanguage.GERMAN) {
        QuoteLanguage.GERMAN
    } else {
        QuoteLanguage.ENGLISH
    }
    val translation = item.optNullableString("translation")?.trim()?.takeIf { it.isNotEmpty() }
    return QuoteEntity(
        text = text,
        author = item.optNullableString("author").orEmpty(),
        category = communityCategory(item.optNullableString("category")),
        tags = tags.joinToString(", "),
        timestamp = item.optLong("publishedAt", 0L) * 1000L,
        origin = QuoteOrigin.COMMUNITY,
        serverId = id,
        serverRevision = item.optLong("revision", 0L),
        language = language,
        textDe = translation.takeIf { language == QuoteLanguage.ENGLISH },
        textEn = translation.takeIf { language == QuoteLanguage.GERMAN },
        submissionId = item.optNullableString("submissionId")?.takeIf { it.isNotBlank() }
    )
}

/** Server categories are free text: only known categories are kept, anything else is "Uncategorized". */
internal fun communityCategory(value: String?): String {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.equals("Inspiration", ignoreCase = true)) return "Inspirational"
    return CategoryMapper.categories.firstOrNull { it.equals(trimmed, ignoreCase = true) } ?: "Uncategorized"
}
