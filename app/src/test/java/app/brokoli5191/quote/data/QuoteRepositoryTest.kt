package app.brokoli5191.quote.data

import org.junit.Assert.assertEquals
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
}
