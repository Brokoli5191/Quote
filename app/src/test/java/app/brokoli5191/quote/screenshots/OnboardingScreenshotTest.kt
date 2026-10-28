package app.brokoli5191.quote.screenshots

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import app.brokoli5191.quote.ui.screens.OnboardingScreen
import org.junit.Test
import org.robolectric.ParameterizedRobolectricTestRunner

class OnboardingScreenshotTest(configId: String) : ScreenshotTestBase(configId) {

    @Test
    fun everyPage() {
        val app = launch(prefs = mapOf("onboarding_completed" to false, "daily_reminder_enabled" to false))
        compose.setContent { AppFrame(app.viewModel) { OnboardingScreen(viewModel = app.viewModel) } }
        val pages = listOf("welcome", "language", "appearance", "sources", "reminder", "authors", "done")
        pages.forEachIndexed { index, page ->
            if (page == "authors") {
                compose.awaitCondition { app.viewModel.authorInfo("Marcus Aurelius").value != null }
            }
            capture("onboarding", "${index + 1}_$page")
            if (index < pages.lastIndex) {
                compose.onNode(hasText(text("Next")) and hasClickAction()).performClick()
                compose.settle()
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ShotConfig.parameters()
    }
}
