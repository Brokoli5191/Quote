package app.brokoli5191.quote.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.brokoli5191.quote.ui.screens.OnboardingScreen
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.ParameterizedRobolectricTestRunner

class OnboardingScreenshotTest(configId: String) : ScreenshotTestBase(configId) {

    @Test
    fun everyPage() {
        val app = launch(prefs = mapOf("onboarding_completed" to false, "daily_reminder_enabled" to false))
        compose.setContent { AppFrame(app.viewModel) { OnboardingScreen(viewModel = app.viewModel) } }
        PAGES.forEachIndexed { index, page ->
            if (page == "authors") {
                compose.awaitCondition { app.viewModel.authorInfo("Marcus Aurelius").value != null }
            }
            capture("onboarding", "${index + 1}_$page")
            if (index < PAGES.lastIndex) {
                compose.onNode(hasText(text("Next")) and hasClickAction()).performClick()
                compose.settle()
            }
        }
    }

    /**
     * Robolectric draws no system bars, so this run shrinks the screen by a typical status bar
     * (24dp) and three-button navigation bar (48dp), which a real 360x780dp phone loses to them.
     */
    @Test
    fun everyPageFitsWithSystemBars() {
        val app = launch(prefs = mapOf("onboarding_completed" to false, "daily_reminder_enabled" to false))
        compose.setContent {
            AppFrame(app.viewModel) {
                Box(Modifier.fillMaxSize().padding(top = 24.dp, bottom = 48.dp)) {
                    OnboardingScreen(viewModel = app.viewModel)
                }
            }
        }
        val overflowing = mutableListOf<String>()
        PAGES.forEachIndexed { index, page ->
            if (page == "authors") {
                compose.awaitCondition { app.viewModel.authorInfo("Marcus Aurelius").value != null }
            }
            val overflow = pageOverflow()
            if (overflow > 0f) overflowing += "'$page' by ${overflow}px"
            if (index < PAGES.lastIndex) {
                compose.onNode(hasText(text("Next")) and hasClickAction()).performClick()
                compose.settle()
            }
        }
        // Every page has to fit between the floating chrome without scrolling.
        assertTrue("Onboarding pages scroll in ${config.id}: $overflowing", overflowing.isEmpty())
    }

    /** How far the page on screen can scroll, in px. */
    private fun pageOverflow(): Float {
        val screenWidth = compose.onRoot().fetchSemanticsNode().size.width
        return compose
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .fetchSemanticsNodes()
            // Only the page on screen; the pager may keep a neighbour composed off to the side.
            .filter { it.boundsInRoot.left >= 0f && it.boundsInRoot.left < screenWidth }
            .maxOfOrNull { it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() } ?: 0f
    }

    companion object {
        private val PAGES = listOf("welcome", "language", "appearance", "sources", "reminder", "authors", "widget", "done")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ShotConfig.parameters()
    }
}
