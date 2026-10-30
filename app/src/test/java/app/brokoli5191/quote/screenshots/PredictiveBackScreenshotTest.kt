package app.brokoli5191.quote.screenshots

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brokoli5191.quote.MainContent
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.ui.localizeUi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real predictive back gesture through the activity's OnBackPressedDispatcher and
 * records the screen mid-gesture: the Daily screen has to peek in behind the page being left.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE, sdk = [36])
class PredictiveBackScreenshotTest(configId: String) {
    private val config = ShotConfig.parse(configId)

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
    fun libraryPeeksDaily() = peeksDaily("Library", "Library")

    @Test
    fun savedPeeksDaily() = peeksDaily("Saved", "Your Collection")

    @Test
    fun settingsPeeksDaily() = peeksDaily("Settings", "Settings")

    @Test
    fun lowPerformanceModeHasNoPeek() {
        val app = showMain(prefs = mapOf("low_performance_mode" to true))
        app.viewModel.selectTab("Library")
        compose.settle()
        val dailyText = app.viewModel.dailyQuote.value!!.text
        val before = nodesWithText(dailyText).count()

        startBackGesture()
        assertEquals("low performance mode must not compose a peek", before, nodesWithText(dailyText).count())
        cancelBackGesture()
    }

    @Test
    fun libraryFilterClearHasNoPeek() {
        val app = showMain()
        app.viewModel.selectTab("Library")
        app.viewModel.setSearchQuery(if (config.language == QuoteLanguage.GERMAN) "Leben" else "life")
        compose.awaitCondition { app.viewModel.filteredQuotes.value.isNotEmpty() }
        val dailyText = app.viewModel.dailyQuote.value!!.text
        val before = nodesWithText(dailyText).count()

        startBackGesture()
        assertEquals("clearing the search must not peek Daily", before, nodesWithText(dailyText).count())
        compose.capture("predictive_back", "library_filter_clear", config)

        cancelBackGesture()
        assertEquals("Library", app.viewModel.selectedTab.value)
        assertTrue(app.viewModel.searchQuery.value.isNotBlank())
    }

    @Test
    fun completingGestureShowsDaily() {
        val app = showMain()
        app.viewModel.selectTab("Settings")
        compose.settle()

        val dailyText = app.viewModel.dailyQuote.value!!.text

        startBackGesture()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        // Frame by frame: Daily stays on screen while the peek is swapped for the real tab.
        repeat(60) { frame ->
            compose.mainClock.advanceTimeByFrame()
            assertTrue("Daily must stay visible (frame $frame)", displayedCount(nodesWithText(dailyText)) >= 1)
        }
        compose.mainClock.autoAdvance = true
        compose.settle()
        assertEquals("Daily", app.viewModel.selectedTab.value)
        // Only the real tab remains; the peek is gone.
        assertEquals(1, nodesWithText(dailyText).count())
        assertTrue(displayedCount(nodesWithText(dailyText)) == 1)
    }

    @Test
    fun completingDeveloperBackKeepsTabVisible() {
        val app = showMain()
        app.viewModel.selectTab("Library")
        app.viewModel.openDevScreen()
        compose.settle()
        val title = localizeUi("Library", config.language)

        startBackGesture()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        repeat(60) { frame ->
            compose.mainClock.advanceTimeByFrame()
            assertTrue("Library must stay visible (frame $frame)", displayedCount(nodesWithText(title)) >= 1)
        }
        compose.mainClock.autoAdvance = true
        compose.settle()
        assertTrue(!app.viewModel.showDevScreen.value)
        assertEquals("Library", app.viewModel.selectedTab.value)
    }

