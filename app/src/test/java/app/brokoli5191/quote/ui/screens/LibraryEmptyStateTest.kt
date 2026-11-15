package app.brokoli5191.quote.ui.screens

import app.brokoli5191.quote.data.QuoteSourceMode
import app.brokoli5191.quote.ui.localizeUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LibraryEmptyStateTest {
    @Test
    fun noCommunityQuotesOnlyInCommunityModeWithoutAnyCommunityQuote() {
        assertEquals(
            LibraryEmptyReason.NoCommunityQuotes,
            libraryEmptyReason(QuoteSourceMode.COMMUNITY, hasCommunityQuotes = false, categories = setOf("Community"))
        )
        assertEquals(
            LibraryEmptyReason.NoCommunityQuotes,
            libraryEmptyReason(QuoteSourceMode.COMMUNITY, hasCommunityQuotes = false, categories = setOf("Wisdom"))
        )
    }

    @Test
    fun unmatchedSearchOrFilterIsNoMatchesWhenCommunityQuotesExist() {
        assertEquals(
            LibraryEmptyReason.NoMatches,
            libraryEmptyReason(QuoteSourceMode.COMMUNITY, hasCommunityQuotes = true, categories = emptySet())
        )
        assertEquals(
            LibraryEmptyReason.NoMatches,
            libraryEmptyReason(QuoteSourceMode.COMMUNITY, hasCommunityQuotes = true, categories = setOf("Local"))
        )
    }

    @Test
    fun otherSourceModesAndOwnQuotesNeverClaimMissingCommunityQuotes() {
        assertEquals(
            LibraryEmptyReason.NoMatches,
            libraryEmptyReason(QuoteSourceMode.ALL, hasCommunityQuotes = false, categories = setOf("Wisdom"))
        )
        assertEquals(
            LibraryEmptyReason.NoMatches,
            libraryEmptyReason(QuoteSourceMode.CURATED, hasCommunityQuotes = false, categories = emptySet())
        )
        assertEquals(
            LibraryEmptyReason.NoMatches,
            libraryEmptyReason(QuoteSourceMode.COMMUNITY, hasCommunityQuotes = false, categories = setOf("Local"))
        )
    }

    @Test
    fun newLibraryTextsAreTranslated() {
        listOf(
            "Try a different search or clear the filters.",
            "The community quotes could not be loaded. Check your connection and try again.",
            "Approved community quotes appear here once they have a German version, after the next sync.",
            "Not saved",
            "Add to favorites",
            "Remove from favorites"
        ).forEach { english ->
            assertNotEquals(english, localizeUi(english, "de"))
        }
    }
}
