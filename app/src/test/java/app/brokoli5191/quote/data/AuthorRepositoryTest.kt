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
class AuthorRepositoryTest {
    @Test
    fun bundledAuthor_prefersGermanWikiForGermanUi() {
        val authors = parseBundledAuthors(
            """{"A.A. Milne": {"title": "A. A. Milne", "image": "https://upload.wikimedia.org/x.jpg",
                "en": "English writer", "de": "englischer Schriftsteller", "dewiki": "A. A. Milne (Autor)"}}"""
        )
        val author = authors.getValue("A.A. Milne")

        val german = author.toAuthorInfo("A.A. Milne", QuoteLanguage.GERMAN, allowImages = true)
        assertEquals("A. A. Milne (Autor)", german.wikiTitle)
        assertEquals("https://de.wikipedia.org/wiki/A._A._Milne_%28Autor%29", german.wikiUrl)
        assertEquals("englischer Schriftsteller", german.description)

        val english = author.toAuthorInfo("A.A. Milne", QuoteLanguage.ENGLISH, allowImages = false)
        assertEquals("https://en.wikipedia.org/wiki/A._A._Milne", english.wikiUrl)
        assertEquals("English writer", english.description)
        assertNull(english.imageUrl)
    }

    @Test
    fun parseWikipediaPage_acceptsPersonPage() {
        val page = parseWikipediaPage(
            """{"batchcomplete":true,"query":{"redirects":[{"from":"Einstein","to":"Albert Einstein"}],
              "pages":[{"pageid":736,"title":"Albert Einstein","description":"German-born physicist",
              "thumbnail":{"source":"https://upload.wikimedia.org/a.jpg","width":330,"height":440},
              "pageprops":{"wikibase_item":"Q937"}}]}}"""
        )!!
        assertEquals("Albert Einstein", page.title)
        assertEquals("Q937", page.wikidataId)
        assertEquals("https://upload.wikimedia.org/a.jpg", page.imageUrl)
    }

    @Test
    fun parseWikipediaPage_rejectsMissingAndDisambiguation() {
        assertNull(parseWikipediaPage("""{"query":{"pages":[{"title":"Nobody Known","missing":true}]}}"""))
        assertNull(
            parseWikipediaPage(
                """{"query":{"pages":[{"title":"John Smith","pageprops":{"disambiguation":"","wikibase_item":"Q1"}}]}}"""
            )
        )
    }

    @Test
    fun isHumanEntity_checksInstanceOfHuman() {
        assertTrue(
            isHumanEntity("""{"claims":{"P31":[{"mainsnak":{"datavalue":{"value":{"entity-type":"item","id":"Q5"}}}}]}}""")
        )
        assertFalse(
            isHumanEntity("""{"claims":{"P31":[{"mainsnak":{"datavalue":{"value":{"id":"Q95074"}}}}]}}""")
        )
        assertFalse(isHumanEntity("""{"claims":{}}"""))
    }
}
