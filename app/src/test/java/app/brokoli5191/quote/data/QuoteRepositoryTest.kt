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
            "You’re dangerous,” he says. “Why?” “Because you make me believe in the impossible",
            normalizeQuoteText("Youâ€™re dangerous,â€� he says. â€œWhy?â€� â€œBecause you make me believe in the impossible")
        )
    }

    @Test
    fun dailyQuoteIndex_isStablePerInstallButDifferentAcrossInstalls() {
        val date = "2026-08-05"
        assertEquals(dailyQuoteIndex(1234L, date, 1175), dailyQuoteIndex(1234L, date, 1175))
        assertNotEquals(dailyQuoteIndex(1234L, date, 1175), dailyQuoteIndex(9876L, date, 1175))
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
}
