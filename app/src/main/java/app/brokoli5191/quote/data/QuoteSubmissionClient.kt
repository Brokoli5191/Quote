package app.brokoli5191.quote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val SUBMISSION_URL =
    "https://quote-submission-api.brokoli5191.workers.dev/api/submissions"

sealed interface QuoteSubmissionResult {
    data class Success(val submissionId: String) : QuoteSubmissionResult
    data class Error(val message: String) : QuoteSubmissionResult
}

class QuoteSubmissionClient {
    suspend fun submit(
        quote: QuoteEntity,
        installationId: String,
        appVersion: String
    ): QuoteSubmissionResult = withContext(Dispatchers.IO) {
        val connection = (URL(SUBMISSION_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(createSubmissionBody(quote, installationId, appVersion).toString())
            }

            val responseCode = connection.responseCode
            val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            val response = runCatching { JSONObject(responseText) }.getOrNull()

            if (responseCode == HttpURLConnection.HTTP_CREATED) {
                val submissionId = response?.optString("id").orEmpty()
                if (submissionId.isNotBlank()) {
                    QuoteSubmissionResult.Success(submissionId)
                } else {
                    QuoteSubmissionResult.Error("The server returned an invalid response.")
                }
            } else {
                QuoteSubmissionResult.Error(
                    response?.optString("error")?.takeIf { it.isNotBlank() }
                        ?: "Submission failed. Please try again."
                )
            }
        } catch (_: Exception) {
            QuoteSubmissionResult.Error("Could not reach the submission service. Check your connection and try again.")
        } finally {
            connection.disconnect()
        }
    }

    suspend fun status(submissionId: String, installationId: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL("$SUBMISSION_URL/$submissionId/status").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("X-Installation-Id", installationId)
                }
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        throw IllegalStateException("Status service returned ${connection.responseCode}")
                    }
                    parseSubmissionStatus(connection.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    connection.disconnect()
                }
            }
        }
}

internal fun parseSubmissionStatus(json: String): String = JSONObject(json).getString("status")

internal fun createSubmissionBody(
    quote: QuoteEntity,
    installationId: String,
    appVersion: String
): JSONObject = JSONObject().apply {
    put("quoteText", quote.text)
    put("author", quote.author)
    put("category", quote.category)
    put(
        "tags",
        JSONArray(
            quote.tags.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .take(8)
        )
    )
    put("installationId", installationId)
    put("appVersion", appVersion)
}
