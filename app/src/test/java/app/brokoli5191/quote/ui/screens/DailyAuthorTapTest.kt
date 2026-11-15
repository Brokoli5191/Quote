package app.brokoli5191.quote.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.brokoli5191.quote.screenshots.FakeQuotes
import app.brokoli5191.quote.screenshots.SCREENSHOT_DEVICE
import app.brokoli5191.quote.screenshots.ScreenshotApp
import app.brokoli5191.quote.screenshots.ScreenshotEnvironmentRule
import app.brokoli5191.quote.screenshots.ShotConfig
import app.brokoli5191.quote.screenshots.TabFrame
import app.brokoli5191.quote.screenshots.awaitCondition
import app.brokoli5191.quote.screenshots.settle
import app.brokoli5191.quote.screenshots.tryAwait
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Daily: a tap on the author's name opens the author sheet, as "Explore this author" does. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE, sdk = [36])
class DailyAuthorTapTest {
    private val config = ShotConfig.parse("en_dark")

    @get:Rule(order = 0)
    val environment = ScreenshotEnvironmentRule(config)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var app: ScreenshotApp? = null

    @After
    fun closeApp() {
        app?.close()
    }

    private fun showDaily(quoteText: String, showAnonymous: Boolean = false) {
        val app = ScreenshotApp(config = config, dailyQuoteText = quoteText, showAnonymous = showAnonymous).also { app = it }
        compose.setContent { TabFrame(app.viewModel, "Daily") }
        // As in DailyScreenshotTest: the ViewModel's own load is occasionally never resumed under Robolectric.
        if (!compose.tryAwait(3_000) { app.viewModel.dailyQuote.value != null }) app.publishDailyQuote()
        compose.awaitCondition { app.viewModel.dailyQuote.value != null }
    }

    @Test
    fun tappingTheAuthorsNameOpensTheAuthorSheet() {
        showDaily(FakeQuotes.MARCUS)
        assertEquals(0, compose.onAllNodesWithText("Read on Wikipedia").fetchSemanticsNodes().size)

        compose.onNode(hasText("Marcus Aurelius") and hasClickAction()).performClick()
        compose.settle()
        compose.onNodeWithText("Read on Wikipedia").assertIsDisplayed()
        // The card's name and the sheet's title.
        assertEquals(2, compose.onAllNodesWithText("Marcus Aurelius").fetchSemanticsNodes().size)
    }

    @Test
    fun anUnknownAuthorIsNoButton() {
        showDaily(FakeQuotes.ANONYMOUS, showAnonymous = true)
        compose.onNodeWithText("Unknown").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("Unknown") and hasClickAction()).fetchSemanticsNodes().size)
    }
}
