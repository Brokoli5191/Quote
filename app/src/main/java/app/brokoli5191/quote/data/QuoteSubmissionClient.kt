package app.brokoli5191.quote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val SUBMISSION_URL =
    "https://quote.cowsay.win/api/submissions"

/**
 * Stable submission error codes. The UI maps them to localized text; most match the
 * `code` field of the backend's error bodies.
 */
object SubmissionError {
    const val NETWORK = "network"
    const val RATE_LIMITED = "rate_limited"
    const val DUPLICATE = "duplicate"
    const val VALIDATION = "validation"
    const val TOO_LARGE = "too_large"
    const val SERVER = "server_error"
    const val ALREADY_REVIEWED = "already_reviewed"
    const val INVALID_REQUEST = "invalid_request"
    const val NOT_FOUND = "not_found"
    const val UNKNOWN = "unknown"

    /** Maps a backend `code` (or, for old backends without one, the HTTP status) to a code above. */
    fun fromResponse(httpStatus: Int, code: String?): String = when (code) {
        RATE_LIMITED, DUPLICATE, VALIDATION, TOO_LARGE, ALREADY_REVIEWED, INVALID_REQUEST, NOT_FOUND -> code
        SERVER, "method_not_allowed", "forbidden" -> SERVER
        else -> when (httpStatus) {
            400, 415 -> VALIDATION
            404 -> NOT_FOUND
            409 -> DUPLICATE
            413 -> TOO_LARGE
            429 -> RATE_LIMITED
            in 500..599 -> SERVER
            else -> UNKNOWN
        }
    }

    /** English fallback text, for logs or a UI that has no mapping for a code yet. */
    fun defaultMessage(code: String): String = when (code) {
        NETWORK -> "Could not reach the submission service. Check your connection and try again."
        RATE_LIMITED -> "Submission limit reached. Please try again tomorrow."
        DUPLICATE -> "This quote has already been submitted."
        VALIDATION -> "This quote can't be submitted. Check its length, author and tags."
        TOO_LARGE -> "This quote is too long to submit."
        ALREADY_REVIEWED -> "This submission has already been reviewed."
        SERVER -> "The submission service is having problems. Please try again later."
        else -> "Submission failed. Please try again."
    }
}

sealed interface QuoteSubmissionResult {
    /** [status] is "pending" for a new submission; a repeated one reports its current state. */
    data class Success(
        val submissionId: String,
        val status: String = QuoteSubmissionStatus.PENDING,
        val reviewNote: String? = null,
        val communityQuoteId: String? = null,
        val duplicate: Boolean = false
    ) : QuoteSubmissionResult

    /** [code] is one of the [SubmissionError] constants; [message] is the server text (may be English). */
    data class Error(val code: String, val message: String = SubmissionError.defaultMessage(code)) : QuoteSubmissionResult
}

sealed interface WithdrawResult {
    /** The pending submission was deleted on the server. */
    data object Success : WithdrawResult
    /** The server knows no such submission for this installation id (already gone, or another id). */
    data object NotFound : WithdrawResult
    data class Error(val code: String) : WithdrawResult
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
            readTimeout = 15_000
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
            parseSubmitResponse(responseCode, responseText)
        } catch (_: Exception) {
            QuoteSubmissionResult.Error(SubmissionError.NETWORK)
        } finally {
            connection.disconnect()
        }
    }

    /** Current server state of a submission. A 404 (withdrawn or deleted) reads as [QuoteSubmissionStatus.REMOVED]. */
    suspend fun status(submissionId: String, installationId: String): Result<SubmissionStatusInfo> =
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
                    when (connection.responseCode) {
                        HttpURLConnection.HTTP_OK ->
                            parseSubmissionStatusInfo(connection.inputStream.bufferedReader().use { it.readText() })
                        HttpURLConnection.HTTP_NOT_FOUND ->
                            SubmissionStatusInfo(QuoteSubmissionStatus.REMOVED, notFound = true)
                        else -> throw IllegalStateException("Status service returned ${connection.responseCode}")
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }

    /**
     * Withdraws a pending submission. 409 maps to [SubmissionError.ALREADY_REVIEWED]. A 404 counts as
     * [WithdrawResult.NotFound] only with the backend's "not_found" code: an old backend without the
     * route answers a bare 404, and the submission is then still in review.
     */
    suspend fun withdraw(submissionId: String, installationId: String): WithdrawResult =
        withContext(Dispatchers.IO) {
            try {
                val connection = (URL("$SUBMISSION_URL/$submissionId").openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("X-Installation-Id", installationId)
                }
                try {
                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_NO_CONTENT || responseCode == HttpURLConnection.HTTP_OK) {
                        WithdrawResult.Success
                    } else {
                        val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                        parseWithdrawError(responseCode, parseErrorCode(body))
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                WithdrawResult.Error(SubmissionError.NETWORK)
            }
        }
}

