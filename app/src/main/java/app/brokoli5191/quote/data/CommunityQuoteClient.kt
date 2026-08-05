package app.brokoli5191.quote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val quoteArray = root.getJSONArray("quotes")
    val quotes = buildList {
        for (index in 0 until quoteArray.length()) {
            val item = quoteArray.getJSONObject(index)
            val tagsArray = item.getJSONArray("tags")
            val tags = buildList {
                for (tagIndex in 0 until tagsArray.length()) add(tagsArray.getString(tagIndex))
            }
            add(
                QuoteEntity(
                    text = item.getString("text"),
                    author = item.getString("author"),
                    category = item.getString("category"),
                    tags = tags.joinToString(", "),
                    timestamp = item.optLong("publishedAt", 0L) * 1000L,
                    origin = QuoteOrigin.COMMUNITY,
                    serverId = item.getString("id"),
                    serverRevision = item.getLong("revision")
                )
            )
        }
    }
    val deletedArray = root.getJSONArray("deletedIds")
    val deletedIds = buildList {
        for (index in 0 until deletedArray.length()) add(deletedArray.getString(index))
    }
    return CommunityQuotePage(
        revision = root.getLong("revision"),
        hasMore = root.getBoolean("hasMore"),
        quotes = quotes,
        deletedIds = deletedIds
    )
}
