package app.brokoli5191.quote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteLocalizationTest {
    private val englishOriginal = QuoteEntity(
        text = "So many books, so little time.",
        textDe = "So viele Bücher, so wenig Zeit.",
        author = "Frank Zappa",
        category = "Books"
    )
    private val germanOriginal = QuoteEntity(
        text = "Ein eigener Gedanke.",
        textEn = "A thought of my own.",
        author = "Ich",
        category = "Life",
        language = QuoteLanguage.GERMAN,
        origin = QuoteOrigin.COMMUNITY
    )

    @Test
    fun englishOriginal_inGerman_showsTranslationAndKeepsOriginal() {
        val german = englishOriginal.localized(QuoteLanguage.GERMAN)
        assertEquals("So viele Bücher, so wenig Zeit.", german.text)
        assertEquals("So many books, so little time.", german.alternateText())
        assertEquals(QuoteLanguage.ENGLISH, german.alternateLanguage())
    }

    @Test
    fun englishOriginal_inEnglish_offersGerman() {
        val english = englishOriginal.localized(QuoteLanguage.ENGLISH)
        assertSame(englishOriginal, english)
        assertEquals("So viele Bücher, so wenig Zeit.", english.alternateText())
        assertEquals(QuoteLanguage.GERMAN, english.alternateLanguage())
    }

    @Test
    fun germanOriginal_worksBothWays() {
        assertTrue(germanOriginal.isAvailableIn(QuoteLanguage.ENGLISH))
        assertTrue(germanOriginal.isAvailableIn(QuoteLanguage.GERMAN))

        val english = germanOriginal.localized(QuoteLanguage.ENGLISH)
        assertEquals("A thought of my own.", english.text)
        assertEquals("Ein eigener Gedanke.", english.alternateText())
        assertEquals(QuoteLanguage.GERMAN, english.alternateLanguage())

        val german = germanOriginal.localized(QuoteLanguage.GERMAN)
        assertEquals("Ein eigener Gedanke.", german.text)
        assertEquals("A thought of my own.", german.alternateText())
        assertEquals(QuoteLanguage.ENGLISH, german.alternateLanguage())
    }

    @Test
    fun untranslatedQuote_hasNoAlternate() {
        val englishOnly = QuoteEntity(text = "English only", author = "A", category = "Life")
        assertFalse(englishOnly.isAvailableIn(QuoteLanguage.GERMAN))
        assertNull(englishOnly.localized(QuoteLanguage.GERMAN).alternateText())
        assertNull(englishOnly.alternateLanguage())

        val germanOnly = germanOriginal.copy(textEn = null)
        assertFalse(germanOnly.isAvailableIn(QuoteLanguage.ENGLISH))
        assertEquals("Ein eigener Gedanke.", germanOnly.localized(QuoteLanguage.ENGLISH).text)
        assertNull(germanOnly.localized(QuoteLanguage.ENGLISH).alternateText())
    }

    @Test
    fun localizedTwice_isStable() {
        val roundTrip = englishOriginal.localized(QuoteLanguage.GERMAN).localized(QuoteLanguage.ENGLISH)
        assertEquals(englishOriginal.text, roundTrip.text)
        assertEquals("So viele Bücher, so wenig Zeit.", roundTrip.alternateText())
    }

    @Test
    fun hasUnknownAuthor_coversBlankAnonymousAndPlaceholders() {
        assertTrue(englishOriginal.copy(author = "").hasUnknownAuthor)
        assertTrue(englishOriginal.copy(author = "Unknown").hasUnknownAuthor)
        assertTrue(englishOriginal.copy(author = "Unbekannt").hasUnknownAuthor)
        assertTrue(englishOriginal.copy(isAnonymous = true).hasUnknownAuthor)
        assertFalse(englishOriginal.hasUnknownAuthor)
    }

    @Test
    fun localizedUnknownAuthor_isDisplayOnly() {
        val raw = englishOriginal.copy(author = "Unknown")
        assertEquals("Unbekannt", raw.localized(QuoteLanguage.GERMAN).author)
        assertEquals("Unknown", raw.author)
    }
}