internal fun parseSubmitResponse(responseCode: Int, responseText: String): QuoteSubmissionResult {
    val response = runCatching { JSONObject(responseText) }.getOrNull()
    if (responseCode in 200..299) {
        val submissionId = response?.optNullableString("id").orEmpty()
        if (submissionId.isBlank()) return QuoteSubmissionResult.Error(SubmissionError.SERVER)
        return QuoteSubmissionResult.Success(
            submissionId = submissionId,
            status = parseStatusValue(response?.optNullableString("status")) ?: QuoteSubmissionStatus.PENDING,
            reviewNote = response?.optNullableString("reviewNote"),
            communityQuoteId = response?.optNullableString("communityQuoteId"),
            duplicate = response?.optBoolean("duplicate", false) ?: false
        )
    }
    val code = SubmissionError.fromResponse(responseCode, response?.optNullableString("code"))
    return QuoteSubmissionResult.Error(
        code,
        response?.optNullableString("error")?.takeIf { it.isNotBlank() } ?: SubmissionError.defaultMessage(code)
    )
}

internal fun parseWithdrawError(responseCode: Int, code: String?): WithdrawResult = when {
    responseCode == HttpURLConnection.HTTP_NOT_FOUND && code == SubmissionError.NOT_FOUND -> WithdrawResult.NotFound
    responseCode == HttpURLConnection.HTTP_NOT_FOUND -> WithdrawResult.Error(SubmissionError.SERVER)
    else -> WithdrawResult.Error(SubmissionError.fromResponse(responseCode, code))
}

private fun parseErrorCode(body: String): String? =
    runCatching { JSONObject(body).optNullableString("code") }.getOrNull()

private fun parseStatusValue(value: String?): String? = when (value) {
    QuoteSubmissionStatus.PENDING,
    QuoteSubmissionStatus.APPROVED,
    QuoteSubmissionStatus.REJECTED,
    QuoteSubmissionStatus.REMOVED -> value
    "duplicate" -> QuoteSubmissionStatus.REJECTED
    else -> null
}

internal fun parseSubmissionStatus(json: String): String = parseSubmissionStatusInfo(json).status

internal fun parseSubmissionStatusInfo(json: String): SubmissionStatusInfo {
    val root = JSONObject(json)
    val status = parseStatusValue(root.getString("status"))
        ?: throw IllegalArgumentException("Unknown submission status")
    return SubmissionStatusInfo(
        status = status,
        reviewNote = root.optNullableString("reviewNote")?.takeIf { it.isNotBlank() },
        communityQuoteId = root.optNullableString("communityQuoteId")?.takeIf { it.isNotBlank() },
        // Every v2 answer carries the submission id.
        legacy = !root.has("id")
    )
}

/** Returns the string value of [key], or null when it is missing or JSON null. */
internal fun JSONObject.optNullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key)

private fun QuoteEntity.submissionTags(): List<String> = tags.split(",")
    .map { it.trim() }
    .filter { it.isNotEmpty() && it.length <= 30 }
    .distinctBy { it.lowercase() }
    .take(8)

/**
 * Checks a personal quote against the backend's validation rules before sending it.
 * Returns [SubmissionError.VALIDATION] when the server would reject it, null when it can be sent.
 */
fun QuoteEntity.submissionValidationError(): String? {
    val cleanText = text.trim()
    return when {
        cleanText.length !in 3..500 -> SubmissionError.VALIDATION
        author.trim().length > 100 -> SubmissionError.VALIDATION
        category.trim().length !in 1..50 -> SubmissionError.VALIDATION
        else -> null
    }
}

/** Whether the UI should offer "Submit for review" (or "Resubmit") for this quote. */
fun QuoteEntity.canSubmitForReview(): Boolean =
    isUserAdded &&
        submissionStatus in setOf(
            QuoteSubmissionStatus.NOT_SUBMITTED,
            QuoteSubmissionStatus.REJECTED,
            QuoteSubmissionStatus.REMOVED
        ) &&
        submissionValidationError() == null

internal fun createSubmissionBody(
    quote: QuoteEntity,
    installationId: String,
    appVersion: String
): JSONObject = JSONObject().apply {
    put("quoteText", quote.text)
    put("author", quote.author)
    put("category", quote.category)
    put("tags", JSONArray(quote.submissionTags()))
    put("installationId", installationId)
    put("appVersion", appVersion)
    put("language", if (quote.language == QuoteLanguage.GERMAN) QuoteLanguage.GERMAN else QuoteLanguage.ENGLISH)
}
