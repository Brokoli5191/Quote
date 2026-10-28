package app.brokoli5191.quote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        val quote = page.quotes.single()
        assertEquals(QuoteOrigin.COMMUNITY, quote.origin)
        assertEquals("server-quote-1", quote.serverId)
        assertEquals("community, wisdom", quote.tags)
        // Old backend: no language, translation or submissionId.
        assertEquals(QuoteLanguage.ENGLISH, quote.language)
        assertNull(quote.textDe)
        assertNull(quote.textEn)
        assertNull(quote.submissionId)
    }

    @Test
    fun parseCommunityQuotePage_readsV2Fields() {
        val page = parseCommunityQuotePage(
            """{
              "revision": 9,
              "currentRevision": 9,
              "hasMore": false,
              "quotes": [
                {"id": "en-1", "text": "Keep going.", "author": "A", "category": "life", "tags": [],
                 "language": "en", "translation": "Mach weiter.", "submissionId": "sub-1", "revision": 8, "publishedAt": 1},
                {"id": "de-1", "text": "Bleib neugierig.", "author": "B", "category": "Made Up", "tags": ["x"],
                 "language": "de", "translation": null, "submissionId": "sub-2", "revision": 9, "publishedAt": 2},
                {"id": "de-2", "text": "Gute Nacht.", "author": "C", "category": "Love", "tags": [],
                 "language": "de", "translation": "Good night.", "revision": 9, "publishedAt": 2}
              ],
              "deletedIds": []
            }""".trimIndent()
        )

        val (english, german, translated) = page.quotes
        assertEquals("Mach weiter.", english.textDe)
        assertNull(english.textEn)
        assertEquals("sub-1", english.submissionId)
        assertEquals("Life", english.category)

        assertEquals(QuoteLanguage.GERMAN, german.language)
        assertNull(german.textEn)
        assertNull(german.textDe)
        assertEquals("Uncategorized", german.category)

        assertEquals("Good night.", translated.textEn)
        assertNull(translated.textDe)
        assertNull(translated.submissionId)
    }

    @Test
    fun communityCategory_onlyAcceptsKnownCategories() {
        assertEquals("Wisdom", communityCategory("wisdom"))
        assertEquals("Inspirational", communityCategory("Inspiration"))
        assertEquals("Uncategorized", communityCategory("<script>"))
        assertEquals("Uncategorized", communityCategory(null))
    }
}
