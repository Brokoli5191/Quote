package app.brokoli5191.quote.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.ui.LocalAppLanguage
import app.brokoli5191.quote.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class AuthorSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private var dismissed = 0
    private var showMoreCalls = 0

    private fun showSheet(quoteCount: Int, language: String = QuoteLanguage.ENGLISH) {
        compose.setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalAppLanguage provides language) {
                    AuthorSheet(
                        name = "Seneca",
                        info = null,
                        imageUrl = null,
                        language = language,
                        onDismiss = { dismissed++ },
                        quoteCount = quoteCount,
                        onShowMoreQuotes = { showMoreCalls++ }
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun moreQuotesHidesTheSheetThenShowsThem() {
        showSheet(quoteCount = 3)

        compose.onNodeWithText("More quotes by Seneca (3)").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { showMoreCalls == 1 }
        assertEquals(1, dismissed)
    }

    @Test
    fun moreQuotesIsTranslated() {
        showSheet(quoteCount = 2, language = QuoteLanguage.GERMAN)

        compose.onNodeWithText("Mehr Quotes von Seneca (2)").assertIsDisplayed()
        compose.onNodeWithText("Auf Wikipedia lesen").assertIsDisplayed()
    }

    @Test
    fun aSingleQuoteOffersOnlyWikipedia() {
        showSheet(quoteCount = 1)

        assertTrue(compose.onAllNodesWithText("More quotes", substring = true).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Read on Wikipedia").assertIsDisplayed()
    }
}
