package app.brokoli5191.quote.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteSearchTest {
    private val quote = QuoteEntity(
        text = "So many books, so little time.",
        textDe = "So viele Bücher, so wenig Zeit.",
        author = "Charlotte Brontë",
        category = "Books",
        tags = "books, self-love"
    )

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(quoteMatchesQuery(quote, "", QuoteLanguage.ENGLISH))
        assertTrue(quoteMatchesQuery(quote, "   ", QuoteLanguage.ENGLISH))
    }

    @Test
    fun queryIsTrimmedAndComparedWithoutCaseOrAccents() {
        // Keyboards add a space after a suggestion.
        assertTrue(quoteMatchesQuery(quote, "bronte ", QuoteLanguage.ENGLISH))
        assertTrue(quoteMatchesQuery(quote, "BRONTË", QuoteLanguage.ENGLISH))
        assertTrue(quoteMatchesQuery(quote.copy(author = "Søren Kierkegaard"), "soren", QuoteLanguage.ENGLISH))
        assertTrue(quoteMatchesQuery(quote.copy(text = "Die Straße"), "strasse", QuoteLanguage.GERMAN))
        assertTrue(quoteMatchesQuery(quote.copy(text = "It’s fine"), "it's", QuoteLanguage.ENGLISH))
    }

    @Test
    fun matchesBothLanguageVersions() {
        assertTrue(quoteMatchesQuery(quote, "bucher", QuoteLanguage.ENGLISH))
        val germanCopy = quote.localized(QuoteLanguage.GERMAN)
        assertTrue(quoteMatchesQuery(germanCopy, "little time", QuoteLanguage.GERMAN))
    }

    @Test
    fun matchesTagSlugsAndOnlyTheLabelsOfTheAppLanguage() {
        assertTrue(quoteMatchesQuery(quote, "self love", QuoteLanguage.ENGLISH))
        assertTrue(quoteMatchesQuery(quote, "self-love", QuoteLanguage.GERMAN))
        assertTrue(quoteMatchesQuery(quote, "Selbstliebe", QuoteLanguage.GERMAN))
        assertFalse(quoteMatchesQuery(quote, "Selbstliebe", QuoteLanguage.ENGLISH))
    }

    @Test
    fun unknownAuthorsMatchTheUnknownLabel() {
        val anonymous = QuoteEntity(text = "A reflection", author = "", category = "Reflections", isAnonymous = true)
        assertTrue(quoteMatchesQuery(anonymous, "unknown", QuoteLanguage.ENGLISH))
        assertTrue(quoteMatchesQuery(anonymous, "Unbekannt", QuoteLanguage.GERMAN))
        assertFalse(quoteMatchesQuery(anonymous, "Unbekannt", QuoteLanguage.ENGLISH))
    }

    @Test
    fun misattributionTagsMatchOnlyWhenAskedFor() {
        val tagged = quote.copy(tags = "misattributed-to-mark-twain")
        assertFalse(quoteMatchesQuery(tagged, "twain", QuoteLanguage.ENGLISH))
        assertTrue(quoteMatchesQuery(tagged, "misattributed", QuoteLanguage.ENGLISH))
    }
}
