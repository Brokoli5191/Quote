package app.brokoli5191.quote.data

import org.junit.Assert.assertEquals
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
    }

    @Test
    fun parseSubmissionStatus_readsApprovedState() {
        assertEquals(
            QuoteSubmissionStatus.APPROVED,
            parseSubmissionStatus("""{"status":"approved","reviewedAt":1785958604}""")
        )
    }
}
