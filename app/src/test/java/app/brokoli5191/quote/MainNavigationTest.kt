package app.brokoli5191.quote

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brokoli5191.quote.screenshots.AppFrame
import app.brokoli5191.quote.screenshots.FakeQuotes
import app.brokoli5191.quote.screenshots.SCREENSHOT_DEVICE
import app.brokoli5191.quote.screenshots.ScreenshotApp
import app.brokoli5191.quote.screenshots.ScreenshotEnvironmentRule
import app.brokoli5191.quote.screenshots.ShotConfig
import app.brokoli5191.quote.screenshots.awaitCondition
import app.brokoli5191.quote.screenshots.settle
import app.brokoli5191.quote.ui.localizeUi
import app.brokoli5191.quote.utils.AppLinks
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Back without a predictive gesture (button navigation, Android 13 and older), leaving New Quote
 * through the bottom bar and opening it again after an entry point closed it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE, sdk = [36])
class MainNavigationTest {
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

    @Test
    fun plainBackLeavesATabAtOnceWithTheTabTransition() {
        val app = showMain()
        app.viewModel.selectTab("Library")
        compose.settle()
        val dailyText = app.viewModel.dailyQuote.value!!.text
        val libraryNodes = libraryContent().count()
        assertTrue(libraryNodes >= 1)

        compose.mainClock.autoAdvance = false
        plainBack()
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        assertEquals("back must not wait for a gesture animation", "Daily", app.viewModel.selectedTab.value)
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        // The normal tab transition runs: Library is still leaving while Daily comes in.
        assertEquals(libraryNodes, libraryContent().count())
        assertTrue(compose.onAllNodesWithText(dailyText, useUnmergedTree = true).count() >= 1)

        compose.mainClock.autoAdvance = true
        compose.settle()
        assertEquals(0, libraryContent().count())
    }

    @Test
    fun plainBackClearsTheLibrarySearchAtOnce() {
        val app = showMain()
        app.viewModel.selectTab("Library")
        app.viewModel.setSearchQuery("life")
        compose.awaitCondition { app.viewModel.hasBackStack.value }

        compose.mainClock.autoAdvance = false
        plainBack()
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        assertEquals("", app.viewModel.searchQuery.value)
        assertEquals("Library", app.viewModel.selectedTab.value)
        compose.mainClock.autoAdvance = true
        compose.settle()
    }

    @Test
    fun plainBackClosesTheDeveloperScreen() {
        val app = showMain()
        app.viewModel.selectTab("Library")
        app.viewModel.openDevScreen()
        compose.settle()

        plainBack()
        compose.mainClock.advanceTimeBy(1_000)
        compose.settle()
        assertFalse(app.viewModel.showDevScreen.value)
        assertEquals("Library", app.viewModel.selectedTab.value)
    }

    @Test
    fun tabTapWithADraftAsksBeforeDiscarding() {
        val app = showMain()
        app.viewModel.selectTab("Saved")
        app.viewModel.openNewQuoteScreen()
        compose.settle()
        // What New Quote reports once something is typed.
        compose.runOnUiThread { app.viewModel.setNewQuoteHasDraft(true) }
        compose.settle()

        bottomTab("Library").performClick()
        compose.settle()
        compose.onNodeWithText(text("Discard draft?")).assertIsDisplayed()
        assertTrue(app.viewModel.showNewQuoteScreen.value)
        assertEquals("Saved", app.viewModel.selectedTab.value)

        compose.onNodeWithText(text("Keep editing")).performClick()
        compose.settle()
        assertEquals(0, compose.onAllNodesWithText(text("Discard draft?")).count())
        assertTrue(app.viewModel.showNewQuoteScreen.value)
        assertEquals("Saved", app.viewModel.selectedTab.value)

        bottomTab("Library").performClick()
        compose.settle()
        compose.onNodeWithText(text("Discard")).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.settle()
        assertFalse(app.viewModel.showNewQuoteScreen.value)
        assertEquals("Library", app.viewModel.selectedTab.value)
    }

    @Test
    fun tabTapWithoutADraftLeavesNewQuote() {
        val app = showMain()
        app.viewModel.selectTab("Saved")
        app.viewModel.openNewQuoteScreen()
        compose.settle()

        bottomTab("Library").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.settle()
        assertEquals(0, compose.onAllNodesWithText(text("Discard draft?")).count())
        assertFalse(app.viewModel.showNewQuoteScreen.value)
        assertEquals("Library", app.viewModel.selectedTab.value)
    }

    @Test
    fun newQuoteSlidesInAgainAfterAnEntryPointClosedIt() {
        val app = showMain()
        app.viewModel.selectTab("Saved")
        app.viewModel.openNewQuoteScreen()
        compose.settle()
        // A widget tap while the form is empty closes it without its exit animation.
        compose.runOnUiThread { app.viewModel.openDestination(AppLinks.DEST_DAILY) }
        compose.settle()
        assertFalse(app.viewModel.showNewQuoteScreen.value)

        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { app.viewModel.openNewQuoteScreen() }
        val width = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().size.width
        // The first frame that has placed the form (its fields report 0 before that).
        var left = 0f
        repeat(20) {
            if (left == 0f) {
                compose.mainClock.advanceTimeByFrame()
                left = compose.onAllNodesWithText(text("Quote or affirmation"), useUnmergedTree = true)
                    .fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.left ?: 0f
            }
        }
        assertTrue("New Quote is still sliding in from the right", left > width / 2f)
        compose.mainClock.autoAdvance = true
        compose.settle()
    }

    private fun text(english: String) = localizeUi(english, config.language)

    private fun plainBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    }

    // The filter button exists only on the Library screen.
    private fun libraryContent(): SemanticsNodeInteractionCollection =
        compose.onAllNodesWithContentDescription(text("Filter Categories"), useUnmergedTree = true)

    private fun bottomTab(tab: String): SemanticsNodeInteraction =
        compose.onNode(hasText(text(tab)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    private fun SemanticsNodeInteractionCollection.count(): Int = fetchSemanticsNodes().size

    private fun showMain(): ScreenshotApp {
        val app = ScreenshotApp(config = config, dailyQuoteText = FakeQuotes.MARCUS).also { app = it }
        compose.setContent {
            val lowPerformanceMode by app.viewModel.lowPerformanceMode.collectAsStateWithLifecycle()
            AppFrame(app.viewModel) {
                MainContent(
                    viewModel = app.viewModel,
                    lowPerformanceMode = lowPerformanceMode,
                    blurNavigationSurfaces = false
                )
            }
        }
        // Waits for the ViewModel's own load by running the main looper. Loading the quote with a
        // blocking call on the main thread instead can deadlock while that load holds the lock.
        compose.awaitCondition {
            shadowOf(Looper.getMainLooper()).idle()
            app.viewModel.dailyQuote.value != null
        }
        return app
    }
}
