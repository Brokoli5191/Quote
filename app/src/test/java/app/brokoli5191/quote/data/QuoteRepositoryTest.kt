package app.brokoli5191.quote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteRepositoryTest {
    @Test
    fun normalizeAuthorName_removesSourcePunctuation() {
        assertEquals("J.K. Rowling", normalizeAuthorName("J.K. Rowling,"))
    }

    @Test
    fun normalizeAuthorName_repairsUtf8DecodedAsLatin1() {
        assertEquals("China Miéville", normalizeAuthorName("China MiÃ©ville,"))
        assertEquals("Søren Kierkegaard", normalizeAuthorName("Søren Kierkegaard"))
    }

    @Test
    fun normalizeAuthorName_repairsKnownDamagedArabicNames() {
        assertEquals(
            "Ahmed Khaled Towfik",
            normalizeAuthorName("Ø£Ø­Ù…Ø¯ Ø®Ø§ÙØ¯ ØªÙˆÙ�ÙŠÙ‚")
        )
    }

    @Test
    fun normalizeQuoteText_repairsMojibakeAndPreservesDialogue() {
        assertEquals(
            // The leading closer and the trailing opener are balanced instead of dropped.
            "“You’re dangerous,” he says. “Why?” “Because you make me believe in the impossible”",
            normalizeQuoteText("Youâ€™re dangerous,â€� he says. â€œWhy?â€� â€œBecause you make me believe in the impossible")
        )
    }

    @Test
    fun normalizeQuoteText_repairsTruncatedDashAndInvalidReplacementCharacter() {
        assertEquals(
            "Something— worth remembering",
            normalizeQuoteText("Somethingâ€\" � worth remembering")
        )
    }

    @Test
    fun normalizeQuoteText_repairsDoubleEncodedPunctuation() {
        assertEquals(
            "It’s true…",
            normalizeQuoteText("ItÃ¢â‚¬â„¢s trueÃ¢â‚¬Â¦")
        )
    }

    @Test
    fun dailyQuoteIndex_isStablePerInstallButDifferentAcrossInstalls() {
        val date = "2026-08-05"
        assertEquals(dailyQuoteIndex(1234L, date, 1175), dailyQuoteIndex(1234L, date, 1175))
        assertNotEquals(dailyQuoteIndex(1234L, date, 1175), dailyQuoteIndex(9876L, date, 1175))
    }

    @Test
    fun noRepeatCycle_coversEveryCandidateBeforeRepeating() {
        val candidates = setOf(1, 2, 3)
        assertEquals(emptySet<Int>(), currentNoRepeatCycle(emptyList(), candidates))
        assertEquals(setOf(1, 2), currentNoRepeatCycle(listOf(1, 2), candidates))
        // A finished cycle starts over, but never with the quote that closed it.
        assertEquals(setOf(3), currentNoRepeatCycle(listOf(1, 2, 3), candidates))
        assertEquals(setOf(2), currentNoRepeatCycle(listOf(1, 2, 3, 2), candidates))
        // Picks that are no longer candidates (other mode, deleted quote) do not count.
        assertEquals(setOf(1), currentNoRepeatCycle(listOf(7, 1, 9), candidates))
        assertEquals(emptySet<Int>(), currentNoRepeatCycle(listOf(5, 5), setOf(5)))
    }

    @Test
    fun mergeReseed_keepsIdsFavoritesAndRetiredFavorites() {
        val old = listOf(
            QuoteEntity(id = 10, text = "Alpha.", author = "Ann", category = "Life", isFavorite = true, savedDate = "5", timestamp = 1),
            QuoteEntity(id = 11, text = "Beta", author = "Bob Smiht", category = "Life", timestamp = 1),
            QuoteEntity(id = 12, text = "Gone", author = "Cy", category = "Life"),
            QuoteEntity(id = 13, text = "Retired", author = "Di", category = "Life", isFavorite = true, savedDate = "7", tags = "love, misattributed-to-x")
        )
        val fresh = listOf(
            QuoteEntity(text = "Alpha", author = "Ann", category = "Wisdom", textDe = "Alpha auf Deutsch", timestamp = 99),
            // The new seed corrected the author's spelling: matched by text alone.
            QuoteEntity(text = "Beta", author = "Bob Smith", category = "Life", timestamp = 99),
            QuoteEntity(text = "New", author = "Ed", category = "Life", timestamp = 99)
        )

        val merged = mergeReseed(old, fresh)

        assertEquals(listOf(10, 11, 0, 13), merged.map { it.id })
        assertTrue(merged[0].isFavorite)
        assertEquals("5", merged[0].savedDate)
        assertEquals("Wisdom", merged[0].category)
        assertEquals("Alpha auf Deutsch", merged[0].textDe)
        assertEquals(1L, merged[0].timestamp)
        assertEquals("Bob Smith", merged[1].author)
        assertFalse(merged[1].isFavorite)
        assertEquals(99L, merged[2].timestamp)
        assertEquals("love", merged[3].tags)
    }

    @Test
    fun mergeReseed_followsTextCorrectionsThatOnlyRestoreSpacesAndPunctuation() {
        val old = listOf(
            QuoteEntity(
                id = 20,
                text = "You only live twice:Once when you are bornAnd once when you look death in the face",
                author = "Ian Fleming",
                category = "Life",
                isFavorite = true,
                savedDate = "9",
                timestamp = 1
            ),
            QuoteEntity(id = 21, text = "Silence is so freaking loud", author = "Sarah Dessen", category = "Life", timestamp = 1),
            // Two old rows with the same letters: ambiguous, so neither is taken over by letters.
            QuoteEntity(id = 22, text = "Same words", author = "A", category = "Life"),
            QuoteEntity(id = 23, text = "Same, words", author = "B", category = "Life")
        )
        val fresh = listOf(
            QuoteEntity(
                text = "You only live twice: once when you are born, and once when you look death in the face.",
                author = "Ian Fleming",
                category = "Life",
                timestamp = 99
            ),
            QuoteEntity(text = "Silence is so freaking loud.", author = "Sarah Dessen", category = "Life", timestamp = 99),
            QuoteEntity(text = "Same. Words.", author = "C", category = "Life", timestamp = 99)
        )

        val merged = mergeReseed(old, fresh)

        assertEquals(listOf(20, 21, 0), merged.map { it.id })
        assertTrue(merged[0].isFavorite)
        assertEquals("9", merged[0].savedDate)
        assertEquals(fresh[0].text, merged[0].text)
        assertEquals(1L, merged[1].timestamp)
    }

    @Test
    fun sourceModes_excludePersonalQuotesFromAutomaticCollections() {
        val bundled = QuoteEntity(text = "Bundled", author = "Author", category = "Life")
        val community = QuoteEntity(
            text = "Community",
            author = "Author",
            category = "Life",
            origin = QuoteOrigin.COMMUNITY
        )
        val personal = QuoteEntity(
            text = "Personal",
            author = "Me",
            category = "Life",
            isUserAdded = true,
            origin = QuoteOrigin.PERSONAL
        )

        assertTrue(bundled.matchesSourceMode(QuoteSourceMode.ALL))
        assertTrue(community.matchesSourceMode(QuoteSourceMode.ALL))
        assertFalse(personal.matchesSourceMode(QuoteSourceMode.ALL))
        assertTrue(bundled.matchesSourceMode(QuoteSourceMode.CURATED))
        assertFalse(community.matchesSourceMode(QuoteSourceMode.CURATED))
        assertTrue(community.matchesSourceMode(QuoteSourceMode.COMMUNITY))
        assertFalse(bundled.matchesSourceMode(QuoteSourceMode.COMMUNITY))
    }

    @Test
    fun localizedQuotes_onlyExposeRealTranslations() {
        val translated = QuoteEntity(
            text = "Some things bloom only to fade.",
            textDe = "Manche Dinge blühen nur, um zu verblassen.",
            author = "",
            category = "Reflections",
            isAnonymous = true
        )
        val englishOnly = QuoteEntity(text = "English only", author = "Author", category = "Life")
        val germanPersonal = QuoteEntity(
            text = "Ein eigener Gedanke",
            author = "Ich",
            category = "Life",
            language = QuoteLanguage.GERMAN,
            isUserAdded = true,
            origin = QuoteOrigin.PERSONAL
        )

        assertTrue(translated.isAvailableIn(QuoteLanguage.ENGLISH))
        assertTrue(translated.isAvailableIn(QuoteLanguage.GERMAN))
        assertEquals("Manche Dinge blühen nur, um zu verblassen.", translated.localized(QuoteLanguage.GERMAN).text)
        assertFalse(englishOnly.isAvailableIn(QuoteLanguage.GERMAN))
        assertTrue(germanPersonal.isAvailableIn(QuoteLanguage.GERMAN))
        assertFalse(germanPersonal.isAvailableIn(QuoteLanguage.ENGLISH))
    }
}
