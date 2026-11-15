package app.brokoli5191.quote.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.brokoli5191.quote.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    private class FakeDao(vararg rows: AuthorInfoEntity) : AuthorInfoDao {
        val stored = rows.associateBy { it.name }.toMutableMap()
        override suspend fun get(name: String) = stored[name]
        override suspend fun put(info: AuthorInfoEntity) {
            stored[info.name] = info
        }
        override suspend fun deleteOlderThan(before: Long) = Unit
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val expired = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000

    @Test
    fun bundledAuthor_germanFallsBackToEnglishDescription() {
        val author = parseBundledAuthors("""{"X": {"title": "X", "en": "English writer"}}""").getValue("X")
        val german = author.toAuthorInfo("X", QuoteLanguage.GERMAN, allowImages = true)
        assertEquals("English writer", german.description)
        assertEquals("https://en.wikipedia.org/wiki/X", german.wikiUrl)
    }

    @Test
    fun bundledAuthorsWithoutAGermanArticle_linkEnglishWikipediaInGerman() {
        val json = context.resources.openRawResource(R.raw.author_wiki).bufferedReader().use { it.readText() }
        val withoutGerman = parseBundledAuthors(json).filterValues { it.dewiki == null }
        assertTrue(withoutGerman.isNotEmpty())
        withoutGerman.forEach { (name, author) ->
            val info = author.toAuthorInfo(name, QuoteLanguage.GERMAN, allowImages = false)
            assertTrue(name, info.wikiUrl!!.startsWith("https://en.wikipedia.org/wiki/"))
            assertNotNull(name, info.description)
        }
    }

    @Test
    fun expiredCachedRow_isReturnedWhenRefreshFails() = runBlocking {
        val dao = FakeDao(AuthorInfoEntity("Zed Nobody", "Zed Nobody", "https://upload.wikimedia.org/z.jpg", "Writer", expired))
        val repo = AuthorRepository(context, dao) { null }
        val info = repo.authorInfo("Zed Nobody", QuoteLanguage.ENGLISH, allowNetwork = true)!!
        assertEquals("Writer", info.description)
        assertEquals("https://upload.wikimedia.org/z.jpg", info.imageUrl)
    }

    @Test
    fun imagesOff_usesCacheOnlyAndDropsImage() = runBlocking {
        val dao = FakeDao(AuthorInfoEntity("Zed Nobody", "Zed Nobody", "https://upload.wikimedia.org/z.jpg", "Writer", expired))
        val repo = AuthorRepository(context, dao) { error("network must not be used") }
        val info = repo.authorInfo("Zed Nobody", QuoteLanguage.ENGLISH, allowNetwork = false)!!
        assertEquals("Writer", info.description)
        assertNull(info.imageUrl)
        assertNull(repo.authorInfo("Other Nobody", QuoteLanguage.ENGLISH, allowNetwork = false))
    }

    @Test
    fun runtimeLookup_germanPrefersGermanWikipediaThenEnglish() = runBlocking {
        fun page(title: String, desc: String) =
            """{"query":{"pages":[{"title":"$title","description":"$desc","pageprops":{"wikibase_item":"Q1"}}]}}"""
        val human = """{"claims":{"P31":[{"mainsnak":{"datavalue":{"value":{"id":"Q5"}}}}]}}"""
        val repo = AuthorRepository(context, FakeDao()) { url ->
            when {
                url.contains("wikidata.org") -> human
                url.startsWith("https://de.") -> """{"query":{"pages":[{"title":"Zed Nobody","missing":true}]}}"""
                else -> page("Zed Nobody", "English writer")
            }
        }
        val info = repo.authorInfo("Zed Nobody", QuoteLanguage.GERMAN, allowNetwork = true)!!
        assertEquals("English writer", info.description)
        assertEquals("https://en.wikipedia.org/wiki/Zed_Nobody", info.wikiUrl)

        val deRepo = AuthorRepository(context, FakeDao()) { url ->
            when {
                url.contains("wikidata.org") -> human
                url.startsWith("https://de.") -> page("Zed Nobody (Autor)", "deutscher Schriftsteller")
                else -> page("Zed Nobody", "English writer")
            }
        }
        val german = deRepo.authorInfo("Zed Nobody", QuoteLanguage.GERMAN, allowNetwork = true)!!
        assertEquals("deutscher Schriftsteller", german.description)
        assertEquals("https://de.wikipedia.org/wiki/Zed_Nobody_%28Autor%29", german.wikiUrl)
    }
}
