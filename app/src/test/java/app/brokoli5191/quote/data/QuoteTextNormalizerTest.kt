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
    fun spacesOnlyOpeningCurlyMarks() {
        assertEquals("“Why?” “Because.”", normalizeQuoteText("“Why?”“Because.”"))
        // In German „…“ the “ closes, so it stays next to the punctuation.
        assertEquals("Ich sage ihm: „Real.“", normalizeQuoteText("Ich sage ihm: „Real.“"))
        assertEquals("„Warum?“ „Darum.“", normalizeQuoteText("„Warum?“ „Darum.“"))
        assertEquals("„Ja,“ sagte er.", normalizeQuoteText("„Ja,“ sagte er."))
        assertEquals("Er sagte: „Komm!“ und ging.", normalizeQuoteText("Er sagte: „Komm!“ und ging."))
    }

    @Test
    fun removesTheSpaceEarlierVersionsPutBeforeAGermanClosingMark() {
        assertEquals("Ich sage ihm: „Real.“", normalizeQuoteText("Ich sage ihm: „Real. “"))
        assertEquals("„Warum?“ „Darum.“", normalizeQuoteText("„Warum? “ „Darum. “"))
    }

    @Test
    fun isIdempotent() {
        listOf(
            "He said “hello",
            "You’re dangerous,” he says. “Why?” “Because",
            "\"Hi?\"She said",
            "„Guten Tag“, sagte er.",
            "„Du liebst mich. Real oder nicht real?“ Ich sage ihm: „Real.“",
            "“Why?”“Because.”"
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