    @Test
    fun newQuoteBackRevealsCurrentTab() {
        val app = showMain()
        app.viewModel.selectTab("Saved")
        app.viewModel.openNewQuoteScreen()
        compose.settle()
        val title = localizeUi("Your Collection", config.language)
        assertTrue(nodesWithText(title).count() >= 1)

        startBackGesture()
        assertTrue("Saved stays composed beneath New Quote", displayedCount(nodesWithText(title)) >= 1)
        compose.capture("predictive_back", "new_quote_over_saved", config)

        cancelBackGesture()
        assertTrue(app.viewModel.showNewQuoteScreen.value)
        assertEquals("Saved", app.viewModel.selectedTab.value)
    }

    @Test
    fun developerBackRevealsCurrentTab() {
        val app = showMain()
        app.viewModel.selectTab("Library")
        app.viewModel.openDevScreen()
        compose.settle()

        startBackGesture()
        compose.capture("predictive_back", "developer_over_library", config)

        cancelBackGesture()
        assertTrue(app.viewModel.showDevScreen.value)
        assertEquals("Library", app.viewModel.selectedTab.value)
    }

    private fun peeksDaily(tab: String, tabTitle: String) {
        val app = showMain()
        app.viewModel.selectTab(tab)
        compose.settle()
        val dailyText = app.viewModel.dailyQuote.value!!.text
        val today = localizeUi("Today", config.language)
        val title = localizeUi(tabTitle, config.language)
        val dailyBefore = nodesWithText(dailyText).count()
        val todayBefore = nodesWithText(today).count()
        val titleBefore = nodesWithText(title).count()

        startBackGesture()
        // The peek is the real Daily screen: its quote and title are added (and on screen).
        val dailyDuring = nodesWithText(dailyText)
        assertEquals(dailyBefore + 1, dailyDuring.count())
        assertTrue(displayedCount(dailyDuring) >= 1)
        assertEquals(todayBefore + 1, nodesWithText(today).count())
        assertEquals(tab, app.viewModel.selectedTab.value)
        compose.capture("predictive_back", "${tab.lowercase()}_mid_gesture", config)

        cancelBackGesture()
        assertEquals(tab, app.viewModel.selectedTab.value)
        assertEquals(dailyBefore, nodesWithText(dailyText).count())
        assertEquals(titleBefore, nodesWithText(title).count())
        assertTrue(displayedCount(nodesWithText(title)) >= 1)
    }

    private fun showMain(prefs: Map<String, Any> = emptyMap()): ScreenshotApp {
        val app = ScreenshotApp(config = config, prefs = prefs, dailyQuoteText = FakeQuotes.MARCUS).also { app = it }
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
        // Same fallback as DailyScreenshotTest: the init load is occasionally never resumed here.
        if (!compose.tryAwait(3_000) { app.viewModel.dailyQuote.value != null }) app.publishDailyQuote()
        compose.awaitCondition { app.viewModel.dailyQuote.value != null }
        compose.awaitCondition { app.viewModel.authorInfo("Marcus Aurelius").value != null }
        return app
    }

    private fun startBackGesture() {
        val dispatcher = compose.activity.onBackPressedDispatcher
        val width = compose.activity.window.decorView.width.toFloat()
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 900f, 0f, BackEventCompat.EDGE_LEFT))
        }
        compose.settle()
        compose.runOnUiThread {
            dispatcher.dispatchOnBackProgressed(BackEventCompat(width * 0.5f, 900f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        compose.settle()
    }

    private fun cancelBackGesture() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        // The handler's cleanup waits a few frames (delay), which only the test clock can pass.
        compose.mainClock.advanceTimeBy(500)
        compose.settle()
    }

    private fun nodesWithText(text: String): SemanticsNodeInteractionCollection =
        compose.onAllNodesWithText(text, useUnmergedTree = true)

    private fun SemanticsNodeInteractionCollection.count(): Int = fetchSemanticsNodes().size

    private fun displayedCount(nodes: SemanticsNodeInteractionCollection): Int =
        (0 until nodes.count()).count { nodes[it].isDisplayed() }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = listOf(arrayOf<Any>("en_dark"), arrayOf<Any>("de_light"))
    }
}
