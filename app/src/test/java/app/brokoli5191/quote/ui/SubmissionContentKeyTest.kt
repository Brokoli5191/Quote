package app.brokoli5191.quote.ui

import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteOrigin
import app.brokoli5191.quote.data.QuoteSubmissionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmissionContentKeyTest {
    private fun own(text: String, author: String, status: String) = QuoteEntity(
        text = text,
        author = author,
        category = "Life",
        isUserAdded = true,
        origin = QuoteOrigin.PERSONAL,
        submissionStatus = status,
        submissionId = "s-1"
    )

    @Test
    fun keyIgnoresWhatSavingAndTheServerNormalizeAway() {
        val key = submissionContentKey("Be kind.", "Rumi")
        // Wrapping quotation marks are removed when the quote is stored.
        assertEquals(key, submissionContentKey("\"Be kind.\"", "Rumi"))
        // The server drops invisible characters, collapses any whitespace and compares NFKC-lowercased.
        assertEquals(key, submissionContentKey("Be  kind.​", "RUMI"))
        assertEquals(submissionContentKey("ﬁne", "Rumi"), submissionContentKey("fine", "Rumi"))
        // Quotation marks around the author are dropped when stored.
        assertEquals(key, submissionContentKey("Be kind.", "\"Rumi\""))
    }

    @Test
    fun keyChangesWithTheWordingOrTheAuthor() {
        val key = submissionContentKey("Be kind.", "Rumi")
        assertNotEquals(key, submissionContentKey("Be kinder.", "Rumi"))
        assertNotEquals(key, submissionContentKey("Be kind.", "Hafez"))
    }

    @Test
    fun anEditThatNormalizationUndoesKeepsTheReview() {
        val rejected = own("Be kind.", "Rumi", QuoteSubmissionStatus.REJECTED)
        assertTrue(rejected.editKeepsReview("\"Be kind.\"", "Rumi"))
        assertTrue(rejected.editKeepsReview("  Be  kind. ", " rumi "))
        assertFalse(rejected.editKeepsReview("Be kind!", "Rumi"))
        assertFalse(rejected.editKeepsReview("Be kind.", "Hafez"))
        // Pending, approved and never submitted quotes have no review to keep.
        assertFalse(own("Be kind.", "Rumi", QuoteSubmissionStatus.APPROVED).editKeepsReview("Be kind.", "Rumi"))
        assertFalse(own("Be kind.", "Rumi", QuoteSubmissionStatus.NOT_SUBMITTED).editKeepsReview("Be kind.", "Rumi"))
    }

    @Test
    fun aBlankAuthorKeepsAPlaceholderAuthor() {
        val removed = own("Be kind.", "Anonymous", QuoteSubmissionStatus.REMOVED)
        assertTrue(removed.editKeepsReview("Be kind.", ""))
        // The display copy shows the placeholder as "Unknown"; a blank field keeps it there too.
        assertTrue(removed.copy(author = "Unknown").editKeepsReview("Be kind.", " "))
        // A blank field over a real name means the author is no longer known: a new submission.
        assertFalse(own("Be kind.", "Rumi", QuoteSubmissionStatus.REJECTED).editKeepsReview("Be kind.", ""))
    }
}
