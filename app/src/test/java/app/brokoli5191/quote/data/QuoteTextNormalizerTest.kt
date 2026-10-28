package app.brokoli5191.quote.data

import org.junit.Assert.assertEquals
import org.junit.Test

// Expected values match cleanText in tools/quote_seed_rules.mjs.
class QuoteTextNormalizerTest {
    @Test
    fun stripsOnlyAPairThatWrapsTheWholeText() {
        assertEquals("Be yourself; everyone else is already taken.", normalizeQuoteText("\"Be yourself; everyone else is already taken.\""))
        assertEquals("Be yourself.", normalizeQuoteText("“Be yourself.”"))
        assertEquals("Outer “inner” text", normalizeQuoteText("“Outer “inner” text”"))
    }

    @Test
    fun keepsInnerClosingMarks() {
        assertEquals("He said “hello” to me", normalizeQuoteText("He said “hello” to me"))
        assertEquals("Ends with \"quote\"", normalizeQuoteText("Ends with \"quote\""))
        assertEquals("The word \"love\" is overused", normalizeQuoteText("The word \"love\" is overused"))
        assertEquals("„Guten Tag“, sagte er.", normalizeQuoteText("„Guten Tag“, sagte er."))
        assertEquals("\"\"Double wrapped\"\"", normalizeQuoteText("\"\"Double wrapped\"\""))
    }

    @Test
    fun balancesAnUnmatchedOpeningMark() {
        assertEquals("He said “hello”", normalizeQuoteText("He said “hello"))
        assertEquals("Open only", normalizeQuoteText("\"Open only"))
    }

    @Test
    fun leavesInchMarksAlone() {
        assertEquals("A 12\" ruler", normalizeQuoteText("A 12\" ruler"))
    }

    @Test
    fun addsSpaceAfterClosingStraightQuote() {
        assertEquals("\"Hi?\" She said", normalizeQuoteText("\"Hi?\"She said"))
    }

    @Test
    fun isIdempotent() {
        listOf(
            "He said “hello",
            "You’re dangerous,” he says. “Why?” “Because",
            "\"Hi?\"She said",
            "„Guten Tag“, sagte er."
        ).forEach { input ->
            val once = normalizeQuoteText(input)
            assertEquals(once, normalizeQuoteText(once))
        }
    }

    @Test
    fun quoteMatchKey_ignoresQuoteMarksCaseAndPunctuation() {
        assertEquals(quoteMatchKey("He said “hello"), quoteMatchKey("he said \"Hello\"."))
    }
}
