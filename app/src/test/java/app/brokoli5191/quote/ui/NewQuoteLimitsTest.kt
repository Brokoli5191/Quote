package app.brokoli5191.quote.ui

import app.brokoli5191.quote.data.SubmissionLimits
import app.brokoli5191.quote.data.storedQuoteTextLength
import app.brokoli5191.quote.data.submissionLength
import app.brokoli5191.quote.ui.screens.truncateToLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NewQuoteLimitsTest {
    @Test
    fun textWithinTheLimitIsKept() {
        val text = "a".repeat(SubmissionLimits.MAX_TEXT_LENGTH)
        assertSame(text, truncateToLimit(text, SubmissionLimits.MAX_TEXT_LENGTH, ::storedQuoteTextLength))
    }

    @Test
    fun pastedTextIsCutAtTheLimit() {
        val cut = truncateToLimit("b".repeat(900), SubmissionLimits.MAX_TEXT_LENGTH, ::storedQuoteTextLength)
        assertEquals(SubmissionLimits.MAX_TEXT_LENGTH, cut.length)
    }

    @Test
    fun emojiCountOnceAndAreNeverSplit() {
        val cut = truncateToLimit("😀".repeat(150), SubmissionLimits.MAX_AUTHOR_LENGTH, ::submissionLength)
        assertEquals(SubmissionLimits.MAX_AUTHOR_LENGTH, submissionLength(cut))
        assertEquals(SubmissionLimits.MAX_AUTHOR_LENGTH * 2, cut.length)
    }
}
