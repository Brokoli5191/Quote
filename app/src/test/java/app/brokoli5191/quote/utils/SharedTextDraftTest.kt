package app.brokoli5191.quote.utils

import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.ui.QuoteDraft
import app.brokoli5191.quote.ui.formattedQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTextDraftTest {
    private fun draft(text: String) = QuoteSharing.draftFromSharedText(text)

    @Test
    fun splitsATrailingAuthorAndRemovesTheQuotationMarks() {
        assertEquals(QuoteDraft("Be yourself.", "Oscar Wilde"), draft("\"Be yourself.\" — Oscar Wilde"))
        assertEquals(QuoteDraft("Sei du selbst.", "Oscar Wilde"), draft("„Sei du selbst.“ – Oscar Wilde"))
        assertEquals(QuoteDraft("Liberté", "Voltaire"), draft("«Liberté» — Voltaire"))
        assertEquals(QuoteDraft("Stay hungry, stay foolish.", "Steve Jobs"), draft("Stay hungry, stay foolish.\n— Steve Jobs"))
        assertEquals(QuoteDraft("Be yourself.", "Oscar Wilde"), draft("Be yourself. - Oscar Wilde"))
    }

    @Test
    fun readsWhatQuoteItselfShares() {
        for (language in listOf(QuoteLanguage.ENGLISH, QuoteLanguage.GERMAN)) {
            assertEquals(
                QuoteDraft("Know thyself.", "Socrates"),
                draft(formattedQuote("Know thyself.", "Socrates", language))
            )
        }
    }

    @Test
    fun onlyTheLastDashStartsTheAuthor() {
        assertEquals(QuoteDraft("We came — and we stayed.", "Carl Sagan"), draft("We came — and we stayed. — Carl Sagan"))
        assertEquals(
            QuoteDraft("Line one line two", "Marcus Aurelius"),
            draft("Line one\nline two\n- Marcus Aurelius")
        )
    }

    @Test
    fun aDashInsideTheSentenceIsNotAnAuthor() {
        assertEquals(QuoteDraft("Life is short - live it", ""), draft("Life is short - live it"))
        assertEquals(QuoteDraft("It costs 5 - 10", ""), draft("It costs 5 - 10"))
        assertEquals(
            QuoteDraft("Something - This Is Far Too Long To Be The Name Of Anyone", ""),
            draft("Something - This Is Far Too Long To Be The Name Of Anyone")
        )
    }

    @Test
    fun quotedTextTakesAnyAuthorSpelling() {
        assertEquals(QuoteDraft("Stay hungry", "steve jobs"), draft("\"Stay hungry\" - steve jobs"))
    }

    @Test
    fun placeholderAuthorsAreLeftEmpty() {
        assertEquals(QuoteDraft("Ok.", ""), draft("\"Ok.\" — Unknown"))
        assertEquals(QuoteDraft("Ok.", ""), draft("„Ok.“ — Unbekannt"))
    }

    @Test
    fun dropsATrailingLinkAndKeepsPlainText() {
        assertEquals(QuoteDraft("Hello there.", ""), draft("“Hello there.” https://example.com/page#:~:text=Hello"))
        assertEquals(QuoteDraft("Just a thought", ""), draft("  Just a thought  "))
    }

    @Test
    fun emptyTextGivesNoDraft() {
        assertNull(draft("   "))
        assertNull(draft("\"\""))
    }
}
