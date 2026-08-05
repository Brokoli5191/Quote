package app.brokoli5191.quote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CommunityQuoteClientTest {
    @Test
    fun parseCommunityQuotePage_mapsServerFields() {
        val page = parseCommunityQuotePage(
            """{
              "revision": 4,
              "hasMore": false,
              "quotes": [{
                "id": "server-quote-1",
                "text": "A community thought.",
                "author": "A Reader",
                "category": "Wisdom",
                "tags": ["community", "wisdom"],
                "revision": 4,
                "publishedAt": 1785957000
              }],
              "deletedIds": ["server-quote-old"]
            }""".trimIndent()
        )

        assertEquals(4L, page.revision)
        assertFalse(page.hasMore)
        assertEquals(listOf("server-quote-old"), page.deletedIds)
        assertEquals(QuoteOrigin.COMMUNITY, page.quotes.single().origin)
        assertEquals("server-quote-1", page.quotes.single().serverId)
        assertEquals("community, wisdom", page.quotes.single().tags)
    }
}
