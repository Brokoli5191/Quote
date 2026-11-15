package app.brokoli5191.quote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuoteSubmissionClientTest {
    @Test
    fun createSubmissionBody_normalizesAndLimitsTags() {
        val quote = QuoteEntity(
            text = "The obstacle is the way.",
            author = "Marcus Aurelius",
            category = "Stoicism",
            tags = " Wisdom, wisdom, courage, focus, life, growth, calm, action, extra",
            isUserAdded = true
        )

        val body = createSubmissionBody(
            quote = quote,
            installationId = "123e4567-e89b-42d3-a456-426614174000",
            appVersion = "1.3.2"
        )

        assertEquals("The obstacle is the way.", body.getString("quoteText"))
        assertEquals("Marcus Aurelius", body.getString("author"))
        assertEquals(8, body.getJSONArray("tags").length())
        assertEquals("Wisdom", body.getJSONArray("tags").getString(0))
        assertEquals("en", body.getString("language"))
    }

    @Test
    fun createSubmissionBody_sendsGermanLanguageAndDropsOverlongTags() {
        val body = createSubmissionBody(
            QuoteEntity(
                text = "Ein eigener Gedanke.",
                author = "Ich",
                category = "Life",
                tags = "kurz, ${"x".repeat(31)}",
                language = QuoteLanguage.GERMAN,
                isUserAdded = true
            ),
            installationId = "123e4567-e89b-42d3-a456-426614174000",
            appVersion = "2.0.0"
        )
        assertEquals("de", body.getString("language"))
        assertEquals(1, body.getJSONArray("tags").length())
    }

    @Test
    fun parseSubmissionStatus_readsApprovedState() {
        assertEquals(
            QuoteSubmissionStatus.APPROVED,
            parseSubmissionStatus("""{"status":"approved","reviewedAt":1785958604}""")
        )
    }

    @Test
    fun parseSubmissionStatusInfo_readsNoteAndCommunityId() {
        val info = parseSubmissionStatusInfo(
            """{"id":"s","status":"approved","reviewNote":"Nice","reviewedAt":1,"communityQuoteId":"c-1"}"""
        )
        assertEquals(SubmissionStatusInfo(QuoteSubmissionStatus.APPROVED, "Nice", "c-1"), info)

        val removed = parseSubmissionStatusInfo("""{"id":"s","status":"removed","reviewNote":null,"communityQuoteId":null}""")
        assertEquals(SubmissionStatusInfo(QuoteSubmissionStatus.REMOVED), removed)
    }

    @Test
    fun parseSubmissionStatusInfo_marksOldBackendAnswers() {
        val legacy = parseSubmissionStatusInfo("""{"status":"approved","reviewedAt":1,"communityQuoteId":"c-1"}""")
        assertTrue(legacy.legacy)
        assertFalse(parseSubmissionStatusInfo("""{"id":"s","status":"approved"}""").legacy)
    }

    @Test
    fun parseWithdrawError_bare404IsNotTreatedAsGone() {
        assertEquals(WithdrawResult.NotFound, parseWithdrawError(404, SubmissionError.NOT_FOUND))
        assertEquals(WithdrawResult.Error(SubmissionError.SERVER), parseWithdrawError(404, null))
        assertEquals(WithdrawResult.Error(SubmissionError.ALREADY_REVIEWED), parseWithdrawError(409, "already_reviewed"))
    }

    @Test
    fun parseSubmitResponse_newSubmission() {
        val result = parseSubmitResponse(201, """{"id":"abc","status":"pending"}""")
        assertEquals(QuoteSubmissionResult.Success("abc", QuoteSubmissionStatus.PENDING), result)
    }

    @Test
    fun parseSubmitResponse_ownDuplicateIsSuccessWithState() {
        val result = parseSubmitResponse(
            200,
            """{"id":"abc","status":"rejected","reviewNote":"Typo","communityQuoteId":null,"duplicate":true}"""
        ) as QuoteSubmissionResult.Success
        assertEquals("abc", result.submissionId)
        assertEquals(QuoteSubmissionStatus.REJECTED, result.status)
        assertEquals("Typo", result.reviewNote)
        assertNull(result.communityQuoteId)
        assertTrue(result.duplicate)
    }

    @Test
    fun parseSubmitResponse_mapsErrorCodes() {
        fun code(status: Int, body: String) = (parseSubmitResponse(status, body) as QuoteSubmissionResult.Error).code

        assertEquals(SubmissionError.DUPLICATE, code(409, """{"error":"Already","code":"duplicate"}"""))
        assertEquals(SubmissionError.RATE_LIMITED, code(429, """{"error":"Limit","code":"rate_limited"}"""))
        assertEquals(SubmissionError.VALIDATION, code(400, """{"error":"Bad","code":"validation"}"""))
        assertEquals(SubmissionError.TOO_LARGE, code(413, """{"error":"Big","code":"too_large"}"""))
        assertEquals(SubmissionError.SERVER, code(500, """{"error":"Oops","code":"server_error"}"""))
        // Old backend: only `error`, so the HTTP status decides.
        assertEquals(SubmissionError.DUPLICATE, code(409, """{"error":"This quote has already been submitted."}"""))
        assertEquals(SubmissionError.RATE_LIMITED, code(429, """{"error":"Limit"}"""))
        assertEquals(SubmissionError.SERVER, code(502, "<html>"))
        // A success without an id is a broken response.
        assertEquals(SubmissionError.SERVER, code(201, "{}"))
    }

    @Test
    fun submissionError_fromResponse_prefersCode() {
        assertEquals(SubmissionError.ALREADY_REVIEWED, SubmissionError.fromResponse(409, "already_reviewed"))
        assertEquals(SubmissionError.SERVER, SubmissionError.fromResponse(403, "forbidden"))
        assertEquals(SubmissionError.UNKNOWN, SubmissionError.fromResponse(418, null))
    }

    @Test
    fun canSubmitForReview_followsBackendRules() {
        val quote = QuoteEntity(text = "Valid quote", author = "Me", category = "Life", isUserAdded = true)
        assertTrue(quote.canSubmitForReview())
        assertFalse(quote.copy(text = "ab").canSubmitForReview())
        assertFalse(quote.copy(author = "x".repeat(101)).canSubmitForReview())
        assertFalse(quote.copy(submissionStatus = QuoteSubmissionStatus.PENDING).canSubmitForReview())
        assertTrue(quote.copy(submissionStatus = QuoteSubmissionStatus.REJECTED).canSubmitForReview())
        assertFalse(quote.copy(isUserAdded = false).canSubmitForReview())
    }

    @Test
    fun createSubmissionBody_sendsTheLanguageTheSubmitterChose() {
        // Written in English while the app was in German: the stored language is the UI language.
        val quote = QuoteEntity(
            text = "Stay hungry, stay foolish.",
            author = "Steve Jobs",
            category = "Life",
            isUserAdded = true,
            language = QuoteLanguage.GERMAN
        )
        val installationId = "123e4567-e89b-42d3-a456-426614174000"

        assertEquals("de", createSubmissionBody(quote, installationId, "2.1.0").getString("language"))
        assertEquals("en", createSubmissionBody(quote, installationId, "2.1.0", QuoteLanguage.ENGLISH).getString("language"))
        // Anything else falls back to English, the backend's default.
        assertEquals("en", createSubmissionBody(quote, installationId, "2.1.0", "fr").getString("language"))
    }

    @Test
    fun createSubmissionBody_countsTagLengthInCodePoints() {
        val body = createSubmissionBody(
            QuoteEntity(
                text = "Valid quote",
                author = "Me",
                category = "Life",
                isUserAdded = true,
                tags = "${"😀".repeat(30)}, ${"😀".repeat(31)}, \u200B"
            ),
            installationId = "123e4567-e89b-42d3-a456-426614174000",
            appVersion = "2.1.0"
        )
        val tags = body.getJSONArray("tags")
        assertEquals(1, tags.length())
        assertEquals("😀".repeat(30), tags.getString(0))
    }

    @Test
    fun submissionLength_countsCodePointsAfterTheServersCleanup() {
        assertEquals(2, submissionLength("a😀"))
        assertEquals(1, submissionLength("👍"))
        // Zero-width and control characters are dropped, whitespace runs collapse, ends are trimmed.
        assertEquals(3, submissionLength("  a\u200B \u0007\n\u00A0 b\uFEFF  "))
        assertEquals(0, submissionLength("\u200B\u2060"))
    }

    @Test
    fun submissionValidationError_matchesTheServersCodePointLimits() {
        val quote = QuoteEntity(text = "Valid quote", author = "Me", category = "Life", isUserAdded = true)
        // "a😀" is three UTF-16 units but two characters: the server (and SQLite) refuse it.
        assertEquals(SubmissionError.VALIDATION, quote.copy(text = "a😀").submissionValidationError())
        assertNull(quote.copy(text = "😀😀😀").submissionValidationError())
        assertNull(quote.copy(text = "😀".repeat(500)).submissionValidationError())
        assertEquals(SubmissionError.VALIDATION, quote.copy(text = "ab\u200B\u200B").submissionValidationError())
        assertNull(quote.copy(author = "😀".repeat(100)).submissionValidationError())
        assertEquals(SubmissionError.VALIDATION, quote.copy(author = "😀".repeat(101)).submissionValidationError())
        assertEquals(SubmissionError.VALIDATION, quote.copy(category = " ").submissionValidationError())
    }

    @Test
    fun storedQuoteTextLength_measuresTheTextAsItWillBeStored() {
        // Saving strips a pair of quotation marks around the whole text: "Hi" in marks is stored as Hi.
        assertEquals(2, storedQuoteTextLength("\"Hi\""))
        assertEquals(6, storedQuoteTextLength("  Hi  you "))
    }
}
